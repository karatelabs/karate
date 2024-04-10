/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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

import com.intuit.karate.*;
import com.intuit.karate.http.*;
import com.intuit.karate.js.JsEngine;
import com.intuit.karate.shell.Command;
import io.karatelabs.js.Invokable;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pthomas3
 */
public class ScenarioBridge implements PerfContext {

    private final ScenarioEngine ENGINE;

    protected ScenarioBridge(ScenarioEngine engine) {
        ENGINE = engine;
    }

    public void abort() {
        getEngine().setAborted(true);
    }

    public Object append(Object... vals) {
        List list = new ArrayList();
        if (vals.length == 0) {
            return list;
        }
        Object val = vals[0];
        if (val instanceof List) {
            list.addAll((List) val);
        } else {
            list.add(val);
        }
        if (vals.length == 1) {
            return list;
        }
        for (int i = 1; i < vals.length; i++) {
            Object v = vals[i];
            if (v instanceof List) {
                list.addAll((List) v);
            } else {
                list.add(v);
            }
        }
        return list;
    }

    private Object appendToInternal(String varName, Object... vals) {
        ScenarioEngine engine = getEngine();
        Variable var = engine.vars.get(varName);
        if (!var.isList()) {
            return null;
        }
        List list = var.getValue();
        for (Object v : vals) {
            if (v instanceof List) {
                list.addAll((List) v);
            } else {
                list.add(v);
            }
        }
        engine.setVariable(varName, list);
        return list;
    }

