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

import java.util.Map;

/**
 * Represents an intercepted HTTP request.
 */
public class InterceptRequest {

    private final String requestId;
    private final String url;
    private final String method;
    private final Map<String, Object> headers;
    private final String postData;
    private final String resourceType;

    public InterceptRequest(String requestId, String url, String method,
                            Map<String, Object> headers, String postData, String resourceType) {
        this.requestId = requestId;
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.postData = postData;
        this.resourceType = resourceType;
    }

    /**
     * Get the CDP request ID for this interception.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Get the request URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Get the HTTP method (GET, POST, etc.).
     */
    public String getMethod() {
        return method;
    }

    /**
     * Get the request headers.
     */
    public Map<String, Object> getHeaders() {
        return headers;
    }

    /**
     * Get a specific header value.
     */
    public String getHeader(String name) {
        if (headers == null) {
            return null;
        }
        Object value = headers.get(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Get the POST data (request body).
     */
    public String getPostData() {
        return postData;
    }

    /**
     * Get the resource type (Document, Script, Stylesheet, Image, etc.).
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Check if the URL contains the given substring.
     */
    public boolean urlContains(String substring) {
        return url != null && url.contains(substring);
    }

    /**
     * Check if the URL matches the given pattern (supports * wildcards).
     */
    public boolean urlMatches(String pattern) {
        if (url == null || pattern == null) {
            return false;
        }
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return url.matches(regex);
    }

    @Override
    public String toString() {
        return "InterceptRequest{" +
                "method='" + method + '\'' +
                ", url='" + url + '\'' +
                ", resourceType='" + resourceType + '\'' +
                '}';
    }

}
