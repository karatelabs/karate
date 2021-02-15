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
package com.intuit.karate;

import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.Variable;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.Response;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Http {

    public final String urlBase;
    private final ScenarioEngine engine;
    private final HttpRequestBuilder builder;

    public static Http to(String url) {
        return new Http(url);
    }

    public void setAppender(LogAppender appender) {
        engine.logger.setAppender(appender);
    }

    private Http(String urlBase) {
        this.urlBase = urlBase;
        engine = ScenarioEngine.forTempUse();
        builder = engine.getRequestBuilder();
        builder.url(urlBase);
    }

    public Http url(String url) {
        builder.url(url);
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

    public Response method(String method, Object body) {
        if (body != null) {
            builder.body(body);
        }
        builder.method(method);
        Response response = engine.httpInvoke();
        if (response.getStatus() >= 400) {
            engine.logger.warn("http response code: {}, response: {}, request: {}",
                    response.getStatus(), response.getBodyAsString(), engine.getRequest());
        }
        return response;
    }

    public Response method(String method) {
        return method(method, null);
    }

    public Response get() {
        return method("get");
    }

    public Response postJson(String body) {
        Json json = Json.of(body);
        return post(json.value());
    }

    public Response post(Object body) {
        return method("post", body instanceof Json ? ((Json) body).value() : body);
    }

    public Response delete() {
        return method("delete");
    }

    public Http configure(String key, Object value) {
        engine.configure(key, new Variable(value));
        return this;
    }

    public Http configure(Map<String, Object> map) {
        map.forEach((k, v) -> configure(k, v));
        return this;
    }

}
