/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.driver.cdp;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import java.util.Map;

/**
 * Wrapper for Chrome DevTools Protocol events with JSONPath extraction.
 */
public class CdpEvent {

    private final String method;
    private final Map<String, Object> params;
    private final String sessionId;
    private final Map<String, Object> raw;

    @SuppressWarnings("unchecked")
    public CdpEvent(Map<String, Object> raw) {
        this.raw = raw;
        this.method = (String) raw.get("method");
        this.params = (Map<String, Object>) raw.get("params");
        this.sessionId = (String) raw.get("sessionId");
    }

    public String getMethod() {
        return method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Get value from params using dot notation or JSONPath.
     * Example: get("frameId") or get("frame.url")
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        if (params == null) {
            return null;
        }
        return getFromMap(params, path);
    }

    /**
     * Get value as String.
     */
    public String getAsString(String path) {
        Object value = get(path);
        return value != null ? value.toString() : null;
    }

    /**
     * Get value as Integer.
     */
    public Integer getAsInt(String path) {
        Object value = get(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * Get value as Boolean.
     */
    public Boolean getAsBoolean(String path) {
        Object value = get(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromMap(Map<String, Object> map, String path) {
        if (map == null) {
            return null;
        }
        // Handle simple dot notation without full JSONPath
        if (!path.contains("[") && !path.startsWith("$")) {
            return getByDotPath(map, path);
        }
        // Use JSONPath for complex expressions
        String jsonPath = path.startsWith("$") ? path : "$." + path;
        try {
            return JsonPath.read(map, jsonPath);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getByDotPath(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return (T) current;
    }

    @Override
    public String toString() {
        return "CdpEvent[" + method + "]";
    }

}
