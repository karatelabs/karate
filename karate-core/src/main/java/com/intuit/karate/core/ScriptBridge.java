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
import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.PerfContext;
import com.intuit.karate.Resource;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.exception.KarateFailException;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiValuedMap;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketOptions;
import com.intuit.karate.shell.Command;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class ScriptBridge implements PerfContext {

    private static final Object GLOBALS_LOCK = new Object();
    private static final Map<String, Object> GLOBALS = new HashMap();

    public final ScenarioContext context;

    public ScriptBridge(ScenarioContext context) {
        this.context = context;
    }

    public ScenarioContext getContext() {
        return context;
    }

    public void configure(String key, Object o) {
        context.configure(key, new ScriptValue(o));
    }

    public Object read(String fileName) {
        return context.read.apply(fileName);
    }

    public String readAsString(String fileName) {
        return FileUtils.readFileAsString(fileName, context);
    }
    
    public String toString(Object o) {
        ScriptValue sv = new ScriptValue(o);
        return sv.getAsString();
    }

    public String pretty(Object o) {
        ScriptValue sv = new ScriptValue(o);
        return sv.getAsPrettyString();
    }

    public String prettyXml(Object o) {
        ScriptValue sv = new ScriptValue(o);
        if (sv.isXml()) {
            Node node = sv.getValue(Node.class);
            return XmlUtils.toString(node, true);
        } else if (sv.isMapLike()) {
            Document doc = XmlUtils.fromMap(sv.getAsMap());
            return XmlUtils.toString(doc, true);
        } else {
            String xml = sv.getAsString();
            Document doc = XmlUtils.toXmlDoc(xml);
            return XmlUtils.toString(doc, true);
        }
    }

    public void set(String name, Object o) {
        context.vars.put(name, o);
    }

    public void setXml(String name, String xml) {
        context.vars.put(name, XmlUtils.toXmlDoc(xml));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void set(String name, String path, Object value) {
        Script.setValueByPath(name, path, new ScriptValue(value), context);
    }

    // this makes sense mainly for xpath manipulation from within js
    public void setXml(String name, String path, String xml) {
        Script.setValueByPath(name, path, new ScriptValue(XmlUtils.toXmlDoc(xml)), context);
    }

    // set multiple variables in one shot
    public void set(Map<String, Object> map) {
        map.forEach((k, v) -> set(k, v));
    }

    // this makes sense for xml / xpath manipulation from within js
    public void remove(String name, String path) {
        Script.removeValueByPath(name, path, context);
    }

    public Object get(String exp) {
        ScriptValue sv;
        try {
            sv = Script.evalKarateExpression(exp, context); // even json path expressions will work
        } catch (Exception e) {
            context.logger.trace("karate.get failed for expression: '{}': {}", exp, e.getMessage());
            return null;
        }
        if (sv != null) {
            return sv.getAfterConvertingFromJsonOrXmlIfNeeded();
        } else {
            return null;
        }
    }

    public Object get(String exp, Object defaultValue) {
        Object result = get(exp);
        return result == null ? defaultValue : result;
    }

    public int sizeOf(Object o) {
        ScriptValue sv = new ScriptValue(o);
        if (sv.isMapLike()) {
            return sv.getAsMap().size();
        }
        if (sv.isListLike()) {
            return sv.getAsList().size();
        }
        return -1;
    }

    public List keysOf(Object o) {
        ScriptValue sv = new ScriptValue(o);
        if (sv.isMapLike()) {
            return new ArrayList(sv.getAsMap().keySet());
        }
        return null;
    }

    public List valuesOf(Object o) {
        ScriptValue sv = new ScriptValue(o);
        if (sv.isMapLike()) {
            return new ArrayList(sv.getAsMap().values());
        }
        if (sv.isListLike()) {
            return sv.getAsList();
        }
        return null;
    }

    public Map<String, Object> match(Object actual, Object expected) {
        AssertionResult ar = Script.matchNestedObject('.', "$", MatchType.EQUALS, actual, null, actual, expected, context);
        return ar.toMap();
    }

    public Map<String, Object> match(String exp) {
        MatchStep ms = new MatchStep(exp);
        AssertionResult ar = Script.matchNamed(ms.type, ms.name, ms.path, ms.expected, context);
        return ar.toMap();
    }

    public void forEach(Object o, ScriptObjectMirror som) {
        ScriptValue sv = new ScriptValue(o);
        if (!sv.isJsonLike()) {
            throw new RuntimeException("not map-like or list-like: " + o);
        }
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        if (sv.isListLike()) {
            List list = sv.getAsList();
            for (int i = 0; i < list.size(); i++) {
                som.call(som, list.get(i), i);
            }
        } else { //map
            Map map = sv.getAsMap();
            AtomicInteger i = new AtomicInteger();
            map.forEach((k, v) -> som.call(som, k, v, i.getAndIncrement()));
        }
    }

    public Object map(List list, ScriptObjectMirror som) {
        if (list == null) {
            return new ArrayList();
        }
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        List res = new ArrayList(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object y = som.call(som, list.get(i), i);
            res.add(new ScriptValue(y).getValue()); // TODO graal
        }
        return res;
    }

    public Object filter(List list, ScriptObjectMirror som) {
        if (list == null) {
            return new ArrayList();
        }
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        List res = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            Object x = list.get(i);
            Object y = som.call(som, x, i);
            if (y instanceof Boolean) {
                if ((Boolean) y) {
                    res.add(x);
                }
            } else if (y instanceof Number) { // support truthy numbers as a convenience
                String exp = y + " == 0";
                ScriptValue sv = Script.evalJsExpression(exp, null);
                if (!sv.isBooleanTrue()) {
                    res.add(x);
                }
            }
        }
        return res;
    }

    public Object filterKeys(Object o, Map<String, Object> filter) {
        ScriptValue sv = new ScriptValue(o);
        if (!sv.isMapLike()) {
            return new LinkedHashMap();
        }
        Map map = sv.getAsMap();
        if (filter == null) {
            return map;
        }
        Map out = new LinkedHashMap(filter.size());
        filter.keySet().forEach(k -> {
            if (map.containsKey(k)) {
                out.put(k, map.get(k));
            }
        });
        return out;
    }

    public Object filterKeys(Object o, List keys) {
        return filterKeys(o, keys.toArray());
    }

    public Object filterKeys(Object o, Object... keys) {
        ScriptValue sv = new ScriptValue(o);
        if (!sv.isMapLike()) {
            return new LinkedHashMap();
        }
        Map map = sv.getAsMap();
        Map out = new LinkedHashMap(keys.length);
        for (Object key : keys) {
            if (map.containsKey(key)) {
                out.put(key, map.get(key));
            }
        }
        return out;
    }

    public Object repeat(int n, ScriptObjectMirror som) {
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        List res = new ArrayList();
        for (int i = 0; i < n; i++) {
            Object o = som.call(som, i);
            res.add(new ScriptValue(o).getValue()); // TODO graal
        }
        return res;
    }

    public Object mapWithKey(List list, String key) {
        if (list == null) {
            return new ArrayList();
        }
        List res = new ArrayList(list.size());
        for (Object o : list) {
            Map map = new LinkedHashMap();
            map.put(key, o);
            res.add(map);
        }
        return res;
    }

    public Object merge(Map... maps) {
        Map out = new LinkedHashMap();
        if (maps == null) {
            return out;
        }
        for (Map map : maps) {
            if (map == null) {
                continue;
            }
            out.putAll(map);
        }
        return out;
    }

    public Object append(Object... items) {
        List out = new ArrayList();
        if (items == null) {
            return out;
        }
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            if (item instanceof ScriptObjectMirror) { // no need when graal
                ScriptObjectMirror som = (ScriptObjectMirror) item;
                if (som.isArray()) {
                    out.addAll(som.values());
                } else {
                    out.add(som);
                }
            } else if (item instanceof Collection) {
                out.addAll((Collection) item);
            } else {
                out.add(item);
            }
        }
        return out;
    }

    public List appendTo(String name, Object... values) {
        ScriptValue sv = context.vars.get(name);
        if (sv == null || !sv.isListLike()) {
            return Collections.EMPTY_LIST;
        }
        List list = appendTo(sv.getAsList(), values);
        context.vars.put(name, list);
        return list;
    }

    public List appendTo(List list, Object... values) {
        for (Object o : values) {
            if (o instanceof Collection) {
                list.addAll((Collection) o);
            } else {
                list.add(o);
            }
        }
        return list;
    }

    public Object jsonPath(Object o, String exp) {
        DocumentContext doc;
        if (o instanceof DocumentContext) {
            doc = (DocumentContext) o;
        } else {
            doc = JsonPath.parse(o);
        }
        return doc.read(exp);
    }

    public Object lowerCase(Object o) {
        ScriptValue sv = new ScriptValue(o);
        return sv.toLowerCase();
    }

    public Object xmlPath(Object o, String path) {
        if (!(o instanceof Node)) {
            if (o instanceof Map) {
                o = XmlUtils.fromMap((Map) o);
            } else {
                throw new RuntimeException("not XML or cannot convert: " + o);
            }
        }
        ScriptValue sv = Script.evalXmlPathOnXmlNode((Node) o, path);
        return sv.getValue();
    }

    public Object toBean(Object o, String className) {
        ScriptValue sv = new ScriptValue(o);
        DocumentContext doc = Script.toJsonDoc(sv, context);
        return JsonUtils.fromJson(doc.jsonString(), className);
    }

    public Object toMap(Object o) {
        if (o instanceof Map) {
            Map<String, Object> src = (Map) o;
            return new LinkedHashMap(src);
        }
        return o;
    }

    public Object toList(Object o) {
        if (o instanceof List) {
            List src = (List) o;
            return new ArrayList(src);
        }
        return o;
    }

    public Object toJson(Object o) {
        return toJson(o, false);
    }

    public Object toJson(Object o, boolean removeNulls) {
        Object result = JsonUtils.toJsonDoc(o).read("$");
        if (removeNulls) {
            JsonUtils.removeKeysWithNullValues(result);
        }
        return result;
    }

    public String toCsv(Object o) {
        ScriptValue sv = new ScriptValue(o);
        if (!sv.isListLike()) {
            throw new RuntimeException("not a list-like value:" + sv);
        }
        List<Map<String, Object>> list = sv.getAsList();
        return JsonUtils.toCsv(list);
    }

    public Object call(String fileName) {
        return call(false, fileName, null);
    }

    public Object call(String fileName, Object arg) {
        return call(false, fileName, arg);
    }

    public Object call(boolean sharedScope, String fileName) {
        return call(sharedScope, fileName, null);
    }

    // note that the implementation is subtly different from context.call()
    // because we are within a JS block
    public Object call(boolean sharedScope, String fileName, Object arg) {
        ScriptValue called = FileUtils.readFile(fileName, context);
        ScriptValue result;
        switch (called.getType()) {
            case FEATURE:
                Feature feature = called.getValue(Feature.class);
                // last param is for edge case where this.context is from function 
                // inited before call hierarchy was determined, see CallContext
                result = Script.evalFeatureCall(feature, arg, context, sharedScope);
                break;
            case JS_FUNCTION:
                ScriptObjectMirror som = called.getValue(ScriptObjectMirror.class);
                result = Script.evalJsFunctionCall(som, arg, context);
                break;
            default: // TODO remove ?
                context.logger.warn("not a js function or feature file: {} - {}", fileName, called);
                return null;
        }
        // if shared scope, a called feature would update the context directly
        if (sharedScope && !called.isFeature() && result.isMapLike()) {
            result.getAsMap().forEach((k, v) -> context.vars.put(k, v));
        }
        return result.getValue();
    }

    public Object callSingle(String fileName) {
        return callSingle(fileName, null);
    }

    public Object callSingle(String fileName, Object arg) {
        if (GLOBALS.containsKey(fileName)) {
            context.logger.trace("callSingle cache hit: {}", fileName);
            return GLOBALS.get(fileName);
        }
        long startTime = System.currentTimeMillis();
        context.logger.trace("callSingle waiting for lock: {}", fileName);
        synchronized (GLOBALS_LOCK) { // lock
            if (GLOBALS.containsKey(fileName)) { // retry
                long endTime = System.currentTimeMillis() - startTime;
                context.logger.warn("this thread waited {} milliseconds for callSingle lock: {}", endTime, fileName);
                return GLOBALS.get(fileName);
            }
            // this thread is the 'winner'
            context.logger.info(">> lock acquired, begin callSingle: {}", fileName);
            int minutes = context.getConfig().getCallSingleCacheMinutes();
            Object result = null;
            File cacheFile = null;
            if (minutes > 0) {
                String qualifiedFileName = FileUtils.toPackageQualifiedName(fileName);
                String cacheFileName = context.getConfig().getCallSingleCacheDir() + File.separator + qualifiedFileName + ".txt";
                cacheFile = new File(cacheFileName);
                long since = System.currentTimeMillis() - minutes * 60 * 1000;
                if (cacheFile.exists()) {
                    long lastModified = cacheFile.lastModified();
                    if (lastModified > since) {
                        String json = FileUtils.toString(cacheFile);
                        result = JsonUtils.toJsonDoc(json);
                        context.logger.info("callSingleCache hit: {}", cacheFile);
                    } else {
                        context.logger.info("callSingleCache stale, last modified {} - is before {} (minutes: {})", lastModified, since,
                                minutes);
                    }
                } else {
                    context.logger.info("callSingleCache file does not exist, will create: {}", cacheFile);
                }
            }
            if (result == null) {
                result = call(fileName, arg);
                if (minutes > 0) { // cacheFile will be not null
                    ScriptValue cacheValue = new ScriptValue(result);
                    if (cacheValue.isJsonLike()) {
                        String json = cacheValue.getAsString();
                        FileUtils.writeToFile(cacheFile, json);
                        context.logger.info("callSingleCache write: {}", cacheFile);
                    } else {
                        context.logger.warn("callSingleCache write failed, not json-like: {}", cacheValue);
                    }
                }
            }
            GLOBALS.put(fileName, result);
            context.logger.info("<< lock released, cached callSingle: {}", fileName);
            return result;
        }
    }

    public HttpRequest getPrevRequest() {
        return context.getPrevRequest();
    }

    public Object eval(String exp) {
        ScriptValue sv = Script.evalJsExpression(exp, context);
        return sv.getValue();
    }

    public ScriptValue fromString(String exp) {
        try {
            return Script.evalKarateExpression(exp, context);
        } catch (Exception e) {
            return new ScriptValue(exp);
        }
    }

    public ScriptValue fromObject(Object o) {
        return new ScriptValue(o);
    }

    public List<String> getTags() {
        return context.tags;
    }

    public Map<String, List<String>> getTagValues() {
        return context.tagValues;
    }

    public Map<String, Object> getInfo() {
        return context.getScenarioInfo();
    }

    public Scenario getScenario() {
        return context.getScenario();
    }

    public void proceed() {
        proceed(null);
    }

    public void proceed(String requestUrlBase) {
        HttpRequestBuilder request = new HttpRequestBuilder();
        String urlBase = requestUrlBase == null ? getAsString(ScriptValueMap.VAR_REQUEST_URL_BASE) : requestUrlBase;
        String uri = getAsString(ScriptValueMap.VAR_REQUEST_URI);
        String url = uri == null ? urlBase : urlBase + uri;
        request.setUrl(url);
        request.setMethod(getAsString(ScriptValueMap.VAR_REQUEST_METHOD));
        request.setHeaders(getValue(ScriptValueMap.VAR_REQUEST_HEADERS).getValue(MultiValuedMap.class));
        request.removeHeaderIgnoreCase(HttpUtils.HEADER_CONTENT_LENGTH);
        request.setBody(getValue(ScriptValueMap.VAR_REQUEST));
        HttpResponse response = context.getHttpClient().invoke(request, context);
        context.setPrevResponse(response);
        context.updateResponseVars();
    }

    public void embed(Object o, String contentType) {
        ScriptValue sv = new ScriptValue(o);
        if (contentType == null) {
            contentType = HttpUtils.getContentType(sv);
        }
        context.embed(sv.getAsByteArray(), contentType);
    }

    public File write(Object o, String path) {
        ScriptValue sv = new ScriptValue(o);
        path = FileUtils.getBuildDir() + File.separator + path;
        File file = new File(path);
        FileUtils.writeToFile(file, sv.getAsByteArray());
        return file;
    }

    public WebSocketClient webSocket(String url) {
        return webSocket(url, null, null);
    }

    public WebSocketClient webSocket(String url, Function<String, Boolean> handler) {
        return webSocket(url, handler, null);
    }

    public WebSocketClient webSocket(String url, Function<String, Boolean> handler, Map<String, Object> map) {
        if (handler == null) {
            handler = t -> true; // auto signal for websocket tests
        }
        WebSocketOptions options = new WebSocketOptions(url, map);
        options.setTextHandler(handler);
        return context.webSocket(options);
    }

    public WebSocketClient webSocketBinary(String url) {
        return webSocketBinary(url, null, null);
    }

    public WebSocketClient webSocketBinary(String url, Function<byte[], Boolean> handler) {
        return webSocketBinary(url, handler, null);
    }

    public WebSocketClient webSocketBinary(String url, Function<byte[], Boolean> handler, Map<String, Object> map) {
        if (handler == null) {
            handler = t -> true; // auto signal for websocket tests
        }
        WebSocketOptions options = new WebSocketOptions(url, map);
        options.setBinaryHandler(handler);
        return context.webSocket(options);
    }

    public void signal(Object result) {
        context.signal(result);
    }

    public Object listen(long timeout, ScriptObjectMirror som) {
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        return context.listen(timeout, () -> Script.evalJsFunctionCall(som, null, context));
    }

    public Object listen(long timeout) {
        return context.listen(timeout, null);
    }

    private ScriptValue getValue(String name) {
        ScriptValue sv = context.vars.get(name);
        return sv == null ? ScriptValue.NULL : sv;
    }

    private String getAsString(String name) {
        return getValue(name).getAsString();
    }

    public boolean pathMatches(String path) {
        String uri = getAsString(ScriptValueMap.VAR_REQUEST_URI);

        Map<String, String> pathParams = HttpUtils.parseUriPattern(path, uri);
        set(ScriptBindings.PATH_PARAMS, pathParams);
        boolean matched = pathParams != null;

        List<Integer> pathMatchScores = null;
        if (matched) {
            pathMatchScores = HttpUtils.calculatePathMatchScore(path);
        }

        set(ScriptBindings.PATH_MATCH_SCORES, pathMatchScores);
        return matched;
    }

    public boolean methodIs(String... methods) {
        String actual = getAsString(ScriptValueMap.VAR_REQUEST_METHOD);
        boolean match = Arrays.stream(methods).anyMatch((m) -> actual.equalsIgnoreCase(m));

        boolean existingValue = (Boolean) get(ScriptBindings.METHOD_MATCH, Boolean.FALSE);
        set(ScriptBindings.METHOD_MATCH, match || existingValue);

        return match;
    }

    public Object paramValue(String name) {
        List<String> list = paramValues(name);
        if (list == null) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        return list;
    }

    private List<String> paramValues(String name) {
        Map<String, List<String>> params = (Map) getValue(ScriptValueMap.VAR_REQUEST_PARAMS).getValue();
        if (params == null) {
            return null;
        }
        return params.get(name);
    }

    public boolean paramExists(String name) {
        List<String> list = paramValues(name);
        return list != null && !list.isEmpty();
    }

    public boolean headerContains(String name, String test) {
        Map<String, List<String>> headers = (Map) getValue(ScriptValueMap.VAR_REQUEST_HEADERS).getValue();
        if (headers == null) {
            return false;
        }
        List<String> list = headers.get(name);
        if (list == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && s.contains(test)) {
                int existingValue = (int) get(ScriptBindings.HEADERS_MATCH_SCORE, 0);
                set(ScriptBindings.HEADERS_MATCH_SCORE, existingValue + 1);
                return true;
            }
        }
        return false;
    }

    public boolean typeContains(String test) {
        return headerContains(HttpUtils.HEADER_CONTENT_TYPE, test);
    }

    public boolean acceptContains(String test) {
        return headerContains(HttpUtils.HEADER_ACCEPT, test);
    }

    public Object bodyPath(String path) {
        ScriptValue sv = context.vars.get(ScriptValueMap.VAR_REQUEST);
        if (sv == null || sv.isNull()) {
            return null;
        }
        if (path.startsWith("/")) {
            return xmlPath(sv.getValue(), path);
        } else {
            return jsonPath(sv.getValue(), path);
        }
    }

    public FeatureServer start(String mock) {
        return start(Collections.singletonMap("mock", mock));
    }

    public FeatureServer start(Map<String, Object> config) {
        List<String> mocks;
        if (config.get("mock") instanceof String) {
            mocks = Arrays.asList((String) config.get("mock"));
        } else {
            mocks = (List<String>) config.get("mock");
        }
        if (mocks == null || mocks.isEmpty()) {
            throw new RuntimeException("'mock' is missing: " + config);
        }
        List<Feature> features = new ArrayList<>();
        for (String mock : mocks) {
            ScriptValue mockSv = FileUtils.readFile(mock, context);
            if (!mockSv.isFeature()) {
                throw new RuntimeException("'mock' is not a feature file: " + config + ", " + mockSv);
            }
            Feature feature = mockSv.getValue(Feature.class);
            features.add(feature);
        }
        String certFile = (String) config.get("cert");
        String keyFile = (String) config.get("key");
        Boolean ssl = (Boolean) config.get("ssl");
        if (ssl == null) {
            ssl = false;
        }
        Integer port = (Integer) config.get("port");
        if (port == null) {
            port = 0;
        }
        Map<String, Object> arg = (Map) config.get("arg");
        if (certFile != null && keyFile != null) {
            ScriptValue certSv = FileUtils.readFile(certFile, context);
            if (!certSv.isStream()) {
                throw new RuntimeException("'cert' is not valid: " + config + ", " + certSv);
            }
            ScriptValue keySv = FileUtils.readFile(keyFile, context);
            if (!keySv.isStream()) {
                throw new RuntimeException("'key' is not valid: " + config + ", " + keySv);
            }
            return new FeatureServer(features.toArray(new Feature[0]), port, ssl, certSv.getAsStream(), keySv.getAsStream(), arg);
        } else {
            return new FeatureServer(features.toArray(new Feature[0]), port, ssl, arg);
        }
    }

    public String getEnv() {
        return context.featureContext.env;
    }

    public Map<String, Object> getOs() {
        String name = FileUtils.getOsName();
        String type = FileUtils.getOsType(name).toString().toLowerCase();
        Map<String, Object> map = new HashMap(2);
        map.put("name", name);
        map.put("type", type);
        return map;
    }

    public Properties getProperties() {
        return System.getProperties();
    }

    public String extract(String text, String regex, int group) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            context.logger.warn("failed to find pattern: {}", regex);
            return null;
        }
        return matcher.group(group);
    }

    public List<String> extractAll(String text, String regex, int group) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        List<String> list = new ArrayList();
        while (matcher.find()) {
            list.add(matcher.group(group));
        }
        return list;
    }

    public Http http(String url) {
        return Http.forUrl(context, url);
    }

    public void stop(int port) {
        Command.waitForSocket(port);
    }

    public void abort() {
        throw new KarateAbortException(null);
    }

    public void fail(String message) {
        throw new KarateFailException(message);
    }

    public String toAbsolutePath(String relativePath) {
        Resource resource = FileUtils.toResource(relativePath, context);
        return resource.getPath().normalize().toAbsolutePath().toString();
    }

    public boolean waitForPort(String host, int port) {
        return Command.waitForPort(host, port);
    }

    public boolean waitForHttp(String url) {
        return Command.waitForHttp(url);
    }

    public String exec(List<String> args) {
        return exec(Collections.singletonMap("args", args));
    }

    public String exec(String line) {
        return exec(Collections.singletonMap("line", line));
    }

    public String exec(Map<String, Object> options) {
        Command command = toCommand(false, options);
        command.waitSync();
        return command.getAppender().collect();
    }

    public Command fork(List<String> args) {
        return fork(Collections.singletonMap("args", args));
    }

    public Command fork(String line) {
        return fork(Collections.singletonMap("line", line));
    }

    public Command fork(Map<String, Object> options) {
        return toCommand(true, options);
    }

    private static String[] toArgs(String line) {
        switch (FileUtils.getOsType()) {
            case WINDOWS:
                return new String[]{"cmd", "/c", line};
            default:
                return new String[]{"sh", "-c", line};
        }
    }

    private Command toCommand(boolean useLineFeed, Map<String, Object> options) {
        options = new ScriptValue(options).getAsMap(); // TODO fix nashorn quirks
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
            args = useShell ? toArgs(line) : Command.tokenize(line);
        } else {
            String joined = StringUtils.join(list, ' ');
            args = useShell ? toArgs(joined) : list.toArray(new String[list.size()]);
        }
        String workingDir = (String) options.get("workingDir");
        File workingFile = workingDir == null ? null : new File(workingDir);
        Command command = new Command(useLineFeed, context.logger, null, null, workingFile, args);
        Map env = (Map) options.get("env");
        if (env != null) {
            command.setEnvironment(env);
        }
        Boolean redirectErrorStream = (Boolean) options.get("redirectErrorStream");
        if (redirectErrorStream != null) {
            command.setRedirectErrorStream(redirectErrorStream);
        }
        ScriptObjectMirror somOut = (ScriptObjectMirror) options.get("listener");
        if (somOut != null) {
            command.setListener(s -> Script.evalJsFunctionCall(somOut, s, context));
        }
        ScriptObjectMirror somErr = (ScriptObjectMirror) options.get("errorListener");
        if (somErr != null) {
            command.setErrorListener(s -> Script.evalJsFunctionCall(somErr, s, context));
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

    public void log(Object... objects) {
        if (context.isPrintEnabled()) {
            context.logger.info("{}", new LogWrapper(objects));
        }
    }

    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        context.capturePerfEvent(event);
    }

    // make sure toString() is lazy
    static class LogWrapper {

        private final Object[] objects;

        LogWrapper(Object... objects) {
            this.objects = objects;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : objects) {
                ScriptValue sv = new ScriptValue(o);
                sb.append(sv.getAsPrettyString()).append(' ');
            }
            return sb.toString();
        }

    }

}
