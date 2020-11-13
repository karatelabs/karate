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

import com.intuit.karate.runtime.ScenarioEngine;
import com.intuit.karate.runtime.Variable;
import com.intuit.karate.server.ArmeriaHttpClient;
import com.intuit.karate.server.HttpClient;
import com.intuit.karate.server.HttpRequest;
import com.intuit.karate.server.HttpRequestBuilder;
import com.intuit.karate.server.Response;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Http {

    public final String urlBase;
    private final HttpRequestBuilder builder;
    private final Logger logger;

    private HttpRequest request;
    private Response response;

    public Http(HttpClient client, String urlBase) {
        this.builder = new HttpRequestBuilder(client);
        this.urlBase = urlBase;
        logger = client.getLogger();
    }

    public Http url(String url) {
        if (url.startsWith("/") && urlBase != null) {
            url = urlBase + url;
        }
        builder.url(url);
        return this;
    }

    public Http path(String... paths) {
        for (String p : paths) {
            builder.path(p);
        }
        return this;
    }

    public Http header(String name, String value) {
        builder.header(name, value);
        return this;
    }

    private Response handleError() {
        if (response.getStatus() >= 400) {
            logger.warn("http response code: {}, response: {}, request: {}",
                    response.getStatus(), response.getBodyAsString(), request);
        }
        return response;
    }

    public Response method(String method, Object body) {
        if (body != null) {
            builder.body(body);
        }
        builder.method(method);
        request = builder.build();
        response = builder.client.invoke(request);
        builder.reset();
        return response;
    }

    public Response method(String method) {
        return method(method, null);
    }

    public Response get() {
        method("get");
        return handleError();
    }

    public Response post(String body) {
        com.intuit.karate.data.Json json = new com.intuit.karate.data.Json(body);
        return post(json.asMapOrList());
    }

    public Response post(Object body) {
        method("post", body);
        return handleError();
    }

    public Response delete() {
        method("delete");
        return handleError();
    }

    public static Http forUrl(String url) {
        HttpClient hc = new ArmeriaHttpClient(new com.intuit.karate.runtime.Config(), new Logger());
        Http http = new Http(hc, url);
        return http.url(url);
    }

    public static Http forUrl(ScenarioEngine engine, String url) {
        if (engine == null) {
            return forUrl(url);
        }
        HttpClient hc = engine.runtime.featureRuntime.suite.clientFactory.create(engine);
        Http http = new Http(hc, url);
        return http.url(url);
    }

    public Http config(String key, String value) {
        com.intuit.karate.runtime.Config config = builder.client.getConfig();
        config.configure(key, new Variable(value));
        builder.client.setConfig(config, key);
        return this;
    }

    public Http config(Map<String, Object> map) {
        com.intuit.karate.runtime.Config config = builder.client.getConfig();
        map.forEach((k, v) -> {
            config.configure(k, new Variable(v));
        });
        builder.client.setConfig(config, null);
        return this;
    }

}
