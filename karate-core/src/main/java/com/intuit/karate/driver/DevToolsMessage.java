/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.driver;

import com.intuit.karate.Json;
import com.intuit.karate.core.Variable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DevToolsMessage {

    private static final Logger logger = LoggerFactory.getLogger(DevToolsMessage.class);

    protected final DevToolsDriver driver;

    private final String sessionId;
    private final String method;

    private Integer id;
    private Json params;
    private Map<String, Object> error;
    private Variable result;
    private Integer timeout;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getMethod() {
        return method;
    }

    public boolean methodIs(String method) {
        return method.equals(this.method);
    }

    public <T> T getParam(String path) {
        if (params == null) {
            return null;
        }
        try {
            return params.get(path);
        } catch (Exception e) {
            if (logger.isTraceEnabled()) {
                logger.trace("get param - json path failed: {} - {}", path, params);
            }
            return null;
        }
    }

    public Variable getResult() {
        return result;
    }

    public <T> T getResult(String path, Class<T> clazz) {
        if (result == null || result.isNull()) {
            return null;
        }
        Json json = Json.of(result.getValue());
        return json.get(path, clazz);
    }

    public void setResult(Variable result) {
        this.result = result;
    }

    private static Map<String, Object> toMap(List<Map<String, Object>> list) {
        Map<String, Object> res = new HashMap();
        for (Map<String, Object> map : list) {
            String key = (String) map.get("name");
            Map<String, Object> valMap = (Map) map.get("value");
            res.put(key, valMap == null ? null : valMap.get("value"));
        }
        return res;
    }

    public boolean isResultError() {
        if (error != null) {
            return true;
        }
        if (result == null || !result.isMap()) {
            return false;
        }
        String resultError = (String) result.<Map>getValue().get("subtype");
        return "error".equals(resultError);
    }

    public Variable getResult(String key) {
        if (result == null || !result.isMap()) {
            return null;
        }
        return new Variable(result.<Map>getValue().get(key));
    }

    public DevToolsMessage(DevToolsDriver driver, String method) {
        this.driver = driver;
        id = driver.nextId();
        this.method = method;
        sessionId = driver.sessionId;
    }

    public DevToolsMessage(DevToolsDriver driver, Map<String, Object> map) {
        this.driver = driver;
        sessionId = (String) map.get("sessionId");;
        id = (Integer) map.get("id");
        method = (String) map.get("method");
        Map temp = (Map) map.get("params");
        if (temp != null) {
            params = Json.of(temp);
        }
        temp = (Map) map.get("result");
        if (temp != null) {
            if (temp.containsKey("result")) {
                Object inner = temp.get("result");
                if (inner instanceof List) {
                    result = new Variable(toMap((List) inner));
                } else {
                    Map innerMap = (Map) inner;
                    String subtype = (String) innerMap.get("subtype");
                    if ("error".equals(subtype) || innerMap.containsKey("objectId")) {
                        result = new Variable(innerMap);
                    } else { // Runtime.evaluate "returnByValue" is true
                        result = new Variable(innerMap.get("value"));
                    }
                }
            } else {
                result = new Variable(temp);
            }
        }
        error = (Map) map.get("error");
    }

    public DevToolsMessage param(String path, Object value) {
        if (params == null) {
            params = Json.object();
        }
        params.set(path, value);
        return this;
    }

    public DevToolsMessage params(Map<String, Object> map) {
        this.params = Json.of(map);
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap(4);
        map.put("id", id);
        if (sessionId != null) {
            map.put("sessionId", sessionId);
        }
        map.put("method", method);
        if (params != null) {
            map.put("params", params.value());
        }
        if (result != null) {
            map.put("result", result.getValue());
        }
        return map;
    }

    public void sendWithoutWaiting() {
        driver.send(this);
    }

    public DevToolsMessage send() {
        return send(null);
    }

    public DevToolsMessage send(Predicate<DevToolsMessage> condition) {
        return driver.sendAndWait(this, condition);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[id: ").append(id);
        if (sessionId != null) {
            sb.append(", sessionId: ").append(sessionId);
        }
        if (method != null) {
            sb.append(", method: ").append(method);
        }
        if (params != null) {
            sb.append(", params: ").append(params);
        }
        if (result != null) {
            sb.append(", result: ").append(result);
        }
        if (error != null) {
            sb.append(", error: ").append(error);
        }
        sb.append("]");
        return sb.toString();
    }

}
