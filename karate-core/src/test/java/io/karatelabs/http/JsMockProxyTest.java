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

import io.karatelabs.core.MockServer;
import io.karatelabs.markup.ResourceResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for request.proceed() proxy mode from JS-file mocks.
 * Mirrors MockProxyTest but drives the proxy via a JavaScript handler
 * evaluated through ServerTestHarness.
 */
class JsMockProxyTest {

    private static MockServer backendServer;
    private static ServerTestHarness proxyHarness;
    private static String backendUrl;

    @BeforeAll
    static void startServers() {
        backendServer = MockServer.featureString("""
                Feature: Backend API

                Scenario: pathMatches('/api/data') && methodIs('get')
                  * def response = { source: 'backend', message: 'hello from backend' }

                Scenario: pathMatches('/echo') && methodIs('post')
                  * def response = ({ received: request, authHeader: requestHeaders['X-Auth'][0] })
                  * def responseStatus = 201

                Scenario: pathMatches('/host-echo')
                  * def response = ({ host: requestHeaders['Host'][0] })

                Scenario: methodIs('get')
                  * def response = { original: true }
                """)
                .port(0)
                .start();
        backendUrl = "http://localhost:" + backendServer.getPort();

        ResourceResolver resolver = (path, caller) -> null;
        proxyHarness = new ServerTestHarness(resolver);
        proxyHarness.start();
    }

    @AfterAll
    static void stopServers() {
        if (proxyHarness != null) {
            proxyHarness.stop();
        }
        if (backendServer != null) {
            backendServer.stopAsync();
        }
    }

    @Test
    void testProceedWithTargetUrl() {
        proxyHarness.setHandler(ctx -> {
            ctx.engine().put("backendUrl", backendUrl);
            ctx.eval("""
                    var r = request.proceed(backendUrl);
                    response.status = r.status;
                    response.body = r.body;
                    """);
            return ctx.response();
        });

        HttpResponse response = proxyHarness.get("/api/data");

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals("backend", body.get("source"));
        assertEquals("hello from backend", body.get("message"));
    }

    @Test
    void testProceedWithPostRequest() {
        proxyHarness.setHandler(ctx -> {
            ctx.engine().put("backendUrl", backendUrl);
            ctx.eval("""
                    var r = request.proceed(backendUrl);
                    response.status = r.status;
                    response.body = r.body;
                    """);
            return ctx.response();
        });

        HttpRequestBuilder builder = new HttpRequestBuilder(new ApacheHttpClient());
        builder.url(proxyHarness.getBaseUrl())
                .path("/echo")
                .method("POST")
                .header("X-Auth", "token-abc")
                .body(Map.of("name", "test", "value", 42));
        HttpResponse response = builder.invoke();

        assertEquals(201, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        @SuppressWarnings("unchecked")
        Map<String, Object> received = (Map<String, Object>) body.get("received");
        assertEquals("test", received.get("name"));
        assertEquals(42, ((Number) received.get("value")).intValue());
        assertEquals("token-abc", body.get("authHeader"));
    }

    @Test
    void testProceedWithResponseModification() {
        proxyHarness.setHandler(ctx -> {
            ctx.engine().put("backendUrl", backendUrl);
            ctx.eval("""
                    var r = request.proceed(backendUrl);
                    response.body = { original: r.body.original, modified: true, timestamp: 'now' };
                    """);
            return ctx.response();
        });

        HttpResponse response = proxyHarness.get("/modify");

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals(true, body.get("original"));
        assertEquals(true, body.get("modified"));
        assertEquals("now", body.get("timestamp"));
    }

    @Test
    void testProceedWithHostHeader() {
        String backendHost = "localhost:" + backendServer.getPort();
        proxyHarness.setHandler(ctx -> {
            // Rewrite Host header so no-arg proceed() targets the backend.
            ctx.request().putHeader("Host", backendHost);
            ctx.eval("""
                    var r = request.proceed();
                    response.status = r.status;
                    response.body = r.body;
                    """);
            return ctx.response();
        });

        HttpResponse response = proxyHarness.get("/host-echo");

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals(backendHost, body.get("host"));
    }

}
