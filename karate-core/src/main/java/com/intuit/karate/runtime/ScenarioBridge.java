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
import com.intuit.karate.Resource;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.data.Json;
import com.intuit.karate.data.JsonUtils;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsList;
import com.intuit.karate.graal.JsMap;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchStep;
import com.intuit.karate.match.MatchType;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketOptions;
import com.intuit.karate.server.HttpClient;
import com.intuit.karate.server.HttpRequest;
import com.intuit.karate.server.HttpRequestBuilder;
import com.intuit.karate.server.ResourceType;
import com.intuit.karate.shell.Command;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public class ScenarioBridge implements PerfContext {
    
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
    
    public Object appendTo(String varName, Value... vals) {
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
        return JsValue.fromJava(result.getValue());
    }
    
    public Object callSingle(String fileName) {
        return callSingle(fileName, null);
    }
    
    public Object callSingle(String fileName, Object arg) {
        ScenarioEngine engine = getEngine();
        final Map<String, Object> CACHE = engine.runtime.featureRuntime.suite.SUITE_CACHE;
        if (CACHE.containsKey(fileName)) {
            engine.logger.trace("callSingle cache hit: {}", fileName);
            return CACHE.get(fileName);
        }
        long startTime = System.currentTimeMillis();
        engine.logger.trace("callSingle waiting for lock: {}", fileName);
        synchronized (CACHE) { // lock
            if (CACHE.containsKey(fileName)) { // retry
                long endTime = System.currentTimeMillis() - startTime;
                engine.logger.warn("this thread waited {} milliseconds for callSingle lock: {}", endTime, fileName);
                return CACHE.get(fileName);
            }
            // this thread is the 'winner'
            engine.logger.info(">> lock acquired, begin callSingle: {}", fileName);
            int minutes = engine.getConfig().getCallSingleCacheMinutes();
            Object result = null;
            File cacheFile = null;
            if (minutes > 0) {
                String qualifiedFileName = FileUtils.toPackageQualifiedName(fileName);
                String cacheFileName = engine.getConfig().getCallSingleCacheDir() + File.separator + qualifiedFileName + ".txt";
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
                Variable argVar = arg == null ? null : new Variable(arg);
                Variable resultVar = engine.call(called, argVar, false);
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
            return result;
        }
    }
    
    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        getEngine().capturePerfEvent(event);
    }
    
    public void configure(String key, Value value) {
        getEngine().configure(key, new Variable(value));
    }
    
    public void embed(Object o, String contentType) {
        if (contentType == null) {
            contentType = ResourceType.fromObject(o).contentType;
        }
        getEngine().runtime.embed(JsValue.toBytes(o), contentType);
    }
    
    public Object eval(String exp) {
        Variable result = getEngine().evalJs(exp);
        return JsValue.fromJava(result.getValue());
    }
    
    public String exec(List<String> args) {
        return execInternal(Collections.singletonMap("args", args));
    }
    
    public String exec(String line) {
        return execInternal(Collections.singletonMap("line", line));
    }
    
    public String exec(Value value) {
        return execInternal(new JsValue(value).getValue());
    }
    
    private String execInternal(Map<String, Object> options) {
        Command command = toCommand(false, options);
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
            Value res = f.execute(v, i);
            // TODO breaking we used to support truthy values
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
    
    public Command fork(List<String> args) {
        return toCommand(true, Collections.singletonMap("args", args));
    }
    
    public Command fork(String line) {
        return toCommand(true, Collections.singletonMap("line", line));
    }
    
    public Command fork(Value value) {
        return toCommand(true, new JsValue(value).getAsMap());
    }
    
    private Command toCommand(boolean useLineFeed, Map<String, Object> options) {
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
            args = useShell ? Command.prefixShellArgs(line) : Command.tokenize(line);
        } else {
            String joined = StringUtils.join(list, ' ');
            args = useShell ? Command.prefixShellArgs(joined) : list.toArray(new String[list.size()]);
        }
        String workingDir = (String) options.get("workingDir");
        File workingFile = workingDir == null ? null : new File(workingDir);
        ScenarioEngine engine = getEngine();
        Command command = new Command(useLineFeed, engine.logger, null, null, workingFile, args);
        Map env = (Map) options.get("env");
        if (env != null) {
            command.setEnvironment(env);
        }
        Boolean redirectErrorStream = (Boolean) options.get("redirectErrorStream");
        if (redirectErrorStream != null) {
            command.setRedirectErrorStream(redirectErrorStream);
        }
        Value funOut = (Value) options.get("listener");
        if (funOut != null) {
            ScenarioListener sl = new ScenarioListener(engine, funOut);
            command.setListener(s -> sl.consume(s));
        }
        Value funErr = (Value) options.get("errorListener");
        if (funErr != null) {
            ScenarioListener sl = new ScenarioListener(engine, funErr);            
            command.setErrorListener(s -> sl.consume(s));
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
        return ScenarioEngine.get();
    }
    
    public String getEnv() {
        return getEngine().runtime.featureRuntime.suite.getEnv();
    }
    
    public Object getInfo() {
        return new JsMap(getEngine().runtime.getScenarioInfo());
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
        return getEngine().getPrevRequest();
    }
    
    public Object getProperties() {
        return new JsMap(System.getProperties());
    }
    
    public Scenario getScenario() {
        return getEngine().runtime.scenario;
    }
    
    public Object getTags() {
        return JsValue.fromJava(getEngine().runtime.tags.getTags());
    }
    
    public Object getTagValues() {
        return JsValue.fromJava(getEngine().runtime.tags.getTagValues());
    }

    //==========================================================================
    //
    public HttpRequestBuilder http(String url) { // TODO breaking change
        ScenarioEngine engine = getEngine();
        HttpClient client = getEngine().getConfig().getClientFactory().apply(engine);
        return new HttpRequestBuilder(client).url(url);
    }
    
    public Object listen(long timeout, Value f) {
        assertIfJsFunction(f);
        Object result = getEngine().listen(timeout, () -> f.execute());
        return JsValue.fromJava(result);
    }
    
    public Object listen(long timeout) {
        return JsValue.fromJava(getEngine().listen(timeout, null));
    }
    
    public void log(Value... values) {
        ScenarioEngine engine = getEngine();
        if (engine.getConfig().isPrintEnabled()) {
            engine.logger.info("{}", new LogWrapper(values));
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
        MatchResult mr = getEngine().match(MatchType.EQUALS, actual, expected);
        return JsValue.fromJava(mr.toMap());
    }
    
    public Object match(String exp) {
        MatchStep ms = new MatchStep(exp);
        MatchResult mr = getEngine().match(ms.type, ms.name, ms.path, ms.expected);
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
        getEngine().proceed(requestUrlBase);
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
            Value v = f.execute(i);
            list.add(new JsValue(v).getValue());
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
    
    public void signal(Object result) {
        getEngine().signal(result);
    }
    
    public void stop(int port) {
        Command.waitForSocket(port);
    }
    
    public String toAbsolutePath(String relativePath) {
        Resource resource = getEngine().fileReader.toResource(relativePath);
        return resource.getPath().normalize().toAbsolutePath().toString();
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
    
    public String typeOf(Value value) {
        Variable v = new Variable(value);
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
    
    public boolean waitForHttp(String url) {
        return Command.waitForHttp(url);
    }
    
    public boolean waitForPort(String host, int port) {
        return Command.waitForPort(host, port);
    }
    
    public WebSocketClient webSocket(String url) {
        return webSocket(url, null, null);
    }
    
    public WebSocketClient webSocket(String url, Value value) {
        return webSocket(url, value, null);
    }
    
    public WebSocketClient webSocket(String url, Value value, Map<String, Object> map) {
        Function<String, Boolean> handler;
        if (value == null) {
            handler = m -> true;
        } else {
            ScenarioListener sl = new ScenarioListener(getEngine(), value);
            handler = m -> sl.apply(m);
        }
        WebSocketOptions options = new WebSocketOptions(url, map);
        options.setTextHandler(handler);
        return getEngine().webSocket(options);
    }
    
    public WebSocketClient webSocketBinary(String url) {
        return webSocketBinary(url, null, null);
    }
    
    public WebSocketClient webSocketBinary(String url, Value value) {
        return webSocketBinary(url, value, null);
    }
    
    public WebSocketClient webSocketBinary(String url, Value value, Map<String, Object> map) {
        Function<byte[], Boolean> handler;
        if (value == null) {
            handler = m -> true;
        } else {
            ScenarioListener sl = new ScenarioListener(getEngine(), value);
            handler = m -> sl.apply(m);
        }
        WebSocketOptions options = new WebSocketOptions(url, map);
        options.setBinaryHandler(handler);
        return getEngine().webSocket(options);
    }
    
    public File write(Object o, String path) {
        path = getEngine().runtime.featureRuntime.suite.buildDir + File.separator + path;
        File file = new File(path);
        FileUtils.writeToFile(file, JsValue.toBytes(o));
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
