/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate;

import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.ScenarioInfo;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.exception.KarateFileNotFoundException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.web.Driver;
import com.intuit.karate.web.DriverUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 *
 * @author pthomas3
 */
public class ScenarioContext {

    public final Logger logger;
    public final ScriptBindings bindings;
    public final int callDepth;
    public final List<String> tags;
    public final Map<String, List<String>> tagValues;
    public final ScriptValueMap vars;
    public final FeatureContext rootFeatureContext;
    public final FeatureContext featureContext;
    public final ExecutionHook executionHook;
    public final boolean perfMode;
    public final ScenarioInfo scenarioInfo;

    // these can get re-built or swapped, so cannot be final
    private HttpClient client;
    private HttpConfig config;
    
    private Driver driver;

    private HttpRequestBuilder request = new HttpRequestBuilder();
    private HttpResponse response;

    // the actual http request last sent on the wire
    private HttpRequest prevRequest;

    // pass call result to engine via this variable (hack)
    private List<FeatureResult> callResults;

    // gatling integration    
    private PerfEvent prevPerfEvent;

    public void logLastPerfEvent(String failureMessage) {
        if (prevPerfEvent != null && executionHook != null) {
            if (failureMessage != null) {
                prevPerfEvent.setFailed(true);
                prevPerfEvent.setMessage(failureMessage);
            }
            executionHook.reportPerfEvent(prevPerfEvent);
        }
        prevPerfEvent = null;
    }

    public void capturePerfEvent(PerfEvent event) {
        logLastPerfEvent(null);
        prevPerfEvent = event;
    }

    public List<FeatureResult> getAndClearCallResults() {
        List<FeatureResult> temp = callResults;
        callResults = null;
        return temp;
    }

    public void addCallResult(FeatureResult callResult) {
        if (callResults == null) {
            callResults = new ArrayList();
        }
        callResults.add(callResult);
    }

    public void setScenarioError(Throwable error) {
        scenarioInfo.setErrorMessage(error.getMessage());
    }

    public void setPrevRequest(HttpRequest prevRequest) {
        this.prevRequest = prevRequest;
    }      

    public HttpRequestBuilder getRequest() {
        return request;
    }

    public HttpRequest getPrevRequest() {
        return prevRequest;
    }

    public HttpClient getHttpClient() {
        return client;
    }

    public int getCallDepth() {
        return callDepth;
    }

    public FeatureContext getFeatureContext() {
        return featureContext;
    }

    public ScriptValueMap getVars() {
        return vars;
    }

    public HttpConfig getConfig() {
        return config;
    }

    public void updateConfigCookies(Map<String, Cookie> cookies) {
        if (cookies == null) {
            return;
        }
        if (config.getCookies().isNull()) {
            config.setCookies(new ScriptValue(cookies));
        } else {
            Map<String, Object> map = config.getCookies().evalAsMap(this);
            map.putAll(cookies);
            config.setCookies(new ScriptValue(map));
        }
    }

    public boolean isPrintEnabled() {
        return config.isPrintEnabled();
    }

