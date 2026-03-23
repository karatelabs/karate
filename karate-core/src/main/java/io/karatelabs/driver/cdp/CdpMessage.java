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

import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for Chrome DevTools Protocol messages.
 */
public class CdpMessage {

    private final CdpClient client;
    private final int id;
    private final String method;
    private Map<String, Object> params;
    private Duration timeout;
    private String sessionId;

    public CdpMessage(CdpClient client, int id, String method) {
        this.client = client;
        this.id = id;
        this.method = method;
    }

    public CdpMessage param(String key, Object value) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        params.put(key, value);
        return this;
    }

    public CdpMessage params(Map<String, Object> params) {
        if (params != null) {
            if (this.params == null) {
                this.params = new LinkedHashMap<>(params);
            } else {
                this.params.putAll(params);
            }
        }
        return this;
    }

    public CdpMessage timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public CdpMessage timeout(long millis) {
        this.timeout = Duration.ofMillis(millis);
        return this;
    }

    public CdpMessage sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Blocking send with response.
     */
    public CdpResponse send() {
        return client.send(this);
    }

    /**
     * Async send.
     */
    public CompletableFuture<CdpResponse> sendAsync() {
        return client.sendAsync(this);
    }

    /**
     * Fire and forget - no response expected.
     */
    public void sendWithoutWaiting() {
        client.sendWithoutWaiting(this);
    }

    // Getters

    public int getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * Convert to CDP JSON map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("method", method);
        if (params != null && !params.isEmpty()) {
            map.put("params", params);
        }
        if (sessionId != null) {
            map.put("sessionId", sessionId);
        }
        return map;
    }

    // Use FLAG_PROTECT_4WEB to avoid escaping forward slashes
    private static final JSONStyle JSON_STYLE = new JSONStyle(JSONStyle.FLAG_PROTECT_4WEB);

    /**
     * Convert to JSON string.
     */
    public String toJson() {
        return JSONValue.toJSONString(toMap(), JSON_STYLE);
    }

    @Override
    public String toString() {
        return "CdpMessage[" + id + ": " + method + "]";
    }

}
