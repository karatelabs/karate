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

import com.intuit.karate.ScriptValue;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class DevToolsMessage {

    private final DevToolsDriver driver;

    private Integer id;
    private final String method;
    private Map<String, Object> params;
    private ScriptValue result;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public boolean isMethod(String method) {
        return method.equals(this.method);
    }

    public String getFrameUrl() {
        if (params == null) {
            return null;
        }
        Map<String, Object> frame = (Map) params.get("frame");
        if (frame == null) {
            return null;
        }
        return (String) frame.get("url");
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public ScriptValue getResult() {
        return result;
    }

    public void setResult(ScriptValue result) {
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
        if (result == null || !result.isMapLike()) {
            return false;
        }
        String error = (String) result.getAsMap().get("subtype");
        return "error".equals(error);
    }

    public ScriptValue getResult(String key) {
        if (result == null || !result.isMapLike()) {
            return null;
        }
        return new ScriptValue(result.getAsMap().get(key));
    }

    public ScriptValue getParam(String key) {
        if (params == null) {
            return ScriptValue.NULL;
        }
        return new ScriptValue(params.get(key));
    }

    public DevToolsMessage(DevToolsDriver driver, String method) {
        this.driver = driver;
        id = driver.getNextId();
        this.method = method;
    }

    public DevToolsMessage(DevToolsDriver driver, Map<String, Object> map) {
        this.driver = driver;
        id = (Integer) map.get("id");
        method = (String) map.get("method");
        params = (Map) map.get("params");
        Map temp = (Map) map.get("result");
        if (temp != null && temp.containsKey("result")) {
            Object inner = temp.get("result");
            if (inner instanceof List) {
                result = new ScriptValue(toMap((List) inner));
            } else { // TODO improve this logic
                Map innerMap = (Map) inner;
                String type = (String) innerMap.get("type");
                String subtype = (String) innerMap.get("subtype");
                if ("object".equals(type) || "error".equals(subtype)) {
                    result = new ScriptValue(innerMap);
                } else {
                    result = new ScriptValue(innerMap.get("value"));
                }
            }
        } else {
            result = new ScriptValue(temp);
        }
    }

    public DevToolsMessage param(String key, Object value) {
        if (params == null) {
            params = new LinkedHashMap();
        }
        params.put(key, value);
        return this;
    }

    public DevToolsMessage params(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(4);
        map.put("id", id);
        map.put("method", method);
        if (params != null) {
            map.put("params", params);
        }
        if (result != null) {
            map.put("result", result);
        }
        return map;
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
        if (method != null) {
            sb.append(", method: ").append(method);
        }
        if (params != null) {
            sb.append(", params: ").append(params);
        }
        if (result != null) {
            sb.append(", result: ").append(result);
        }
        sb.append("]");
        return sb.toString();
    }

}
