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
import io.karatelabs.js.Engine;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.JsLazy;
import io.karatelabs.markup.ActionDispatchHost;
import io.karatelabs.markup.MarkupContext;
import io.karatelabs.markup.MarkupScope;

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
public class ServerMarkupContext implements MarkupContext, ActionDispatchHost {

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
    private final Map<String, Object> actions = new HashMap<>();
    // Per-request JS engine, wired in by ServerRequestCycle.createEngine().
    // Backs context.set (engine.put → script-level global) and the engine-binding
    // fallback in context.get. Null in non-server contexts (plain templating /
    // direct unit construction); set/get degrade silently in that case.
    private Engine engine;
    private boolean actionDispatched = false;
    private java.util.function.Consumer<String> eagerDispatchHook;
    private MarkupScope markupScope;

    @Override
    public void setMarkupScope(MarkupScope scope) {
        this.markupScope = scope;
    }

    @Override
    public MarkupScope getMarkupScope() {
        return markupScope;
    }

    public ServerMarkupContext(HttpRequest request, HttpResponse response, ServerConfig config) {
        this.request = request;
        this.response = response;
        this.config = config;
    }

    /**
     * Build the standard variables map for template rendering.
     * Includes global variables, request, response, context, and session.
     */
    public Map<String, Object> toVars() {
        Map<String, Object> vars = new HashMap<>();
        if (config.getGlobalVariables() != null) {
            vars.putAll(config.getGlobalVariables());
        }
        vars.put("request", request);
        vars.put("response", response);
        vars.put("context", this);
        if (config.isSessionEnabled()) {
            // Wrap session as a JsLazy so template expressions see a live view.
            // Without this, the snapshot taken here is stale if a ka:scope block
            // later calls context.init() — subsequent th:* expressions would still
            // see the old null session. The JS engine auto-unwraps JsLazy on
            // property access (see docs/JS_ENGINE.md § Lazy Variables).
            vars.put("session", (JsLazy) () -> this.session);
        } else {
            // sessionStore is unconfigured. Install a proxy that throws a
            // clear, actionable error on any property access (instead of
            // letting `null.foo` bubble up as an opaque TemplateInputException).
            // This deliberately surfaces misconfiguration loudly: an
            // `if (session) { session.foo }` pattern that would silently skip
            // the branch when session is null now enters and fails noisily,
            // pointing the developer at ServerConfig.sessionStore(...).
            vars.put("session", SessionUnavailableProxy.INSTANCE);
        }
        return vars;
    }

    /**
     * Placeholder for the {@code session} binding when no sessionStore
     * is configured. Throws a clear, actionable error on any access.
     */
    private static final class SessionUnavailableProxy implements io.karatelabs.js.ObjectLike {

        static final SessionUnavailableProxy INSTANCE = new SessionUnavailableProxy();

        @Override
        public Object getMember(String name) {
            throw sessionUnavailable(name, false);
        }

        @Override
        public void putMember(String name, Object value) {
            throw sessionUnavailable(name, true);
        }

        @Override
        public void removeMember(String name) {
            throw sessionUnavailable(name, true);
        }

        @Override
        public Map<String, Object> toMap() {
            throw sessionUnavailable("(toMap)", false);
        }

