/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.http;

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Resource;
import io.karatelabs.common.ResourceType;
import io.karatelabs.markup.Markup;
import io.karatelabs.markup.MarkupConfig;
import io.karatelabs.markup.ResourceResolver;
import io.karatelabs.markup.HxDialect;

import java.util.function.Function;

/**
 * Main request handler implementing routing for static files, API endpoints, and HTML templates.
 * Implements Function&lt;HttpRequest, HttpResponse&gt; for use with HttpServer.
 * <p>
 * Routing logic:
 * <ol>
 *   <li>If path starts with static prefix (e.g., /pub/) → serve static file</li>
 *   <li>If path starts with API prefix (e.g., /api/) → execute JS file</li>
 *   <li>Otherwise → render HTML template</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>
 * ServerConfig config = new ServerConfig()
 *     .resourceRoot("classpath:web")
 *     .sessionStore(new InMemorySessionStore())
 *     .engineSupplier(Engine::new);
 *
 * ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
 * HttpServer server = HttpServer.start(8080, handler);
 * </pre>
 */
public class ServerRequestHandler implements Function<HttpRequest, HttpResponse> {

    private final ServerConfig config;
    private final ResourceResolver resolver;
    private final Markup markup;

    public ServerRequestHandler(ServerConfig config, ResourceResolver resolver) {
        this.config = config;
        this.resolver = resolver;
        // Initialize markup with HxDialect for HTMX support
        MarkupConfig markupConfig = new MarkupConfig();
        markupConfig.setResolver(resolver);
        markupConfig.setDevMode(config.isDevMode());
        markupConfig.setEngineSupplier(config.getEngineSupplier());
        this.markup = Markup.init(markupConfig, new HxDialect(markupConfig));
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        String path = request.getPath();

        try {
            // Process form/multipart body if present
            request.processBody();

            // 1. Static files - handle early, no session/engine needed
            if (config.isStaticPath(path)) {
                return handleStatic(request, response);
            }

            // Create context for this request
            ServerMarkupContext context = createContext(request, response);

            // Load existing session from cookie if present
            loadSession(request, context);

            // Load flash messages from previous request (if any)
            context.loadFlashFromSession();

            // CSRF validation for state-changing requests
            HttpResponse csrfError = validateCsrf(request, context);
            if (csrfError != null) {
                return csrfError;
            }

            // Call request interceptor if configured
            if (config.getRequestInterceptor() != null) {
                config.getRequestInterceptor().accept(request);
            }

            // 2. Create RequestCycle and delegate routing (API or HTML)
            ServerRequestCycle rc = ServerRequestCycle.init(config, context, resolver, markup);
            HttpResponse result = rc.handle();

            // Handle redirect if set
            if (context.hasRedirect()) {
                // Persist flash messages to session so they survive the redirect
                context.persistFlashToSession();
                result.setStatus(302);
                result.setHeader("Location", context.getRedirectPath());
                result.setBody("");
            }

            // Save session if modified
            saveSession(context, result);

            // Apply security headers for HTML responses
            if (SecurityHeaders.isHtmlResponse(result.getHeader("Content-Type"))) {
                SecurityHeaders.apply(result, config);
            }

            return result;

        } catch (RedirectException e) {
            response.setStatus(302);
            response.setHeader("Location", e.getLocation());
            return response;

        } catch (Exception e) {
            response.setStatus(500);
            response.setBody("Internal Server Error: " + e.getMessage());
            response.setHeader("Content-Type", "text/plain");
            return response;
        }
    }

    private ServerMarkupContext createContext(HttpRequest request, HttpResponse response) {
        ServerMarkupContext context = new ServerMarkupContext(request, response, config);
        context.setResourceResolver(path -> resolver.resolve(path, null));
        return context;
    }

    private void loadSession(HttpRequest request, ServerMarkupContext context) {
        if (!config.isSessionEnabled()) {
            return;
        }
        String sessionId = getSessionIdFromCookie(request);
        if (sessionId != null) {
            Session session = config.getSessionStore().get(sessionId);
            if (session != null) {
                context.setSession(session);
            }
        }
    }

