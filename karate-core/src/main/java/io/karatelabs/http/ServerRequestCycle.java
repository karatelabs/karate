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

import io.karatelabs.common.Resource;
import io.karatelabs.common.ResourceNotFoundException;
import io.karatelabs.js.Engine;
import io.karatelabs.markup.Markup;
import io.karatelabs.markup.ResourceResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-request lifecycle wrapper that provides isolated Engine instances.
 * Similar to v1 RequestCycle - sets up engine once, then branches for API or HTML.
 * <p>
 * Usage pattern:
 * <pre>
 * ServerRequestCycle cycle = ServerRequestCycle.init(config, context, resolver, markup);
 * return cycle.handle();
 * </pre>
 */
public class ServerRequestCycle {

    private static final ThreadLocal<ServerRequestCycle> THREAD_LOCAL = new ThreadLocal<>();

    private final Engine engine;
    private final HttpRequest request;
    private final HttpResponse response;
    private final ServerMarkupContext context;
    private final ServerConfig config;
    private final ResourceResolver resolver;
    private final Markup markup;

    public static ServerRequestCycle get() {
        return THREAD_LOCAL.get();
    }

    public static ServerRequestCycle init(ServerConfig config, ServerMarkupContext context,
                                          ResourceResolver resolver, Markup markup) {
        ServerRequestCycle rc = new ServerRequestCycle(config, context, resolver, markup);
        THREAD_LOCAL.set(rc);
        return rc;
    }

    private ServerRequestCycle(ServerConfig config, ServerMarkupContext context,
                               ResourceResolver resolver, Markup markup) {
        this.config = config;
        this.request = context.getRequest();
        this.response = context.getResponse();
        this.context = context;
        this.resolver = resolver;
        this.markup = markup;
        this.engine = createEngine();
        // Wire up session sync callback
        context.setOnSessionInit(this::bindSession);
    }

    private Engine createEngine() {
        // Create engine directly - don't use supplier here as it would be circular
        // (supplier points back to this cycle's engine via ThreadLocal)
        Engine engine = new Engine();
        // Inject global variables (can be overridden by request-specific vars)
        if (config.getGlobalVariables() != null) {
            config.getGlobalVariables().forEach(engine::put);
        }
        engine.put("request", request);
        engine.put("response", response);
        engine.put("context", context);
        if (context.getSession() != null) {
            engine.put("session", context.getSession());
        }
        return engine;
    }

    /**
     * Update the session binding after context.init() creates a new session.
     */
    public void bindSession(Session session) {
        engine.put("session", session);
    }

    /**
     * Main entry point - routes to API or HTML handler.
     */
    public HttpResponse handle() {
        try {
            String path = request.getPath();
            if (config.isApiPath(path)) {
                return handleApi();
            } else {
                return handleTemplate();
            }
        } catch (Exception e) {
            return handleError(e);
        } finally {
            close();
        }
    }

    private HttpResponse handleApi() {
        String path = request.getPath();

        // Path traversal protection
        if (!PathSecurity.isSafe(path)) {
            return forbidden("Invalid path");
        }

        // Convert API path to JS file path: /api/users -> api/users.js
        String jsPath = path.substring(1); // Remove leading /
        if (!jsPath.endsWith(".js")) {
            jsPath = jsPath + ".js";
        }

        try {
            Resource resource;
            try {
                resource = resolver.resolve(jsPath, null);
            } catch (RuntimeException e) {
                return notFound(path);
            }
            if (resource == null) {
                return notFound(path);
            }

            String jsCode = resource.getText();

            // Execute the JS with our per-request engine
            engine.eval(jsCode);

            // Set default content type if not set
            if (response.getHeader("Content-Type") == null) {
                Object body = response.getBody();
                if (body instanceof Map || body instanceof java.util.List) {
                    response.setHeader("Content-Type", "application/json");
                }
            }

            return response;

        } catch (Exception e) {
            return handleError(e);
        }
    }

    private HttpResponse handleTemplate() {
        String path = request.getPath();

        // Path traversal protection
        if (!PathSecurity.isSafe(path)) {
            return forbidden("Invalid path");
        }

        // Convert path to template: /signin -> signin.html, / -> index.html
        String templatePath = path.equals("/") ? "index.html" : path.substring(1);
        if (!templatePath.endsWith(".html")) {
            templatePath = templatePath + ".html";
        }

        try {
            // Set template name in context
            context.setTemplateName(templatePath);

            // Prepare variables for template - these get merged into MarkupTemplateContext
            Map<String, Object> vars = new HashMap<>();
            if (config.getGlobalVariables() != null) {
                vars.putAll(config.getGlobalVariables());
            }
            vars.put("request", request);
            vars.put("response", response);
            vars.put("context", context);
            if (context.getSession() != null) {
                vars.put("session", context.getSession());
            }

            // Render template
            String html = markup.processPath(templatePath, vars);

            // Check if template switched
            if (context.isSwitched()) {
                String newTemplate = context.getSwitchTemplate();
                context.setTemplateName(newTemplate);
                html = markup.processPath(newTemplate, vars);
            }

            response.setBody(html);
            response.setHeader("Content-Type", "text/html; charset=utf-8");

            return response;

        } catch (ResourceNotFoundException e) {
            return notFound(path);
        } catch (Exception e) {
            return handleError(e);
        }
    }

    private HttpResponse handleError(Exception e) {
        response.setStatus(500);

        // Try custom error template
        if (config.getErrorTemplate500() != null) {
            try {
                Map<String, Object> vars = new HashMap<>();
                vars.put("error", e.getMessage());
                vars.put("request", request);
                String html = markup.processPath(config.getErrorTemplate500(), vars);
                response.setBody(html);
                response.setHeader("Content-Type", "text/html; charset=utf-8");
                return response;
            } catch (Exception ignored) {
            }
        }

        // Fallback to simple text
        response.setBody("Internal Server Error: " + e.getMessage());
        response.setHeader("Content-Type", "text/plain");
        return response;
    }

    private HttpResponse notFound(String path) {
        response.setStatus(404);

        // Try custom 404 template
        if (config.getErrorTemplate404() != null) {
            try {
                Map<String, Object> vars = new HashMap<>();
                vars.put("path", path);
                String html = markup.processPath(config.getErrorTemplate404(), vars);
                response.setBody(html);
                response.setHeader("Content-Type", "text/html; charset=utf-8");
                return response;
            } catch (Exception ignored) {
            }
        }

        // Fallback to simple text
        response.setBody("Not Found: " + path);
        response.setHeader("Content-Type", "text/plain");
        return response;
    }

    private HttpResponse forbidden(String message) {
        response.setStatus(403);
        response.setBody("Forbidden: " + message);
        response.setHeader("Content-Type", "text/plain");
        return response;
    }

    private void close() {
        THREAD_LOCAL.remove();
    }

    // Getters

    public Engine getEngine() {
        return engine;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public ServerMarkupContext getContext() {
        return context;
    }

    public ServerConfig getConfig() {
        return config;
    }

}
