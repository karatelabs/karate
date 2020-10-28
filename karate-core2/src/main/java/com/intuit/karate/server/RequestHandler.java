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

import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.template.TemplateContext;
import com.intuit.karate.template.TemplateUtils;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;

/**
 *
 * @author pthomas3
 */
public class RequestHandler implements ServerHandler {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private final SessionStore sessionStore;
    private final ITemplateEngine engine;
    private final String homePagePath;
    private final ServerConfig config;
    private final Function<Request, ServerContext> contextFactory;
    private final ResourceResolver resourceResolver;
    private final String stripHostContextPath;

    public RequestHandler(ServerConfig config) {
        this.config = config;
        contextFactory = config.getContextFactory();
        resourceResolver = config.getResourceResolver();
        engine = TemplateUtils.createEngine(config);
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
        if (request.getResourceType().isStatic()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}", request);
            }
            return response().buildStatic(request);
        }
        Session session;
        boolean newSession = false;
        if (context.isStateless()) {
            session = null;
        } else if (context.isApi()) {
            session = context.getSession();
        } else {
            String sessionId = context.getSessionCookieValue();
            if (sessionId == null) {
                session = null;
            } else {
                session = sessionStore.get(sessionId);
                if (session != null && isExpired(session)) {
                    logger.debug("session expired: {}", session);
                    sessionStore.delete(sessionId);
                    session = null;
                }
            }
            if (session == null) {
                if (homePagePath.equals(request.getPath())) {
                    session = createSession();
                    newSession = true;
                    logger.debug("creating new session for '{}': {}", homePagePath, session);
                } else {
                    logger.warn("session not found: {}", request);
                    ResponseBuilder rb = response().deleteSessionCookie(sessionId);
                    if (request.isAjax()) {
                        rb.trigger("{redirect:{url:'" + redirectPath() + "'}}");
                    } else {
                        rb.locationHeader(redirectPath());
                    }
                    return rb.status(302);
                }
            } else {
                // logger.debug("session exists: {} - {}", request, sessionId);
            }
        }
        RequestCycle rc = RequestCycle.get();
        rc.init(context, session);
        try {
            if (context.isApi()) {
                InputStream is = resourceResolver.read(request.getResourcePath());
                ResponseBuilder rb = response(session, newSession);
                if (context.isLockNeeded()) {
                    synchronized (this) {
                        return apiResponse(is, rb, rc);
                    }
                } else {
                    return apiResponse(is, rb, rc);
                }
            } else {
                String html = htmlResponse(request);
                return response(session, newSession).html(html).build(rc);
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

    private String htmlResponse(Request request) {
        TemplateContext ctx = new TemplateContext(Locale.US);
        try {
            return engine.process(request.getPath(), ctx);
        } catch (RedirectException re) {
            String template = re.getTemplate();
            logger.debug("redirect requested to: {}", template);
            return engine.process(template, ctx);
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
        return new ResponseBuilder(config);
    }

    private ResponseBuilder response(Session session, boolean newSession) {
        return new ResponseBuilder(config).session(session, newSession);
    }

}
