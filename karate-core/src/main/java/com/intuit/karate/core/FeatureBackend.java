/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.CallContext;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Match;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StepActions;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureBackend {

    private final Feature feature;
    private final StepActions actions;

    private boolean corsEnabled;

    private final ScenarioContext context;
    private final String featureName;

    private static void putBinding(String name, ScenarioContext context) {
        String function = "function(a){ return " + ScriptBindings.KARATE + "." + name + "(a) }";
        context.vars.put(name, Script.evalJsExpression(function, context));
    }
    
    private static void putBinding2(String name, ScenarioContext context) {
        String function = "function(a, b){ return " + ScriptBindings.KARATE + "." + name + "(a, b) }";
        context.vars.put(name, Script.evalJsExpression(function, context));
    }    

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public ScenarioContext getContext() {
        return context;
    }

    public FeatureBackend(Feature feature) {
        this(feature, null);
    }

    public FeatureBackend(Feature feature, Map<String, Object> arg) {
        this.feature = feature;
        featureName = feature.getPath().toFile().getName();
        CallContext callContext = new CallContext(null, false);
        FeatureContext featureContext = new FeatureContext(null, feature, null);
        actions = new StepActions(featureContext, callContext, null, null);
        context = actions.context;
        putBinding(ScriptBindings.PATH_MATCHES, context);
        putBinding(ScriptBindings.METHOD_IS, context);
        putBinding(ScriptBindings.PARAM_VALUE, context);
        putBinding(ScriptBindings.TYPE_CONTAINS, context);
        putBinding(ScriptBindings.ACCEPT_CONTAINS, context);
        putBinding2(ScriptBindings.HEADER_CONTAINS, context);
        putBinding(ScriptBindings.BODY_PATH, context);
        if (arg != null) {
            arg.forEach((k, v) -> context.vars.put(k, v));
        }
        // the background is evaluated one-time
        if (feature.isBackgroundPresent()) {
            for (Step step : feature.getBackground().getSteps()) {
                Result result = Engine.executeStep(step, actions);
                if (result.isFailed()) {
                    String message = "server-side background init failed - " + featureName + ":" + step.getLine();
                    context.logger.error(message);
                    throw new KarateException(message, result.getError());
                }
            }
        }
        // this is a special case, we support the auto-handling of cors
        // only if '* configure cors = true' has been done in the Background
        corsEnabled = context.getConfig().isCorsEnabled(); 
        context.logger.info("backend initialized");
    }

    public ScriptValueMap handle(ScriptValueMap args) {
        boolean matched = false;
        context.vars.putAll(args);
        for (FeatureSection fs : feature.getSections()) {
            if (fs.isOutline()) {
                context.logger.warn("skipping scenario outline - {}:{}", featureName, fs.getScenarioOutline().getLine());
                break;
            }
            Scenario scenario = fs.getScenario();
            if (isMatchingScenario(scenario)) {
                matched = true;
                for (Step step : scenario.getSteps()) {
                    Result result = Engine.executeStep(step, actions);
                    if (result.isAborted()) {
                        context.logger.debug("abort at {}:{}", featureName, step.getLine());
                        break;
                    }
                    if (result.isFailed()) {
                        String message = "server-side scenario failed - " + featureName + ":" + step.getLine();
                        context.logger.error(message);
                        throw new KarateException(message, result.getError());
                    }
                }
                break; // process only first matching scenario
            }
        }
        if (!matched) {
            context.logger.warn("no scenarios matched");
        }
        return context.vars;
    }

    private boolean isMatchingScenario(Scenario scenario) {
        String expression = StringUtils.trimToNull(scenario.getName() + scenario.getDescription());
        if (expression == null) {
            context.logger.debug("scenario matched: (empty)");
            return true;
        }
        try {
            ScriptValue sv = Script.evalJsExpression(expression, context);
            if (sv.isBooleanTrue()) {
                context.logger.debug("scenario matched: {}", expression);
                return true;
            } else {
                context.logger.debug("scenario skipped: {}", expression);
                return false;
            }
        } catch (Exception e) {
            context.logger.warn("scenario match evaluation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private static final String VAR_AFTER_SCENARIO = "afterScenario";
    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, DELETE, PATCH";
    
    public HttpResponse buildResponse(HttpRequest request, long startTime) {
        if (corsEnabled && "OPTIONS".equals(request.getMethod())) {
            HttpResponse response = new HttpResponse(startTime, System.currentTimeMillis());
            response.setStatus(200);
            response.addHeader(HttpUtils.HEADER_ALLOW, ALLOWED_METHODS);
            response.addHeader(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
            response.addHeader(HttpUtils.HEADER_AC_ALLOW_METHODS, ALLOWED_METHODS);
            List requestHeaders = request.getHeaders().get(HttpUtils.HEADER_AC_REQUEST_HEADERS);
            if (requestHeaders != null) {
                response.putHeader(HttpUtils.HEADER_AC_ALLOW_HEADERS, requestHeaders);
            }            
            return response;
        }
        Match match = new Match()
                .text(ScriptValueMap.VAR_REQUEST_URL_BASE, request.getUrlBase())
                .text(ScriptValueMap.VAR_REQUEST_URI, request.getUri())
                .text(ScriptValueMap.VAR_REQUEST_METHOD, request.getMethod())
                .def(ScriptValueMap.VAR_REQUEST_HEADERS, request.getHeaders())
                .def(ScriptValueMap.VAR_RESPONSE_STATUS, 200)
                .def(ScriptValueMap.VAR_REQUEST_PARAMS, request.getParams());
        byte[] requestBytes = request.getBody();
        if (requestBytes != null) {
            match.def(ScriptValueMap.VAR_REQUEST_BYTES, requestBytes);
            String requestString = FileUtils.toString(requestBytes);
            Object requestBody = requestString;
            if (Script.isJson(requestString)) {
                try {
                    requestBody = JsonUtils.toJsonDoc(requestString);
                } catch (Exception e) {
                    context.logger.warn("json parsing failed, request data type set to string: {}", e.getMessage());
                }
            } else if (Script.isXml(requestString)) {
                try {
                    requestBody = XmlUtils.toXmlDoc(requestString);
                } catch (Exception e) {
                    context.logger.warn("xml parsing failed, request data type set to string: {}", e.getMessage());
                }
            }
            match.def(ScriptValueMap.VAR_REQUEST, requestBody);
        }
        ScriptValue responseValue, responseStatusValue, responseHeaders, afterScenario;
        Map<String, Object> responseHeadersMap, configResponseHeadersMap;
        // this is a sledgehammer approach to concurrency !
        // which is why for simulating 'delay', users should use the VAR_AFTER_SCENARIO (see end)
        synchronized (this) { // BEGIN TRANSACTION !
            ScriptValueMap result = handle(match.vars());
            ScriptValue configResponseHeaders = context.getConfig().getResponseHeaders();
            responseValue = result.remove(ScriptValueMap.VAR_RESPONSE);
            responseStatusValue = result.remove(ScriptValueMap.VAR_RESPONSE_STATUS);
            responseHeaders = result.remove(ScriptValueMap.VAR_RESPONSE_HEADERS);
            afterScenario = result.remove(VAR_AFTER_SCENARIO);
            if (afterScenario == null) {
                afterScenario = context.getConfig().getAfterScenario();
            }
            configResponseHeadersMap = configResponseHeaders == null ? null : configResponseHeaders.evalAsMap(context);
            responseHeadersMap = responseHeaders == null ? null : responseHeaders.evalAsMap(context);
        } // END TRANSACTION !!
        int responseStatus = responseStatusValue == null ? 200 : Integer.valueOf(responseStatusValue.getAsString());
        HttpResponse response = new HttpResponse(startTime, System.currentTimeMillis());
        response.setStatus(responseStatus);
        if (responseValue != null && !responseValue.isNull()) {
            response.setBody(responseValue.getAsByteArray());
        }
        // trying to avoid creating a map unless absolutely necessary
        if (responseHeadersMap != null) {
            if (configResponseHeadersMap != null) {
                // this is slightly different from how the client-side configure headers works
                // here, scenarios can over-ride what the "global" hook does
                for (Map.Entry<String, Object> e : configResponseHeadersMap.entrySet()) {
                    responseHeadersMap.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        } else if (configResponseHeadersMap != null) {
            responseHeadersMap = configResponseHeadersMap;
        }
        if (responseHeadersMap != null) {
            responseHeadersMap.forEach((k, v) -> {
                if (v instanceof List) { // MultiValueMap returned by proceed / response.headers
                    response.putHeader(k, (List) v);
                } else if (v != null) {                    
                    response.addHeader(k, v.toString());
                }
            }); 
        }
        if (responseValue != null && (responseHeadersMap == null || !responseHeadersMap.containsKey(HttpUtils.HEADER_CONTENT_TYPE))) {
            response.addHeader(HttpUtils.HEADER_CONTENT_TYPE, HttpUtils.getContentType(responseValue));
        }        
        if (corsEnabled) {
            response.addHeader(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
        }
        // functions here are outside of the 'transaction' and should not mutate global state !
        // typically this is where users can set up an artificial delay or sleep
        if (afterScenario != null && afterScenario.isFunction()) {
            afterScenario.invokeFunction(context, null);
        }
        return response;
    }    

}