    public ScenarioContext(FeatureContext featureContext, CallContext call) {
        this.featureContext = featureContext; // make sure references below to env.env use the updated one
        logger = featureContext.logger;
        callDepth = call.callDepth;
        executionHook = call.executionHook;
        perfMode = call.perfMode;
        tags = call.getTags().getTags();
        tagValues = call.getTags().getTagValues();
        scenarioInfo = call.getScenarioInfo();
        if (call.reuseParentContext) {
            vars = call.parentContext.vars; // shared context !
            config = call.parentContext.config;
            rootFeatureContext = call.parentContext.rootFeatureContext;
        } else if (call.parentContext != null) {
            // complex objects like JSON and XML are "global by reference" TODO           
            vars = call.parentContext.vars.copy(false);
            config = new HttpConfig(call.parentContext.config);
            rootFeatureContext = call.parentContext.rootFeatureContext;
        } else {
            vars = new ScriptValueMap();
            config = new HttpConfig();
            config.setClientClass(call.httpClientClass);
            rootFeatureContext = featureContext;
        }
        client = HttpClient.construct(config, this);
        bindings = new ScriptBindings(this);
        if (call.parentContext == null && call.evalKarateConfig) {
            // base config is only looked for in the classpath
            try {
                Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, ScriptBindings.READ_KARATE_CONFIG_BASE, null, this);
            } catch (Exception e) {
                if (e instanceof KarateFileNotFoundException) {
                    logger.trace("skipping 'classpath:karate-base.js': {}", e.getMessage());
                } else {
                    throw new RuntimeException("evaluation of 'classpath:karate-base.js' failed", e);
                }
            }
            String configDir = System.getProperty(ScriptBindings.KARATE_CONFIG_DIR);
            String configScript = ScriptBindings.readKarateConfigForEnv(true, configDir, null);
            try {
                Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, configScript, null, this);
            } catch (Exception e) {
                if (e instanceof KarateFileNotFoundException) {
                    logger.warn("skipping bootstrap configuration: {}", e.getMessage());
                } else {
                    throw new RuntimeException("evaluation of '" + ScriptBindings.KARATE_CONFIG_JS + "' failed", e);
                }
            }
            if (featureContext.env != null) {
                configScript = ScriptBindings.readKarateConfigForEnv(false, configDir, featureContext.env);
                try {
                    Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, configScript, null, this);
                } catch (Exception e) {
                    if (e instanceof KarateFileNotFoundException) {
                        logger.trace("skipping bootstrap configuration for env: {} - {}", featureContext.env, e.getMessage());
                    } else {
                        throw new RuntimeException("evaluation of 'karate-config-" + featureContext.env + ".js' failed", e);
                    }
                }
            }
        }
        if (call.callArg != null) { // if call.reuseParentContext is true, arg will clobber parent context
            for (Map.Entry<String, Object> entry : call.callArg.entrySet()) {
                vars.put(entry.getKey(), entry.getValue());
            }
            vars.put(Script.VAR_ARG, call.callArg);
            vars.put(Script.VAR_LOOP, call.loopIndex);
        } else if (call.parentContext != null) {
            vars.put(Script.VAR_ARG, ScriptValue.NULL);
            vars.put(Script.VAR_LOOP, -1);
        }
        logger.trace("karate context init - initial properties: {}", vars);
    }
    
    public ScenarioContext copy(ScenarioInfo info) {
        return new ScenarioContext(this, info);
    }
    
    private ScenarioContext(ScenarioContext sc, ScenarioInfo info) {
        featureContext = sc.featureContext;
        logger = sc.logger;
        callDepth = sc.callDepth;
        executionHook = sc.executionHook;
        perfMode = sc.perfMode;
        tags = sc.tags;
        tagValues = sc.tagValues;
        scenarioInfo = info;
        vars = sc.vars.copy(true); // deep / snap-shot copy
        config = new HttpConfig(sc.config); // safe copy
        rootFeatureContext = sc.rootFeatureContext;
        client = HttpClient.construct(config, this);
        bindings = new ScriptBindings(this);
        // state
        request = sc.request.copy();
        response = sc.response;
        driver = sc.driver;
        prevRequest = sc.prevRequest;
        prevPerfEvent = sc.prevPerfEvent;
        callResults = sc.callResults;
    }

    public void configure(HttpConfig config) {
        this.config = config;
        client = HttpClient.construct(config, this);
    }

    public void configure(String key, ScriptValue value) { // TODO use enum
        key = StringUtils.trimToEmpty(key);
        if (key.equals("headers")) {
            config.setHeaders(value);
            return;
        }
        if (key.equals("cookies")) {
            config.setCookies(value);
            return;
        }
        if (key.equals("responseHeaders")) {
            config.setResponseHeaders(value);
            return;
        }
        if (key.equals("lowerCaseResponseHeaders")) {
            config.setLowerCaseResponseHeaders(value.isBooleanTrue());
            return;
        }
        if (key.equals("cors")) {
            config.setCorsEnabled(value.isBooleanTrue());
            return;
        }
        if (key.equals("logPrettyResponse")) {
            config.setLogPrettyResponse(value.isBooleanTrue());
            return;
        }
        if (key.equals("logPrettyRequest")) {
            config.setLogPrettyRequest(value.isBooleanTrue());
            return;
        }
        if (key.equals("printEnabled")) {
            config.setPrintEnabled(value.isBooleanTrue());
            return;
        }
        if (key.equals("afterScenario")) {
            config.setAfterScenario(value);
            return;
        }
        if (key.equals("afterFeature")) {
            config.setAfterFeature(value);
            return;
        }
        if (key.equals("httpClientClass")) {
            config.setClientClass(value.getAsString());
            // re-construct all the things ! and we exit early
            client = HttpClient.construct(config, this);
            return;
        }
        if (key.equals("httpClientInstance")) {
            config.setClientInstance(value.getValue(HttpClient.class));
            // here too, re-construct client - and exit early
            client = HttpClient.construct(config, this);
            return;
        }
        if (key.equals("charset")) {
            if (value.isNull()) {
                config.setCharset(null);
            } else {
                config.setCharset(Charset.forName(value.getAsString()));
            }
            // here again, re-construct client - and exit early
            client = HttpClient.construct(config, this);
            return;
        }
        if (key.equals("report")) {
            if (value.isMapLike()) {
                Map<String, Object> map = value.getAsMap();
                config.setShowLog((Boolean) map.get("showLog"));
                config.setShowAllSteps((Boolean) map.get("showAllSteps"));
            } else if (value.isBooleanTrue()) {
                config.setShowLog(true);
                config.setShowAllSteps(true);
            } else {
                config.setShowLog(false);
                config.setShowAllSteps(false);
            }
            return;
        }
        if (key.equals("driver")) {
            config.setDriverOptions(value.getAsMap());
            return;
        }
        // beyond this point, we don't exit early and we have to re-configure the http client
        if (key.equals("ssl")) {
            if (value.isString()) {
                config.setSslEnabled(true);
                config.setSslAlgorithm(value.getAsString());
            } else if (value.isMapLike()) {
                config.setSslEnabled(true);
                Map<String, Object> map = value.getAsMap();
                config.setSslKeyStore((String) map.get("keyStore"));
                config.setSslKeyStorePassword((String) map.get("keyStorePassword"));
                config.setSslKeyStoreType((String) map.get("keyStoreType"));
                config.setSslTrustStore((String) map.get("trustStore"));
                config.setSslTrustStorePassword((String) map.get("trustStorePassword"));
                config.setSslTrustStoreType((String) map.get("trustStoreType"));
                String trustAll = (String) map.get("trustAll");
                if (trustAll != null) {
                    config.setSslTrustAll(Boolean.valueOf(trustAll));
                }
                config.setSslAlgorithm((String) map.get("algorithm"));
            } else {
                config.setSslEnabled(value.isBooleanTrue());
            }
        } else if (key.equals("followRedirects")) {
            config.setFollowRedirects(value.isBooleanTrue());
        } else if (key.equals("connectTimeout")) {
            config.setConnectTimeout(Integer.valueOf(value.getAsString()));
        } else if (key.equals("readTimeout")) {
            config.setReadTimeout(Integer.valueOf(value.getAsString()));
        } else if (key.equals("proxy")) {
            if (value.isString()) {
                config.setProxyUri(value.getAsString());
            } else {
                Map<String, Object> map = value.getAsMap();
                config.setProxyUri((String) map.get("uri"));
                config.setProxyUsername((String) map.get("username"));
                config.setProxyPassword((String) map.get("password"));
                config.setNonProxyHosts(((List) ((ScriptObjectMirror) map.get("nonProxyHosts")).values()));
            }
        } else if (key.equals("userDefined")) {
            config.setUserDefined(value.getAsMap());
        } else {
            throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        }
        client.configure(config, this);
    }

    private List<String> evalList(List<String> values) {
        List<String> list = new ArrayList(values.size());
        try {
            for (String value : values) {
                ScriptValue temp = Script.evalKarateExpression(value, this);
                list.add(temp.getAsString());
            }
        } catch (Exception e) { // hack. for e.g. json with commas would land here
            String joined = StringUtils.join(values, ',');
            ScriptValue temp = Script.evalKarateExpression(joined, this);
            if (temp.isListLike()) {
                return temp.getAsList();
            } else {
                return Collections.singletonList(temp.getAsString());
            }
        }
        return list;
    }

    private Map<String, Object> evalMapExpr(String expr) {
        ScriptValue value = Script.evalKarateExpression(expr, this);
        if (!value.isMapLike()) {
            throw new KarateException("cannot convert to map: " + expr);
        }
        return value.getAsMap();
    }

    private String getVarAsString(String name) {
        ScriptValue sv = vars.get(name);
        if (sv == null) {
            throw new RuntimeException("no variable found with name: " + name);
        }
        return sv.getAsString();
    }

    private static String asString(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? null : o.toString();
    }

    public void updateResponseVars(HttpResponse response) {
        vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, response.getStatus());
        vars.put(ScriptValueMap.VAR_REQUEST_TIME_STAMP, response.getStartTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_TIME, response.getResponseTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_COOKIES, response.getCookies());
        if (config.isLowerCaseResponseHeaders()) {
            Object temp = new ScriptValue(response.getHeaders()).toLowerCase();
            vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, temp);
        } else {
            vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, response.getHeaders());
        }
        byte[] responseBytes = response.getBody();
        vars.put(ScriptValueMap.VAR_RESPONSE_BYTES, responseBytes);
        String responseString = FileUtils.toString(responseBytes);
        Object responseBody = responseString;
        responseString = StringUtils.trimToEmpty(responseString);
        if (Script.isJson(responseString)) {
            try {
                responseBody = JsonUtils.toJsonDoc(responseString);
            } catch (Exception e) {
                logger.warn("json parsing failed, response data type set to string: {}", e.getMessage());
            }
        } else if (Script.isXml(responseString)) {
            try {
                responseBody = XmlUtils.toXmlDoc(responseString);
            } catch (Exception e) {
                logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
            }
        }
        vars.put(ScriptValueMap.VAR_RESPONSE, responseBody);
    }

    public void invokeAfterHookIfConfigured(boolean afterFeature) {
        if (callDepth > 0) {
            return;
        }
        ScriptValue sv = afterFeature ? config.getAfterFeature() : config.getAfterScenario();
        if (sv.isFunction()) {
            try {
                sv.invokeFunction(this);
            } catch (Exception e) {
                String prefix = afterFeature ? "afterFeature" : "afterScenario";
                logger.warn("{} hook failed: {}", prefix, e.getMessage());
            }
        }
    }

    //==========================================================================
    
    public void configure(String key, String exp) {
        configure(key, Script.evalKarateExpression(exp, this));
    }

    public void url(String expression) {
        String temp = Script.evalKarateExpression(expression, this).getAsString();
        request.setUrl(temp);
    }

    public void path(List<String> paths) {
        for (String path : paths) {
            ScriptValue temp = Script.evalKarateExpression(path, this);
            if (temp.isListLike()) {
                List list = temp.getAsList();
                for (Object o : list) {
                    if (o == null) {
                        continue;
                    }
                    request.addPath(o.toString());
                }
            } else {
                request.addPath(temp.getAsString());
            }
        }
    }

    public void param(String name, List<String> values) {
        request.setParam(name, evalList(values));
    }

    public void params(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeParam(key);
            } else {
                if (temp instanceof List) {
                    request.setParam(key, (List) temp);
                } else {
                    request.setParam(key, temp.toString());
                }
            }
        }
    }

    public void cookie(String name, String value) {
        ScriptValue sv = Script.evalKarateExpression(value, this);
        Cookie cookie;
        if (sv.isMapLike()) {
            cookie = new Cookie((Map) sv.getAsMap());
            cookie.put(Cookie.NAME, name);
        } else {
            cookie = new Cookie(name, sv.getAsString());
        }
        request.setCookie(cookie);
    }

    public void cookies(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeCookie(key);
            } else {
                request.setCookie(new Cookie(key, temp.toString()));
            }
        }
    }

    public void header(String name, List<String> values) {
        request.setHeader(name, evalList(values));
    }

    public void headers(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeHeader(key);
            } else {
                if (temp instanceof List) {
                    request.setHeader(key, (List) temp);
                } else {
                    request.setHeader(key, temp.toString());
                }
            }
        }
    }

    public void formField(String name, List<String> values) {
        request.setFormField(name, evalList(values));
    }

    public void formFields(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeFormField(key);
            } else {
                if (temp instanceof List) {
                    request.setFormField(key, (List) temp);
                } else {
                    request.setFormField(key, temp.toString());
                }
            }
        }
    }

    public void request(String requestBody) {
        ScriptValue temp = Script.evalKarateExpression(requestBody, this);
        request.setBody(temp);
    }

    public void table(String name, List<Map<String, String>> table) {
        int pos = name.indexOf('='); // backward compatibility, we used to require this till v0.5.0
        if (pos != -1) {
            name = name.substring(0, pos);
        }
        List<Map<String, Object>> list = Script.evalTable(table, this);
        DocumentContext doc = JsonPath.parse(list);
        vars.put(name.trim(), doc);
    }

    public void replace(String name, List<Map<String, String>> table) {
        name = name.trim();
        String text = getVarAsString(name);
        String replaced = Script.replacePlaceholders(text, table, this);
        vars.put(name, replaced);
    }

    public void replace(String name, String token, String value) {
        name = name.trim();
        String text = getVarAsString(name);
        String replaced = Script.replacePlaceholderText(text, token, value, this);
        vars.put(name, replaced);
    }

    public void assign(AssignType assignType, String name, String exp) {
        Script.assign(assignType, name, exp, this, true);
    }

    public void assertTrue(String expression) {
        AssertionResult ar = Script.assertBoolean(expression, this);
        if (!ar.pass) {
            logger.error("{}", ar);
            throw new KarateException(ar.message);
        }
    }

    public void method(String method) {
        if (!HttpUtils.HTTP_METHODS.contains(method.toUpperCase())) { // support expressions also
            method = Script.evalKarateExpression(method, this).getAsString();
        }
        request.setMethod(method);
        try {
            response = client.invoke(request, this);
        } catch (Exception e) {
            String message = e.getMessage();
            logger.error("http request failed: {}", message);
            throw new KarateException(message); // reduce log verbosity
        }
        updateResponseVars(response);
        String prevUrl = request.getUrl();
        request = new HttpRequestBuilder();
        request.setUrl(prevUrl);
    }

    public void soapAction(String action) {
        action = Script.evalKarateExpression(action, this).getAsString();
        if (action == null) {
            action = "";
        }
        request.setHeader("SOAPAction", action);
        request.setHeader("Content-Type", "text/xml");
        method("post");
    }

    public void multipartField(String name, String value) {
        ScriptValue sv = Script.evalKarateExpression(value, this);
        request.addMultiPartItem(name, sv);
    }

    public void multipartFields(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        map.forEach((k, v) -> {
            ScriptValue sv = new ScriptValue(v);
            request.addMultiPartItem(k, sv);
        });
    }

    public void multipartFile(String name, String value) {
        name = name.trim();
        ScriptValue sv = Script.evalKarateExpression(value, this);
        if (!sv.isMapLike()) {
            throw new RuntimeException("mutipart file value should be json");
        }
        Map<String, Object> map = sv.getAsMap();
        String read = asString(map, "read");
        if (read == null) {
            throw new RuntimeException("mutipart file json should have a value for 'read'");
        }
        ScriptValue fileValue = FileUtils.readFile(read, this);
        MultiPartItem item = new MultiPartItem(name, fileValue);
        String filename = asString(map, "filename");
        if (filename == null) {
            filename = name;
        }
        item.setFilename(filename);
        String contentType = asString(map, "contentType");
        if (contentType != null) {
            item.setContentType(contentType);
        }
        request.addMultiPartItem(item);
    }

    public void multipartFiles(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        map.forEach((k, v) -> {
            ScriptValue sv = new ScriptValue(v);
            multipartFile(k, sv.getAsString());
        });
    }

    public void print(List<String> exps) {
        if (isPrintEnabled()) {
            String prev = ""; // handle rogue commas embedded in string literals
            StringBuilder sb = new StringBuilder();
            sb.append("[print]");
            for (String exp : exps) {
                if (!prev.isEmpty()) {
                    exp = prev + StringUtils.trimToNull(exp);
                }
                if (exp == null) {
                    sb.append("null");
                } else {
                    ScriptValue sv = Script.getIfVariableReference(exp, this);
                    if (sv == null) {
                        try {
                            sv = Script.evalJsExpression(exp, this);
                            prev = ""; // evalKarateExpression success, reset rogue comma detector
                        } catch (Exception e) {
                            prev = exp + ", ";
                            continue;
                        }
                    }
                    sb.append(' ').append(sv.getAsPrettyString());
                }
            }
            logger.info("{}", sb);
        }
    }

    public void status(int status) {
        if (status != response.getStatus()) {
            String rawResponse = vars.get(ScriptValueMap.VAR_RESPONSE).getAsString();
            String responseTime = vars.get(ScriptValueMap.VAR_RESPONSE_TIME).getAsString();
            String message = "status code was: " + response.getStatus() + ", expected: " + status
                    + ", response time: " + responseTime + ", url: " + response.getUri() + ", response: " + rawResponse;
            logger.error(message);
            throw new KarateException(message);
        }
    }

    public void match(MatchType matchType, String name, String path, String expected) {
        AssertionResult ar = Script.matchNamed(matchType, name, path, expected, this);
        if (!ar.pass) {
            logger.error("{}", ar);
            throw new KarateException(ar.message);
        }
    }

    public void set(String name, String path, String value) {
        Script.setValueByPath(name, path, value, this);
    }

    public void set(String name, String path, List<Map<String, String>> table) {
        Script.setByPathTable(name, path, table, this);
    }

    public void remove(String name, String path) {
        Script.removeValueByPath(name, path, this);
    }

    public void call(boolean callonce, String name, String arg) {
        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(callonce, name, arg, this);
    }

    public void eval(String exp) {
        Script.evalJsExpression(exp, this);
    }
    
    //==========================================================================
    
    public void location(String expression) {        
        if (driver == null) {
            driver = DriverUtils.construct(config.getDriverOptions());
            bindings.setDriver(driver);
        }
        String temp = Script.evalKarateExpression(expression, this).getAsString();
        driver.location(temp);
    }
    
    public void input(String name, String value) {
        String temp = Script.evalKarateExpression(value, this).getAsString();
        driver.input(name, temp);
    }
    
    public void click(String name) {
        driver.click(name);
    }
    
    public void submit(String name) {
        driver.submit(name);
    }    
    
    public void stop() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

}
