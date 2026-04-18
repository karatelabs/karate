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

import io.karatelabs.js.Engine;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Configuration for the HTTP server including routing, sessions, and security settings.
 * Use the builder pattern for fluent configuration.
 */
public class ServerConfig {

    private String resourceRoot;
    private String contextPath = "";
    private boolean devMode = false;
    private int sessionExpirySeconds = 600;
    private String sessionCookieName = "karate.sid";
    private SessionStore sessionStore;
    private String apiPrefix = "/api/";
    private boolean apiPrefixEnabled = true;
    private String staticPrefix = "/pub/";
    private boolean staticPrefixEnabled = true;

    // Security
    private boolean csrfEnabled = true;
    private String[] allowedOrigins;
    private boolean securityHeadersEnabled = true;
    private String contentSecurityPolicy;
    private String xFrameOptions = "DENY";
    private String referrerPolicy = "strict-origin-when-cross-origin";
    private boolean hstsEnabled = false;
    private long hstsMaxAge = 31536000; // 1 year in seconds
    private boolean hstsIncludeSubDomains = true;

    // Error templates
    // TODO: improve default 404/500 pages with a styled HTML response when no custom template is set
    private String errorTemplate404;
    private String errorTemplate500;
    private java.util.LinkedHashMap<String, String> templateRoutes;

    // Layout shell (applied to full-page navigations — skipped for HX-Request and rawPaths)
    private String shellTemplate;
    private java.util.Set<String> rawPaths;

    // Callbacks
    private Consumer<HttpRequest> requestInterceptor;
    private BiFunction<HttpRequest, ServerMarkupContext, HttpResponse> requestFilter;
    private Consumer<String> logHandler;

    // Engine supplier - default gets engine from current RequestCycle (for template processing)
    private Supplier<Engine> engineSupplier = () -> ServerRequestCycle.get().getEngine();

    // Global variables available in all templates and API handlers
    private Map<String, Object> globalVariables;

    public ServerConfig() {

    }

    public ServerConfig(String resourceRoot) {
        this.resourceRoot = resourceRoot;
    }

    // Getters

    public String getResourceRoot() {
        return resourceRoot;
    }

    public String getContextPath() {
        return contextPath;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public int getSessionExpirySeconds() {
        return sessionExpirySeconds;
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public boolean isApiPrefixEnabled() {
        return apiPrefixEnabled;
    }

    public String getStaticPrefix() {
        return staticPrefix;
    }

    public boolean isStaticPrefixEnabled() {
        return staticPrefixEnabled;
    }

    public boolean isCsrfEnabled() {
        return csrfEnabled;
    }

    public String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    public boolean isSecurityHeadersEnabled() {
        return securityHeadersEnabled;
    }

    public String getContentSecurityPolicy() {
        return contentSecurityPolicy;
    }

    public String getXFrameOptions() {
        return xFrameOptions;
    }

    public String getReferrerPolicy() {
        return referrerPolicy;
    }

    public boolean isHstsEnabled() {
        return hstsEnabled;
    }

    public long getHstsMaxAge() {
        return hstsMaxAge;
    }

    public boolean isHstsIncludeSubDomains() {
        return hstsIncludeSubDomains;
    }

    public String getErrorTemplate404() {
        return errorTemplate404;
    }

    public String getErrorTemplate500() {
        return errorTemplate500;
    }

    public java.util.LinkedHashMap<String, String> getTemplateRoutes() {
        return templateRoutes;
    }

    public String getShellTemplate() {
        return shellTemplate;
    }

    public java.util.Set<String> getRawPaths() {
        return rawPaths;
    }

    public Consumer<HttpRequest> getRequestInterceptor() {
        return requestInterceptor;
    }

    public BiFunction<HttpRequest, ServerMarkupContext, HttpResponse> getRequestFilter() {
        return requestFilter;
    }

    public Consumer<String> getLogHandler() {
        return logHandler;
    }

    public Supplier<Engine> getEngineSupplier() {
        return engineSupplier;
    }

    public Map<String, Object> getGlobalVariables() {
        return globalVariables;
    }

    // Fluent setters (builder pattern)

    public ServerConfig resourceRoot(String resourceRoot) {
        this.resourceRoot = resourceRoot;
        return this;
    }

    public ServerConfig contextPath(String contextPath) {
        this.contextPath = contextPath != null ? contextPath : "";
        return this;
    }

    public ServerConfig devMode(boolean devMode) {
        this.devMode = devMode;
        return this;
    }

    public ServerConfig sessionExpirySeconds(int sessionExpirySeconds) {
        this.sessionExpirySeconds = sessionExpirySeconds;
        return this;
    }

    public ServerConfig sessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
        return this;
    }

    public ServerConfig sessionStore(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
        return this;
    }

    public ServerConfig apiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
        return this;
    }

