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

import com.intuit.karate.EventContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.KarateException;
import com.intuit.karate.Logger;
import com.intuit.karate.Match;
import com.intuit.karate.MatchStep;
import com.intuit.karate.PerfContext;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsLambda;
import com.intuit.karate.graal.JsList;
import com.intuit.karate.graal.JsMap;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.http.WebSocketClient;
import com.intuit.karate.http.WebSocketOptions;
import com.intuit.karate.shell.Command;
import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public class ScenarioBridge implements PerfContext, EventContext {

    private final ScenarioEngine ENGINE;

    protected ScenarioBridge(ScenarioEngine engine) {
        ENGINE = engine;
    }

    public void abort() {
        getEngine().setAborted(true);
    }

    public Object append(Value... vals) {
        List list = new ArrayList();
        JsList jsList = new JsList(list);
        if (vals.length == 0) {
            return jsList;
        }
        Value val = vals[0];
        if (val.hasArrayElements()) {
            list.addAll(val.as(List.class));
        } else {
            list.add(val.as(Object.class));
        }
        if (vals.length == 1) {
            return jsList;
        }
        for (int i = 1; i < vals.length; i++) {
            Value v = vals[i];
            if (v.hasArrayElements()) {
                list.addAll(v.as(List.class));
            } else {
                list.add(v.as(Object.class));
            }
        }
        return jsList;
    }

    private Object appendToInternal(String varName, Value... vals) {
        ScenarioEngine engine = getEngine();
        Variable var = engine.vars.get(varName);
        if (!var.isList()) {
            return null;
        }
        List list = var.getValue();
        for (Value v : vals) {
            if (v.hasArrayElements()) {
                list.addAll(v.as(List.class));
            } else {
                Object temp = v.as(Object.class);
                list.add(temp);
            }
        }
        engine.setVariable(varName, list);
        return new JsList(list);
    }

    public Object appendTo(Value ref, Value... vals) {
        if (ref.isString()) {
            return appendToInternal(ref.asString(), vals);
        }
        List list;
        if (ref.hasArrayElements()) {
            list = new JsValue(ref).getAsList(); // make sure we unwrap the "original" list
        } else {
            list = new ArrayList();
        }
        for (Value v : vals) {
            if (v.hasArrayElements()) {
                list.addAll(v.as(List.class));
            } else {
                Object temp = v.as(Object.class);
                list.add(temp);
            }
        }
        return new JsList(list);
    }

    public Object call(String fileName) {
        return call(false, fileName, null);
    }

    public Object call(String fileName, Value arg) {
        return call(false, fileName, arg);
    }

    public Object call(boolean sharedScope, String fileName) {
        return call(sharedScope, fileName, null);
    }

    public Object call(boolean sharedScope, String fileName, Value arg) {
        ScenarioEngine engine = getEngine();
        Variable called = new Variable(engine.fileReader.readFile(fileName));
        Variable result = engine.call(called, arg == null ? null : new Variable(arg), sharedScope);
        return JsValue.fromJava(result.getValue());
    }

    private static Object callSingleResult(ScenarioEngine engine, Object o) throws Exception {
        if (o instanceof Exception) {
            engine.logger.warn("callSingle() cached result is an exception");
            throw (Exception) o;
        }
        // if we don't clone, an attach operation would update the tree within the cached value
        // causing future cache hit + attach attempts to fail !
        o = engine.recurseAndAttachAndShallowClone(o);
        return JsValue.fromJava(o);
    }

    public Object callSingle(String fileName) throws Exception {
        return callSingle(fileName, null);
    }

    public Object callSingle(String fileName, Value arg) throws Exception {
        ScenarioEngine engine = getEngine();
        final Map<String, Object> CACHE = engine.runtime.featureRuntime.suite.callSingleCache;
        if (CACHE.containsKey(fileName)) {
            engine.logger.trace("callSingle cache hit: {}", fileName);
            return callSingleResult(engine, CACHE.get(fileName));
        }
        long startTime = System.currentTimeMillis();
        engine.logger.trace("callSingle waiting for lock: {}", fileName);
        synchronized (CACHE) { // lock
            if (CACHE.containsKey(fileName)) { // retry
                long endTime = System.currentTimeMillis() - startTime;
                engine.logger.warn("this thread waited {} milliseconds for callSingle lock: {}", endTime, fileName);
                return callSingleResult(engine, CACHE.get(fileName));
            }
            // this thread is the 'winner'
            engine.logger.info(">> lock acquired, begin callSingle: {}", fileName);
            int minutes = engine.getConfig().getCallSingleCacheMinutes();
            Object result = null;
            File cacheFile = null;
            if (minutes > 0) {
                String cleanedName = StringUtils.toIdString(fileName);
                String cacheFileName = engine.getConfig().getCallSingleCacheDir() + File.separator + cleanedName + ".txt";
                cacheFile = new File(cacheFileName);
                long since = System.currentTimeMillis() - minutes * 60 * 1000;
                if (cacheFile.exists()) {
                    long lastModified = cacheFile.lastModified();
                    if (lastModified > since) {
                        String json = FileUtils.toString(cacheFile);
                        result = JsonUtils.fromJson(json);
                        engine.logger.info("callSingleCache hit: {}", cacheFile);
                    } else {
                        engine.logger.info("callSingleCache stale, last modified {} - is before {} (minutes: {})",
                                lastModified, since, minutes);
                    }
                } else {
                    engine.logger.info("callSingleCache file does not exist, will create: {}", cacheFile);
                }
            }
            if (result == null) {
                Variable called = new Variable(read(fileName));
                Variable argVar;
                if (arg == null || arg.isNull()) {
                    argVar = null;
                } else {
                    argVar = new Variable(arg);
                }
                Variable resultVar;
                try {
                    resultVar = engine.call(called, argVar, false);
                } catch (Exception e) {
                    // don't retain any vestiges of graal-js 
                    RuntimeException re = new RuntimeException(e.getMessage());
                    // we do this so that an exception is also "cached"
                    resultVar = new Variable(re); // will be thrown at end
                    engine.logger.warn("callSingle() will cache an exception");
                }
                if (minutes > 0) { // cacheFile will be not null
                    if (resultVar.isMapOrList()) {
                        String json = resultVar.getAsString();
                        FileUtils.writeToFile(cacheFile, json);
                        engine.logger.info("callSingleCache write: {}", cacheFile);
                    } else {
                        engine.logger.warn("callSingleCache write failed, not json-like: {}", resultVar);
                    }
                }
                // functions have to be detached so that they can be re-hydrated in another js context
                result = engine.recurseAndDetachAndShallowClone(resultVar.getValue());
            }
            CACHE.put(fileName, result);
            engine.logger.info("<< lock released, cached callSingle: {}", fileName);
            return callSingleResult(engine, result);
        }
    }

    public Object callonce(String path) {
        return callonce(false, path);
    }

    public Object callonce(boolean sharedScope, String path) {
        String exp = "read('" + path + "')";
        Variable v = getEngine().call(true, exp, sharedScope);
        return JsValue.fromJava(v.getValue());
    }

    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        getEngine().capturePerfEvent(event);
    }

    public void configure(String key, Value value) {
        getEngine().configure(key, new Variable(value));
    }

    public Object distinct(Value o) {
        if (!o.hasArrayElements()) {
            return JsList.EMPTY;
        }
        long count = o.getArraySize();
        Set<Object> set = new LinkedHashSet();
        for (int i = 0; i < count; i++) {
            Object value = JsValue.toJava(o.getArrayElement(i));
            set.add(value);
        }
        return JsValue.fromJava(new ArrayList(set));
    }

    public String doc(Value v) {
        Map<String, Object> arg;
        if (v.isString()) {
            arg = Collections.singletonMap("read", v.asString());
        } else if (v.hasMembers()) {
            arg = new JsValue(v).getAsMap();
        } else {
            getEngine().logger.warn("doc - unexpected argument: {}", v);
            return null;
        }
        return getEngine().docInternal(arg);
    }

    public void embed(Object o, String contentType) {
        ResourceType resourceType;
        if (contentType == null) {
            resourceType = ResourceType.fromObject(o, ResourceType.BINARY);
        } else {
            resourceType = ResourceType.fromContentType(contentType);
        }
        getEngine().runtime.embed(JsValue.toBytes(o), resourceType);
    }

    public Object eval(String exp) {
        Variable result = getEngine().evalJs(exp);
        return JsValue.fromJava(result.getValue());
    }

    public String exec(Value value) {
        if (value.isString()) {
            return execInternal(Collections.singletonMap("line", value.asString()));
        } else if (value.hasArrayElements()) {
            List args = new JsValue(value).getAsList();
            return execInternal(Collections.singletonMap("args", args));
        } else {
            return execInternal(new JsValue(value).getAsMap());
        }
    }

    private String execInternal(Map<String, Object> options) {
        Command command = getEngine().fork(false, options);
        command.waitSync();
        return command.getAppender().collect();
    }

    public String extract(String text, String regex, int group) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            getEngine().logger.warn("failed to find pattern: {}", regex);
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

    public void fail(String reason) {
        getEngine().setFailedReason(new KarateException(reason));
    }

    public Object filter(Value o, Value f) {
        if (!o.hasArrayElements()) {
            return JsList.EMPTY;
        }
        assertIfJsFunction(f);
        long count = o.getArraySize();
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            Value v = o.getArrayElement(i);
            Value res = JsEngine.execute(f, v, i);
            if (res.isBoolean() && res.asBoolean()) {
                list.add(new JsValue(v).getValue());
            }
        }
        return new JsList(list);
    }

    public Object filterKeys(Value o, Value... args) {
        if (!o.hasMembers() || args.length == 0) {
            return JsMap.EMPTY;
        }
        List<String> keys = new ArrayList();
        if (args.length == 1) {
            if (args[0].isString()) {
                keys.add(args[0].asString());
            } else if (args[0].hasArrayElements()) {
                long count = args[0].getArraySize();
                for (int i = 0; i < count; i++) {
                    keys.add(args[0].getArrayElement(i).toString());
                }
            } else if (args[0].hasMembers()) {
                for (String s : args[0].getMemberKeys()) {
                    keys.add(s);
                }
            }
        } else {
            for (Value v : args) {
                keys.add(v.toString());
            }
        }
        Map map = new LinkedHashMap(keys.size());
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Value v = o.getMember(key);
            if (v != null) {
                map.put(key, v.as(Object.class));
            }
        }
        return new JsMap(map);
    }

    public void forEach(Value o, Value f) {
        assertIfJsFunction(f);
        if (o.hasArrayElements()) {
            long count = o.getArraySize();
            for (int i = 0; i < count; i++) {
                Value v = o.getArrayElement(i);
                f.executeVoid(v, i);
            }
        } else if (o.hasMembers()) { //map
            int i = 0;
            for (String k : o.getMemberKeys()) {
                Value v = o.getMember(k);
                f.executeVoid(k, v, i++);
            }
        } else {
            throw new RuntimeException("not an array or object: " + o);
        }
    }

    public Command fork(Value value) {
        if (value.isString()) {
            return getEngine().fork(true, value.asString());
        } else if (value.hasArrayElements()) {
            List args = new JsValue(value).getAsList();
            return getEngine().fork(true, args);
        } else {
            return getEngine().fork(true, new JsValue(value).getAsMap());
        }
    }

    // TODO breaking returns actual object not wrapper
    // and fromObject() has been removed
    // use new typeOf() method to find type
    public Object fromString(String exp) {
        ScenarioEngine engine = getEngine();
        try {
            Variable result = engine.evalKarateExpression(exp);
            return JsValue.fromJava(result.getValue());
        } catch (Exception e) {
            engine.setFailedReason(null); // special case
            engine.logger.warn("auto evaluation failed: {}", e.getMessage());
            return exp;
        }
    }

    public Object get(String exp) {
        ScenarioEngine engine = getEngine();
        Variable v;
        try {
            v = engine.evalKarateExpression(exp); // even json path expressions will work
        } catch (Exception e) {
            engine.logger.trace("karate.get failed for expression: '{}': {}", exp, e.getMessage());
            engine.setFailedReason(null); // special case !
            return null;
        }
        if (v != null) {
            return JsValue.fromJava(v.getValue());
        } else {
            return null;
        }
    }

    public Object get(String exp, Object defaultValue) {
        Object result = get(exp);
        return result == null ? defaultValue : result;
    }

    // getters =================================================================
    // TODO migrate these to functions not properties
    //
    public ScenarioEngine getEngine() {
        ScenarioEngine engine = ScenarioEngine.get();
        return engine == null ? ENGINE : engine;
    }

    public String getEnv() {
        return getEngine().runtime.featureRuntime.suite.env;
    }

    public Object getFeature() {
        return new JsMap(getEngine().runtime.featureRuntime.result.toInfoJson());
    }

    public Object getInfo() { // TODO deprecate
        return new JsMap(getEngine().runtime.getScenarioInfo());
    }

    private LogFacade logFacade;

    public Object getLogger() {
        if (logFacade == null) {
            logFacade = new LogFacade();
        }
        return logFacade;
    }

    public Object getOs() {
        String name = FileUtils.getOsName();
        String type = FileUtils.getOsType(name).toString().toLowerCase();
        Map<String, Object> map = new HashMap(2);
        map.put("name", name);
        map.put("type", type);
        return new JsMap(map);
    }

    // TODO breaking uri has been renamed to url
    public Object getPrevRequest() {
        HttpRequest hr = getEngine().getRequest();
        if (hr == null) {
            return null;
        }
        Map<String, Object> map = new HashMap();
        map.put("method", hr.getMethod());
        map.put("url", hr.getUrl());
        map.put("headers", hr.getHeaders());
        map.put("body", hr.getBody());
        return JsValue.fromJava(map);
    }

    public Object getProperties() {
        return new JsMap(getEngine().runtime.featureRuntime.suite.systemProperties);
    }

    public Object getScenario() {
        return new JsMap(getEngine().runtime.result.toKarateJson());
    }

    public Object getTags() {
        return JsValue.fromJava(getEngine().runtime.tags.getTags());
    }

    public Object getTagValues() {
        return JsValue.fromJava(getEngine().runtime.tags.getTagValues());
    }

    //==========================================================================
    //
    public HttpRequestBuilder http(String url) {
        ScenarioEngine engine = getEngine();
        HttpClient client = engine.runtime.featureRuntime.suite.clientFactory.create(engine);
        return new HttpRequestBuilder(client).url(url);
    }

    public Object jsonPath(Object o, String exp) {
        Json json = Json.of(o);
        return JsValue.fromJava(json.get(exp));
    }

    public Object keysOf(Value o) {
        return new JsList(o.getMemberKeys());
    }

    public void log(Value... values) {
        ScenarioEngine engine = getEngine();
        if (engine.getConfig().isPrintEnabled()) {
            engine.logger.info("{}", new LogWrapper(values));
        }
    }

    public Object lowerCase(Object o) {
        Variable var = new Variable(o);
        return JsValue.fromJava(var.toLowerCase().getValue());
    }

    public Object map(Value o, Value f) {
        if (!o.hasArrayElements()) {
            return JsList.EMPTY;
        }
        assertIfJsFunction(f);
        long count = o.getArraySize();
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            Value v = o.getArrayElement(i);
            Value res = JsEngine.execute(f, v, i);
            list.add(new JsValue(res).getValue());
        }
        return new JsList(list);
    }

    public Object mapWithKey(Value v, String key) {
        if (!v.hasArrayElements()) {
            return JsList.EMPTY;
        }
        long count = v.getArraySize();
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            Map map = new LinkedHashMap();
            Value res = v.getArrayElement(i);
            map.put(key, res.as(Object.class));
            list.add(map);
        }
        return new JsList(list);
    }

    public Object match(Object actual, Object expected) {
        Match.Result mr = getEngine().match(Match.Type.EQUALS, actual, expected);
        return JsValue.fromJava(mr.toMap());
    }

    public Object match(String exp) {
        MatchStep ms = new MatchStep(exp);
        Match.Result mr = getEngine().match(ms.type, ms.name, ms.path, ms.expected);
        return JsValue.fromJava(mr.toMap());
    }

    public Object merge(Value... vals) {
        if (vals.length == 0) {
            return null;
        }
        if (vals.length == 1) {
            return vals[0];
        }
        Map map = new HashMap(vals[0].as(Map.class));
        for (int i = 1; i < vals.length; i++) {
            map.putAll(vals[i].as(Map.class));
        }
        return new JsMap(map);
    }

    public void pause(Value value) {
        ScenarioEngine engine = getEngine();
        if (!value.isNumber()) {
            engine.logger.warn("pause argument is not a number:", value);
            return;
        }
        if (engine.runtime.perfMode) {
            engine.runtime.featureRuntime.perfHook.pause(value.asInt());
        } else if (engine.getConfig().isPauseIfNotPerf()) {
            try {
                Thread.sleep(value.asInt());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String pretty(Object o) {
        Variable v = new Variable(o);
        return v.getAsPrettyString();
    }

    public String prettyXml(Object o) {
        Variable v = new Variable(o);
        return v.getAsPrettyXmlString();
    }

    public void proceed() {
        proceed(null);
    }

    public void proceed(String requestUrlBase) {
        getEngine().mockProceed(requestUrlBase);
    }

    public Object range(int start, int end) {
        return range(start, end, 1);
    }

    public Object range(int start, int end, int interval) {
        if (interval <= 0) {
            throw new RuntimeException("interval must be a positive integer");
        }
        List<Integer> list = new ArrayList();
        if (start <= end) {
            for (int i = start; i <= end; i += interval) {
                list.add(i);
            }
        } else {
            for (int i = start; i >= end; i -= interval) {
                list.add(i);
            }
        }
        return JsValue.fromJava(list);
    }

    public Object read(String name) {
        Object result = getEngine().fileReader.readFile(name);
        return JsValue.fromJava(result);
    }

    public String readAsString(String fileName) {
        return getEngine().fileReader.readFileAsString(fileName);
    }

    public void remove(String name, String path) {
        getEngine().remove(name, path);
    }

    public Object repeat(int n, Value f) {
        assertIfJsFunction(f);
        List list = new ArrayList(n);
        for (int i = 0; i < n; i++) {
            Value v = JsEngine.execute(f, i);
            list.add(new JsValue(v).getValue());
        }
        return new JsList(list);
    }

    // set multiple variables in one shot
    public void set(Map<String, Object> map) {
        getEngine().setVariables(map);
    }

    public void set(String name, Value value) {
        getEngine().setVariable(name, new Variable(value));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void set(String name, String path, Object value) {
        getEngine().set(name, path, new Variable(value));
    }

    public void setXml(String name, String xml) {
        getEngine().setVariable(name, XmlUtils.toXmlDoc(xml));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void setXml(String name, String path, String xml) {
        getEngine().set(name, path, new Variable(XmlUtils.toXmlDoc(xml)));
    }

    @Override
    public void signal(Object o) {
        Value v = Value.asValue(o);
        getEngine().signal(JsValue.toJava(v));
    }

    public Object sizeOf(Value v) {
        if (v.hasArrayElements()) {
            return v.getArraySize();
        } else if (v.hasMembers()) {
            return v.getMemberKeys().size();
        } else {
            return -1;
        }
    }

    public Object sort(Value o) {
        return sort(o, getEngine().JS.evalForValue("x => x"));
    }

    public Object sort(Value o, Value f) {
        if (!o.hasArrayElements()) {
            return JsList.EMPTY;
        }
        assertIfJsFunction(f);
        long count = o.getArraySize();
        Map<Object, Object> map = new TreeMap();
        for (int i = 0; i < count; i++) {
            Object item = JsValue.toJava(o.getArrayElement(i));
            Value key = JsEngine.execute(f, item, i);
            if (key.isNumber()) {
                map.put(key.as(Number.class), item);
            } else {
                map.put(key.asString(), item);
            }
        }
        return JsValue.fromJava(new ArrayList(map.values()));
    }

    public MockServer start(Value value) {
        if (value.isString()) {
            return startInternal(Collections.singletonMap("mock", value.asString()));
        } else {
            return startInternal(new JsValue(value).getAsMap());
        }
    }

    private MockServer startInternal(Map<String, Object> config) {
        String mock = (String) config.get("mock");
        if (mock == null) {
            throw new RuntimeException("'mock' is missing: " + config);
        }
        File feature = toJavaFile(mock);
        MockServer.Builder builder = MockServer.feature(feature);
        String certFile = (String) config.get("cert");
        if (certFile != null) {
            builder.certFile(toJavaFile(certFile));
        }
        String keyFile = (String) config.get("key");
        if (keyFile != null) {
            builder.keyFile(toJavaFile(keyFile));
        }
        Boolean ssl = (Boolean) config.get("ssl");
        if (ssl == null) {
            ssl = false;
        }
        Integer port = (Integer) config.get("port");
        if (port == null) {
            port = 0;
        }
        Map<String, Object> arg = (Map) config.get("arg");
        builder.args(arg);
        if (ssl) {
            builder.https(port);
        } else {
            builder.http(port);
        }
        return builder.build();
    }

    public void stop(int port) {
        Command.waitForSocket(port);
    }

    public String toAbsolutePath(String relativePath) {
        return getEngine().fileReader.toAbsolutePath(relativePath);
    }

    public Object toBean(Object o, String className) {
        Json json = Json.of(o);
        Object bean = JsonUtils.fromJson(json.toString(), className);
        return JsValue.fromJava(bean);
    }

    public String toCsv(Object o) {
        Variable v = new Variable(o);
        if (!v.isList()) {
            throw new RuntimeException("not a json array: " + v);
        }
        List<Map<String, Object>> list = v.getValue();
        return JsonUtils.toCsv(list);
    }

    public Object toJava(Value value) {
        if (value.canExecute()) {
            JsEngine copy = getEngine().JS.copy();
            return new JsLambda(copy.attach(value));
        } else {
            return new JsValue(value).getValue();
        }
    }

    private File toJavaFile(String path) {
        return getEngine().fileReader.toResource(path).getFile();
    }

    public Object toJson(Value value) {
        return toJson(value, false);
    }

    public Object toJson(Value value, boolean removeNulls) {
        JsValue jv = new JsValue(value);
        String json = JsonUtils.toJson(jv.getValue());
        Object result = Json.of(json).value();
        if (removeNulls) {
            JsonUtils.removeKeysWithNullValues(result);
        }
        return JsValue.fromJava(result);
    }

    // TODO deprecate
    public Object toList(Value value) {
        return new JsValue(value).getValue();
    }

    // TODO deprecate
    public Object toMap(Value value) {
        return new JsValue(value).getValue();
    }

    public String toString(Object o) {
        Variable v = new Variable(o);
        return v.getAsString();
    }

    public String trim(String s) {
        return s == null ? null : s.trim();
    }

    public String typeOf(Value value) {
        Variable v = new Variable(value);
        return v.getTypeString();
    }

    public String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            getEngine().logger.warn("url encode failed: {}", e.getMessage());
            return s;
        }
    }

    public String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            getEngine().logger.warn("url encode failed: {}", e.getMessage());
            return s;
        }
    }

    public Object valuesOf(Value v) {
        if (v.hasArrayElements()) {
            return v;
        } else if (v.hasMembers()) {
            List list = new ArrayList();
            for (String k : v.getMemberKeys()) {
                Value res = v.getMember(k);
                list.add(res.as(Object.class));
            }
            return new JsList(list);
        } else {
            return null;
        }
    }

    public boolean waitForHttp(String url) {
        return Command.waitForHttp(url);
    }

    public boolean waitForPort(String host, int port) {
        return new Command().waitForPort(host, port);
    }

    public WebSocketClient webSocket(String url) {
        return webSocket(url, null, null);
    }

    public WebSocketClient webSocket(String url, Value value) {
        return webSocket(url, value, null);
    }

    public WebSocketClient webSocket(String url, Value listener, Value value) {
        Function<String, Boolean> handler;
        ScenarioEngine engine = getEngine();
        if (listener == null || !listener.canExecute()) {
            handler = m -> true;
        } else {
            JsEngine copy = engine.JS.copy();
            handler = new JsLambda(copy.attach(listener));
        }
        WebSocketOptions options = new WebSocketOptions(url, value == null ? null : new JsValue(value).getValue());
        options.setTextHandler(handler);
        return engine.webSocket(options);
    }

    public WebSocketClient webSocketBinary(String url) {
        return webSocketBinary(url, null, null);
    }

    public WebSocketClient webSocketBinary(String url, Value value) {
        return webSocketBinary(url, value, null);
    }

    public WebSocketClient webSocketBinary(String url, Value listener, Value value) {
        Function<byte[], Boolean> handler;
        ScenarioEngine engine = getEngine();
        if (listener == null || !listener.canExecute()) {
            handler = m -> true;
        } else {
            JsEngine copy = engine.JS.copy();
            handler = new JsLambda(copy.attach(listener));
        }
        WebSocketOptions options = new WebSocketOptions(url, value == null ? null : new JsValue(value).getValue());
        options.setBinaryHandler(handler);
        return engine.webSocket(options);
    }

    public File write(Object o, String path) {
        ScenarioEngine engine = getEngine();
        path = engine.runtime.featureRuntime.suite.buildDir + File.separator + path;
        File file = new File(path);
        FileUtils.writeToFile(file, JsValue.toBytes(o));
        engine.logger.debug("write to file: {}", file);
        return file;
    }

    public Object xmlPath(Object o, String path) {
        Variable var = new Variable(o);
        Variable res = ScenarioEngine.evalXmlPath(var, path);
        return JsValue.fromJava(res.getValue());
    }

    // helpers =================================================================
    //
    private static void assertIfJsFunction(Value f) {
        if (!f.canExecute()) {
            throw new RuntimeException("not a js function: " + f);
        }
    }

    // make sure log() toString() is lazy
    static class LogWrapper {

        final Value[] values;

        LogWrapper(Value... values) {
            // sometimes a null array gets passed in, graal weirdness
            this.values = values == null ? new Value[0] : values;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Value v : values) {
                Variable var = new Variable(v);
                sb.append(var.getAsPrettyString()).append(' ');
            }
            return sb.toString();
        }

    }

    public static class LogFacade {

        private static Logger getLogger() {
            return ScenarioEngine.get().logger;
        }

        private static String wrap(Value... values) {
            return new LogWrapper(values).toString();
        }

        public void debug(Value... values) {
            getLogger().debug(wrap(values));
        }

        public void info(Value... values) {
            getLogger().info(wrap(values));
        }

        public void trace(Value... values) {
            getLogger().trace(wrap(values));
        }

        public void warn(Value... values) {
            getLogger().warn(wrap(values));
        }

        public void error(Value... values) {
            getLogger().error(wrap(values));
        }

    }

}
