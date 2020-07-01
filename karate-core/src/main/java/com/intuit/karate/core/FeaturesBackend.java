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
package com.intuit.karate.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Match;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;

/**
 * A wrapper class to run multiple mock files, picking first match based on
 * path, method, header.
 *
 */
public class FeaturesBackend {

    private final List<FeatureBackend> featureBackends;
    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, DELETE, PATCH";

    public FeaturesBackend(Feature feature) {
        this(new Feature[]{feature});
    }

    public FeaturesBackend(Feature[] features) {
        this(features, null);
    }

    public FeaturesBackend(Feature[] features, Map<String, Object> arg) {
        this.featureBackends = Arrays.stream(features).map((feature) -> new FeatureBackend(feature, arg)).collect(
                Collectors.toList());
        //TODO would be good to pass the root context to backends
        getContext().logger.info("all backends initialized");
    }

    public boolean isCorsEnabled() {
        return featureBackends.stream().anyMatch((fb) -> fb.getContext().getConfig().isCorsEnabled());
    }

    public ScenarioContext getContext() {
        return featureBackends.get(0).getContext();
    }

    public HttpResponse buildResponse(HttpRequest request, long startTime) {
        if ("OPTIONS".equals(request.getMethod()) && isCorsEnabled()) {
            return corsCheck(request, startTime);
        }
        // this is a sledgehammer approach to concurrency !
        // which is why for simulating 'delay', users should use the VAR_AFTER_SCENARIO (see end)
        synchronized (this) { // BEGIN TRANSACTION !
            //This is not expected to be an actual scenario
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
                        getContext().logger.warn("json parsing failed, request data type set to string: {}", e.getMessage());
                    }
                } else if (Script.isXml(requestString)) {
                    try {
                        requestBody = XmlUtils.toXmlDoc(requestString);
                    } catch (Exception e) {
                        getContext().logger.warn("xml parsing failed, request data type set to string: {}", e.getMessage());
                    }
                }
                match.def(ScriptValueMap.VAR_REQUEST, requestBody);
            }

            FeatureBackend.FeatureScenarioMatch matchingInfo = getMatchingScenario(match.vars());
            if (matchingInfo == null) {
                getContext().logger.warn("no matching scenarios in backend feature files");
                HttpResponse response = new HttpResponse(startTime, System.currentTimeMillis());
                response.addHeader("Content-Type", "text/plain");
                String requestId = request.getRequestId();
                if (requestId != null) {
                    response.addHeader("X-Karate-Request-Id", requestId);
                }
                response.setStatus(404);
                response.setBody("no matching scenarios in backend feature files".getBytes(Charset.forName("UTF-8")));
                return response;
            }
            FeatureBackend matchingFeature = matchingInfo.getFeatureBackend();
            Scenario matchingScenario = matchingInfo.getScenario();

            return matchingFeature.buildResponse(request, startTime, matchingScenario, match.vars());
        }
        //Delays should be configured using def/configure responseDelay semantics instead of Thread.sleep
    }

    public HttpResponse corsCheck(HttpRequest request, long startTime) {
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

    public ScriptValueMap handle(ScriptValueMap args) {
        FeatureBackend.FeatureScenarioMatch matchingInfo = getMatchingScenario(args);
        //TODO what happens when no matching scenario exists?
        return matchingInfo.getFeatureBackend().handle(args, matchingInfo.getScenario());
    }

    public FeatureBackend.FeatureScenarioMatch getMatchingScenario(ScriptValueMap args) {
        FeatureBackend.FeatureScenarioMatch matched = null;
        List<FeatureBackend.FeatureScenarioMatch> matches = new ArrayList<>();
        List<FeatureBackend.FeatureScenarioMatch> defaults = new ArrayList<>();
        for (FeatureBackend featureBackend : featureBackends) {
            //This can be optimised by saying give me the first one
            List<FeatureBackend.FeatureScenarioMatch> featureMatches = featureBackend.getMatchingScenarios(args);
            Scenario defaultMatch = featureBackend.getDefaultScenario(args);

            matches.addAll(featureMatches);
            if (defaultMatch != null) {
                defaults.add(new FeatureBackend.FeatureScenarioMatch(featureBackend, defaultMatch));
            }

        }
        if (matches.isEmpty() && defaults.isEmpty()) {
            getContext().logger.error("no scenarios matched request");
            return null;
        } else {
            matched = matches.stream().max((left, right) -> left.compareScores(right)).orElse(null);
            if (matched == null) {
                matched = defaults.stream().findFirst().get();
                getContext().logger.debug("scenario defaulted: {}", matchInfo(matched));
            } else {
                getContext().logger.debug("scenario matched: {}", matchInfo(matched));
            }

        }
        return matched;
    }

    private static String matchInfo(FeatureBackend.FeatureScenarioMatch matched) {
        Scenario scenario = matched.getScenario();
        String featureInfo = matched.getFeatureBackend().getFeatureName() + " " + scenario.getDisplayMeta();
        String scenarioName = scenario.getName();
        return StringUtils.isBlank(scenarioName) ? featureInfo : featureInfo + " " + scenarioName;
    }

}
