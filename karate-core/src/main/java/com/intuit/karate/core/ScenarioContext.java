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
package com.intuit.karate.core;

import com.intuit.karate.AssertionResult;
import com.intuit.karate.AssignType;
import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.exception.KarateFileNotFoundException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.Config;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.netty.WebSocketClient;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class ScenarioContext {

    public final Logger logger;
    public final ScriptBindings bindings;
    public final int callDepth;
    public final boolean reuseParentContext;
    public final ScenarioContext parentContext;
    public final List<String> tags;
    public final Map<String, List<String>> tagValues;
    public final ScriptValueMap vars;
    public final FeatureContext rootFeatureContext;
    public final FeatureContext featureContext;
    public final ExecutionHook executionHook;
    public final boolean perfMode;
    public final ScenarioInfo scenarioInfo;

    // these can get re-built or swapped, so cannot be final
    private Config config;
    private HttpClient client;
    private Driver driver;

    private HttpRequestBuilder request = new HttpRequestBuilder();

    // the actual http request/response last sent on the wire    
    private HttpRequest prevRequest;
    private HttpResponse prevResponse;

    // pass call result to engine via this variable (hack)
    private List<FeatureResult> callResults;

    // gatling integration    
    private PerfEvent prevPerfEvent;

    // report embed
    protected Embed prevEmbed;

    // ui support
    private Function<CallContext, FeatureResult> callable;

    // websocket
    private final Object LOCK = new Object();
    private List<WebSocketClient> webSocketClients;
    private Object signalResult;

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

    public void setPrevResponse(HttpResponse prevResponse) {
        this.prevResponse = prevResponse;
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

    public Config getConfig() {
        return config;
    }

    public void setCallable(Function<CallContext, FeatureResult> callable) {
        this.callable = callable;
    }

    public Function<CallContext, FeatureResult> getCallable() {
        return callable;
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
        this.featureContext = featureContext;
        logger = featureContext.logger;
        callDepth = call.callDepth;
        reuseParentContext = call.reuseParentContext;
        executionHook = call.executionHook;
        perfMode = call.perfMode;
        tags = call.getTags().getTags();
        tagValues = call.getTags().getTagValues();
        scenarioInfo = call.getScenarioInfo();
        if (reuseParentContext) {
            parentContext = call.context;
            vars = call.context.vars; // shared context !
            config = call.context.config;
            rootFeatureContext = call.context.rootFeatureContext;
            driver = call.context.driver;
            webSocketClients = call.context.webSocketClients;
        } else if (call.context != null) {
            parentContext = call.context;
            // complex objects like JSON and XML are "global by reference" TODO           
            vars = call.context.vars.copy(false);
            config = new Config(call.context.config);
            rootFeatureContext = call.context.rootFeatureContext;
        } else {
            parentContext = null;
            vars = new ScriptValueMap();
            config = new Config();
            config.setClientClass(call.httpClientClass);
            rootFeatureContext = featureContext;
        }
        client = HttpClient.construct(config, this);
        bindings = new ScriptBindings(this);
        if (call.context == null && call.evalKarateConfig) {
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
            call.callArg.forEach((k, v) -> vars.put(k, v));
            vars.put(Script.VAR_ARG, call.callArg);
            vars.put(Script.VAR_LOOP, call.loopIndex);
        } else if (call.context != null) {
            vars.put(Script.VAR_ARG, ScriptValue.NULL);
            vars.put(Script.VAR_LOOP, -1);
        }
        logger.trace("karate context init - initial properties: {}", vars);
    }

    public ScenarioContext copy(ScenarioInfo info) {
        return new ScenarioContext(this, info);
    }

    public ScenarioContext copy() {
        return new ScenarioContext(this, scenarioInfo);
    }

    private ScenarioContext(ScenarioContext sc, ScenarioInfo info) {
        featureContext = sc.featureContext;
        logger = sc.logger;
        callDepth = sc.callDepth;
        reuseParentContext = sc.reuseParentContext;
        parentContext = sc.parentContext;
        executionHook = sc.executionHook;
        perfMode = sc.perfMode;
        tags = sc.tags;
        tagValues = sc.tagValues;
        scenarioInfo = info;
        vars = sc.vars.copy(true); // deep / snap-shot copy
        config = new Config(sc.config); // safe copy
        rootFeatureContext = sc.rootFeatureContext;
        client = HttpClient.construct(config, this);
        bindings = new ScriptBindings(this);
        // state
        request = sc.request.copy();
        driver = sc.driver;
        prevRequest = sc.prevRequest;
        prevResponse = sc.prevResponse;
        prevPerfEvent = sc.prevPerfEvent;
        callResults = sc.callResults;
        webSocketClients = sc.webSocketClients;
        signalResult = sc.signalResult;
    }

    public void configure(Config config) {
        this.config = config;
        client = HttpClient.construct(config, this);
    }

    public void configure(String key, ScriptValue value) { // TODO use enum
        key = StringUtils.trimToEmpty(key);
        // if next line returns true, http-client needs re-building
        if (config.configure(key, value)) {
            if (key.startsWith("httpClient")) { // special case
                client = HttpClient.construct(config, this);
            } else {
                client.configure(config, this);
            }
        }
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

    public void updateResponseVars() {
        vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, prevResponse.getStatus());
        vars.put(ScriptValueMap.VAR_REQUEST_TIME_STAMP, prevResponse.getStartTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_TIME, prevResponse.getResponseTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_COOKIES, prevResponse.getCookies());
        if (config.isLowerCaseResponseHeaders()) {
            Object temp = new ScriptValue(prevResponse.getHeaders()).toLowerCase();
            vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, temp);
        } else {
            vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, prevResponse.getHeaders());
        }
        byte[] responseBytes = prevResponse.getBody();
        bindings.putAdditionalVariable(ScriptValueMap.VAR_RESPONSE_BYTES, responseBytes);
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
                sv.invokeFunction(this, null);
            } catch (Exception e) {
                String prefix = afterFeature ? "afterFeature" : "afterScenario";
                logger.warn("{} hook failed: {}", prefix, e.getMessage());
            }
        }
    }

    //==========================================================================
    //
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

    public void request(ScriptValue body) {
        request.setBody(body);
    }

    public void request(String requestBody) {
        ScriptValue temp = Script.evalKarateExpression(requestBody, this);
        request(temp);
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

    private void clientInvoke() {
        try {
            prevResponse = client.invoke(request, this);
            updateResponseVars();
        } catch (Exception e) {
            String message = e.getMessage();
            logger.error("http request failed: {}", message);
            throw new KarateException(message); // reduce log verbosity
        }
    }

    private void clientInvokeWithRetries() {
        int maxRetries = config.getRetryCount();
        int sleep = config.getRetryInterval();
        int retryCount = 0;
        while (true) {
            if (retryCount == maxRetries) {
                throw new KarateException("too many retry attempts: " + maxRetries);
            }
            if (retryCount > 0) {
                try {
                    logger.debug("sleeping before retry #{}", retryCount);
                    Thread.sleep(sleep);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            clientInvoke();
            ScriptValue sv;
            try {
                sv = Script.evalKarateExpression(request.getRetryUntil(), this);
            } catch (Exception e) {
                logger.warn("retry condition evaluation failed: {}", e.getMessage());
                sv = ScriptValue.NULL;
            }
            if (sv.isBooleanTrue()) {
                if (retryCount > 0) {
                    logger.debug("retry condition satisfied");
                }
                break;
            } else {
                logger.debug("retry condition not satisfied: {}", request.getRetryUntil());
            }
            retryCount++;
        }
    }

    public void method(String method) {
        if (!HttpUtils.HTTP_METHODS.contains(method.toUpperCase())) { // support expressions also
            method = Script.evalKarateExpression(method, this).getAsString();
        }
        request.setMethod(method);
        if (request.isRetry()) {
            clientInvokeWithRetries();
        } else {
            clientInvoke();
        }
        String prevUrl = request.getUrl();
        request = new HttpRequestBuilder();
        request.setUrl(prevUrl);
    }

    public void retry(String expression) {
        request.setRetryUntil(expression);
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
        ScriptValue fileValue;
        Map<String, Object> map = sv.getAsMap();
        String read = asString(map, "read");
        if (read == null) {
            Object o = map.get("value");
            fileValue = o == null ? null : new ScriptValue(o);
        } else {
            fileValue = FileUtils.readFile(read, this);
        }
        if (fileValue == null) {
            throw new RuntimeException("mutipart file json should have a value for 'read' or 'value'");
        }
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
        if (status != prevResponse.getStatus()) {
            String rawResponse = vars.get(ScriptValueMap.VAR_RESPONSE).getAsString();
            String responseTime = vars.get(ScriptValueMap.VAR_RESPONSE_TIME).getAsString();
            String message = "status code was: " + prevResponse.getStatus() + ", expected: " + status
                    + ", response time: " + responseTime + ", url: " + prevResponse.getUri() + ", response: " + rawResponse;
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

    public Embed getAndClearEmbed() {
        Embed temp = prevEmbed;
        prevEmbed = null;
        return temp;
    }

    public void embed(byte[] bytes, String contentType) {
        Embed embed = new Embed();
        embed.setBytes(bytes);
        embed.setMimeType(contentType);
        prevEmbed = embed;
    }

    // websocket / async =======================================================
    private WebSocketClient createWebSocketClient(String url, Consumer<String> textHandler, Consumer<byte[]> binaryHandler) {
        WebSocketClient webSocketClient = new WebSocketClient(url, textHandler, binaryHandler);
        if (webSocketClients == null) {
            webSocketClients = new ArrayList();
        }
        webSocketClients.add(webSocketClient);
        return webSocketClient;
    }

    public WebSocketClient webSocket(String url, Consumer<String> textHandler) {
        return webSocket(url, textHandler, null);
    }

    public WebSocketClient webSocket(String url, Consumer<String> textHandler, Consumer<byte[]> binaryHandler) {
        return createWebSocketClient(url, textHandler, binaryHandler);
    }

    public void signal(Object result) {
        logger.trace("signal called: {}", result);
        synchronized (LOCK) {
            signalResult = result;
            LOCK.notify();
        }
    }

    public Object listen(long timeout, Runnable runnable) {
        if (runnable != null) {
            logger.trace("submitting listen function");
            new Thread(runnable).start();
        }
        synchronized (LOCK) {
            if (signalResult != null) {
                logger.debug("signal arrived early ! result: {}", signalResult);
                Object temp = signalResult;
                signalResult = null;
                return temp;
            }
            try {
                logger.trace("entered listen wait state");
                LOCK.wait(timeout);
                logger.trace("exit listen wait state, result: {}", signalResult);
            } catch (InterruptedException e) {
                logger.error("listen timed out: {}", e.getMessage());
            }
            return signalResult;
        }
    }

    // driver ==================================================================       
    //
    private void setDriver(Driver driver) {
        this.driver = driver;
        bindings.putAdditionalVariable(ScriptBindings.DRIVER, driver);
    }

    public void driver(String expression) {
        ScriptValue sv = Script.evalKarateExpression(expression, this);
        if (driver == null) {
            Map<String, Object> options = config.getDriverOptions();
            if (options == null) {
                options = new HashMap();
            }
            if (sv.isMapLike()) {
                options.putAll(sv.getAsMap());
            }
            setDriver(DriverOptions.start(this, options, logger));
        }
        if (sv.isString()) {
            driver.setLocation(sv.getAsString());
        }
    }

    public void driverDot(String expression) {
        eval(ScriptBindings.DRIVER_DOT + expression);
    }

    public void stop() {
        if (reuseParentContext) {
            if (driver != null) {
                parentContext.setDriver(driver);
            }
            parentContext.webSocketClients = webSocketClients;
            return;
        }
        if (webSocketClients != null) {
            webSocketClients.forEach(WebSocketClient::close);
        }
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

}
