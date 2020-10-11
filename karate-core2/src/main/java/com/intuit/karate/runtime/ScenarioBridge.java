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
import com.intuit.karate.data.JsonUtils;
import com.intuit.karate.graal.JsList;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchStep;
import com.intuit.karate.match.MatchType;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public class ScenarioBridge implements PerfContext {

    public ScenarioRuntime getRuntime() {
        return ScenarioRuntime.LOCAL.get();
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

    public void forEach(Value o, Value f) {
        if (!f.canExecute()) {
            throw new RuntimeException("not a js function: " + f);
        }
        if (o.hasArrayElements()) {
            long count = o.getArraySize();
            for (int i = 0; i < count; i++) {
                Value v = o.getArrayElement(i);
                f.execute(v, i);
            }
        } else if (o.hasMembers()) { //map
            int i = 0;
            for (String k : o.getMemberKeys()) {
                Value v = o.getMember(k);
                f.execute(k, v, i++);
            }
        } else {
            throw new RuntimeException("not an array or object: " + o);
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

    public Object keysOf(Value o) {
        return new JsList(o.getMemberKeys());
    }

    public Object map(Value o, Value f) {
        if (!o.hasArrayElements()) {
            return JsList.EMPTY;
        }
        if (!f.canExecute()) {
            throw new RuntimeException("not a js function: " + f);
        }
        long count = o.getArraySize();
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            Value v = o.getArrayElement(i);
            JsValue jv = new JsValue(f.execute(v, i));
            list.add(jv.getValue());
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

    public String pretty(Object o) {
        Variable v = new Variable(o);
        return v.getAsPrettyString();
    }

    public String prettyXml(Object o) {
        Variable v = new Variable(o);
        return v.getAsPrettyXmlString();
    }

    public Object read(String name) {
        return getRuntime().fileReader.readFile(name);
    }

    public String readAsString(String fileName) {
        return getRuntime().fileReader.readFileAsString(fileName);
    }

    public void remove(String name, String path) {
        getRuntime().engine.remove(name, path);
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

    public String toString(Object o) {
        Variable v = new Variable(o);
        return v.getAsString();
    }

    public Object valuesOf(Value v) {
        if (v.hasArrayElements()) {
            return v;
        } else if (v.hasMembers()) {
            List list = new ArrayList();
            for (String k : v.getMemberKeys()) {
                JsValue jv = new JsValue(v.getMember(k));
                list.add(jv.getValue());
            }
            return new JsList(list);
        } else {
            return null;
        }
    }

}