        private static RuntimeException sessionUnavailable(String name, boolean write) {
            return new RuntimeException(
                    "session is unavailable: no sessionStore is configured. "
                            + "Call ServerConfig.sessionStore(...) at app startup to enable sessions. "
                            + "Attempted to " + (write ? "write" : "read") + " session." + name);
        }
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
     * <p>
     * Throws a {@link TemplateFlowSignal} so the current template render or API
     * handler evaluation is aborted cleanly (no further statements run, no
     * downstream template errors logged). The signal is caught silently by the
     * server request cycle; the actual redirect is driven by the {@code redirectPath}
     * state set here.
     */
    public void redirect(String path) {
        this.redirectPath = path;
        throw new TemplateFlowSignal(TemplateFlowSignal.Kind.REDIRECT);
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
     * Script and template bindings that reference {@code session} do so through
     * {@link JsLazy} (see {@link #toVars()} and {@code ServerRequestCycle.createEngine()}),
     * so no callback is needed — the next access reads the live value.
     */
    public void init() {
        if (config != null && config.isSessionEnabled()) {
            this.session = config.createSession();
            this.closed = false;
        }
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
     * <p>
     * Throws a {@link TemplateFlowSignal} so the current template render is
     * aborted cleanly (the original template is abandoned; the new one is
     * rendered instead). The signal is caught silently by the server request
     * cycle, which then renders {@code switchTemplate} in place of the original.
     */
    public void switchTemplate(String template) {
        this.switchTemplate = template;
        this.switched = true;
        throw new TemplateFlowSignal(TemplateFlowSignal.Kind.SWITCH);
    }

    /**
     * Per-request escape hatch for setting JS-engine globals from inside a
     * {@code ka:scope="local"} block. Routes to {@code engine.put(name, value)}
     * so the value becomes a script-level global for the rest of the request,
     * visible as a bare JS name in any subsequent eval (local or global) and
     * via {@link #jsGet(String) context.getGlobal(name)}.
     * <p>
     * Distinct from {@code context.set} (which writes to {@code _}, the
     * per-template-render namespace). Use {@code setGlobal} only when a value
     * needs to cross template renders within a request (e.g. content → shell).
     * <p>
     * No-op when no engine is wired (non-server contexts).
     */
    public void setGlobal(String name, Object value) {
        if (engine != null) {
            engine.put(name, value);
        }
    }

    /**
     * Per-request engine-binding read with optional default.
     * Symmetric reader for {@link #setGlobal(String, Object)}.
     * Returns the engine binding's value if set (non-null), else the default
     * (or null when no default is given). Returns null when no engine is wired.
     */
    public Object getGlobal(String name, Object defaultValue) {
        if (engine != null) {
            Object v = engine.get(name);
            if (v != null) return v;
        }
        return defaultValue;
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
            case "setGlobal" -> (JavaInvokable) args -> {
                if (args.length == 0 || args[0] == null) {
                    throw new RuntimeException("context.setGlobal() requires a name argument");
                }
                String name = args[0].toString();
                Object value = args.length > 1 ? args[1] : null;
                setGlobal(name, value);
                return null;
            };
            case "getGlobal" -> (JavaInvokable) args -> {
                if (args.length == 0 || args[0] == null) {
                    throw new RuntimeException("context.getGlobal() requires a name argument");
                }
                String name = args[0].toString();
                Object defaultValue = args.length > 1 ? args[1] : null;
                return getGlobal(name, defaultValue);
            };

            // Properties
            case "ajax" -> isAjax();
            case "sessionId" -> session != null ? session.getId() : null;
            case "closed" -> isClosed();
            case "switched" -> switched;
            case "flash" -> flash;
            case "actions" -> actionsView();
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

    /**
     * Wired by {@link ServerRequestCycle#createEngine()} so
     * {@link #setGlobal(String, Object)} / {@link #getGlobal(String, Object)}
     * can route to the per-request JS engine bindings.
     */
    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public Engine getEngine() {
        return engine;
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

    /**
     * True iff {@link #close()} was explicitly called this request — distinct
     * from the broader {@link #isClosed()} which also returns true when there
     * was never a session to begin with. Used by {@link ServerRequestHandler}
     * to decide whether to emit a session-cookie-clear {@code Set-Cookie}
     * (defense-in-depth signout: server-side record is gone, so make sure
     * the client's stale cookie is gone too).
     */
    public boolean wasExplicitlyClosed() {
        return closed;
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

    // ActionDispatchHost (POST-handler dispatch)

    @Override
    public Map<String, Object> getActions() {
        return actions;
    }

    @Override
    public boolean isActionDispatched() {
        return actionDispatched;
    }

    @Override
    public void markActionDispatched() {
        this.actionDispatched = true;
    }

    @Override
    public void setEagerDispatchHook(java.util.function.Consumer<String> hook) {
        this.eagerDispatchHook = hook;
    }

    /**
     * View over {@link #actions} that intercepts {@code context.actions[name] = fn}
     * (and {@code context.actions.name = fn}). After the put, fires the engine's
     * eager-dispatch hook so the matching handler runs immediately — before any
     * state reads later in the same {@code ka:scope="global"} block. This matches
     * the mental model of the legacy {@code if (request.post) {{ switch }}}}
     * pattern, which runs mutations at the top of the script and lets subsequent
     * reads see fresh data.
     */
    private io.karatelabs.js.ObjectLike actionsView() {
        return new io.karatelabs.js.ObjectLike() {
            @Override
            public Object getMember(String name) {
                return actions.get(name);
            }

            @Override
            public void putMember(String name, Object value) {
                actions.put(name, value);
                if (eagerDispatchHook != null) {
                    eagerDispatchHook.accept(name);
                }
            }

            @Override
            public void removeMember(String name) {
                actions.remove(name);
            }

            @Override
            public Map<String, Object> toMap() {
                return new java.util.LinkedHashMap<>(actions);
            }
        };
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