    private String getSessionIdFromCookie(HttpRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        // Parse cookie header: "name1=value1; name2=value2"
        String cookieName = config.getSessionCookieName();
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring(cookieName.length() + 1);
            }
        }
        return null;
    }

    /**
     * Validate CSRF token for state-changing requests.
     *
     * @return null if valid or not required, HttpResponse with 403 if invalid
     */
    private HttpResponse validateCsrf(HttpRequest request, ServerMarkupContext context) {
        // Skip if CSRF protection is disabled
        if (!config.isCsrfEnabled()) {
            return null;
        }

        // Only validate state-changing methods
        String method = request.getMethod();
        if (!CsrfProtection.requiresValidation(method)) {
            return null;
        }

        // Skip if no session exists - nothing to protect from CSRF
        // This allows signin/signup pages to work without a session
        Session session = context.getSession();
        if (session == null) {
            return null;
        }

        // Validate the token
        if (!CsrfProtection.validate(request, session)) {
            HttpResponse response = new HttpResponse();
            response.setStatus(403);
            response.setBody("Forbidden: Invalid or missing CSRF token");
            response.setHeader("Content-Type", "text/plain");
            return response;
        }

        return null;
    }

    private void saveSession(ServerMarkupContext context, HttpResponse response) {
        Session session = context.getSession();
        if (session == null || session.isTemporary()) {
            return;
        }
        // Save to store
        config.getSessionStore().save(session);

        // Set cookie if new session
        String cookieValue = config.getSessionCookieName() + "=" + session.getId() + "; Path=/; HttpOnly";
        if (!config.isDevMode()) {
            cookieValue += "; Secure; SameSite=Strict";
        }
        response.setHeader("Set-Cookie", cookieValue);
    }

    private HttpResponse handleStatic(HttpRequest request, HttpResponse response) {
        String path = request.getPath();

        // Path traversal protection
        if (!PathSecurity.isSafe(path)) {
            return forbidden(response, "Invalid path");
        }

        // Use path without leading slash as resource path
        // e.g., /pub/app.js -> pub/app.js
        String resourcePath = path.startsWith("/") ? path.substring(1) : path;

        try {
            Resource resource = resolver.resolve(resourcePath, null);
            if (resource == null) {
                return notFound(response, path);
            }

            byte[] content = FileUtils.toBytes(resource.getStream());

            // Determine content type based on extension
            ResourceType resourceType = ResourceType.fromFileExtension(resourcePath);
            if (resourceType != null) {
                response.setBody(content, resourceType);
            } else {
                // Fallback for unknown types
                response.setBody(content, ResourceType.BINARY);
                response.setHeader("Content-Type", getContentType(resourcePath));
            }

            // Cache headers
            if (config.isDevMode()) {
                response.setHeader("Cache-Control", "no-cache, no-store");
            } else {
                response.setHeader("Cache-Control", "public, max-age=86400"); // 1 day
            }

            return response;

        } catch (Exception e) {
            return notFound(response, path);
        }
    }

    private HttpResponse notFound(HttpResponse response, String path) {
        response.setStatus(404);
        response.setBody("Not Found: " + path);
        response.setHeader("Content-Type", "text/plain");
        return response;
    }

    private HttpResponse forbidden(HttpResponse response, String message) {
        response.setStatus(403);
        response.setBody("Forbidden: " + message);
        response.setHeader("Content-Type", "text/plain");
        return response;
    }

    private String getContentType(String path) {
        ResourceType type = ResourceType.fromFileExtension(path);
        if (type != null) {
            return type.contentType;
        }
        // Fallback for common types
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // Getter for testing
    public ServerConfig getConfig() {
        return config;
    }

    /**
     * Exception used internally to signal a redirect.
     */
    public static class RedirectException extends RuntimeException {
        private final String location;

        public RedirectException(String location) {
            super("Redirect to: " + location);
            this.location = location;
        }

        public String getLocation() {
            return location;
        }
    }

}
