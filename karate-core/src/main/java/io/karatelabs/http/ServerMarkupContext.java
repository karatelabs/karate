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
import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.markup.MarkupContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-side context exposed to templates and API handlers.
 * Implements MarkupContext for template compatibility and adds server-specific methods.
 * <p>
 * In templates and API handlers, access via the 'context' variable:
 * <pre>
 * &lt;script ka:scope="global"&gt;
 *     context.log('Processing request');
 *     if (!session.user) {
 *         context.redirect('/signin');
 *     }
 *     var id = context.uuid();
 * &lt;/script&gt;
 * </pre>
 */
public class ServerMarkupContext implements MarkupContext {

    /**
     * Session key used to persist flash messages across redirects.
     */
    public static final String FLASH_SESSION_KEY = "_flash";

    private final HttpRequest request;
    private final HttpResponse response;
    private final ServerConfig config;
    private Session session;
    private Function<String, Resource> resourceResolver;

    // Template context (set when rendering templates)
    private String templateName;
    private String callerTemplateName;

    // State
    private boolean closed = false;
    private boolean switched = false;
    private String switchTemplate;
    private String redirectPath;
    private final List<String> logMessages = new ArrayList<>();
    private final Map<String, Object> flash = new HashMap<>();

    // Callback for syncing session to JS engine after init()
    private java.util.function.Consumer<Session> onSessionInit;

    public ServerMarkupContext(HttpRequest request, HttpResponse response, ServerConfig config) {
        this.request = request;
        this.response = response;
        this.config = config;
    }

    // MarkupContext implementation

    @Override
    public String getTemplateName() {
        return templateName;
    }

    @Override
    public String getCallerTemplateName() {
        return callerTemplateName;
    }

    @Override
    public String read(String path) {
        if (resourceResolver == null) {
            throw new RuntimeException("Resource resolver not configured");
        }
        Resource resource = resourceResolver.apply(path);
        return resource.getText();
    }

    @Override
    public byte[] readBytes(String path) {
        if (resourceResolver == null) {
            throw new RuntimeException("Resource resolver not configured");
        }
        Resource resource = resourceResolver.apply(path);
        return FileUtils.toBytes(resource.getStream());
    }

    @Override
    public String toJson(Object obj) {
        return Json.stringifyStrict(obj);
    }

    @Override
    public Object fromJson(String json) {
        return Json.of(json).value();
    }

    @Override
    public Object getContextSession() {
        return session;
    }

    // Server-specific methods

    /**
     * Set redirect path. This will cause a 302 redirect response.
     */
    public void redirect(String path) {
        this.redirectPath = path;
    }

