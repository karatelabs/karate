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
package com.intuit.karate.runtime;

import com.intuit.karate.FileUtils;
import com.intuit.karate.PerfContext;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.data.Json;
import com.intuit.karate.data.JsonUtils;
import com.intuit.karate.graal.JsList;
import com.intuit.karate.graal.JsMap;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchStep;
import com.intuit.karate.match.MatchType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public class ScenarioBridge implements PerfContext {

    public Object append(Value... vals) {
        if (vals.length == 0) {
            return null;
        }
        if (vals.length == 1) {
            return vals[0];
        }
        List list = new ArrayList(vals[0].as(List.class));
        for (int i = 1; i < vals.length; i++) {
            Value v = vals[i];
            if (v.hasArrayElements()) {
                list.addAll(v.as(List.class));
            } else {
                list.add(v.as(Object.class));
            }
        }
        return new JsList(list);
    }

    // TODO breaking appendTo(ref) will not work, use append()
    public Object appendTo(String varName, Value... vals) {
        ScenarioRuntime runtime = getRuntime();
        Variable var = getRuntime().engine.vars.get(varName);
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
        runtime.engine.setVariable(varName, list);
        return new JsList(list);
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
        ScenarioRuntime runtime = getRuntime();
        Variable called = new Variable(runtime.engine.fileReader.readFile(fileName));
        Variable result = runtime.engine.call(called, arg == null ? null : new Variable(arg), sharedScope);
        return JsValue.fromJava(result.getValue());
    }

    public Object callSingle(String fileName) {
        return callSingle(fileName, null);
    }

    public Object callSingle(String fileName, Object arg) {
        ScenarioRuntime runtime = getRuntime();
        final Map<String, Object> CACHE = runtime.featureRuntime.suite.SUITE_CACHE;
        if (CACHE.containsKey(fileName)) {
            runtime.logger.trace("callSingle cache hit: {}", fileName);
            return CACHE.get(fileName);
        }
        long startTime = System.currentTimeMillis();
        runtime.logger.trace("callSingle waiting for lock: {}", fileName);
        synchronized (CACHE) { // lock
            if (CACHE.containsKey(fileName)) { // retry
                long endTime = System.currentTimeMillis() - startTime;
                runtime.logger.warn("this thread waited {} milliseconds for callSingle lock: {}", endTime, fileName);
                return CACHE.get(fileName);
            }
            // this thread is the 'winner'
            runtime.logger.info(">> lock acquired, begin callSingle: {}", fileName);
            Config config = runtime.getConfig();
            int minutes = config.getCallSingleCacheMinutes();
            Object result = null;
            File cacheFile = null;
            if (minutes > 0) {
                String qualifiedFileName = FileUtils.toPackageQualifiedName(fileName);
                String cacheFileName = config.getCallSingleCacheDir() + File.separator + qualifiedFileName + ".txt";
                cacheFile = new File(cacheFileName);
                long since = System.currentTimeMillis() - minutes * 60 * 1000;
                if (cacheFile.exists()) {
                    long lastModified = cacheFile.lastModified();
                    if (lastModified > since) {
                        String json = FileUtils.toString(cacheFile);
                        result = JsonUtils.fromJson(json);
                        runtime.logger.info("callSingleCache hit: {}", cacheFile);
                    } else {
                        runtime.logger.info("callSingleCache stale, last modified {} - is before {} (minutes: {})",
                                lastModified, since, minutes);
                    }
                } else {
                    runtime.logger.info("callSingleCache file does not exist, will create: {}", cacheFile);
                }
            }
            if (result == null) {
                Variable called = new Variable(read(fileName));
                Variable argVar = arg == null ? null : new Variable(arg);
                Variable resultVar = runtime.engine.call(called, argVar, false);
                if (minutes > 0) { // cacheFile will be not null
                    if (resultVar.isMapOrList()) {
                        String json = resultVar.getAsString();
                        FileUtils.writeToFile(cacheFile, json);
                        runtime.logger.info("callSingleCache write: {}", cacheFile);
                    } else {
                        runtime.logger.warn("callSingleCache write failed, not json-like: {}", resultVar);
                    }
                }
                result = resultVar.getValue();
            }
            CACHE.put(fileName, result);
            runtime.logger.info("<< lock released, cached callSingle: {}", fileName);
            return result;
        }
    }

    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        getRuntime().capturePerfEvent(event);
    }

    public void configure(String key, Object o) {
        getRuntime().configure(key, new Variable(o));
    }

    public Object eval(String exp) {
        Variable result = getRuntime().engine.eval(exp);
        return JsValue.fromJava(result.getValue());
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
            Value res = f.execute(v, i);
            // TODO breaking we used to support truthy values
            if (res.isBoolean() && res.asBoolean()) {
                list.add(v.as(Object.class));
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
            map.put(key, v.as(Object.class));
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

    // TODO breaking returns actual object not wrapper
    // and fromObject() has been removed
    // use new typeOf() method to find type
    public Object fromString(String exp) {
        ScenarioRuntime runtime = getRuntime();
        try {
            Variable result = runtime.engine.evalKarateExpression(exp);
            return JsValue.fromJava(result.getValue());
        } catch (Exception e) {
            runtime.logger.warn("auto evaluation failed: {}", e.getMessage());
            return new Variable(exp);
        }
    }

    public Object get(String exp) {
        ScenarioRuntime runtime = getRuntime();
        Variable v;
        try {
            v = runtime.engine.evalKarateExpression(exp); // even json path expressions will work
        } catch (Exception e) {
            runtime.logger.trace("karate.get failed for expression: '{}': {}", exp, e.getMessage());
            return null;
        }
        if (v != null) {
            return v.getValueAndConvertIfXmlToMap();
        } else {
            return null;
        }
    }

    public Object get(String exp, Object defaultValue) {
        Object result = get(exp);
        return result == null ? defaultValue : result;
    }

    // getters =================================================================
    // TODO make these functions
    //
    public String getEnv() {
        return getRuntime().featureRuntime.suite.env;
    }

    public Object getInfo() {
        return new JsMap(getRuntime().getScenarioInfo());
    }

    public Object getOs() {
        String name = FileUtils.getOsName();
        String type = FileUtils.getOsType(name).toString().toLowerCase();
        Map<String, Object> map = new HashMap(2);
        map.put("name", name);
        map.put("type", type);
        return new JsMap(map);
    }

    public HttpRequest getPrevRequest() {
        return getRuntime().getPrevRequest();
    }

    public Object getProperties() {
        return new JsMap(System.getProperties());
    }

    public ScenarioRuntime getRuntime() {
        return ScenarioRuntime.LOCAL.get();
    }

    //==========================================================================
    //
    public void log(Value... values) {
        ScenarioRuntime runtime = getRuntime();
        if (runtime.getConfig().isPrintEnabled()) {
            runtime.logger.info("{}", new LogWrapper(values));
        }
    }

    public Object jsonPath(Object o, String exp) {
        Json json = new Json(o);
        return JsValue.fromJava(json.get(exp));
    }

    public Object keysOf(Value o) {
        return new JsList(o.getMemberKeys());
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
            Value res = f.execute(v, i);
            list.add(res.as(Object.class));
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

    public Map<String, Object> match(Object actual, Object expected) {
        MatchResult mr = getRuntime().engine.match(MatchType.EQUALS, actual, expected);
        return mr.toMap();
    }

    public Map<String, Object> match(String exp) {
        MatchStep ms = new MatchStep(exp);
        MatchResult mr = getRuntime().engine.match(ms.type, ms.name, ms.path, ms.expected);
        return mr.toMap();
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

    public String pretty(Object o) {
        Variable v = new Variable(o);
        return v.getAsPrettyString();
    }

    public String prettyXml(Object o) {
        Variable v = new Variable(o);
        return v.getAsPrettyXmlString();
    }

    public Object read(String name) {
        return getRuntime().engine.fileReader.readFile(name);
    }

    public String readAsString(String fileName) {
        return getRuntime().engine.fileReader.readFileAsString(fileName);
    }

    public void remove(String name, String path) {
        getRuntime().engine.remove(name, path);
    }

    public Object repeat(int n, Value f) {
        assertIfJsFunction(f);
        List list = new ArrayList(n);
        for (int i = 0; i < n; i++) {
            Value v = f.execute(i);
            list.add(v.as(Object.class));
        }
        return new JsList(list);
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

    // set multiple variables in one shot
    public void set(Map<String, Object> map) {
        getRuntime().engine.setVariables(map);
    }

    public void set(String name, Object o) {
        getRuntime().engine.setVariable(name, o);
    }

    // this makes sense mainly for xpath manipulation from within js
    public void set(String name, String path, Object value) {
        getRuntime().engine.set(name, path, new Variable(value));
    }

    public void setXml(String name, String xml) {
        getRuntime().engine.setVariable(name, XmlUtils.toXmlDoc(xml));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void setXml(String name, String path, String xml) {
        getRuntime().engine.set(name, path, new Variable(XmlUtils.toXmlDoc(xml)));
    }

    public Object toBean(Object o, String className) {
        Json json = new Json(o);
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

    public Object toJson(Object o) {
        return toJson(o, false);
    }

    public Object toJson(Object o, boolean removeNulls) {
        Object result = new Json(o).asMapOrList();
        if (removeNulls) {
            JsonUtils.removeKeysWithNullValues(result);
        }
        return result;
    }

    // TODO deprecate
    public Object toList(Object o) {
        return o;
    }

    // TODO deprecate
    public Object toMap(Object o) {
        return o;
    }

    public String toString(Object o) {
        Variable v = new Variable(o);
        return v.getAsString();
    }

    public String typeOf(Object o) {
        Variable v = new Variable(o);
        return v.getTypeString();
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
            this.values = values;
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

}
