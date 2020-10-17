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
package com.intuit.karate.server;

import com.intuit.karate.FileUtils;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class RequestBuilder {

    private String method;
    private String path;
    private byte[] body;
    private Set<Cookie> cookies;
    private Map<String, List<String>> headers;

    public Request build() {
        Request req = new Request();
        req.setMethod(method);
        req.setPath(path);
        if (cookies != null) {
            cookies.forEach(c -> header(HttpConstants.HDR_COOKIE, ServerCookieEncoder.STRICT.encode(c)));
        }
        req.setHeaders(headers);
        req.setBody(body);
        return req;
    }

    public Request get() {
        return method("GET").build();
    }

    public Request post() {
        return method("POST").build();
    }

    public Request put() {
        return method("PUT").build();
    }

    public Request delete() {
        return method("DELETE").build();
    }

    public RequestBuilder method(String method) {
        this.method = method.toUpperCase();
        return this;
    }

    public RequestBuilder path(String path) {
        this.path = path;
        return this;
    }

    public RequestBuilder body(String body) {
        this.body = FileUtils.toBytes(body);
        return this;
    }

    public RequestBuilder header(String name, List<String> values) {
        if (headers == null) {
            headers = new LinkedHashMap();
        }
        headers.put(name, values);
        return this;
    }

    public RequestBuilder header(String name, String value) {
        return header(name, Collections.singletonList(value));
    }

    public RequestBuilder contentType(String contentType) {
        if (contentType != null) {
            header(HttpConstants.HDR_CONTENT_TYPE, contentType);
        }
        return this;
    }

    public RequestBuilder cookie(String name, String value) {
        DefaultCookie cookie = new DefaultCookie(name, value);
        if (cookies == null) {
            cookies = new HashSet();
        }
        cookies.add(cookie);
        return this;
    }

}
