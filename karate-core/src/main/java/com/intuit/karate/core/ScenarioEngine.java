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

import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.KarateException;
import com.intuit.karate.Logger;
import com.intuit.karate.Match;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Key;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsExecutable;
import com.intuit.karate.graal.JsFunction;
import com.intuit.karate.graal.JsLambda;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.*;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.shell.Command;
import com.intuit.karate.template.KarateTemplateEngine;
import com.intuit.karate.template.TemplateUtils;
import com.jayway.jsonpath.PathNotFoundException;
import org.graalvm.polyglot.Value;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScenarioEngine {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ScenarioEngine.class);

    private static final String KARATE = "karate";
    private static final String READ = "read";
    private static final String DRIVER = "driver";
    private static final String ROBOT = "robot";
    private static final String KEY = "Key";

    public static final String RESPONSE = "response";
    public static final String RESPONSE_HEADERS = "responseHeaders";
    public static final String RESPONSE_STATUS = "responseStatus";
    private static final String RESPONSE_BYTES = "responseBytes";
    private static final String RESPONSE_COOKIES = "responseCookies";
    private static final String RESPONSE_TIME = "responseTime";
    private static final String RESPONSE_TYPE = "responseType";

    private static final String LISTEN_RESULT = "listenResult";

    public static final String REQUEST = "request";
    public static final String REQUEST_URL_BASE = "requestUrlBase";
    public static final String REQUEST_URI = "requestUri";
    private static final String REQUEST_PARAMS = "requestParams";
    public static final String REQUEST_METHOD = "requestMethod";
    public static final String REQUEST_HEADERS = "requestHeaders";
    private static final String REQUEST_TIME_STAMP = "requestTimeStamp";

    public final ScenarioRuntime runtime;
    public final ScenarioFileReader fileReader;
    public final Map<String, Variable> vars;
    public final Logger logger;

    private final Function<String, Object> readFunction;
    private final ScenarioBridge bridge;
    private final Collection<RuntimeHook> hooks;

    private boolean aborted;
    private Throwable failedReason;

    protected JsEngine JS;

    // only used by mock server
    public ScenarioEngine(ScenarioRuntime runtime, Map<String, Variable> vars) {
        this(runtime.engine.config, runtime, vars, runtime.logger);
    }

    public ScenarioEngine(Config config, ScenarioRuntime runtime, Map<String, Variable> vars, Logger logger) {
        this.config = config;
        this.runtime = runtime;
        hooks = runtime.featureRuntime.suite.hooks;
        fileReader = new ScenarioFileReader(this, runtime.featureRuntime);
        readFunction = s -> JsValue.fromJava(fileReader.readFile(s));
        bridge = new ScenarioBridge(this);
        this.vars = vars;
        this.logger = logger;
    }

    public static ScenarioEngine forTempUse() {
        FeatureRuntime fr = FeatureRuntime.forTempUse();
        ScenarioRuntime sr = new ScenarioIterator(fr).first();
        sr.engine.init();
        return sr.engine;
    }

    private static final ThreadLocal<ScenarioEngine> THREAD_LOCAL = new ThreadLocal<ScenarioEngine>();

    public static ScenarioEngine get() {
        return THREAD_LOCAL.get();
    }

    public static void set(ScenarioEngine se) {
        THREAD_LOCAL.set(se);
    }

    protected static void remove() {
        THREAD_LOCAL.remove();
    }

    // engine ==================================================================
    //
    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    public boolean isFailed() {
        return failedReason != null;
    }

    public boolean isIgnoringStepErrors() {
        return !config.getContinueOnStepFailureMethods().isEmpty();
    }

    public void setFailedReason(Throwable failedReason) {
        this.failedReason = failedReason;
    }

    public Throwable getFailedReason() {
        return failedReason;
    }

    public void matchResult(Match.Type matchType, String expression, String path, String expected) {
        Match.Result mr = match(matchType, expression, path, expected);
        if (!mr.pass) {
            setFailedReason(new KarateException(mr.message));
        }
    }

    public void set(String name, String path, String exp) {
        set(name, path, exp, false, false);
    }

    public void remove(String name, String path) {
        set(name, path, null, true, false);
    }

    public void table(String name, List<Map<String, String>> rows) {
        name = StringUtils.trimToEmpty(name);
        validateVariableName(name);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, String> map : rows) {
            Map<String, Object> row = new LinkedHashMap<>(map);
            List<String> toRemove = new ArrayList(map.size());
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String exp = (String) entry.getValue();
                Variable sv = evalKarateExpression(exp);
                if (sv.isNull() && !isWithinParentheses(exp)) { // by default empty / null will be stripped, force null like this: '(null)'
                    toRemove.add(entry.getKey());
                } else {
                    if (sv.isString()) {
                        entry.setValue(sv.getAsString());
                    } else { // map, list etc
                        entry.setValue(sv.getValue());
                    }
                }
            }
            for (String keyToRemove : toRemove) {
                row.remove(keyToRemove);
            }
            result.add(row);
        }
        setVariable(name, result);
    }

    public void replace(String name, String token, String value) {
        name = name.trim();
        Variable v = vars.get(name);
        if (v == null) {
            throw new RuntimeException("no variable found with name: " + name);
        }
        String text = v.getAsString();
        String replaced = replacePlaceholderText(text, token, value);
        setVariable(name, replaced);
    }

    public void assertTrue(String expression) {
        if (!evalJs(expression).isTrue()) {
            String message = "did not evaluate to 'true': " + expression;
            setFailedReason(new KarateException(message));
        }
    }

    public void print(String exp) {
        if (!config.isPrintEnabled()) {
            return;
        }
        evalJs("karate.log('[print]'," + exp + ")");
    }

    public void invokeAfterHookIfConfigured(boolean afterFeature) {
        if (runtime.caller.depth > 0) {
            return;
        }
        Variable v = afterFeature ? config.getAfterFeature() : config.getAfterScenario();
        if (v.isJsOrJavaFunction()) {
            if (afterFeature) {
                ScenarioEngine.set(this); // for any bridge / js to work
            }
            try {
                executeFunction(v);
            } catch (Exception e) {
                String prefix = afterFeature ? "afterFeature" : "afterScenario";
                logger.warn("{} hook failed: {}", prefix, e + "");
            }
        }
    }

    // gatling =================================================================
    //   
    private PerfEvent prevPerfEvent;

    public void logLastPerfEvent(String failureMessage) {
        if (prevPerfEvent != null && runtime.perfMode) {
            if (failureMessage != null) {
                prevPerfEvent.setFailed(true);
                prevPerfEvent.setMessage(failureMessage);
            }
            runtime.featureRuntime.perfHook.reportPerfEvent(prevPerfEvent);
        }
        prevPerfEvent = null;
    }

    public void capturePerfEvent(PerfEvent event) {
        logLastPerfEvent(null);
        prevPerfEvent = event;
    }

    // http ====================================================================
    //
    protected HttpRequestBuilder requestBuilder; // see init() method
    private HttpRequest request;
    private Response response;
    private Config config;

    public Config getConfig() {
        return config;
    }

    // important: use this to trigger client re-config
    // callonce routine is one example
    public void setConfig(Config config) {
        this.config = config;
        config.attach(JS);
        if (requestBuilder != null) {
            requestBuilder.client.setConfig(config);
        }
    }

    public HttpRequest getRequest() {
        return request;
    }

    public Response getResponse() {
        return response;
    }

    public HttpRequestBuilder getRequestBuilder() {
        return requestBuilder;
    }

    public void configure(String key, String exp) {
        Variable v = evalKarateExpression(exp);
        configure(key, v);
    }

    public void configure(String key, Variable v) {
        key = StringUtils.trimToEmpty(key);
        // if next line returns true, config is http-client related
        if (config.configure(key, v)) {
            if (requestBuilder != null) {
                requestBuilder.client.setConfig(config);
            }
        }
    }

    private void evalAsMap(String exp, BiConsumer<String, List<String>> fun) {
        Variable var = evalKarateExpression(exp);
        if (!var.isMap()) {
            logger.warn("did not evaluate to map {}: {}", exp, var);
            return;
        }
        Map<String, Object> map = var.getValue();
        map.forEach((k, v) -> {
            if (v instanceof List) {
                List list = (List) v;
                List<String> values = new ArrayList(list.size());
                for (Object o : list) { // support non-string values, e.g. numbers
                    if (o != null) {
                        values.add(o.toString());
                    }
                }
                fun.accept(k, values);
            } else if (v != null) {
                fun.accept(k, Collections.singletonList(v.toString()));
            }
        });
    }

    public void url(String exp) {
        Variable var = evalKarateExpression(exp);
        requestBuilder.url(var.getAsString());
    }

    public void path(String exp) {
        if (exp.contains(",")) {
            exp = "[" + exp + "]";
        }
        Variable v = evalJs(exp);
        List<?> list;
        if (v.isList()) {
            list = v.getValue();
        } else {
            list = Collections.singletonList(v.getValue());
        }
        for (Object o : list) {
            if (o != null) {
                requestBuilder.path(o.toString());
            }
        }
    }

    public void param(String name, String exp) {
        Variable var = evalJs(exp);
        if (var.isList()) {
            requestBuilder.param(name, var.<List>getValue());
        } else {
            requestBuilder.param(name, var.getAsString());
        }
    }

    public void params(String expr) {
        evalAsMap(expr, (k, v) -> requestBuilder.param(k, v));
    }

    public void header(String name, String exp) {
        Variable var = evalKarateExpression(exp);
        if (var.isList()) {
            requestBuilder.header(name, var.<List>getValue());
        } else {
            requestBuilder.header(name, var.getAsString());
        }
    }

    public void headers(String expr) {
        evalAsMap(expr, (k, v) -> requestBuilder.header(k, v));
    }

    public void cookie(String name, String exp) {
        Variable var = evalKarateExpression(exp);
        if (var.isString()) {
            requestBuilder.cookie(name, var.getAsString());
        } else if (var.isMap()) {
            Map<String, Object> map = var.getValue();
            map.put("name", name);
            requestBuilder.cookie(map);
        }
    }

    public void cookies(String exp) {
        Variable var = evalKarateExpression(exp);
        Map<String, Map> cookies = Cookies.normalize(var.getValue());
        requestBuilder.cookies(cookies.values());
    }

    private void updateConfigCookies(Map<String, Map> cookies) {
        if (cookies == null) {
            return;
        }
        if (config.getCookies().isNull()) {
            config.setCookies(new Variable(cookies));
        } else {
            Map<String, Map> map = getOrEvalAsMap(config.getCookies());
            map.putAll(cookies);
            config.setCookies(new Variable(map));
        }
    }

    public void formField(String name, String exp) {
        Variable var = evalKarateExpression(exp);
        if (var.isList()) {
            requestBuilder.formField(name, var.<List>getValue());
        } else {
            requestBuilder.formField(name, var.getAsString());
        }
    }

    public void formFields(String exp) {
        Variable var = evalKarateExpression(exp);
        if (var.isMap()) {
            Map<String, Object> map = var.getValue();
            map.forEach((k, v) -> {
                requestBuilder.formField(k, v);
            });
        } else {
            logger.warn("did not evaluate to map {}: {}", exp, var);
        }
    }

    public void multipartField(String name, String value) {
        multipartFile(name, value);
    }

    public void multipartFields(String exp) {
        multipartFiles(exp);
    }

    private void multiPartInternal(String name, Object value) {
        Map<String, Object> map = new HashMap();
        if (name != null) {
            map.put("name", name);
        }
        if (value instanceof Map) {
            map.putAll((Map) value);
            String toRead = (String) map.get("read");
            if (toRead != null) {
                Resource resource = fileReader.toResource(toRead);
                if (resource.isFile()) {
                    File file = resource.getFile();
                    map.put("value", file);
                } else {
                    map.put("value", FileUtils.toBytes(resource.getStream()));
                }
            }
            requestBuilder.multiPart(map);
        } else if (value instanceof String) {
            map.put("value", (String) value);
            multiPartInternal(name, map);
        } else if (value instanceof List) {
            List list = (List) value;
            for (Object o : list) {
                multiPartInternal(null, o);
            }
        } else if (logger.isTraceEnabled()) {
            logger.trace("did not evaluate to string, map or list {}: {}", name, value);
        }
    }

    public void multipartFile(String name, String exp) {
        Variable var = evalKarateExpression(exp);
        multiPartInternal(name, var.getValue());
    }

    public void multipartFiles(String exp) {
        Variable var = evalKarateExpression(exp);
        if (var.isMap()) {
            Map<String, Object> map = var.getValue();
            map.forEach((k, v) -> multiPartInternal(k, v));
        } else if (var.isList()) {
            List<Map> list = var.getValue();
            for (Map map : list) {
                multiPartInternal(null, map);
            }
        } else {
            logger.warn("did not evaluate to map or list {}: {}", exp, var);
        }
    }

    public void request(String body) {
        Variable v = evalKarateExpression(body);
        requestBuilder.body(v.getValue());
    }

    public void soapAction(String exp) {
        String action = evalKarateExpression(exp).getAsString();
        if (action == null) {
            action = "";
        }
        requestBuilder.header("SOAPAction", action);
        requestBuilder.contentType("text/xml");
        method("POST");
    }

    public void retry(String condition) {
        requestBuilder.setRetryUntil(condition);
    }

    public void method(String method) {
        if (!HttpConstants.HTTP_METHODS.contains(method.toUpperCase())) { // support expressions also
            method = evalKarateExpression(method).getAsString();
        }
        requestBuilder.method(method);
        httpInvoke();
    }

    // extracted for mock proceed()
    public Response httpInvoke() {
        if (requestBuilder.isRetry()) {
            httpInvokeWithRetries();
        } else {
            httpInvokeOnce();
        }
        requestBuilder.reset();
        return response;
    }

    private void httpInvokeOnce() {
        Map<String, Map> cookies = getOrEvalAsMap(config.getCookies());
        if (cookies != null) {
            requestBuilder.cookies(cookies.values());
        }
        Map<String, Object> headers;
        if (config.getHeaders().isJsOrJavaFunction()) {
            headers = getOrEvalAsMap(config.getHeaders(), requestBuilder.build());
        } else {
            headers = getOrEvalAsMap(config.getHeaders()); // avoid an extra http request build
        }
        if (headers != null) {
            requestBuilder.headers(headers);
        }
        request = requestBuilder.build();
        String perfEventName = null; // acts as a flag to report perf if not null
        if (runtime.perfMode) {
            perfEventName = runtime.featureRuntime.perfHook.getPerfEventName(request, runtime);
        }
        long startTime = System.currentTimeMillis();
        request.setStartTimeMillis(startTime); // this may be fine-adjusted by actual http client
        if (hooks != null) {
            hooks.forEach(h -> h.beforeHttpCall(request, runtime));
        }
        try {
            response = requestBuilder.client.invoke(request);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            String message = "http call failed after " + responseTime + " milliseconds for url: " + request.getUrl();
            logger.error(e.getMessage() + ", " + message);
            if (perfEventName != null) {
                PerfEvent pe = new PerfEvent(startTime, endTime, perfEventName, 0);
                capturePerfEvent(pe); // failure flag and message should be set by logLastPerfEvent()
            }
            throw new KarateException(message, e);
        }
        if (hooks != null) {
            hooks.forEach(h -> h.afterHttpCall(request, response, runtime));
        }
        byte[] bytes = response.getBody();
        Object body;
        String responseType;
        ResourceType resourceType = response.getResourceType();
        if (resourceType != null && resourceType.isBinary()) {
            responseType = "binary";
            body = bytes;
        } else {
            try {
                body = JsValue.fromBytes(bytes, true, resourceType);
            } catch (Exception e) {
                body = FileUtils.toString(bytes);
                logger.warn("auto-conversion of response failed: {}", e.getMessage());
            }
            if (body instanceof Map || body instanceof List) {
                responseType = "json";
            } else if (body instanceof Node) {
                responseType = "xml";
            } else {
                responseType = "string";
            }
        }
        setVariable(RESPONSE_STATUS, response.getStatus());
        setVariable(RESPONSE, body);
        if (config.isLowerCaseResponseHeaders()) {
            setVariable(RESPONSE_HEADERS, response.getHeadersWithLowerCaseNames());
        } else {
            setVariable(RESPONSE_HEADERS, response.getHeaders());
        }
        setHiddenVariable(RESPONSE_BYTES, bytes);
        setHiddenVariable(RESPONSE_TYPE, responseType);
        cookies = response.getCookies();
        updateConfigCookies(cookies);
        setHiddenVariable(RESPONSE_COOKIES, cookies);
        startTime = request.getStartTimeMillis(); // in case it was re-adjusted by http client
        long endTime = request.getEndTimeMillis();
        setHiddenVariable(REQUEST_TIME_STAMP, startTime);
        setHiddenVariable(RESPONSE_TIME, endTime - startTime);
        if (perfEventName != null) {
            PerfEvent pe = new PerfEvent(startTime, endTime, perfEventName, response.getStatus());
            capturePerfEvent(pe);
        }
    }

    private void httpInvokeWithRetries() {
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
            httpInvokeOnce();
            Variable v;
            try {
                v = evalKarateExpression(requestBuilder.getRetryUntil());
            } catch (Exception e) {
                logger.warn("retry condition evaluation failed: {}", e.getMessage());
                v = Variable.NULL;
            }
            if (v.isTrue()) {
                if (retryCount > 0) {
                    logger.debug("retry condition satisfied");
                }
                break;
            } else {
                logger.debug("retry condition not satisfied: {}", requestBuilder.getRetryUntil());
            }
            retryCount++;
        }
    }

    public void status(int status) {
        if (status != response.getStatus()) {
            // make sure log masking is applied
            String message = HttpLogger.getStatusFailureMessage(status, config, request, response);
            setFailedReason(new KarateException(message));
        }
    }

    public KeyStore getKeyStore(String trustStoreFile, String password, String type) {
        if (trustStoreFile == null) {
            return null;
        }
        char[] passwordChars = password == null ? null : password.toCharArray();
        if (type == null) {
            type = KeyStore.getDefaultType();
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            InputStream is = fileReader.readFileAsStream(trustStoreFile);
            keyStore.load(is, passwordChars);
            logger.debug("key store key count for {}: {}", trustStoreFile, keyStore.size());
            return keyStore;
        } catch (Exception e) {
            logger.error("key store init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // http mock ===============================================================
    //
    public void mockProceed(String requestUrlBase) {
        String urlBase = requestUrlBase == null ? vars.get(REQUEST_URL_BASE).getValue() : requestUrlBase;
        String uri = vars.get(REQUEST_URI).getValue();
        String url = uri == null ? urlBase : urlBase + "/" + uri;
        requestBuilder.url(url);
        requestBuilder.params(vars.get(REQUEST_PARAMS).getValue());
        requestBuilder.method(vars.get(REQUEST_METHOD).getValue());
        requestBuilder.headers(vars.get(REQUEST_HEADERS).<Map>getValue());
        requestBuilder.removeHeader(HttpConstants.HDR_CONTENT_LENGTH);
        requestBuilder.body(vars.get(REQUEST).getValue());
        if (requestBuilder.client instanceof ArmeriaHttpClient) {
            Request mockRequest = MockHandler.LOCAL_REQUEST.get();
            if (mockRequest != null) {
                ArmeriaHttpClient client = (ArmeriaHttpClient) requestBuilder.client;
                client.setRequestContext(mockRequest.getRequestContext());
            }
        }
        httpInvoke();
    }

    public Map<String, Object> mockConfigureHeaders() {
        return getOrEvalAsMap(config.getResponseHeaders());
    }

    public void mockAfterScenario() {
        if (config.getAfterScenario().isJsOrJavaFunction()) {
            executeFunction(config.getAfterScenario());
        }
    }

    // websocket / async =======================================================
    //   
    private List<WebSocketClient> webSocketClients;
    private CompletableFuture SIGNAL = new CompletableFuture();

    public WebSocketClient webSocket(WebSocketOptions options) {
        WebSocketClient webSocketClient = new WebSocketClient(options, logger);
        if (webSocketClients == null) {
            webSocketClients = new ArrayList();
        }
        webSocketClients.add(webSocketClient);
        return webSocketClient;
    }

    public synchronized void signal(Object result) {
        SIGNAL.complete(result);
    }

    public void listen(String exp) {
        Variable v = evalKarateExpression(exp);
        int timeout = v.getAsInt();
        logger.debug("entered listen state with timeout: {}", timeout);
        Object listenResult = null;
        try {
            listenResult = SIGNAL.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("listen timed out: {}", e + "");
        }
        synchronized (JS.context) {
            setHiddenVariable(LISTEN_RESULT, listenResult);
            logger.debug("exit listen state with result: {}", listenResult);
            SIGNAL = new CompletableFuture();
        }
    }

    public Command fork(boolean useLineFeed, List<String> args) {
        return fork(useLineFeed, Collections.singletonMap("args", args));
    }

    public Command fork(boolean useLineFeed, String line) {
        return fork(useLineFeed, Collections.singletonMap("line", line));
    }

    public Command fork(boolean useLineFeed, Map<String, Object> options) {
        Boolean useShell = (Boolean) options.get("useShell");
        if (useShell == null) {
            useShell = false;
        }
        List<String> list = (List) options.get("args");
        String[] args;
        if (list == null) {
            String line = (String) options.get("line");
            if (line == null) {
                throw new RuntimeException("'line' or 'args' is required");
            }
            args = Command.tokenize(line);
        } else {
            args = list.toArray(new String[list.size()]);
        }
        if (useShell) {
            args = Command.prefixShellArgs(args);
        }
        String workingDir = (String) options.get("workingDir");
        File workingFile = workingDir == null ? null : new File(workingDir);
        Command command = new Command(useLineFeed, logger, null, null, workingFile, args);
        Map env = (Map) options.get("env");
        if (env != null) {
            command.setEnvironment(env);
        }
        Boolean redirectErrorStream = (Boolean) options.get("redirectErrorStream");
        if (redirectErrorStream != null) {
            command.setRedirectErrorStream(redirectErrorStream);
        }
        Value funOut = (Value) options.get("listener");
        if (funOut != null && funOut.canExecute()) {
            command.setListener(new JsLambda(funOut));
        }
        Value funErr = (Value) options.get("errorListener");
        if (funErr != null && funErr.canExecute()) {
            command.setErrorListener(new JsLambda(funErr));
        }
        Boolean start = (Boolean) options.get("start");
        if (start == null) {
            start = true;
        }
        if (start) {
            command.start();
        }
        return command;
    }

    // ui driver / robot =======================================================
    //
    protected Driver driver;
    protected Plugin robot;

    private void autoDef(Plugin plugin, String instanceName) {
        for (String methodName : plugin.methodNames()) {
            String invoke = instanceName + "." + methodName;
            StringBuilder sb = new StringBuilder();
            sb.append("(function(){ if (arguments.length == 0) return ").append(invoke).append("();")
                    .append(" if (arguments.length == 1) return ").append(invoke).append("(arguments[0]);")
                    .append(" if (arguments.length == 2) return ").append(invoke).append("(arguments[0], arguments[1]);")
                    .append(" return ").append(invoke).append("(arguments[0], arguments[1], arguments[2]) })");
            setHiddenVariable(methodName, evalJs(sb.toString()));
        }
    }

    public void driver(String exp) {
        Variable v = evalKarateExpression(exp);
        // re-create driver within a test if needed
        // but user is expected to call quit() OR use the driver keyword with a JSON argument
        if (driver == null || driver.isTerminated() || v.isMap()) {
            Map<String, Object> options = config.getDriverOptions();
            if (options == null) {
                options = new HashMap();
            }
            options.put("target", config.getDriverTarget());
            if (v.isMap()) {
                options.putAll(v.getValue());
            }
            setDriver(DriverOptions.start(options, runtime));
        }
        if (v.isString()) {
            driver.setUrl(v.getAsString());
        }
    }

    public void robot(String exp) {
        Variable v = evalKarateExpression(exp);
        if (robot == null) {
            Map<String, Object> options = config.getRobotOptions();
            if (options == null) {
                options = new HashMap();
            }
            if (v.isMap()) {
                options.putAll(v.getValue());
            } else if (v.isString()) {
                options.put("window", v.getAsString());
            }
            try {
                Class clazz = Class.forName("com.intuit.karate.robot.RobotFactory");
                PluginFactory factory = (PluginFactory) clazz.newInstance();
                robot = factory.create(runtime, options);
            } catch (KarateException ke) {
                throw ke;
            } catch (Exception e) {
                String message = "cannot instantiate robot, is 'karate-robot' included as a maven / gradle dependency ? " + e.getMessage();
                logger.error(message);
                throw new RuntimeException(message, e);
            }
            setRobot(robot);
        }
    }

    public void setDriverToNull() {
        this.driver = null;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
        setHiddenVariable(DRIVER, driver);
        if (robot != null) {
            logger.warn("'robot' is active, use 'driver.' prefix for driver methods");
            return;
        }
        autoDef(driver, DRIVER);
        setHiddenVariable(KEY, Key.INSTANCE);
    }

    public void setRobot(Plugin robot) { // TODO unify
        this.robot = robot;
        // robot.setContext(this);
        setHiddenVariable(ROBOT, robot);
        if (driver != null) {
            logger.warn("'driver' is active, use 'robot.' prefix for robot methods");
            return;
        }
        autoDef(robot, ROBOT);
        setHiddenVariable(KEY, Key.INSTANCE);
    }

    public void stop(StepResult lastStepResult) {
        if (runtime.caller.isSharedScope()) {
            // TODO life-cycle this hand off
            ScenarioEngine caller = runtime.caller.parentRuntime.engine;
            if (driver != null) { // a called feature inited the driver
                caller.setDriver(driver);
            }
            if (robot != null) {
                caller.setRobot(robot);
            }
            caller.webSocketClients = webSocketClients;
            // return, don't kill driver just yet
        } else if (runtime.caller.depth == 0) { // end of top-level scenario (no caller)
            if (webSocketClients != null) {
                webSocketClients.forEach(WebSocketClient::close);
            }
            if (driver != null) { // TODO move this to Plugin.afterScenario()                
                DriverOptions options = driver.getOptions();
                if (options.stop) {
                    driver.quit();
                }
                if (options.target != null) {
                    logger.debug("custom target configured, attempting stop()");
                    Map<String, Object> map = options.target.stop(runtime);
                    String video = (String) map.get("video");
                    embedVideo(video);
                } else {
                    if (options.afterStop != null) {
                        Command.execLine(null, options.afterStop);
                    }
                    embedVideo(options.videoFile);
                }
            }
            if (robot != null) {
                robot.afterScenario();
            }
        }
    }

    private void embedVideo(String path) {
        if (path != null) {
            File videoFile = new File(path);
            if (videoFile.exists()) {
                Embed embed = runtime.embedVideo(videoFile);
                logger.debug("appended video to report: {}", embed);
            }
        }
    }

    // doc =====================================================================
    //    
    private KarateTemplateEngine templateEngine;

    public void doc(String exp) {
        Variable v = evalKarateExpression(exp);
        if (v.isString()) {
            docInternal(Collections.singletonMap("read", v.getAsString()));
        } else if (v.isMap()) {
            Map<String, Object> map = v.getValue();
            docInternal(map);
        } else {
            logger.warn("doc is not string or json: {}", v);
        }
    }

    protected String docInternal(Map<String, Object> options) {
        String path = (String) options.get("read");
        if (path == null) {
            logger.warn("doc json missing 'read' property: {}", options);
            return null;
        }
        if (templateEngine == null) {
            String prefixedPath = runtime.featureRuntime.rootFeature.feature.getResource().getPrefixedParentPath();
            templateEngine = TemplateUtils.forResourceRoot(JS, prefixedPath);
        }
        String html = templateEngine.process(path);
        if (!runtime.reportDisabled) {
            runtime.embed(FileUtils.toBytes(html), ResourceType.HTML);
        }
        return html;
    }

    //==========================================================================        
    //       
    public void init() { // not in constructor because it has to be on Runnable.run() thread 
        JS = JsEngine.local();
        logger.trace("js context: {}", JS);
        // to avoid re-processing objects that have cyclic dependencies
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap());
        runtime.magicVariables.forEach((k, v) -> {
            // even hidden variables may need pre-processing
            // for e.g. the __arg may contain functions that originated in a different js context
            Object o = recurseAndAttach(k, v, seen);
            JS.put(k, o == null ? v : o); // attach returns null if "not dirty"
        });
        vars.forEach((k, v) -> {
            // re-hydrate any functions from caller or background  
            Object o = recurseAndAttach(k, v.getValue(), seen);
            // note that we don't update the vars !
            // if we do, any "bad" out-of-context values will crash the constructor of Variable
            // it is possible the vars are detached / re-used later, so we kind of defer the inevitable
            JS.put(k, o == null ? v.getValue() : o); // attach returns null if "not dirty"
        });
        if (runtime.caller.arg != null && runtime.caller.arg.isMap()) {
            // add the call arg as separate "over ride" variables
            Map<String, Object> arg = runtime.caller.arg.getValue();
            recurseAndAttach("", arg, seen); // since arg is a map, it will not be cloned
            arg.forEach((k, v) -> {
                vars.put(k, new Variable(v));
                JS.put(k, v);
            });
        }
        JS.put(KARATE, bridge);
        JS.put(READ, readFunction);
        HttpClient client = runtime.featureRuntime.suite.clientFactory.create(this);
        // edge case: can be set by dynamic scenario outline background
        // or be left as-is because a callonce triggered init()
        if (requestBuilder == null) {
            requestBuilder = new HttpRequestBuilder(client);
        }
        // TODO improve life cycle and concept of shared objects
        if (!runtime.caller.isNone()) {
            ScenarioEngine caller = runtime.caller.parentRuntime.engine;
            if (caller.driver != null) {
                setDriver(caller.driver);
            }
            if (caller.robot != null) {
                setRobot(caller.robot);
            }
        }
    }

    protected Map<String, Variable> detachVariables() {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap());
        Map<String, Variable> detached = new HashMap(vars.size());
        vars.forEach((k, v) -> {
            Object o = recurseAndDetachAndShallowClone(k, v.getValue(), seen);
            detached.put(k, new Variable(o));
        });
        return detached;
    }
    
    // callSingle
    protected Object recurseAndAttachAndShallowClone(Object o) {
        return recurseAndAttachAndShallowClone(o, Collections.newSetFromMap(new IdentityHashMap()));
    }

    // callonce
    protected Object recurseAndAttachAndShallowClone(Object o, Set<Object> seen) {
        if (o instanceof List) {
            o = new ArrayList((List) o);
        } else if (o instanceof Map) {
            o = new LinkedHashMap((Map) o);
        }
        Object result = recurseAndAttach("", o, seen);
        return result == null ? o : result;
    }    

    private Object recurseAndAttach(String name, Object o, Set<Object> seen) {
        if (o instanceof Value) {
            Value value = Value.asValue(o);
            try {
                if (value.canExecute()) {
                    if (value.isMetaObject()) { // js function
                        return attach(value);
                    } else { // java function
                        return new JsExecutable(value);
                    }
                } else { // anything else, including java-type references
                    return value;
                }
            } catch (Exception e) {
                logger.warn("[*** attach ***] ignoring non-json value: '{}' - {}", name, e.getMessage());
                // here we try our luck and hope that graal does not notice !
                return value;
            }
        }
        if (o instanceof Class) {
            Class clazz = (Class) o;
            Value value = JS.evalForValue("Java.type('" + clazz.getCanonicalName() + "')");
            return value;
        } else if (o instanceof JsFunction) {
            JsFunction jf = (JsFunction) o;
            try {
                return attachSource(jf.source);
            } catch (Exception e) {
                logger.warn("[*** attach ***] ignoring js-function: '{}' - {}", name, e.getMessage());
                return Value.asValue(null); // make sure we return a "dirty" value to force an update
            }
        } else if (o instanceof List) {
            if (seen.add(o)) {
                List list = (List) o;
                int count = list.size();
                try {
                    for (int i = 0; i < count; i++) {
                        Object child = list.get(i);
                        Object childResult = recurseAndAttach(name + "[" + i + "]", child, seen);
                        if (childResult != null) {
                            list.set(i, childResult);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("attach - immutable list: {}", name);
                }
            }
            return null;
        } else if (o instanceof Map) {
            if (seen.add(o)) {
                Map<String, Object> map = (Map) o;
                try {
                    map.forEach((k, v) -> {
                        Object childResult = recurseAndAttach(name + "." + k, v, seen);
                        if (childResult != null) {
                            map.put(k, childResult);
                        }
                    });
                } catch (Exception e) {
                    logger.warn("attach - immutable map: {}", name);
                }
            }
            return null;
        } else {
            return null;
        }
    }

    protected Object recurseAndDetachAndShallowClone(Object o) {
        return recurseAndDetachAndShallowClone("", o, Collections.newSetFromMap(new IdentityHashMap()));
    }

    // callonce, callSingle and detachVariables()
    private Object recurseAndDetachAndShallowClone(String name, Object o, Set<Object> seen) {
        if (o instanceof List) {
            o = new ArrayList((List) o);
        } else if (o instanceof Map) {
            o = new LinkedHashMap((Map) o);
        }
        Object result = recurseAndDetach(name, o, seen);
        return result == null ? o : result;
    }

    private Object recurseAndDetach(String name, Object o, Set<Object> seen) {
        if (o instanceof Value) {
            Value value = (Value) o;
            try {
                if (value.canExecute()) {
                    if (value.isMetaObject()) { // js function
                        return new JsFunction(value);
                    } else { // java function                        
                        return new JsExecutable(value);
                    }
                } else if (value.isHostObject()) {
                    return value.asHostObject();
                }
            } catch (Exception e) {
                logger.warn("[*** detach ***] ignoring non-json value: '{}' - {}", name, e.getMessage());
            }
            return null;
        } else if (o instanceof List) {
            List list = (List) o;
            int count = list.size();
            try {
                for (int i = 0; i < count; i++) {
                    Object child = list.get(i);
                    Object childResult = recurseAndDetach(name + "[" + i + "]", child, seen);
                    if (childResult != null) {
                        list.set(i, childResult);
                    }
                }
            } catch (Exception e) {
                logger.warn("detach - immutable list: {}", name);
            }
            return null;
        } else if (o instanceof Map) {
            if (seen.add(o)) {
                Map<String, Object> map = (Map) o;
                try {
                    map.forEach((k, v) -> {
                        Object childResult = recurseAndDetach(name + "." + k, v, seen);
                        if (childResult != null) {
                            map.put(k, childResult);
                        }
                    });
                } catch (Exception e) {
                    logger.warn("detach - immutable map: {}", name);
                }
            }
            return null;
        } else {
            return null;
        }
    }

    public Value attachSource(CharSequence source) {
        return JS.attachSource(source);
    }

    public Value attach(Value before) {
        return JS.attach(before);
    }

    protected <T> Map<String, T> getOrEvalAsMap(Variable var, Object... args) {
        if (var.isJsOrJavaFunction()) {
            Variable res = executeFunction(var, args);
            return res.isMap() ? res.getValue() : null;
        } else {
            return var.isMap() ? var.getValue() : null;
        }
    }

    public Variable executeFunction(Variable var, Object... args) {
        switch (var.type) {
            case JS_FUNCTION:
                Value jsFunction = var.getValue();
                JsValue jsResult = executeJsValue(jsFunction, args);
                return new Variable(jsResult);
            case JAVA_FUNCTION:  // definitely a "call" with a single argument
                Function javaFunction = var.getValue();
                Object arg = args.length == 0 ? null : args[0];
                Object javaResult = javaFunction.apply(arg);
                return new Variable(JsValue.unWrap(javaResult));
            default:
                throw new RuntimeException("expected function, but was: " + var);
        }
    }

    private JsValue executeJsValue(Value function, Object... args) {
        try {
            return new JsValue(JsEngine.execute(function, args));
        } catch (Exception e) {
            String jsSource = function.getSourceLocation().getCharacters().toString();
            KarateException ke = JsEngine.fromJsEvalException(jsSource, e, null);
            setFailedReason(ke);
            throw ke;
        }
    }

    public Variable evalJs(String js) {
        try {
            return new Variable(JS.eval(js));
        } catch (Exception e) {
            KarateException ke = JsEngine.fromJsEvalException(js, e, null);
            setFailedReason(ke);
            throw ke;
        }
    }

    public void setHiddenVariable(String key, Object value) {
        if (value instanceof Variable) {
            value = ((Variable) value).getValue();
        }
        JS.put(key, value);
    }

    public void setVariable(String key, Object value) {
        Variable v;
        Object o;
        if (value instanceof Variable) {
            v = (Variable) value;
            o = v.getValue();
        } else {
            o = value;
            try {
                v = new Variable(value);
            } catch (Exception e) {
                v = null;
                logger.warn("[*** set variable ***] ignoring non-json value: {} - {}", key, e.getMessage());
            }
        }
        if (v != null) {
            vars.put(key, v);
        }
        if (JS != null) {
            JS.put(key, o);
        }
    }

    public void setVariables(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        map.forEach((k, v) -> setVariable(k, v));
    }

    public Map<String, Object> getAllVariablesAsMap() {
        Map<String, Object> map = new HashMap(vars.size());
        vars.forEach((k, v) -> map.put(k, v == null ? null : v.getValue()));
        return map;
    }

    private static void validateVariableName(String name) {
        if (!isValidVariableName(name)) {
            throw new RuntimeException("invalid variable name: " + name);
        }
        if (KARATE.equals(name)) {
            throw new RuntimeException("'karate' is a reserved name");
        }
        if (REQUEST.equals(name) || "url".equals(name)) {
            throw new RuntimeException("'" + name + "' is a reserved name, also use the form '* " + name + " <expression>' instead");
        }
    }

    private Variable evalAndCastTo(AssignType assignType, String exp) {
        Variable v = evalKarateExpression(exp);
        switch (assignType) {
            case BYTE_ARRAY:
                return new Variable(v.getAsByteArray());
            case STRING:
                return new Variable(v.getAsString());
            case XML:
                return new Variable(v.getAsXml());
            case XML_STRING:
                String xml = XmlUtils.toString(v.getAsXml());
                return new Variable(xml);
            case JSON:
                return new Variable(v.getValueAndForceParsingAsJson());
            case YAML:
                return new Variable(JsonUtils.fromYaml(v.getAsString()));
            case CSV:
                return new Variable(JsonUtils.fromCsv(v.getAsString()));
            case COPY:
                return v.copy(true);
            default: // AUTO (TEXT is pre-handled, see below)
                return v; // as is
        }
    }

    public void assign(AssignType assignType, String name, String exp) {
        name = StringUtils.trimToEmpty(name);
        validateVariableName(name); // always validate when gherkin
        if (vars.containsKey(name)) {
            LOGGER.debug("over-writing existing variable '{}' with new value: {}", name, exp);
        }
        if (assignType == AssignType.TEXT) {
            setVariable(name, exp);
        } else {
            setVariable(name, evalAndCastTo(assignType, exp));
        }
    }

    private static boolean isEmbeddedExpression(String text) {
        return text != null && (text.startsWith("#(") || text.startsWith("##(")) && text.endsWith(")");
    }

    private static class EmbedAction {

        final boolean remove;
        final Object value;

        private EmbedAction(boolean remove, Object value) {
            this.remove = remove;
            this.value = value;
        }

        static EmbedAction remove() {
            return new EmbedAction(true, null);
        }

        static EmbedAction update(Object value) {
            return new EmbedAction(false, value);
        }

    }

    public Variable evalEmbeddedExpressions(Variable value, boolean forMatch) {
        switch (value.type) {
            case STRING:
            case MAP:
            case LIST:
                EmbedAction ea = recurseEmbeddedExpressions(value, forMatch);
                if (ea != null) {
                    return ea.remove ? Variable.NULL : new Variable(ea.value);
                } else {
                    return value;
                }
            case XML:
                recurseXmlEmbeddedExpressions(value.getValue(), forMatch);
            default:
                return value;
        }
    }

    private EmbedAction recurseEmbeddedExpressions(Variable node, boolean forMatch) {
        switch (node.type) {
            case LIST:
                List list = node.getValue();
                Set<Integer> indexesToRemove = new HashSet();
                int count = list.size();
                for (int i = 0; i < count; i++) {
                    EmbedAction ea = recurseEmbeddedExpressions(new Variable(list.get(i)), forMatch);
                    if (ea != null) {
                        if (ea.remove) {
                            indexesToRemove.add(i);
                        } else {
                            list.set(i, ea.value);
                        }
                    }
                }
                if (!indexesToRemove.isEmpty()) {
                    List copy = new ArrayList(count - indexesToRemove.size());
                    for (int i = 0; i < count; i++) {
                        if (!indexesToRemove.contains(i)) {
                            copy.add(list.get(i));
                        }
                    }
                    return EmbedAction.update(copy);
                } else {
                    return null;
                }
            case MAP:
                Map<String, Object> map = node.getValue();
                List<String> keysToRemove = new ArrayList();
                map.forEach((k, v) -> {
                    EmbedAction ea = recurseEmbeddedExpressions(new Variable(v), forMatch);
                    if (ea != null) {
                        if (ea.remove) {
                            keysToRemove.add(k);
                        } else {
                            map.put(k, ea.value);
                        }
                    }
                });
                for (String key : keysToRemove) {
                    map.remove(key);
                }
                return null;
            case XML:
                return null;
            case STRING:
                String value = StringUtils.trimToNull(node.getValue());
                if (!isEmbeddedExpression(value)) {
                    return null;
                }
                boolean optional = value.charAt(1) == '#';
                value = value.substring(optional ? 2 : 1);
                try {
                    JsValue result = JS.eval(value);
                    if (optional) {
                        if (result.isNull()) {
                            return EmbedAction.remove();
                        }
                        if (forMatch && (result.isObject() || result.isArray())) {
                            // preserve optional JSON chunk schema-like references as-is, they are needed for future match attempts
                            return null;
                        }
                    }
                    return EmbedAction.update(result.getValue());
                } catch (Exception e) {
                    logger.trace("embedded expression failed {}: {}", value, e.getMessage());
                    return null;
                }
            default:
                // do nothing
                return null;
        }
    }

    private void recurseXmlEmbeddedExpressions(Node node, boolean forMatch) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        NamedNodeMap attribs = node.getAttributes();
        int attribCount = attribs == null ? 0 : attribs.getLength();
        Set<Attr> attributesToRemove = new HashSet(attribCount);
        for (int i = 0; i < attribCount; i++) {
            Attr attrib = (Attr) attribs.item(i);
            String value = attrib.getValue();
            value = StringUtils.trimToNull(value);
            if (isEmbeddedExpression(value)) {
                boolean optional = value.charAt(1) == '#';
                value = value.substring(optional ? 2 : 1);
                try {
                    JsValue jv = JS.eval(value);
                    if (optional && jv.isNull()) {
                        attributesToRemove.add(attrib);
                    } else {
                        attrib.setValue(jv.getAsString());
                    }
                } catch (Exception e) {
                    logger.trace("xml-attribute embedded expression failed, {}: {}", attrib.getName(), e.getMessage());
                }
            }
        }
        for (Attr toRemove : attributesToRemove) {
            attribs.removeNamedItem(toRemove.getName());
        }
        NodeList nodeList = node.getChildNodes();
        int childCount = nodeList.getLength();
        List<Node> nodes = new ArrayList(childCount);
        for (int i = 0; i < childCount; i++) {
            nodes.add(nodeList.item(i));
        }
        Set<Node> elementsToRemove = new HashSet(childCount);
        for (Node child : nodes) {
            String value = child.getNodeValue();
            if (value != null) {
                value = StringUtils.trimToEmpty(value);
                if (isEmbeddedExpression(value)) {
                    boolean optional = value.charAt(1) == '#';
                    value = value.substring(optional ? 2 : 1);
                    try {
                        JsValue jv = JS.eval(value);
                        if (optional) {
                            if (jv.isNull()) {
                                elementsToRemove.add(child);
                            } else if (forMatch && (jv.isXml() || jv.isObject())) {
                                // preserve optional XML chunk schema-like references as-is, they are needed for future match attempts
                            } else {
                                child.setNodeValue(jv.getAsString());
                            }
                        } else {
                            if (jv.isXml() || jv.isObject()) {
                                Node evalNode = jv.isXml() ? jv.getValue() : XmlUtils.fromMap(jv.getValue());
                                if (evalNode.getNodeType() == Node.DOCUMENT_NODE) {
                                    evalNode = evalNode.getFirstChild();
                                }
                                if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                                    child.setNodeValue(XmlUtils.toString(evalNode));
                                } else {
                                    evalNode = node.getOwnerDocument().importNode(evalNode, true);
                                    child.getParentNode().replaceChild(evalNode, child);
                                }
                            } else {
                                child.setNodeValue(jv.getAsString());
                            }
                        }
                    } catch (Exception e) {
                        logger.trace("xml embedded expression failed, {}: {}", child.getNodeName(), e.getMessage());
                    }
                }
            } else if (child.hasChildNodes() || child.hasAttributes()) {
                recurseXmlEmbeddedExpressions(child, forMatch);
            }
        }
        for (Node toRemove : elementsToRemove) { // because of how the above routine works, these are always of type TEXT_NODE
            Node parent = toRemove.getParentNode(); // element containing the text-node
            Node grandParent = parent.getParentNode(); // parent element
            grandParent.removeChild(parent);
        }
    }

    public String replacePlaceholderText(String text, String token, String replaceWith) {
        if (text == null) {
            return null;
        }
        replaceWith = StringUtils.trimToNull(replaceWith);
        if (replaceWith == null) {
            return text;
        }
        try {
            Variable v = evalKarateExpression(replaceWith);
            replaceWith = v.getAsString();
        } catch (Exception e) {
            throw new RuntimeException("expression error (replace string values need to be within quotes): " + e.getMessage());
        }
        if (replaceWith == null) { // ignore if eval result is null
            return text;
        }
        token = StringUtils.trimToNull(token);
        if (token == null) {
            return text;
        }
        char firstChar = token.charAt(0);
        if (Character.isLetterOrDigit(firstChar)) {
            token = '<' + token + '>';
        }
        return text.replace(token, replaceWith);
    }

    private static final String TOKEN = "token";

    public void replaceTable(String text, List<Map<String, String>> list) {
        if (text == null) {
            return;
        }
        if (list == null) {
            return;
        }
        for (Map<String, String> map : list) {
            String token = map.get(TOKEN);
            if (token == null) {
                continue;
            }
            // the verbosity below is to be lenient with table second column name
            List<String> keys = new ArrayList(map.keySet());
            keys.remove(TOKEN);
            Iterator<String> iterator = keys.iterator();
            if (iterator.hasNext()) {
                String key = keys.iterator().next();
                String value = map.get(key);
                replace(text, token, value);
            }
        }

    }

    public void set(String name, String path, Variable value) {
        set(name, path, false, value, false, false);
    }

    private void set(String name, String path, String exp, boolean delete, boolean viaTable) {
        set(name, path, isWithinParentheses(exp), evalKarateExpression(exp), delete, viaTable);
    }

    private void set(String name, String path, boolean isWithinParentheses, Variable value, boolean delete, boolean viaTable) {
        name = StringUtils.trimToEmpty(name);
        path = StringUtils.trimToNull(path);
        if (viaTable && value.isNull() && !isWithinParentheses) {
            // by default, skip any expression that evaluates to null unless the user expressed
            // intent to over-ride by enclosing the expression in parentheses
            return;
        }
        if (path == null) {
            StringUtils.Pair nameAndPath = parseVariableAndPath(name);
            name = nameAndPath.left;
            path = nameAndPath.right;
        }
        Variable target = JS.bindings.hasMember(name) ? new Variable(JS.get(name)) : null; // should work in called features
        if (isXmlPath(path)) {
            if (target == null || target.isNull()) {
                if (viaTable) { // auto create if using set via cucumber table as a convenience
                    Document empty = XmlUtils.newDocument();
                    target = new Variable(empty);
                    setVariable(name, target);
                } else {
                    throw new RuntimeException("variable is null or not set '" + name + "'");
                }
            }
            Document doc = target.getValue();
            if (delete) {
                XmlUtils.removeByPath(doc, path);
            } else if (value.isXml()) {
                Node node = value.getValue();
                XmlUtils.setByPath(doc, path, node);
            } else if (value.isMap()) { // cast to xml
                Node node = XmlUtils.fromMap(value.getValue());
                XmlUtils.setByPath(doc, path, node);
            } else {
                XmlUtils.setByPath(doc, path, value.getAsString());
            }
            setVariable(name, new Variable(doc));
        } else { // assume json-path
            if (target == null || target.isNull()) {
                if (viaTable) { // auto create if using set via cucumber table as a convenience
                    Json json;
                    if (path.startsWith("$[") && !path.startsWith("$['")) {
                        json = Json.of("[]");
                    } else {
                        json = Json.of("{}");
                    }
                    target = new Variable(json.value());
                    setVariable(name, target);
                } else {
                    throw new RuntimeException("variable is null or not set '" + name + "'");
                }
            }
            Json json;
            if (target.isMapOrList()) {
                json = Json.of(target.<Object>getValue());
            } else {
                throw new RuntimeException("cannot set json path on type: " + target);
            }
            if (delete) {
                json.remove(path);
            } else {
                json.set(path, value.<Object>getValue());
            }
        }
    }

    private static final String PATH = "path";

    public void setViaTable(String name, String path, List<Map<String, String>> list) {
        name = StringUtils.trimToEmpty(name);
        path = StringUtils.trimToNull(path);
        if (path == null) {
            StringUtils.Pair nameAndPath = parseVariableAndPath(name);
            name = nameAndPath.left;
            path = nameAndPath.right;
        }
        for (Map<String, String> map : list) {
            String append = (String) map.get(PATH);
            if (append == null) {
                continue;
            }
            List<String> keys = new ArrayList(map.keySet());
            keys.remove(PATH);
            int columnCount = keys.size();
            for (int i = 0; i < columnCount; i++) {
                String key = keys.get(i);
                String expression = StringUtils.trimToNull(map.get(key));
                if (expression == null) { // cucumber cell was left blank
                    continue; // skip
                    // default behavior is to skip nulls when the expression evaluates 
                    // this is driven by the routine in setValueByPath
                    // and users can over-ride this by simply enclosing the expression in parentheses
                }
                String suffix;
                try {
                    int arrayIndex = Integer.valueOf(key);
                    suffix = "[" + arrayIndex + "]";
                } catch (NumberFormatException e) { // default to the column position as the index
                    suffix = columnCount > 1 ? "[" + i + "]" : "";
                }
                String finalPath;
                if (append.startsWith("/") || (path != null && path.startsWith("/"))) { // XML
                    if (path == null) {
                        finalPath = append + suffix;
                    } else {
                        finalPath = path + suffix + '/' + append;
                    }
                } else {
                    if (path == null) {
                        path = "$";
                    }
                    finalPath = path + suffix + '.' + append;
                }
                set(name, finalPath, expression, false, true);
            }
        }
    }

    public static StringUtils.Pair parseVariableAndPath(String text) {
        Matcher matcher = VAR_AND_PATH_PATTERN.matcher(text);
        matcher.find();
        String name = text.substring(0, matcher.end());
        String path;
        if (matcher.end() == text.length()) {
            path = "";
        } else {
            path = text.substring(matcher.end()).trim();
        }
        if (isXmlPath(path) || isXmlPathFunction(path)) {
            // xml, don't prefix for json
        } else {
            path = "$" + path;
        }
        return StringUtils.pair(name, path);
    }

    public Match.Result match(Match.Type matchType, String expression, String path, String rhs) {
        String name = StringUtils.trimToEmpty(expression);
        if (isDollarPrefixedJsonPath(name) || isXmlPath(name)) { // 
            path = name;
            name = RESPONSE;
        }
        if (name.startsWith("$")) { // in case someone used the dollar prefix by mistake on the LHS
            name = name.substring(1);
        }
        path = StringUtils.trimToNull(path);
        if (path == null) {
            StringUtils.Pair pair = parseVariableAndPath(name);
            name = pair.left;
            path = pair.right;
        }
        if ("header".equals(name)) { // convenience shortcut for asserting against response header
            return matchHeader(matchType, path, rhs);
        }
        Variable actual;
        // karate started out by "defaulting" to JsonPath on the LHS of a match so we have this kludge
        // but we now handle JS expressions of almost any shape on the LHS, if in doubt, wrap in parentheses
        // actually it is not too bad - the XPath function check is the only odd one out
        // rules:
        // if not XPath function, wrapped in parentheses, involves function call
        //      [then] JS eval
        // else if XPath, JsonPath, JsonPath wildcard ".." or "*" or "[?"
        //      [then] eval name, and do a JsonPath or XPath using the parsed path
        if (isXmlPathFunction(path)
                || (!name.startsWith("(") && !path.endsWith(")") && !path.contains(")."))
                && (isDollarPrefixed(path) || isJsonPath(path) || isXmlPath(path))) {
            actual = evalKarateExpression(name);
            // edge case: java property getter, e.g. "driver.cookies"
            if (!actual.isMap() && !actual.isList() && !isXmlPath(path) && !isXmlPathFunction(path)) {
                actual = evalKarateExpression(expression); // fall back to JS eval of entire LHS
                path = "$";
            }
        } else {
            actual = evalKarateExpression(expression); // JS eval of entire LHS
            path = "$";
        }
        if ("$".equals(path) || "/".equals(path)) {
            // we have eval-ed the entire LHS, so proceed to match RHS to "$"
        } else {
            if (isDollarPrefixed(path)) { // json-path
                actual = evalJsonPath(actual, path);
            } else { // xpath
                actual = evalXmlPath(actual, path);
            }
        }
        Variable expected = evalKarateExpression(rhs, true);
        return match(matchType, actual.getValue(), expected.getValue());
    }

    private Match.Result matchHeader(Match.Type matchType, String name, String exp) {
        Variable expected = evalKarateExpression(exp, true);
        String actual = response.getHeader(name);
        return match(matchType, actual, expected.getValue());
    }

    public Match.Result match(Match.Type matchType, Object actual, Object expected) {
        return Match.execute(JS, matchType, actual, expected);
    }

    private static final Pattern VAR_AND_PATH_PATTERN = Pattern.compile("\\w+");
    private static final String VARIABLE_PATTERN_STRING = "[a-zA-Z][\\w]*";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE_PATTERN_STRING);
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("^function[^(]*\\(");
    private static final Pattern JS_PLACEHODER = Pattern.compile("\\$\\{.*?\\}");

    public static boolean isJavaScriptFunction(String text) {
        return FUNCTION_PATTERN.matcher(text).find();
    }

    public static boolean isValidVariableName(String name) {
        return VARIABLE_PATTERN.matcher(name).matches();
    }

    public static boolean hasJavaScriptPlacehoder(String exp) {
        return JS_PLACEHODER.matcher(exp).find();
    }

    public static final boolean isVariableAndSpaceAndPath(String text) {
        return text.matches("^" + VARIABLE_PATTERN_STRING + "\\s+.+");
    }

    public static final boolean isVariable(String text) {
        return VARIABLE_PATTERN.matcher(text).matches();
    }

    public static final boolean isWithinParentheses(String text) {
        return text != null && text.startsWith("(") && text.endsWith(")");
    }

    public static final boolean isCallSyntax(String text) {
        return text.startsWith("call ");
    }

    public static final boolean isCallOnceSyntax(String text) {
        return text.startsWith("callonce ");
    }

    public static final boolean isGetSyntax(String text) {
        return text.startsWith("get ") || text.startsWith("get[");
    }

    public static final boolean isJson(String text) {
        return text.startsWith("{") || text.startsWith("[");
    }

    public static final boolean isXml(String text) {
        return text.startsWith("<");
    }

    public static boolean isXmlPath(String text) {
        return text.startsWith("/");
    }

    public static boolean isXmlPathFunction(String text) {
        return text.matches("^[a-z-]+\\(.+");
    }

    public static final boolean isJsonPath(String text) {
        return text.indexOf('*') != -1 || text.contains("..") || text.contains("[?");
    }

    public static final boolean isDollarPrefixed(String text) {
        return text.startsWith("$");
    }

    public static final boolean isDollarPrefixedJsonPath(String text) {
        return text.startsWith("$.") || text.startsWith("$[") || text.equals("$");
    }

    public static StringUtils.Pair parseCallArgs(String line) {
        int pos = line.indexOf("read(");
        if (pos != -1) {
            pos = line.indexOf(')');
            if (pos == -1) {
                throw new RuntimeException("failed to parse call arguments: " + line);
            }
            return new StringUtils.Pair(line.substring(0, pos + 1), StringUtils.trimToNull(line.substring(pos + 1)));
        }
        pos = line.indexOf(' ');
        if (pos == -1) {
            return new StringUtils.Pair(line, null);
        }
        return new StringUtils.Pair(line.substring(0, pos), StringUtils.trimToNull(line.substring(pos)));
    }

    public Variable call(Variable called, Variable arg, boolean sharedScope) {
        switch (called.type) {
            case JS_FUNCTION:
            case JAVA_FUNCTION:
                return arg == null ? executeFunction(called) : executeFunction(called, new Object[]{arg.getValue()});
            case FEATURE:
                // will be always a map or a list of maps (loop call result)                
                Object callResult = callFeature(called.getValue(), arg, -1, sharedScope);
                Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap());
                recurseAndAttach("", callResult, seen);
                return new Variable(callResult);
            default:
                throw new RuntimeException("not a callable feature or js function: " + called);
        }
    }

    public Variable call(boolean callOnce, String exp, boolean sharedScope) {
        StringUtils.Pair pair = parseCallArgs(exp);
        Variable called = evalKarateExpression(pair.left);
        Variable arg = pair.right == null ? null : evalKarateExpression(pair.right);
        Variable result;
        if (callOnce) {
            result = callOnce(exp, called, arg, sharedScope);
        } else {
            result = call(called, arg, sharedScope);
        }
        if (sharedScope && result.isMap()) {
            setVariables(result.getValue());
        }
        return result;
    }

    private Variable callOnceResult(ScenarioCall.Result result, boolean sharedScope) {
        if (sharedScope) { // if shared scope
            vars.clear(); // clean slate            
            if (result.vars != null) {
                Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap());
                result.vars.forEach((k, v) -> {
                    // clone maps and lists so that subsequent steps don't modify data / references being passed around
                    Object o = recurseAndAttachAndShallowClone(v.getValue(), seen);
                    try {
                        vars.put(k, new Variable(o));
                    } catch (Exception e) {
                        logger.warn("[*** callonce result ***] ignoring non-json value: '{}' - {}", k, e.getMessage());
                    }
                });
            }
            init(); // this will attach and also insert magic variables
            // re-apply config from time of snapshot
            // and note that setConfig() will attach functions such as configured "headers"
            setConfig(new Config(result.config));
            return Variable.NULL; // since we already reset the vars above we return null
            // else the call() routine would try to do it again
            // note that shared scope means a return value is meaningless
        } else {
            // deep-clone for the same reasons mentioned above
            Object resultValue = recurseAndAttachAndShallowClone(result.value.getValue());
            return new Variable(resultValue);
        }
    }

    private Variable callOnce(String cacheKey, Variable called, Variable arg, boolean sharedScope) {
        final Map<String, ScenarioCall.Result> CACHE;
        if (runtime.perfMode) { // use suite-wide cache for gatling
            CACHE = runtime.featureRuntime.suite.callOnceCache;
        } else {
            CACHE = runtime.featureRuntime.CALLONCE_CACHE;
        }
        ScenarioCall.Result result = CACHE.get(cacheKey);
        if (result != null) {
            logger.trace("callonce cache hit for: {}", cacheKey);
            return callOnceResult(result, sharedScope);
        }
        long startTime = System.currentTimeMillis();
        logger.trace("callonce waiting for lock: {}", cacheKey);
        synchronized (CACHE) {
            result = CACHE.get(cacheKey); // retry
            if (result != null) {
                long endTime = System.currentTimeMillis() - startTime;
                logger.warn("this thread waited {} milliseconds for callonce lock: {}", endTime, cacheKey);
                return callOnceResult(result, sharedScope);
            }
            // this thread is the 'winner'
            logger.info(">> lock acquired, begin callonce: {}", cacheKey);
            Variable resultValue = call(called, arg, sharedScope);
            // we clone result (and config) here, to snapshot state at the point the callonce was invoked
            // detaching is important (see JsFunction) so that we can keep the source-code aside
            // and use it to re-create functions in a new JS context - and work around graal-js limitations
            Map<String, Variable> clonedVars = called.isFeature() && sharedScope ? detachVariables() : null;
            Config clonedConfig = new Config(config);
            clonedConfig.detach();
            Object resultObject = recurseAndDetachAndShallowClone(resultValue.getValue());
            result = new ScenarioCall.Result(new Variable(resultObject), clonedConfig, clonedVars);
            CACHE.put(cacheKey, result);
            logger.info("<< lock released, cached callonce: {}", cacheKey);
            return resultValue; // another routine will apply globally if needed
        }
    }

    public Object callFeature(Feature feature, Variable arg, int index, boolean sharedScope) {
        if (arg == null || arg.isMap()) {
            ScenarioCall call = new ScenarioCall(runtime, feature, arg);
            call.setLoopIndex(index);
            call.setSharedScope(sharedScope);
            FeatureRuntime fr = new FeatureRuntime(call);
            fr.run();
            // VERY IMPORTANT ! switch back from called feature js context
            THREAD_LOCAL.set(this);
            FeatureResult result = fr.result;
            runtime.addCallResult(result);
            if (result.isFailed()) {
                KarateException ke = result.getErrorMessagesCombined();
                throw ke;
            } else {
                return result.getVariables();
            }
        } else if (arg.isList() || arg.isJsOrJavaFunction()) {
            List result = new ArrayList();
            List<String> errors = new ArrayList();
            int loopIndex = 0;
            boolean isList = arg.isList();
            Iterator iterator = isList ? arg.<List>getValue().iterator() : null;
            while (true) {
                Variable loopArg;
                if (isList) {
                    loopArg = iterator.hasNext() ? new Variable(iterator.next()) : Variable.NULL;
                } else { // function
                    loopArg = executeFunction(arg, new Object[]{loopIndex});
                }
                if (!loopArg.isMap()) {
                    if (!isList) {
                        logger.info("feature call loop function ended at index {}, returned: {}", loopIndex, loopArg);
                    }
                    break;
                }
                try {
                    Object loopResult = callFeature(feature, loopArg, loopIndex, sharedScope);
                    result.add(loopResult);
                } catch (Exception e) {
                    String message = "feature call loop failed at index: " + loopIndex + ", " + e.getMessage();
                    errors.add(message);
                    runtime.logError(message);
                    if (!isList) { // this is a generator function, abort infinite loop !
                        break;
                    }
                }
                loopIndex++;
            }
            if (errors.isEmpty()) {
                return result;
            } else {
                String errorMessage = StringUtils.join(errors, "\n");
                throw new KarateException(errorMessage);
            }
        } else {
            throw new RuntimeException("feature call argument is not a json object or array: " + arg);
        }
    }

    public Variable evalJsonPath(Variable v, String path) {
        Json json = Json.of(v.getValueAndForceParsingAsJson());
        try {
            return new Variable(json.get(path));
        } catch (PathNotFoundException e) {
            return Variable.NOT_PRESENT;
        }
    }

    public static Variable evalXmlPath(Variable xml, String path) {
        NodeList nodeList;
        Node doc = xml.getAsXml();
        try {
            nodeList = XmlUtils.getNodeListByPath(doc, path);
        } catch (Exception e) {
            // hack, this happens for xpath functions that don't return nodes (e.g. count)
            String strValue = XmlUtils.getTextValueByPath(doc, path);
            Variable v = new Variable(strValue);
            if (path.startsWith("count")) { // special case
                return new Variable(v.getAsInt());
            } else {
                return v;
            }
        }
        int count = nodeList.getLength();
        if (count == 0) { // xpath / node does not exist !
            return Variable.NOT_PRESENT;
        }
        if (count == 1) {
            return nodeToValue(nodeList.item(0));
        }
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            Variable v = nodeToValue(nodeList.item(i));
            list.add(v.getValue());
        }
        return new Variable(list);
    }

    private static Variable nodeToValue(Node node) {
        int childElementCount = XmlUtils.getChildElementCount(node);
        if (childElementCount == 0) {
            // hack assuming this is the most common "intent"
            return new Variable(node.getTextContent());
        }
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return new Variable(node);
        } else { // make sure we create a fresh doc else future xpath would run against original root
            return new Variable(XmlUtils.toNewDocument(node));
        }
    }

    public Variable evalJsonPathOnVariableByName(String name, String path) {
        Variable v = new Variable(JS.get(name)); // should work in called features
        return evalJsonPath(v, path);
    }

    public Variable evalXmlPathOnVariableByName(String name, String path) {
        Variable v = new Variable(JS.get(name)); // should work in called features
        return evalXmlPath(v, path);
    }

    public Variable evalKarateExpression(String text) {
        return evalKarateExpression(text, false);
    }

    public Variable evalKarateExpression(String text, boolean forMatch) {
        text = StringUtils.trimToNull(text);
        if (text == null) {
            return Variable.NULL;
        }
        // don't re-evaluate if this is clearly a direct reference to a variable
        // this avoids un-necessary conversion of xml into a map in some cases
        // e.g. 'Given request foo' - where foo is a Variable of type XML      
        if (JS.bindings.hasMember(text)) {
            return new Variable(JS.get(text));
        }
        boolean callOnce = isCallOnceSyntax(text);
        if (callOnce || isCallSyntax(text)) { // special case in form "callBegin foo arg"
            if (callOnce) {
                text = text.substring(9);
            } else {
                text = text.substring(5);
            }
            return call(callOnce, text, false);
        } else if (isDollarPrefixedJsonPath(text)) {
            return evalJsonPathOnVariableByName(RESPONSE, text);
        } else if (isGetSyntax(text) || isDollarPrefixed(text)) { // special case in form
            // get json[*].path
            // $json[*].path
            // get /xml/path
            // get xpath-function(expression)
            int index = -1;
            if (text.startsWith("$")) {
                text = text.substring(1);
            } else if (text.startsWith("get[")) {
                int pos = text.indexOf(']');
                index = Integer.valueOf(text.substring(4, pos));
                text = text.substring(pos + 2);
            } else {
                text = text.substring(4);
            }
            String left;
            String right;
            if (isDollarPrefixedJsonPath(text)) { // edge case get[0] $..foo
                left = RESPONSE;
                right = text;
            } else if (isVariableAndSpaceAndPath(text)) {
                int pos = text.indexOf(' ');
                right = text.substring(pos + 1);
                left = text.substring(0, pos);
            } else {
                StringUtils.Pair pair = parseVariableAndPath(text);
                left = pair.left;
                right = pair.right;
            }
            Variable sv;
            if (isXmlPath(right) || isXmlPathFunction(right)) {
                sv = evalXmlPathOnVariableByName(left, right);
            } else {
                sv = evalJsonPathOnVariableByName(left, right);
            }
            if (index != -1 && sv.isList()) {
                List list = sv.getValue();
                if (!list.isEmpty()) {
                    return new Variable(list.get(index));
                }
            }
            return sv;
        } else if (isJson(text)) {
            Json json = Json.of(text);
            return evalEmbeddedExpressions(new Variable(json.value()), forMatch);
        } else if (isXml(text)) {
            Document doc = XmlUtils.toXmlDoc(text);
            return evalEmbeddedExpressions(new Variable(doc), forMatch);
        } else if (isXmlPath(text)) {
            return evalXmlPathOnVariableByName(RESPONSE, text);
        } else {
            // old school function declarations e.g. function() { } need wrapping in graal
            if (isJavaScriptFunction(text)) {
                text = "(" + text + ")";
            }
            // js expressions e.g. foo, foo(bar), foo.bar, foo + bar, foo + '', 5, true
            // including arrow functions e.g. x => x + 1
            return evalJs(text);
        }
    }

}
