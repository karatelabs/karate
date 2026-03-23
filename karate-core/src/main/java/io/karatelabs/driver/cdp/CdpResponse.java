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
 * Wrapper for Chrome DevTools Protocol responses with JSONPath extraction.
 */
public class CdpResponse {

    private final int id;
    private final Map<String, Object> result;
    private final Map<String, Object> error;
    private final Map<String, Object> raw;

    @SuppressWarnings("unchecked")
    public CdpResponse(Map<String, Object> raw) {
        this.raw = raw;
        this.id = raw.containsKey("id") ? ((Number) raw.get("id")).intValue() : -1;
        this.result = (Map<String, Object>) raw.get("result");
        this.error = (Map<String, Object>) raw.get("error");
    }

    public int getId() {
        return id;
    }

    public boolean isError() {
        return error != null;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public String getErrorMessage() {
        if (error == null) {
            return null;
        }
        Object message = error.get("message");
        return message != null ? message.toString() : null;
    }

    public Integer getErrorCode() {
        if (error == null) {
            return null;
        }
        Object code = error.get("code");
        return code != null ? ((Number) code).intValue() : null;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Get value from result using JSONPath.
     * Example: getResult("frameId") or getResult("result.value")
     */
    @SuppressWarnings("unchecked")
    public <T> T getResult(String path) {
        if (result == null) {
            return null;
        }
        return get(result, path);
    }

    /**
     * Get value from result as String.
     */
    public String getResultAsString(String path) {
        Object value = getResult(path);
        return value != null ? value.toString() : null;
    }

    /**
     * Get value from result as Integer.
     */
    public Integer getResultAsInt(String path) {
        Object value = getResult(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * Get value from result as Boolean.
     */
    public Boolean getResultAsBoolean(String path) {
        Object value = getResult(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Get value from any part of response using JSONPath.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        return get(raw, path);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(Map<String, Object> map, String path) {
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
        if (isError()) {
            return "CdpResponse[" + id + " ERROR: " + getErrorMessage() + "]";
        }
        return "CdpResponse[" + id + "]";
    }

}
