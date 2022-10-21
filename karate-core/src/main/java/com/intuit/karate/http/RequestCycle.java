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

import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.resource.ResourceResolver;
import com.intuit.karate.template.KarateTemplateEngine;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RequestCycle {

    private static final Logger logger = LoggerFactory.getLogger(RequestCycle.class);

    public static final String CONTEXT = "context";
    private static final String REQUEST = "request";
    protected static final String SESSION = "session";
    private static final String RESPONSE = "response";

    private static final ThreadLocal<RequestCycle> THREAD_LOCAL = new ThreadLocal();

    public static RequestCycle get() {
        return THREAD_LOCAL.get();
    }

    protected static RequestCycle init(KarateTemplateEngine te, ServerContext context) {
        RequestCycle rc = new RequestCycle(JsEngine.global(), te, context);
        THREAD_LOCAL.set(rc);
        return rc;
    }

    private final JsEngine engine;
    private final KarateTemplateEngine templateEngine;
    private final Request request;
    private final Response response;
    private final ServerContext context;
    private final ServerConfig config;
    private final Supplier<Response> customHandler;

    private String switchTemplate;
    private Map<String, Object> switchParams;

    private RequestCycle(JsEngine engine, KarateTemplateEngine templateEngine, ServerContext context) {
        this.engine = engine;
        this.templateEngine = templateEngine;
        this.context = context;
        config = context.getConfig();
        customHandler = context.getCustomHandler();
        Session session = context.getSession();
        if (session != null && !session.isTemporary()) {
            engine.put(SESSION, session.getData());
        } else {
            // easier for users to write code such as
            // if (session.foo) {}
            engine.put(SESSION, Collections.emptyMap());
        }
        // this has to be after the session init
        Map<String, Object> variables = context.getVariables();
        if (variables != null) {
            engine.putAll(variables);
        }
        request = context.getRequest();
        request.processBody();
        engine.put(REQUEST, request);
        response = new Response(200);
        engine.put(RESPONSE, response);
        engine.put(CONTEXT, context);
    }

    public RequestCycle copy(Request request, Map<String, Object> variables) {
        ServerContext temp = new ServerContext(config, request, variables);
        temp.setSession(context.getSession());
        return new RequestCycle(JsEngine.local(), templateEngine, temp);
    }

    public JsEngine getEngine() {
        return engine;
    }

    public KarateTemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    public ResourceResolver getResourceResolver() {
        return config.getResourceResolver();
    }

    private void close() {
        Session session = context.getSession();
        if (session != null && !session.isTemporary()) {
            if (context.isClosed()) {
                // note that session cookie is deleted in response-builder
                context.getConfig().getSessionStore().delete(session.getId());
                logger.debug("session deleted: {}", session.getId());
            } else {
                JsValue sessionValue = engine.get(SESSION);
                if (sessionValue.isObject()) {
                    session.getData().putAll(sessionValue.getAsMap());
                    context.getConfig().getSessionStore().save(session);
                } else {
                    logger.error("invalid session, not map-like: {}", sessionValue);
                }
            }
        }
        JsEngine.remove();
        THREAD_LOCAL.remove();
    }

    public Session getSession() {
        return context.getSession();
    }

    public Request getRequest() {
        return request;
    }

    public Response getResponse() {
        return response;
    }

    public ServerContext getContext() {
        return context;
    }

    public void setSwitchTemplate(String switchTemplate) {
        this.switchTemplate = switchTemplate;
    }

    public String getSwitchTemplate() {
        return switchTemplate;
    }

    public void setSwitchParams(Map<String, Object> switchParams) {
        this.switchParams = switchParams;
    }

    protected Response handle() {
        try {
            if (customHandler != null) {
                return customHandler.get();
            } else if (context.isApi()) {
                InputStream is = apiResource();
                if (context.isLockNeeded()) {
                    synchronized (config) {
                        engine.eval(is);
                    }
                } else {
                    engine.eval(is);
                }
                return response().build();
            } else {
                return htmlResponse();
            }
        } catch (Exception e) {
            logger.error("handle failed: {}", e.getMessage());
            response.setStatus(500); // just for logging below
            return response().buildWithStatus(500);
        } finally {
            close();
            if (logger.isDebugEnabled()) {
                logger.debug("{} {} [{} ms]", request, response.getStatus(), System.currentTimeMillis() - request.getStartTime());
            }
        }
    }

    private Response htmlResponse() {
        String html;
        try {
            // template system uses [root:] prefix system, remove forward slash
            html = templateEngine.process(request.getPath().substring(1));
        } catch (Exception e) {
            if (context.isSwitched()) {
                if (switchTemplate == null) {
                    logger.debug("abort template requested");
                    html = null;
                } else {
                    logger.debug("switch template requested: {}", switchTemplate);
                    request.getParams().clear();
                    if (switchParams != null) {
                        switchParams.forEach((k, v) -> request.setParam(k, v));
                    }
                    html = templateEngine.process(switchTemplate);
                }
            } else {
                throw e;
            }
        }
        return response().html(html).build();
    }

    private static final String DOT_JS = ".js";

    private InputStream apiResource() {
        String resourcePath = request.getResourcePath();
        String jsPath = resourcePath == null ? request.getPathOriginal() + DOT_JS : resourcePath;
        try {
            return config.getResourceResolver().resolve(jsPath).getStream();
        } catch (Exception e) {
            throw new RuntimeException("failed to resolve api resource: " + resourcePath + ", " + request + ", " + e.getMessage());
        }
    }

    public ResponseBuilder response() {
        return new ResponseBuilder(config, this).session(context.getSession(), context.isNewSession());
    }

}
