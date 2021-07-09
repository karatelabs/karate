/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import com.intuit.karate.Logger;
import com.intuit.karate.core.Config;
import com.intuit.karate.resource.ResourceResolver;
import com.linecorp.armeria.common.RequestContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class ServerConfig {

    private final ResourceResolver resourceResolver;
    private final Set<String> jsFiles;

    private String hostContextPath = null;
    private String homePagePath = "index";
    private String sessionCookieName = "karate.sid";
    private boolean stripContextPathFromRequest;
    private boolean useGlobalSession;
    private boolean autoCreateSession;
    private SessionStore sessionStore = JvmSessionStore.INSTANCE;
    private int sessionExpirySeconds = 60 * 10;

    private static final Session GLOBAL_SESSION = new Session("-1", new HashMap(), -1, -1, -1);

    private Function<Request, ServerContext> contextFactory = request -> {
        ServerContext context = new ServerContext(this, request);
        String path = request.getPath();
        if (path.startsWith("api/")) {
            context.setApi(true);
            context.setLockNeeded(true);
            context.setSession(GLOBAL_SESSION);
        } else if (useGlobalSession) {
            context.setSession(GLOBAL_SESSION);
        }
        return context;
    };

    private Config httpClientConfig = new Config(); // TODO decouple http config
    private Logger logger = new Logger();

    private Function<Request, HttpClient> httpClientFactory = request -> {
        RequestContext context = request == null ? null : request.getRequestContext();
        ArmeriaHttpClient client = new ArmeriaHttpClient(httpClientConfig, logger);
        client.setRequestContext(context);
        return client;
    };

    public ServerConfig(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        jsFiles = Collections.emptySet();
    }

    public ServerConfig(String root) {
        resourceResolver = new ResourceResolver(root);
        jsFiles = resourceResolver.getJsFiles();
    }

    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public Set<String> getJsFiles() {
        return jsFiles;
    }

    public String getHostContextPath() {
        return hostContextPath;
    }

    public String getHomePagePath() {
        return homePagePath;
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public boolean isStripContextPathFromRequest() {
        return stripContextPathFromRequest;
    }

    public boolean isUseGlobalSession() {
        return useGlobalSession;
    }

    public boolean isAutoCreateSession() {
        return autoCreateSession;
    }

    public int getSessionExpirySeconds() {
        return sessionExpirySeconds;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public Function<Request, ServerContext> getContextFactory() {
        return contextFactory;
    }

    public Function<Request, HttpClient> getHttpClientFactory() {
        return httpClientFactory;
    }

    public ServerConfig hostContextPath(String value) {
        if (value.charAt(0) != '/') {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        hostContextPath = value;
        return this;
    }

    public ServerConfig homePagePath(String value) {
        homePagePath = value;
        return this;
    }

    public ServerConfig sessionCookieName(String value) {
        sessionCookieName = value;
        return this;
    }

    public ServerConfig stripContextPathFromRequest(boolean value) {
        stripContextPathFromRequest = value;
        return this;
    }

    public ServerConfig useGlobalSession(boolean value) {
        useGlobalSession = value;
        return this;
    }

    public ServerConfig autoCreateSession(boolean value) {
        autoCreateSession = value;
        return this;
    }

    public ServerConfig sessionStore(SessionStore value) {
        sessionStore = value;
        return this;
    }

    public ServerConfig sessionExpirySeconds(int value) {
        sessionExpirySeconds = value;
        return this;
    }

    public ServerConfig contextFactory(Function<Request, ServerContext> value) {
        contextFactory = value;
        return this;
    }

    public ServerConfig httpClientFactory(Function<Request, HttpClient> value) {
        httpClientFactory = value;
        return this;
    }

}