    /**
     * Log a message. Messages are captured and can be retrieved via getLogMessages().
     * If a logHandler is configured in ServerConfig, it will be called.
     * Otherwise, falls back to System.out.println.
     */
    public void log(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }
        String message = sb.toString();
        logMessages.add(message);
        if (config != null && config.getLogHandler() != null) {
            config.getLogHandler().accept(message);
        } else {
            System.out.println("[ServerMarkupContext] " + message);
        }
    }

    /**
     * Generate a new UUID string.
     */
    public String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Initialize a new session. If sessions are not enabled in config, this is a no-op.
     * After creating the session, the onSessionInit callback is invoked to sync with JS engine.
     */
    public void init() {
        if (config != null && config.isSessionEnabled()) {
            this.session = config.createSession();
            this.closed = false;
            // Notify JS engine to update its session variable
            if (onSessionInit != null) {
                onSessionInit.accept(this.session);
            }
        }
    }

    /**
     * Set a callback to be invoked after session initialization.
     * Used by MarkupTemplateContext to sync session to JS engine.
     */
    public void setOnSessionInit(java.util.function.Consumer<Session> callback) {
        this.onSessionInit = callback;
    }

    /**
     * Close/invalidate the current session.
     */
    public void close() {
        if (session != null && config != null && config.getSessionStore() != null) {
            config.getSessionStore().delete(session.getId());
        }
        this.session = null;
        this.closed = true;
    }

    /**
     * Switch to a different template for rendering.
     */
    public void switchTemplate(String template) {
        this.switchTemplate = template;
        this.switched = true;
    }

    // SimpleObject/jsGet implementation

    @Override
    public Object jsGet(String key) {
        // First check MarkupContext base implementation
        Object baseResult = MarkupContext.super.jsGet(key);
        if (baseResult != null) {
            return baseResult;
        }

        // Server-specific methods and properties
        return switch (key) {
            // Methods
            case "redirect" -> (JavaInvokable) args -> {
                if (args.length == 0) throw new RuntimeException("redirect() requires a URL argument");
                redirect(args[0].toString());
                return null;
            };
            case "log" -> (JavaInvokable) args -> {
                log(args);
                return null;
            };
            case "uuid" -> (JavaInvokable) args -> uuid();
            case "init" -> (JavaInvokable) args -> {
                init();
                return session;  // Return session so scripts can do: session = context.init()
            };
            case "close" -> (JavaInvokable) args -> {
                close();
                return null;
            };
            case "switch" -> (JavaInvokable) args -> {
                if (args.length == 0) throw new RuntimeException("switch() requires a template argument");
                switchTemplate(args[0].toString());
                return null;
            };

            // Properties
            case "ajax" -> isAjax();
            case "sessionId" -> session != null ? session.getId() : null;
            case "closed" -> isClosed();
            case "switched" -> switched;
            case "flash" -> flash;
            case "request" -> request;
            case "response" -> response;
            case "session" -> session;
            case "csrf" -> getCsrfToken();

            default -> null;
        };
    }

    // Getters and setters

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
        this.closed = session == null;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public void setCallerTemplateName(String callerTemplateName) {
        this.callerTemplateName = callerTemplateName;
    }

    public void setResourceResolver(Function<String, Resource> resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public boolean hasRedirect() {
        return redirectPath != null;
    }

    public boolean isClosed() {
        return closed || session == null || session.isTemporary();
    }

    public boolean isSwitched() {
        return switched;
    }

    public String getSwitchTemplate() {
        return switchTemplate;
    }

    public List<String> getLogMessages() {
        return logMessages;
    }

    public Map<String, Object> getFlash() {
        return flash;
    }

    /**
     * Load flash messages from session (if any) and clear them from session.
     * Called at the start of request processing to retrieve messages set before a redirect.
     */
    @SuppressWarnings("unchecked")
    public void loadFlashFromSession() {
        if (session == null) {
            return;
        }
        Object stored = session.getMember(FLASH_SESSION_KEY);
        if (stored instanceof Map) {
            flash.putAll((Map<String, Object>) stored);
            session.removeMember(FLASH_SESSION_KEY);
        }
    }

    /**
     * Persist current flash messages to session.
     * Called before redirect so messages survive to the next request.
     */
    public void persistFlashToSession() {
        if (session == null || flash.isEmpty()) {
            return;
        }
        session.putMember(FLASH_SESSION_KEY, new HashMap<>(flash));
    }

    /**
     * Check if this is an AJAX/HTMX request.
     */
    public boolean isAjax() {
        if (request == null) {
            return false;
        }
        String hxRequest = request.getHeader("HX-Request");
        String xhrHeader = request.getHeader("X-Requested-With");
        return hxRequest != null || "XMLHttpRequest".equals(xhrHeader);
    }

    /**
     * Get the CSRF token object for templates.
     * Returns a CsrfToken with token, headerName, and fieldName properties.
     *
     * @return CsrfToken object or null if CSRF is disabled or no session
     */
    public CsrfProtection.CsrfToken getCsrfToken() {
        if (config == null || !config.isCsrfEnabled()) {
            return null;
        }
        return CsrfProtection.createTemplateToken(session);
    }

}
