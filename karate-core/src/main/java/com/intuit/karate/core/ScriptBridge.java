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
import com.intuit.karate.JsonUtils;
import com.intuit.karate.PerfContext;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiValuedMap;
import com.intuit.karate.netty.WebSocketClient;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
        ScriptValue sv = FileUtils.readFile(fileName, context);
        // json should behave like json within js / function
        return sv.isJsonLike() ? sv.getAfterConvertingFromJsonOrXmlIfNeeded() : sv.getValue();
    }
    
    public String readAsString(String fileName) {
        return FileUtils.readFileAsString(fileName, context);
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
    
    public Map<String, Object> match(Object actual, Object expected) {
        AssertionResult result = Script.matchNestedObject('.', "$", MatchType.EQUALS, actual, null, actual, expected, context);
        Map<String, Object> map = new HashMap(2);
        map.put("pass", result.pass);
        map.put("message", result.message);
        return map;
    }   
    
    public void forEach(Map<String, Object> map, ScriptObjectMirror som) {
        if (map == null) {
            return;
        }
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        AtomicInteger i = new AtomicInteger();
        map.forEach((k, v) -> som.call(som, k, v, i.getAndIncrement()));
    }    
    
    public void forEach(List list, ScriptObjectMirror som) {
        if (list == null) {
            return;
        }
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        for (int i = 0; i < list.size(); i++) {
            som.call(som, list.get(i), i);
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
            res.add(y);
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
        Node result = XmlUtils.getNodeByPath((Node) o, path, false);
        int childElementCount = XmlUtils.getChildElementCount(result);
        if (childElementCount == 0) {
            return StringUtils.trimToNull(result.getTextContent());
        }
        return XmlUtils.toNewDocument(result);
    }    
    
    public Object toBean(Object o, String className) {
        ScriptValue sv = new ScriptValue(o);
        DocumentContext doc = Script.toJsonDoc(sv, context);
        return JsonUtils.fromJson(doc.jsonString(), className);
    }   
    
    public Object call(String fileName) {
        return call(fileName, null);
    }

    public Object call(String fileName, Object arg) {
        ScriptValue sv = FileUtils.readFile(fileName, context);
        switch(sv.getType()) {
            case FEATURE:
                Feature feature = sv.getValue(Feature.class);
                return Script.evalFeatureCall(feature, arg, context, false).getValue();
            case JS_FUNCTION:
                ScriptObjectMirror som = sv.getValue(ScriptObjectMirror.class);
                return Script.evalFunctionCall(som, arg, context).getValue();
            default:
                context.logger.warn("not a js function or feature file: {} - {}", fileName, sv);
                return null;
        }        
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
            Object result = call(fileName, arg);
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
    
    public List<String> getTags() {
        return context.tags;
    }
    
    public Map<String, List<String>> getTagValues() {
        return context.tagValues;
    }    
    
    public Map<String, Object> getInfo() {
        DocumentContext doc = JsonUtils.toJsonDoc(context.scenarioInfo);
        return doc.read("$");        
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
        request.removeHeader(HttpUtils.HEADER_CONTENT_LENGTH);
        request.setBody(getValue(ScriptValueMap.VAR_REQUEST));
        HttpResponse response = context.getHttpClient().invoke(request, context);
        context.setPrevResponse(response);
        context.updateResponseVars();
    }    
    
    public void abort() {
        throw new KarateAbortException(null);
    }
    
    public void embed(Object o, String contentType) {
       ScriptValue sv = new ScriptValue(o);
       if (contentType == null) {
           contentType = HttpUtils.getContentType(sv);
       }
       Embed embed = new Embed();
       embed.setBytes(sv.getAsByteArray());
       embed.setMimeType(contentType);
       context.prevEmbed = embed;
    }   
    
    public void write(Object o, String path) {        
        ScriptValue sv = new ScriptValue(o);
        path = Engine.getBuildDir() + File.separator + path;
        FileUtils.writeToFile(new File(path), sv.getAsByteArray());
    }    
    
    public WebSocketClient webSocket(String url, Consumer<String> textHandler) {
        return webSocket(url, textHandler, null);
    }
    
    public WebSocketClient webSocket(String url, Consumer<String> textHandler, Consumer<byte[]> binaryHandler) {
        return context.webSocket(url, textHandler, binaryHandler);
    }
    
    public void signal(Object result) {
        context.signal(result);
    }
    
    public Object listen(long timeout, ScriptObjectMirror som) {
        if (!som.isFunction()) {
            throw new RuntimeException("not a JS function: " + som);
        }
        return context.listen(timeout, () -> Script.evalFunctionCall(som, null, context));
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
        Map<String, String> map = HttpUtils.parseUriPattern(path, uri);
        set(ScriptBindings.PATH_PARAMS, map);
        return map != null;
    }
    
    public boolean methodIs(String method) {
        String actual = getAsString(ScriptValueMap.VAR_REQUEST_METHOD);        
        return actual.equalsIgnoreCase(method);
    } 
    
    public Object paramValue(String name) {
        Map<String, List<String>> params = (Map) getValue(ScriptValueMap.VAR_REQUEST_PARAMS).getValue();
        if (params == null) {
            return null;
        }
        List<String> list = params.get(name);
        if (list == null) {
            return null;
        }        
        if (list.size() == 1) {
            return list.get(0);
        }
        return list;
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
        for (String s: list) {
            if (s != null && s.contains(test)) {
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
    
    public String getEnv() {
        return context.featureContext.env;
    }
    
    public Properties getProperties() {
        return System.getProperties();
    }
    
    public void setLocation(String expression) {
        context.driver(expression);
    }
    
    public void log(Object ... objects) {
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
        
        LogWrapper(Object ... objects) {
            this.objects = objects;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : objects) {
                sb.append(o).append(' ');                
            }
            return sb.toString();
        }
                
    }
    
}
