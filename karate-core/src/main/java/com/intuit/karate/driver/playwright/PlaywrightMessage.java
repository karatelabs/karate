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
package com.intuit.karate.driver.playwright;

import com.intuit.karate.Json;
import java.util.ArrayList;
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
public class PlaywrightMessage {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightMessage.class);

    protected final PlaywrightDriver driver;

    private Integer id;
    private final String guid;
    private final String method;
    private Json params;
    private Json result;
    private Json error;
    private Integer timeout;

    public void sendWithoutWaiting() {
        driver.send(this);
    }

    public PlaywrightMessage send() {
        return send(null);
    }

    public PlaywrightMessage send(Predicate<PlaywrightMessage> condition) {
        return driver.sendAndWait(this, condition);
    }

    public boolean methodIs(String method) {
        return method.equals(this.method);
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public PlaywrightMessage param(String path, Object value) {
        if (params == null) {
            params = Json.object();
        }
        params.set(path, value);
        return this;
    }

    public PlaywrightMessage params(Json json) {
        params = json;
        return this;
    }

    public PlaywrightMessage params(Map<String, Object> map) {
        params = Json.of(map);
        return this;
    }

    public String getParam(String path) {
        return getParam(path, String.class);
    }

    public <T> T getParam(String path, Class<T> clazz) {
        if (params == null) {
            return null;
        }
        return params.get(path, clazz);
    }

    public <T> boolean paramHas(String path, T expected) {
        Object actual = getParam(path, Object.class);
        if (actual == null) {
            return expected == null;
        }
        return actual.equals(expected);
    }

    public Json getResult() {
        return result;
    }

    public String getResult(String path) {
        return getResult(path, String.class);
    }

    public <T> T getResult(String path, Class<T> clazz) {
        if (result == null) {
            return null;
        }
        return result.get(path, clazz);
    }

    public <T> T getResultValue() {
        if (result == null) {
            return null;
        }
        Map<String, Object> map = result.get("value", Map.class);
        return (T) recurse(map);
    }

    private static Object recurse(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String key = raw.keySet().iterator().next();
        Object val = raw.get(key);
        switch (key) {
            case "o":
                List<Map<String, Object>> objectItems = (List) val;
                Map<String, Object> map = new HashMap(objectItems.size());
                for (Map<String, Object> entry : objectItems) {
                    String entryKey = (String) entry.get("k");
                    Map<String, Object> entryValue = (Map) entry.get("v");
                    map.put(entryKey, recurse(entryValue));
                }
                return map;
            case "a":
                List<Map<String, Object>> arrayItems = (List) val;
                List<Object> list = new ArrayList(arrayItems.size());
                for (Map<String, Object> entry : arrayItems) {
                    list.add(recurse(entry));
                }
                return list;
        default: // s: string, n: number, b: boolean
                return val;
        }
    }    

    public boolean isError() {
        return error != null;
    }

    public Json getError() {
        return error;
    }

    public PlaywrightMessage(PlaywrightDriver driver, String method, String guid) {
        this.driver = driver;
        this.method = method;
        this.guid = guid;
        this.result = null;
        id = driver.nextId();
    }

    public PlaywrightMessage(PlaywrightDriver driver, Map<String, Object> map) {
        this.driver = driver;
        id = (Integer) map.get("id");
        guid = (String) map.get("guid");
        method = (String) map.get("method");
        Map temp = (Map) map.get("params");
        if (temp != null) {
            params = Json.of(temp);
        }
        temp = (Map) map.get("result");
        if (temp != null) {
            result = Json.of(temp);
        }
        temp = (Map) map.get("error");
        if (temp != null) {
            if (temp.containsKey("error")) {
                temp = (Map) temp.get("error");
            }
            error = Json.of(temp);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap(4);
        if (id != null) {
            map.put("id", id);
        }
        map.put("guid", guid);
        map.put("method", method);
        if (params != null) {
            map.put("params", params.value());
        }
        return map;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }

    public String getMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (id == null) {
            sb.append("[guid: ").append(guid);
        } else {
            sb.append("[id: ").append(id);
            sb.append(", guid: ").append(guid);
        }
        if (method != null) {
            sb.append(", method: ").append(method);
        }
        if (params != null) {
            sb.append(", params: ").append(params);
        }
        if (result != null) {
            sb.append(", results: ").append(result);
        }
        if (error != null) {
            sb.append(", error: ").append(error);
        }
        sb.append("]");
        return sb.toString();
    }

}
