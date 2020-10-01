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
package com.intuit.karate.server;

import com.intuit.karate.server.ResourceResolver.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class Config {

    private String hostContextPath = null;
    private String homePagePath = "index";
    private String sessionCookieName = "karate.sid";
    private boolean stripContextPathFromRequest;
    private SessionStore sessionStore = JvmSessionStore.INSTANCE;
    private int sessionExpirySeconds = 60 * 10;
    private ResourceResolver resourceResolver = new ClassPathResourceResolver(null);
    private Map<String, String> resourceMounts;

    private static final Session GLOBAL_SESSION = new Session("-1", new HashMap(), -1, -1, -1);

    private Function<Request, Context> contextFactory = request -> {
        Context context = new Context(this, request);
        String path = request.getPath();
        if (path.startsWith("api/")) {
            context.setApi(true);
            context.setLockNeeded(true);
            context.setSession(GLOBAL_SESSION);
        }
        return context;
    };

    public ResourceResolver getResourceResolver() {
        return resourceResolver;
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

    public int getSessionExpirySeconds() {
        return sessionExpirySeconds;
    }

    public Config mount(String from, String to) {
        if (resourceMounts == null) {
            resourceMounts = new HashMap();
        }
        resourceMounts.put(from, to);
        return this;
    }
    
    public String getMountPath(String from) {
        if (resourceMounts == null) {
            return null;
        }
        return resourceMounts.get(from);
    }
    
    public Config classPathRoot(String value) {
        resourceResolver = new ClassPathResourceResolver(value);
        return this;
    }

    public Config fileSystemRoot(String value) {
        resourceResolver = new FileSystemResourceResolver(value);
        return this;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public Function<Request, Context> getContextFactory() {
        return contextFactory;
    }

    public Config hostContextPath(String value) {
        if (value.charAt(0) != '/') {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        hostContextPath = value;
        return this;
    }

    public Config homePagePath(String value) {
        homePagePath = value;
        return this;
    }

    public Config sessionCookieName(String value) {
        sessionCookieName = value;
        return this;
    }

    public Config stripContextPathFromRequest(boolean value) {
        stripContextPathFromRequest = value;
        return this;
    }

    public Config sessionStore(SessionStore value) {
        sessionStore = value;
        return this;
    }

    public Config sessionExpirySeconds(int value) {
        sessionExpirySeconds = value;
        return this;
    }

    public Config contextFactory(Function<Request, Context> value) {
        contextFactory = value;
        return this;
    }

}
