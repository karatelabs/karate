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
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StepActions;
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureBackend {

    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, DELETE, PATCH";

    private final Feature feature;
    private final StepActions actions;

    private final ScenarioContext context;
    private final String featureName;

    public static class FeatureScenarioMatch {

        private final FeatureBackend featureBackend;
        private final Scenario scenario;
        private final List<Integer> scores;

        private static final List<Integer> DEFAULT_SCORES = Collections.unmodifiableList(Arrays.asList(0, 0, 0, 0, 0, 0));

        public FeatureScenarioMatch(FeatureBackend featureBackend, Scenario scenario) {
            this.scenario = scenario;
            this.scores = DEFAULT_SCORES;
            this.featureBackend = featureBackend;
        }

        public FeatureScenarioMatch(FeatureBackend featureBackend, Scenario scenario, List<Integer> scores) {
            this.scenario = scenario;
            this.scores = Collections.unmodifiableList(scores);
            this.featureBackend = featureBackend;
        }

        public FeatureBackend getFeatureBackend() {
            return featureBackend;
        }

        public Scenario getScenario() {
            return scenario;
        }

        public List<Integer> getScores() {
            return scores;
        }

        public int compareScores(FeatureScenarioMatch other) {
            for (int i = 0; i < scores.size(); i++) {
                Integer score = scores.get(i);
                Integer otherScore = other.getScores().get(i);
                int compareTo = score.compareTo(otherScore);
                if (compareTo != 0) {
                    return compareTo;
                }
            }
            return 0;
        }
    }

    private static void putBinding(String name, ScenarioContext context) {
        String function = "function(a){ return " + ScriptBindings.KARATE + "." + name + "(a) }";
        context.vars.put(name, Script.evalJsExpression(function, context));
    }

    private static void putBinding2(String name, ScenarioContext context) {
        String function = "function(a, b){ return " + ScriptBindings.KARATE + "." + name + "(a, b) }";
        context.vars.put(name, Script.evalJsExpression(function, context));
    }

    public ScenarioContext getContext() {
        return context;
    }

    public String getFeatureName() {
        return featureName;
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
        putBinding(ScriptBindings.PARAM_EXISTS, context);
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
        context.logger.info("backend {} initialized", featureName);
    }

    public ScriptValueMap handle(ScriptValueMap args, Scenario scenario) {
        context.vars.putAll(args);
        // This forces context to be refreshed by matching scenarios context variables
        isMatchingScenario(scenario);
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
        return context.vars;
    }

    public List<FeatureScenarioMatch> getMatchingScenarios(ScriptValueMap args) {
        context.vars.putAll(args);
        List<FeatureScenarioMatch> matchingScenarios = new ArrayList<>();
        for (FeatureSection fs : feature.getSections()) {
            if (fs.isOutline()) {
                context.logger.warn("skipping scenario outline - {}:{}", featureName, fs.getScenarioOutline().getLine());
                break;
            }
            Scenario scenario = fs.getScenario();
            if (isMatchingScenario(scenario)) {
                List<Integer> scores = new ArrayList<>();
                ScriptValue pathMatchScoresValue = context.vars.getOrDefault(ScriptBindings.PATH_MATCH_SCORES, ScriptValue.NULL);
                boolean methodMatch = context.vars.getOrDefault(ScriptBindings.METHOD_MATCH, ScriptValue.FALSE).getValue(Boolean.class);
                int headersMatchScore = context.vars.getOrDefault(ScriptBindings.HEADERS_MATCH_SCORE, ScriptValue.ZERO).getAsInt();
                int queryMatchScore = context.vars.getOrDefault(ScriptBindings.QUERY_MATCH_SCORE, ScriptValue.ZERO).getAsInt();

                scores.addAll(pathMatchScoresValue.isNull() ? Arrays.asList(0, 0, 0) : pathMatchScoresValue.getAsList());
                scores.add(methodMatch ? 1 : 0);
                scores.add(queryMatchScore);
                scores.add(headersMatchScore);

                matchingScenarios.add(new FeatureScenarioMatch(this, scenario, scores));
            }
        }
        return matchingScenarios;
    }

    private boolean isMatchingScenario(Scenario scenario) {
        if (isDefaultScenario(scenario)) {
            return false;
        }
        String expression = StringUtils.trimToNull(scenario.getName() + scenario.getDescription());
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

    public Scenario getDefaultScenario(ScriptValueMap args) {
        for (FeatureSection fs : feature.getSections()) {

            Scenario scenario = fs.getScenario();
            if (isDefaultScenario(scenario)) {
                return scenario;
            }
        }
        return null;
    }

    private boolean isDefaultScenario(Scenario scenario) {
        return StringUtils.trimToNull(scenario.getName() + scenario.getDescription()) == null;
    }

    private static final String VAR_AFTER_SCENARIO = "afterScenario";

    public HttpResponse corsCheck(HttpRequest request, long startTime) {
        if (context.getConfig().isCorsEnabled()) {
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
        return null;
    }

    public HttpResponse buildResponse(HttpRequest request, long startTime, Scenario scenario, ScriptValueMap args) {
        ScriptValue responseValue, responseStatusValue, responseHeaders, afterScenario, responseDelayValue;
        Map<String, Object> responseHeadersMap, configResponseHeadersMap;
        ScriptValueMap result = handle(args, scenario);
        ScriptValue configResponseHeaders = context.getConfig().getResponseHeaders();
        responseValue = result.remove(ScriptValueMap.VAR_RESPONSE);
        responseStatusValue = result.remove(ScriptValueMap.VAR_RESPONSE_STATUS);
        long configResponseDelayValue = context.getConfig().getResponseDelay();
        responseHeaders = result.remove(ScriptValueMap.VAR_RESPONSE_HEADERS);
        afterScenario = result.remove(VAR_AFTER_SCENARIO);
        if (afterScenario == null) {
            afterScenario = context.getConfig().getAfterScenario();
        }
        configResponseHeadersMap = configResponseHeaders == null ? null : configResponseHeaders.evalAsMap(context);
        responseHeadersMap = responseHeaders == null ? null : responseHeaders.evalAsMap(context);

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
        boolean contentTypeHeaderExists = false;
        if (responseHeadersMap != null) {
            for (Map.Entry<String, Object> entry : responseHeadersMap.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                if (HttpUtils.HEADER_CONTENT_TYPE.equalsIgnoreCase(k)) {
                    contentTypeHeaderExists = true;
                }
                if (v instanceof List) { // MultiValueMap returned by proceed / response.headers
                    response.putHeader(k, (List) v);
                } else if (v != null) {
                    response.addHeader(k, v.toString());
                }
            }
        }
        String requestId = request.getRequestId();
        if (requestId != null) {
            response.addHeader("X-Karate-Request-Id", requestId);
        }
        if (!contentTypeHeaderExists && responseValue != null) {
            response.addHeader(HttpUtils.HEADER_CONTENT_TYPE, HttpUtils.getContentType(responseValue));
        }
        if (context.getConfig().isCorsEnabled()) {
            response.addHeader(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
        }
        if (afterScenario != null && afterScenario.isFunction()) {
            afterScenario.invokeFunction(context, null);
        }
        responseDelayValue = result.remove(ScriptValueMap.VAR_RESPONSE_DELAY);
        if (responseDelayValue == null || responseDelayValue.isNull()) {
            response.setDelay(configResponseDelayValue);
        } else {
            response.setDelay(Double.valueOf(responseDelayValue.getAsString()).longValue());
        }
        return response;
    }

}
