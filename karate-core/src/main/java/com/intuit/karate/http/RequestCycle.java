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
import com.intuit.karate.template.KarateTemplateEngine;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private static final String SESSION = "session";
    private static final String RESPONSE = "response";

    private static final ThreadLocal<RequestCycle> THREAD_LOCAL = new ThreadLocal();

    public static RequestCycle get() {
        return THREAD_LOCAL.get();
    }

    public static RequestCycle init(JsEngine je, KarateTemplateEngine te) {
        RequestCycle rc = new RequestCycle(je, te);
        THREAD_LOCAL.set(rc);
        return rc;
    }

    private final JsEngine engine;
    private final KarateTemplateEngine templateEngine;
    private Session session;
    private Response response;
    private ServerContext context;
    private String switchTemplate;
    private String redirectPath;

    public RequestCycle(JsEngine engine, KarateTemplateEngine templateEngine) {
        this.engine = engine;
        this.templateEngine = templateEngine;
    }

    public JsEngine getEngine() {
        return engine;
    }

    public KarateTemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    public void close() {
        if (session != null) {
            JsValue sessionValue = engine.get(SESSION);
            if (sessionValue.isObject()) {
                session.getData().putAll(sessionValue.getAsMap());
                context.getConfig().getSessionStore().save(session);
            } else {
                logger.error("invalid session, not map-like: {}", sessionValue);
            }
        }
        JsEngine.remove();
        THREAD_LOCAL.remove();
    }

    public Session getSession() {
        return session;
    }

    public boolean isApi() {
        return context.isApi();
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

    public void setRedirectPath(String redirectPath) {
        this.redirectPath = redirectPath;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public void init(ServerContext context, Session session) {
        this.context = context;
        if (session != null) {
            engine.put(SESSION, session.getData());
            this.session = session;
        }
        // this has to be after the session init
        Map<String, Object> variables = context.getVariables();
        if (variables != null) {
            engine.putAll(variables);
        }
        Request request = context.getRequest();
        request.processBody();
        engine.put(REQUEST, request);
        response = new Response(200);
        engine.put(RESPONSE, response);
        engine.put(CONTEXT, context);
    }

}
