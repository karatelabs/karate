/*
 * The MIT License
 *
 * Copyright 2020 pthomas3.
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
package com.intuit.karate.core;

import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.http.ArmeriaHttpClient;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class HttpMockHandlerRunner { // TODO investigate intermittent CI failure

    static final Logger logger = LoggerFactory.getLogger(HttpMockHandlerRunner.class);

    MockHandler handler;
    HttpServer server;
    FeatureBuilder mock;
    HttpRequestBuilder http;
    Response response;

    HttpRequestBuilder handle() {
        handler = new MockHandler(mock.build());
        server = HttpServer.handler(handler).build();
        ArmeriaHttpClient client = new ArmeriaHttpClient(new Config(), new com.intuit.karate.Logger());
        http = new HttpRequestBuilder(client);
        http.url("http://localhost:" + server.getPort());
        return http;
    }

    FeatureBuilder background(String... lines) {
        mock = FeatureBuilder.background(lines);
        return mock;
    }

    @AfterEach
    void afterEach() {
        server.stop();
    }

    @Test
    void testProceed() {
        FeatureBuilder fb = background().scenario(
                "pathMatches('/hello')",
                "def response = 'world'");
        HttpServer downStream = HttpServer.handler(new MockHandler(fb.build())).build();
        String downStreamUrl = "http://localhost:" + downStream.getPort();
        background().scenario(
                "pathMatches('/hello')",
                "karate.proceed('" + downStreamUrl + "')",
                "def response = 'hello ' + response");
        response = handle().path("/hello").invoke("get");
        match(response.getBodyAsString(), "hello world");
        downStream.stop();
    }

}