    public ServerConfig apiPrefixEnabled(boolean apiPrefixEnabled) {
        this.apiPrefixEnabled = apiPrefixEnabled;
        return this;
    }

    public ServerConfig staticPrefix(String staticPrefix) {
        this.staticPrefix = staticPrefix;
        return this;
    }

    public ServerConfig staticPrefixEnabled(boolean staticPrefixEnabled) {
        this.staticPrefixEnabled = staticPrefixEnabled;
        return this;
    }

    public ServerConfig csrfEnabled(boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
        return this;
    }

    public ServerConfig allowedOrigins(String... allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
        return this;
    }

    public ServerConfig securityHeadersEnabled(boolean securityHeadersEnabled) {
        this.securityHeadersEnabled = securityHeadersEnabled;
        return this;
    }

    public ServerConfig contentSecurityPolicy(String contentSecurityPolicy) {
        this.contentSecurityPolicy = contentSecurityPolicy;
        return this;
    }

    public ServerConfig xFrameOptions(String xFrameOptions) {
        this.xFrameOptions = xFrameOptions;
        return this;
    }

    public ServerConfig referrerPolicy(String referrerPolicy) {
        this.referrerPolicy = referrerPolicy;
        return this;
    }

    public ServerConfig hstsEnabled(boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
        return this;
    }

    public ServerConfig hstsMaxAge(long hstsMaxAge) {
        this.hstsMaxAge = hstsMaxAge;
        return this;
    }