    public Object appendTo(Object ref, Object... vals) {
        if (ref instanceof String) {
            return appendToInternal((String) ref, vals);
        }
        List list;
        if (ref instanceof List) {
            list = (List) ref;
        } else {
            list = new ArrayList();
        }
        for (Object v : vals) {
            if (v instanceof List) {
                list.addAll((List) v);
            } else {
                list.add(v);
            }
        }
        return list;
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

    public Object call(boolean sharedScope, String fileName, Object arg) {
        ScenarioEngine engine = getEngine();
        Variable called = new Variable(engine.fileReader.readFile(fileName));
        Variable result = engine.call(called, arg == null ? null : new Variable(arg), sharedScope);
        if (sharedScope) {
            if (result.isMap()) {
                engine.setVariables(result.getValue());
            }
        }
        return result.getValue();
    }

    private static Object callSingleResult(ScenarioEngine engine, Object o) throws Exception {
        if (o instanceof Exception) {
            engine.logger.warn("callSingle() cached result is an exception");
            throw (Exception) o;
        }
        return o;
    }

    public Object callSingle(String fileName) throws Exception {
        return callSingle(fileName, null);
    }

    public Object callSingle(String fileName, Object arg) throws Exception {
        ScenarioEngine engine = getEngine();
        final Map<String, Object> CACHE = engine.runtime.featureRuntime.suite.callSingleCache;
        int minutes = engine.getConfig().getCallSingleCacheMinutes();
        if ((minutes == 0) && CACHE.containsKey(fileName)) {
            engine.logger.trace("callSingle cache hit: {}", fileName);
            return callSingleResult(engine, CACHE.get(fileName));
        }
        long startTime = System.currentTimeMillis();
        engine.logger.trace("callSingle waiting for lock: {}", fileName);
        synchronized (CACHE) { // lock
            if ((minutes == 0) && CACHE.containsKey(fileName)) { // retry
                long endTime = System.currentTimeMillis() - startTime;
                engine.logger.warn("this thread waited {} milliseconds for callSingle lock: {}", endTime, fileName);
                return callSingleResult(engine, CACHE.get(fileName));
            }
            // this thread is the 'winner'
            engine.logger.info(">> lock acquired, begin callSingle: {}", fileName);
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
                if (arg == null) {
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
                result = resultVar.getValue();
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
        return v.getValue();
    }

    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        getEngine().capturePerfEvent(event);
    }

    public Object compareImage(Object baseline, Object latest, Object... optionsVal) {
        if (optionsVal.length > 0 && !(optionsVal[0] instanceof Map)) {
            throw new RuntimeException("invalid image comparison options: expected map");
        }
        Map<String, Object> options = new HashMap<>();
        if (optionsVal.length > 0) {
            Map<String, Object> map = (Map<String, Object>) optionsVal[0];
            for (String k : map.keySet()) {
                options.put(k, map.get(k));
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("baseline", baseline);
        params.put("latest", latest);
        params.put("options", options);

        return getEngine().compareImageInternal(params);
    }

    public void configure(String key, Object value) {
        getEngine().configure(key, new Variable(value));
    }

    public Object consume(String type) {
        return getEngine().consume(type);
    }

    public Object distinct(Object o) {
        if (!(o instanceof List)) {
            return new ArrayList<>();
        }
        List list = (List) o;
        long count = list.size();
        Set<Object> set = new LinkedHashSet();
        for (int i = 0; i < count; i++) {
            Object value = list.get(i);
            set.add(value);
        }
        return new ArrayList(set);
    }

    public String doc(Object v) {
        Map<String, Object> arg;
        if (v instanceof String) {
            arg = Collections.singletonMap("read", (String) v);
        } else if (v instanceof Map) {
            arg = (Map) v;
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
        getEngine().runtime.embed(JsonUtils.toBytes(o), resourceType);
    }

    public Object eval(String exp) {
        Variable result = getEngine().evalJs(exp);
        return result.getValue();
    }

    public String exec(Object value) {
        if (value instanceof String) {
            return execInternal(Collections.singletonMap("line", (String) value));
        } else if (value instanceof List) {
            List args = (List) value;
            return execInternal(Collections.singletonMap("args", args));
        } else {
            return execInternal((Map) value);
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

    public Object filter(Object o, Object f) {
        if (!(o instanceof List)) {
            return new ArrayList<>();
        }
        Invokable invokable = assertIfJsFunction(f);
        List list = (List) o;
        long count = list.size();
        List result = new ArrayList();
        for (int i = 0; i < count; i++) {
            Object v = list.get(i);
            Object res = JsEngine.invoke(invokable, v, i);
            if (res instanceof Boolean && (Boolean) res) {
                result.add(v);
            }
        }
        return result;
    }

    public Object filterKeys(Object o, Object... args) {
        Variable v = new Variable(o);
        if (!v.isMap()) {
            return new LinkedHashMap<>();
        }
        List<String> keys = new ArrayList();
        if (args.length == 1) {
            if (args[0] instanceof String) {
                keys.add((String) args[0]);
            } else if (args[0] instanceof List) {
                List argsList = (List) args[0];
                long count = argsList.size();
                for (int i = 0; i < count; i++) {
                    keys.add(argsList.get(i).toString());
                }
            } else if (args[0] instanceof Map) {
                Map<String, Object> argsMap = (Map) args[0];
                for (String s : argsMap.keySet()) {
                    keys.add(s);
                }
            }
        } else {
            for (Object key : args) {
                keys.add(key.toString());
            }
        }
        Map map = v.getValue();
        Map result = new LinkedHashMap(keys.size());
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (map.containsKey(key)) {
                result.put(key, map.get(key));
            }
        }
        return result;
    }

    public void forEach(Object o, Object f) {
        Invokable invokable = assertIfJsFunction(f);
        if (o instanceof List) {
            List list = (List) o;
            long count = list.size();
            for (int i = 0; i < count; i++) {
                Object v = list.get(i);
                JsEngine.invoke(invokable, v, i);
            }
        } else if (o instanceof Map) {
            Map<String, Object> map = (Map) o;
            int i = 0;
            for (String k : map.keySet()) {
                Object v = map.get(k);
                JsEngine.invoke(invokable, k, v, i++);
            }
        } else {
            throw new RuntimeException("not an array or object: " + o);
        }
    }

    public Command fork(Object value) {
        if (value instanceof String) {
            return getEngine().fork(true, (String) value);
        } else if (value instanceof List) {
            List args = (List) value;
            return getEngine().fork(true, args);
        } else {
            return getEngine().fork(true, (Map) value);
        }
    }

    // TODO breaking returns actual object not wrapper
    // and fromObject() has been removed
    // use new typeOf() method to find type
    public Object fromString(String exp) {
        ScenarioEngine engine = getEngine();
        try {
            Variable result = engine.evalKarateExpression(exp);
            return result.getValue();
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
            return v.getValue();
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
        return getEngine().runtime.featureRuntime.result.toInfoJson();
    }

    public Object getInfo() { // TODO deprecate
        return getEngine().runtime.getScenarioInfo();
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
        return map;
    }

    public Object getPrevRequest() {
        HttpRequest hr = getEngine().getHttpRequest();
        if (hr == null) {
            return null;
        }
        Map<String, Object> map = new HashMap();
        map.put("method", hr.getMethod());
        map.put("url", hr.getUrl());
        map.put("headers", hr.getHeaders());
        map.put("body", hr.getBody());
        return map;
    }

    public Object getProperties() {
        return getEngine().runtime.featureRuntime.suite.systemProperties;
    }

    public Object getResponse() {
        return getEngine().getResponse();
    }

    public Object getRequest() {
        return getEngine().getRequest();
    }

    public Object getScenario() {
        return getEngine().runtime.result.toKarateJson();
    }

    public Object getTags() {
        return getEngine().runtime.tags.getTags();
    }

    public Object getTagValues() {
        return getEngine().runtime.tags.getTagValues();
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
        return json.get(exp);
    }

    public Object keysOf(Object o) {
        Variable v = new Variable(o);
        if (v.isMap()) {
            return new ArrayList<>(v.<Map>getValue().keySet());
        } else {
            return new ArrayList<>();
        }
    }

    public void log(Object... values) {
        ScenarioEngine engine = getEngine();
        if (engine.getConfig().isPrintEnabled()) {
            engine.logger.info("{}", new LogWrapper(values));
        }
    }

    public Object lowerCase(Object o) {
        Variable var = new Variable(o);
        return var.toLowerCase().getValue();
    }

    public Object map(Object o, Object f) {
        if (!(o instanceof List)) {
            return new ArrayList<>();
        }
        List list = (List) o;
        Invokable invokable = assertIfJsFunction(f);
        long count = list.size();
        List result = new ArrayList();
        for (int i = 0; i < count; i++) {
            Object v = list.get(i);
            Object res = JsEngine.invoke(invokable, v, i);
            result.add(res);
        }
        return result;
    }

    public Object mapWithKey(Object v, String key) {
        if (!(v instanceof List)) {
            return new ArrayList<>();
        }
        List list = (List) v;
        long count = list.size();
        List result = new ArrayList();
        for (int i = 0; i < count; i++) {
            Map map = new LinkedHashMap();
            Object res = list.get(i);
            map.put(key, res);
            result.add(map);
        }
        return result;
    }

    public Object match(Object actual, Object expected) {
        Match.Result mr = getEngine().match(Match.Type.EQUALS, actual, expected);
        return mr.toMap();
    }

    public Object match(String exp) {
        MatchStep ms = new MatchStep(exp);
        Match.Result mr = getEngine().match(ms.type, ms.name, ms.path, ms.expected);
        return mr.toMap();
    }

    public Object merge(Object... vals) {
        if (vals.length == 0) {
            return null;
        }
        if (vals.length == 1) {
            return vals[0];
        }
        Map map = new HashMap((Map) vals[0]);
        for (int i = 1; i < vals.length; i++) {
            map.putAll((Map) vals[i]);
        }
        return map;
    }

    public void pause(Object value) {
        ScenarioEngine engine = getEngine();
        if (!(value instanceof Number)) {
            engine.logger.warn("pause argument is not a number:", value);
            return;
        }
        if (engine.runtime.perfMode) {
            engine.runtime.featureRuntime.perfHook.pause((Number) value);
        } else if (engine.getConfig().isPauseIfNotPerf()) {
            try {
                Thread.sleep(((Number) value).intValue());
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
        return list;
    }

    public Object read(String path) {
        Object result = getEngine().fileReader.readFile(path);
        return result;
    }

    public byte[] readAsBytes(String path) {
        return getEngine().fileReader.readFileAsBytes(path);
    }

    public String readAsString(String path) {
        return getEngine().fileReader.readFileAsString(path);
    }

    public InputStream readAsStream(String path) {
        return getEngine().fileReader.readFileAsStream(path);
    }

    public void remove(String name, String path) {
        getEngine().remove(name, path);
    }

    public String render(Object v) {
        Map<String, Object> arg;
        if (v instanceof String) {
            arg = Collections.singletonMap("read", (String) v);
        } else if (v instanceof Map) {
            arg = (Map) v;
        } else {
            getEngine().logger.warn("render - unexpected argument: {}", v);
            return null;
        }
        return getEngine().renderHtml(arg);
    }

    public Object repeat(int n, Object f) {
        Invokable invokable = assertIfJsFunction(f);
        List list = new ArrayList(n);
        for (int i = 0; i < n; i++) {
            Object v = JsEngine.invoke(invokable, i);
            list.add(v);
        }
        return list;
    }

    // set multiple variables in one shot
    public void set(Map<String, Object> map) {
        getEngine().setVariables(map);
    }

    public void set(String name, Object value) {
        getEngine().setVariable(name, new Variable(value));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void set(String name, String path, Object value) {
        getEngine().set(name, path, new Variable(value));
    }

    public Object setup() {
        return setup(null);
    }

    public Object setup(String name) {
        return setupInternal(getEngine(), name);
    }

    private static Map<String, Object> setupInternal(ScenarioEngine engine, String name) {
        Feature feature = engine.runtime.featureRuntime.featureCall.feature;
        Scenario scenario = feature.getSetup(name);
        if (scenario == null) {
            String message = "no scenario found with @setup tag";
            if (name != null) {
                message = message + " and name '" + name + "'";
            }
            engine.logger.error(message);
            throw new RuntimeException(message);
        }
        ScenarioRuntime sr = new ScenarioRuntime(engine.runtime.featureRuntime, scenario);
        sr.setSkipBackground(true);
        sr.run();
        ScenarioEngine.set(engine);
        engine.runtime.featureRuntime.setupResult = sr.result; // hack to embed setup into report
        return sr.engine.getAllVariablesAsMap();
    }

    public Object setupOnce() {
        return setupOnce(null);
    }

    public Object setupOnce(String name) {
        ScenarioEngine engine = getEngine();
        final Map<String, Map<String, Object>> CACHE = engine.runtime.featureRuntime.SETUPONCE_CACHE;
        Map<String, Object> result = CACHE.get(name);
        if (result != null) {
            return setupOnceResult(result);
        }
        long startTime = System.currentTimeMillis();
        engine.logger.trace("setupOnce waiting for lock: {}", name);
        synchronized (CACHE) {
            result = CACHE.get(name); // retry
            if (result != null) {
                long endTime = System.currentTimeMillis() - startTime;
                engine.logger.warn("this thread waited {} milliseconds for setupOnce lock: {}", endTime, name);
                return setupOnceResult(result);
            }
            result = setupInternal(engine, name);
            CACHE.put(name, result);
            return setupOnceResult(result);
        }
    }

    private static Object setupOnceResult(Map<String, Object> result) {
        Map<String, Object> clone = new HashMap(result.size());
        result.forEach((k, v) -> { // shallow clone
            Variable variable = new Variable(v);
            clone.put(k, variable.copy(false).getValue());
        });
        return clone;
    }

    public void setXml(String name, String xml) {
        getEngine().setVariable(name, XmlUtils.toXmlDoc(xml));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void setXml(String name, String path, String xml) {
        getEngine().set(name, path, new Variable(XmlUtils.toXmlDoc(xml)));
    }

    public void signal(Object v) {
        getEngine().signal(v);
    }

    public Object sizeOf(Object o) {
        Variable v = new Variable(o);
        if (v.isList()) {
            return v.<List>getValue().size();
        } else if (v.isMap()) {
            return v.<Map>getValue().size();
        } else if (v.isBytes()) {
            return v.<byte[]>getValue().length;
        } else {
            return -1;
        }
    }

    static abstract class ValueIndex<T> implements Comparable<ValueIndex<T>> {

        final T object;
        final long index;

        ValueIndex(T o, long index) {
            this.object = o;
            this.index = index;
        }

    }

    static class StringValueIndex extends ValueIndex<String> {

        public StringValueIndex(String o, long index) {
            super(o, index);
        }

        @Override
        public int compareTo(ValueIndex<String> other) {
            int result = this.object.compareTo(other.object);
            return result == 0 ? (int) (this.index - other.index) : result;
        }

    }

    static class NumberValueIndex extends ValueIndex<Number> {

        public NumberValueIndex(Number o, long index) {
            super(o, index);
        }

        @Override
        public int compareTo(ValueIndex<Number> other) {
            double result = this.object.doubleValue() - other.object.doubleValue();
            return result == 0 ? (int) (this.index - other.index) : (int) result;
        }

    }

    public Object sort(Object o) {
        return sort(o, getEngine().JS.eval("x => x"));
    }

    public Object sort(Object o, Object f) {
        if (!(o instanceof List)) {
            return new ArrayList<>();
        }
        List list = (List) o;
        Invokable invokable = assertIfJsFunction(f);
        long count = list.size();
        List<ValueIndex> pointers = new ArrayList((int) count);
        List<Object> items = new ArrayList(pointers.size());
        for (int i = 0; i < count; i++) {
            Object item = list.get(i);
            items.add(item);
            Object key = JsEngine.invoke(invokable, item, i);
            if (key instanceof Number) {
                pointers.add(new NumberValueIndex((Number) key, i));
            } else {
                pointers.add(new StringValueIndex(key.toString(), i));
            }
        }
        Collections.sort(pointers);
        List<Object> result = new ArrayList(pointers.size());
        pointers.forEach(item -> result.add(items.get((int) item.index)));
        return result;
    }

    public MockServer start(Object value) {
        if (value instanceof String) {
            return startInternal(Collections.singletonMap("mock", value));
        } else {
            return startInternal((Map) value);
        }
    }

    private MockServer startInternal(Map<String, Object> config) {
        String mock = (String) config.get("mock");
        if (mock == null) {
            throw new RuntimeException("'mock' is missing: " + config);
        }
        File feature = toJavaFile(mock);
        MockServer.Builder builder = MockServer.feature(feature);
        String pathPrefix = (String) config.get("pathPrefix");
        if (pathPrefix != null) {
            builder.pathPrefix(pathPrefix);
        }
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
        return JsonUtils.fromJson(json.toString(), className);
    }

    public String toCsv(Object o) {
        Variable v = new Variable(o);
        if (!v.isList()) {
            throw new RuntimeException("not a json array: " + v);
        }
        List<Map<String, Object>> list = v.getValue();
        return JsonUtils.toCsv(list);
    }


    public File toJavaFile(String path) {
        return getEngine().fileReader.toResource(path).getFile();
    }


    public Object toJson(Object value) {
        return toJson(value, false);
    }

    public Object toJson(Object value, boolean removeNulls) {
        String json = JsonUtils.toJson(value);
        Object result = Json.of(json).value();
        if (removeNulls) {
            JsonUtils.removeKeysWithNullValues(result);
        }
        return result;
    }

    public String toString(Object o) {
        Variable v = new Variable(o);
        return v.getAsString();
    }

    public String typeOf(Object value) {
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

    public Object valuesOf(Object o) {
        Variable v = new Variable(o);
        if (v.isList()) {
            return v.<List>getValue();
        } else if (v.isMap()) {
            return new ArrayList(v.<Map>getValue().values());
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

    public WebSocketClient webSocket(String url, Object value) {
        return webSocket(url, value, null);
    }

    public WebSocketClient webSocket(String url, Object listener, Object value) {
        Function<String, Boolean> handler;
        ScenarioEngine engine = getEngine();
        if (listener == null || !(listener instanceof Invokable)) {
            handler = m -> true;
        } else {
            handler = text -> (Boolean) JsEngine.invoke((Invokable) listener, text);
        }
        WebSocketOptions options = new WebSocketOptions(url, value == null ? null : (Map) value);
        options.setTextHandler(handler);
        return engine.webSocket(options);
    }

    public WebSocketClient webSocketBinary(String url) {
        return webSocketBinary(url, null, null);
    }

    public WebSocketClient webSocketBinary(String url, Object value) {
        return webSocketBinary(url, value, null);
    }

    public WebSocketClient webSocketBinary(String url, Object listener, Object value) {
        Function<byte[], Boolean> handler;
        ScenarioEngine engine = getEngine();
        if (listener == null || !(listener instanceof Invokable)) {
            handler = m -> true;
        } else {
            handler = bytes -> (Boolean) JsEngine.invoke((Invokable) listener, (Object) bytes);
        }
        WebSocketOptions options = new WebSocketOptions(url, value == null ? null : (Map) value);
        options.setBinaryHandler(handler);
        return engine.webSocket(options);
    }

    public File write(Object o, String path) {
        ScenarioEngine engine = getEngine();
        path = engine.runtime.featureRuntime.suite.buildDir + File.separator + path;
        File file = new File(path);
        FileUtils.writeToFile(file, JsonUtils.toBytes(o));
        engine.logger.debug("write to file: {}", file);
        return file;
    }

    public Object xmlPath(Object o, String path) {
        Variable var = new Variable(o);
        Variable res = ScenarioEngine.evalXmlPath(var, path);
        return res.getValue();
    }

    // helpers =================================================================
    //
    private static Invokable assertIfJsFunction(Object f) {
        if (!(f instanceof Invokable)) {
            throw new RuntimeException("not a js function: " + f);
        }
        return (Invokable) f;
    }

    // make sure log() toString() is lazy
    static class LogWrapper {

        final Object[] values;

        LogWrapper(Object... values) {
            // sometimes a null array gets passed in, graal weirdness
            this.values = values == null ? new Object[0] : values;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object v : values) {
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

        private static String wrap(Object... values) {
            return new LogWrapper(values).toString();
        }

        public void debug(Object... values) {
            getLogger().debug(wrap(values));
        }

        public void info(Object... values) {
            getLogger().info(wrap(values));
        }

        public void trace(Object... values) {
            getLogger().trace(wrap(values));
        }

        public void warn(Object... values) {
            getLogger().warn(wrap(values));
        }

        public void error(Object... values) {
            getLogger().error(wrap(values));
        }

    }

}
