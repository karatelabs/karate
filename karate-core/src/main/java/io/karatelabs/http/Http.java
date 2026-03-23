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
package io.karatelabs.http;

import io.karatelabs.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Fluent HTTP client API for making HTTP requests from Java code.
 * <p>
 * Example usage:
 * <pre>
 * HttpResponse response = Http.to("https://api.example.com")
 *     .path("users")
 *     .header("Authorization", "Bearer token")
 *     .get();
 * </pre>
 */
public class Http {

    private static final Logger logger = LoggerFactory.getLogger(Http.class);

    public final String urlBase;
    private final HttpClient client;
    private final HttpRequestBuilder builder;

    public static Http to(String url) {
        return new Http(url);
    }

    private Http(String urlBase) {
        this.urlBase = urlBase;
        this.client = new DefaultHttpClientFactory().create();
        this.builder = new HttpRequestBuilder(client);
        builder.url(urlBase);
    }

    public Http url(String url) {
        builder.url(url);
        return this;
    }

    public Http param(String key, String... values) {
        builder.param(key, values);
        return this;
    }

    public Http path(String... paths) {
        builder.paths(paths);
        return this;
    }

    public Http header(String name, String value) {
        builder.header(name, value);
        return this;
    }

    public Http body(Object body) {
        builder.body(body);
        return this;
    }

    public Http contentType(String contentType) {
        builder.contentType(contentType);
        return this;
    }

    public Http configure(String key, Object value) {
        client.config(key, value);
        return this;
    }

    public Http configure(Map<String, Object> map) {
        map.forEach(this::configure);
        return this;
    }

    public HttpResponse method(String method, Object body) {
        if (body != null) {
            builder.body(body instanceof Json ? ((Json) body).value() : body);
        }
        builder.method(method);
        HttpResponse response = builder.invoke();
        if (response.getStatus() >= 400) {
            logger.warn("http response code: {}, response: {}, url: {}",
                    response.getStatus(), response.getBodyString(), builder.getUri());
        }
        return response;
    }

    public HttpResponse method(String method) {
        return method(method, null);
    }

    public HttpResponse methodJson(String method, String body) {
        return method(method, Json.of(body));
    }

    public HttpResponse get() {
        return method("GET");
    }

    public HttpResponse post(Object body) {
        return method("POST", body);
    }

    public HttpResponse postJson(String body) {
        return post(Json.of(body).value());
    }

    public HttpResponse put(Object body) {
        return method("PUT", body);
    }

    public HttpResponse putJson(String body) {
        return put(Json.of(body).value());
    }

    public HttpResponse patch(Object body) {
        return method("PATCH", body);
    }

    public HttpResponse patchJson(String body) {
        return patch(Json.of(body).value());
    }

    public HttpResponse delete() {
        return method("DELETE");
    }

    public HttpResponse delete(Object body) {
        return method("DELETE", body);
    }

}
