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

import com.intuit.karate.template.KarateTemplateEngine;
import com.intuit.karate.template.TemplateUtils;
import java.time.Instant;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RequestHandler implements ServerHandler {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private final SessionStore sessionStore;
    private final KarateTemplateEngine templateEngine;
    private final String homePagePath;
    private final ServerConfig config;
    private final Function<Request, ServerContext> contextFactory;
    private final String stripHostContextPath;

    public RequestHandler(ServerConfig config) {
        this.config = config;
        contextFactory = config.getContextFactory();
        templateEngine = TemplateUtils.forServer(config);
        homePagePath = config.getHomePagePath();
        sessionStore = config.getSessionStore();
        stripHostContextPath = config.isStripContextPathFromRequest() ? config.getHostContextPath() : null;
    }

    @Override
    public Response handle(Request request) {
        if (stripHostContextPath != null) {
            if (request.getPath().startsWith(stripHostContextPath)) {
                request.setPath(request.getPath().substring(stripHostContextPath.length()));
            }
        }
        if (request.getPath().isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("redirecting to home page: {}", request);
            }
            return response().locationHeader(config.getHomeRedirectPath()).buildWithStatus(302);
        }
        ServerContext context = contextFactory.apply(request);
        if (request.getResourceType() == null) { // can be set by context factory
            request.setResourceType(ResourceType.fromFileExtension(request.getPath()));
        }
        if (!context.isApi() && request.isForStaticResource() && context.isHttpGetAllowed()) {
            if (request.getResourcePath() == null) { // can be set by context factory
                request.setResourcePath(request.getPath()); // static resource
            }
            try {
                return response().buildStatic(request);
            } finally {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} {} [{} ms]", request, 200, System.currentTimeMillis() - request.getStartTime());
                }
            }
        }
        Session session = context.getSession(); // can be pre-resolved by context-factory
        if (session == null && !context.isStateless()) {
            String sessionId = context.getSessionCookieValue();
            if (sessionId != null) {
                session = sessionStore.get(sessionId);
                if (session != null && isExpired(session)) {
                    logger.debug("session expired: {}", session);
                    sessionStore.delete(sessionId);
                    session = null;
                }
            }
            if (session == null) {
                if (config.isAutoCreateSession() || homePagePath.equals(request.getPath())) {
                    session = createSession();
                    context.setNewSession(true);
                    logger.debug("creating new session for '{}': {}", request.getPath(), session);
                } else {
                    logger.warn("session not found: {}", request);
                    ResponseBuilder rb = response();
                    if (sessionId != null) {
                        rb.deleteSessionCookie(sessionId);
                    }
                    if (request.isAjax()) {
                        rb.ajaxRedirect(config.getLogoutRedirectPath());
                    } else {
                        rb.locationHeader(config.getHomeRedirectPath());
                    }
                    return rb.buildWithStatus(302);
                }
            }
            context.setSession(session);
        }
        RequestCycle rc = RequestCycle.init(templateEngine, context);
        return rc.handle();
    }

    private boolean isExpired(Session session) {
        long now = Instant.now().getEpochSecond();
        long expires = session.getUpdated() + config.getSessionExpirySeconds();
        if (now > expires) {
            return true;
        }
        session.setUpdated(now);
        session.setExpires(expires);
        return false;
    }

    private Session createSession() {
        long now = Instant.now().getEpochSecond();
        long expires = now + config.getSessionExpirySeconds();
        return sessionStore.create(now, expires);
    }

    private ResponseBuilder response() {
        return new ResponseBuilder(config, null);
    }

}
