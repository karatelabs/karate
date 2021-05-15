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

import com.intuit.karate.resource.ResourceResolver;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.template.KarateTemplateEngine;
import com.intuit.karate.template.TemplateUtils;
import java.io.InputStream;
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
    private final KarateTemplateEngine engine;
    private final String homePagePath;
    private final ServerConfig config;
    private final Function<Request, ServerContext> contextFactory;
    private final ResourceResolver resourceResolver;
    private final String stripHostContextPath;

    public RequestHandler(ServerConfig config) {
        this.config = config;
        contextFactory = config.getContextFactory();
        resourceResolver = config.getResourceResolver();
        engine = TemplateUtils.forServer(config);
        homePagePath = config.getHomePagePath();
        sessionStore = config.getSessionStore();
        stripHostContextPath = config.isStripContextPathFromRequest() ? config.getHostContextPath() : null;
    }

    @Override
    public Response handle(Request request) {
        long startTime = System.currentTimeMillis();
        if (stripHostContextPath != null) {
            String path = request.getPath();
            if (path.startsWith(stripHostContextPath)) {
                request.setPath(path.substring(stripHostContextPath.length()));
            }
        }
        if (request.getPath().isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("redirecting to home page: {}", request);
            }
            return response().locationHeader(redirectPath()).status(302);
        }
        ServerContext context = contextFactory.apply(request);
        context.prepare();
        if (!context.isApi() && request.isForStaticResource()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}", request);
            }
            return response().buildStatic(request);
        }
        Session session = context.getSession(); // can be pre-resolved by context-factory
        boolean newSession = false;
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
                    newSession = true;
                    logger.debug("creating new session for '{}': {}", request.getPath(), session);
                } else {
                    logger.warn("session not found: {}", request);
                    ResponseBuilder rb = response().deleteSessionCookie(sessionId);
                    if (request.isAjax()) {
                        rb.ajaxRedirect(redirectPath());
                    } else {
                        rb.locationHeader(redirectPath());
                    }
                    return rb.status(302);
                }
            }
        }
        RequestCycle rc = RequestCycle.init(JsEngine.global(), engine);
        rc.init(context, session);
        try {
            if (context.isApi()) {
                InputStream is = resourceResolver.resolve(request.getResourcePath()).getStream();
                ResponseBuilder rb = response(rc, session, newSession);
                if (context.isLockNeeded()) {
                    synchronized (this) {
                        return apiResponse(is, rb, rc);
                    }
                } else {
                    return apiResponse(is, rb, rc);
                }
            } else {
                String html = htmlResponse(request, rc);
                return response(rc, session, newSession).html(html).build(rc);
            }
        } catch (Exception e) {
            logger.error("handle failed: {}", e.getMessage());
            return response().status(500);
        } finally {
            rc.close();
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (logger.isDebugEnabled()) {
                logger.debug("{} {} ms", request, elapsedTime);
            }
        }
    }

    private String redirectPath() {
        String contextPath = config.getHostContextPath();
        return contextPath == null ? "/" + homePagePath : contextPath + homePagePath;
    }

    private String htmlResponse(Request request, RequestCycle rc) {
        try {
            return engine.process(request.getPath());
        } catch (Exception e) {
            String redirectPath = rc.getRedirectPath();
            if (redirectPath != null) {
                logger.debug("redirect (full) requested to: {}", redirectPath);
                return null; // will be handled by response builder
            }
            String switchTemplate = rc.getSwitchTemplate();
            if (switchTemplate != null) {
                logger.debug("redirect (ajax) requested to: {}", switchTemplate);
                return engine.process(switchTemplate);
            }
            throw e;
        }
    }

    private Response apiResponse(InputStream is, ResponseBuilder rb, RequestCycle rc) {
        JsEngine.evalGlobal(is);
        return rb.build(rc);
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

    private ResponseBuilder response(RequestCycle rc, Session session, boolean newSession) {
        return new ResponseBuilder(config, rc).session(session, newSession);
    }

}