    public ServerConfig hstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
        return this;
    }

    public ServerConfig errorTemplate404(String errorTemplate404) {
        this.errorTemplate404 = errorTemplate404;
        return this;
    }

    public ServerConfig errorTemplate500(String errorTemplate500) {
        this.errorTemplate500 = errorTemplate500;
        return this;
    }

    /**
     * Add a path pattern → template mapping. Checked before file resolution —
     * routes take priority. The original request path is preserved so the template
     * can use request.pathMatches() to extract parameters.
     * <p>
     * Example:
     * <pre>
     * config.templateRoute("/sessions/{id}", "session.html")
     *       .templateRoute("/sessions/{id}/report", "report.html")
     *       .templateRoute("/flows/{path}", "flows.html")
     * </pre>
     * <p>
     * Register more specific patterns first.
     */
    public ServerConfig templateRoute(String pathPattern, String templateName) {
        if (templateRoutes == null) {
            templateRoutes = new java.util.LinkedHashMap<>();
        }
        templateRoutes.put(pathPattern, templateName);
        return this;
    }

    /**
     * Set a layout shell template that wraps full-page navigations. The shell's
     * {@code th:utext="content"} slot receives the rendered content template's HTML.
     * <p>
     * The wrap is skipped when:
     * <ul>
     *   <li>The request has the {@code HX-Request} header (HTMX fragment swap)</li>
     *   <li>The resolved path is in {@link #rawPaths(String...)}</li>
     *   <li>The resolved template equals the shell template (defensive anti-recursion)</li>
     *   <li>The resolved content short-circuited with {@code context.redirect()}</li>
     * </ul>
     * <p>
     * Content renders first (so its ka:scope side effects — flash, session,
     * switch, redirect — commit before the shell evaluates). If content calls
     * {@code context.switch(t)}, the switched template becomes the new content
     * and the shell still wraps it.
     * <p>
     * The shell receives these additional template variables:
     * <ul>
     *   <li>{@code content} — the rendered content HTML (use {@code th:utext="content"})</li>
     *   <li>{@code contentTemplate} — the resolved content template path (e.g., {@code items.html})</li>
     * </ul>
     */
    public ServerConfig shellTemplate(String shellTemplate) {
        this.shellTemplate = shellTemplate;
        return this;
    }

    /**
     * Register paths that ship their own full HTML shell and should NOT be
     * wrapped with {@link #shellTemplate(String)}. Typical uses: {@code /signin},
     * {@code /signout}, landing pages with bespoke branding.
     * <p>
     * Exact path match (not prefix). Use the request path, not the template name.
     */
    public ServerConfig rawPaths(String... rawPaths) {
        if (rawPaths == null || rawPaths.length == 0) {
            this.rawPaths = null;
        } else {
            this.rawPaths = new java.util.HashSet<>(Arrays.asList(rawPaths));
        }
        return this;
    }

    public ServerConfig requestInterceptor(Consumer<HttpRequest> requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
        return this;
    }

    /**
     * Set a request filter that runs after session loading but before CSRF validation.
     * The filter receives the request and fully initialized context (with session loaded).
     * Return null to continue normal processing, or an HttpResponse to short-circuit.
     * <p>
     * Use this to:
     * <ul>
     *   <li>Set {@code context.setSession(Session.TEMPORARY)} for public API endpoints (webhooks)</li>
     *   <li>Return a 401/403 response for unauthorized requests based on session state</li>
     *   <li>Implement centralized auth validation across all routes</li>
     * </ul>
     */
    public ServerConfig requestFilter(BiFunction<HttpRequest, ServerMarkupContext, HttpResponse> requestFilter) {
        this.requestFilter = requestFilter;
        return this;
    }

    public ServerConfig logHandler(Consumer<String> logHandler) {
        this.logHandler = logHandler;
        return this;
    }

    /**
     * Set a custom ExternalBridge to control Java interop.
     * Override forType() to restrict which Java classes can be accessed from templates.
     * By default, all classes are accessible.
     */
    public ServerConfig engineSupplier(Supplier<Engine> engineSupplier) {
        this.engineSupplier = engineSupplier;
        return this;
    }

    /**
     * Set global variables that will be available in all templates and API handlers.
     * These variables are injected into the JavaScript engine context.
     */
    public ServerConfig globalVariables(Map<String, Object> globalVariables) {
        this.globalVariables = globalVariables;
        return this;
    }

    // Utility methods

    /**
     * Check if the given path should be handled as an API route.
     */
    public boolean isApiPath(String path) {
        if (!apiPrefixEnabled || apiPrefix == null) {
            return false;
        }
        return path.startsWith(apiPrefix);
    }

    /**
     * Check if the given path should be served as a static file.
     */
    public boolean isStaticPath(String path) {
        if (!staticPrefixEnabled || staticPrefix == null) {
            return false;
        }
        return path.startsWith(staticPrefix);
    }

    /**
     * Check if the given request path opts out of shell-wrap.
     */
    public boolean isRawPath(String path) {
        return rawPaths != null && rawPaths.contains(path);
    }

    /**
     * Check if sessions are enabled.
     */
    public boolean isSessionEnabled() {
        return sessionStore != null;
    }

    /**
     * Create a new session using the configured session store.
     * Returns null if sessions are not enabled.
     */
    public Session createSession() {
        if (sessionStore == null) {
            return null;
        }
        return sessionStore.create(sessionExpirySeconds);
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "resourceRoot='" + resourceRoot + '\'' +
                ", contextPath='" + contextPath + '\'' +
                ", devMode=" + devMode +
                ", apiPrefix='" + apiPrefix + '\'' +
                ", staticPrefix='" + staticPrefix + '\'' +
                ", csrfEnabled=" + csrfEnabled +
                ", allowedOrigins=" + Arrays.toString(allowedOrigins) +
                '}';
    }

}
