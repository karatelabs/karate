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
package io.karatelabs.driver;

import net.minidev.json.JSONValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a mock HTTP response for request interception.
 */
public class InterceptResponse {

    private final int status;
    private final Map<String, Object> headers;
    private final byte[] body;

    private InterceptResponse(int status, Map<String, Object> headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    /**
     * Create a response with the given status, headers, and body.
     */
    public static InterceptResponse of(int status, Map<String, Object> headers, byte[] body) {
        return new InterceptResponse(status, headers, body);
    }

    /**
     * Create a response with the given status and body.
     */
    public static InterceptResponse of(int status, byte[] body) {
        return new InterceptResponse(status, new LinkedHashMap<>(), body);
    }

    /**
     * Create a response with the given status and string body.
     */
    public static InterceptResponse of(int status, String body) {
        return new InterceptResponse(status, new LinkedHashMap<>(), body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a JSON response.
     */
    public static InterceptResponse json(int status, Object body) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        String json = JSONValue.toJSONString(body);
        return new InterceptResponse(status, headers, json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a JSON response with 200 OK status.
     */
    public static InterceptResponse json(Object body) {
        return json(200, body);
    }

    /**
     * Create an HTML response.
     */
    public static InterceptResponse html(int status, String html) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/html");
        return new InterceptResponse(status, headers, html.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create an HTML response with 200 OK status.
     */
    public static InterceptResponse html(String html) {
        return html(200, html);
    }

    /**
     * Create a text response.
     */
    public static InterceptResponse text(int status, String text) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain");
        return new InterceptResponse(status, headers, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a text response with 200 OK status.
     */
    public static InterceptResponse text(String text) {
        return text(200, text);
    }

    /**
     * Create an empty response with just a status code.
     */
    public static InterceptResponse status(int status) {
        return new InterceptResponse(status, new LinkedHashMap<>(), new byte[0]);
    }

    /**
     * Create a 200 OK response.
     */
    public static InterceptResponse ok() {
        return status(200);
    }

    /**
     * Create a 404 Not Found response.
     */
    public static InterceptResponse notFound() {
        return status(404);
    }

    /**
     * Create a 500 Internal Server Error response.
     */
    public static InterceptResponse serverError() {
        return status(500);
    }

    /**
     * Create a redirect response.
     */
    public static InterceptResponse redirect(String location) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Location", location);
        return new InterceptResponse(302, headers, new byte[0]);
    }

    /**
     * Add a header to this response.
     */
    public InterceptResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Get the HTTP status code.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the response headers.
     */
    public Map<String, Object> getHeaders() {
        return headers;
    }

    /**
     * Get the response body as bytes.
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Get the response body as a Base64-encoded string.
     */
    public String getBodyBase64() {
        if (body == null || body.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(body);
    }

    /**
     * Get the response body as a string (UTF-8).
     */
    public String getBodyString() {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

}
