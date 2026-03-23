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
package io.karatelabs.core.mock;

import io.karatelabs.common.Resource;
import io.karatelabs.core.MockHandler;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MockHandlerTest {

    private Feature parseFeature(String content) {
        return Feature.read(Resource.text(content));
    }

    private HttpRequest createRequest(String method, String path) {
        HttpRequest request = new HttpRequest();
        request.setMethod(method);
        request.setPath(path);
        return request;
    }

    private HttpRequest createRequest(String method, String path, byte[] body, String contentType) {
        HttpRequest request = createRequest(method, path);
        request.setBody(body);
        if (contentType != null) {
            request.putHeader("Content-Type", contentType);
        }
        return request;
    }

    @Test
    void testSimpleGetResponse() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/hello')
              * def response = { message: 'world' }
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/hello");
        HttpResponse response = handler.apply(request);

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("world"));
    }

    @Test
    void testMethodMatching() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/api') && methodIs('get')
              * def response = { method: 'get' }

            Scenario: pathMatches('/api') && methodIs('post')
              * def response = { method: 'post' }
            """);

        MockHandler handler = new MockHandler(feature);

        // Test GET
        HttpRequest getRequest = createRequest("GET", "/api");
        HttpResponse getResponse = handler.apply(getRequest);
        assertEquals(200, getResponse.getStatus());
        assertTrue(getResponse.getBodyString().contains("get"));

        // Test POST
        HttpRequest postRequest = createRequest("POST", "/api");
        HttpResponse postResponse = handler.apply(postRequest);
        assertEquals(200, postResponse.getStatus());
        assertTrue(postResponse.getBodyString().contains("post"));
    }

    @Test
    void testPathParamsExtraction() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/users/{id}')
              * def response = { id: pathParams.id }
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/users/123");
        HttpResponse response = handler.apply(request);

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("123"));
    }

    @Test
    void testResponseStatus() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/created')
              * def response = { created: true }
              * def responseStatus = 201
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/created");
        HttpResponse response = handler.apply(request);

        assertEquals(201, response.getStatus());
    }

    @Test
    void testCatchAllScenario() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/specific')
              * def response = { path: 'specific' }

            Scenario:
              * def response = { path: 'catch-all' }
              * def responseStatus = 404
            """);

        MockHandler handler = new MockHandler(feature);

        // Test specific path
        HttpRequest specific = createRequest("GET", "/specific");
        HttpResponse specificResponse = handler.apply(specific);
        assertEquals(200, specificResponse.getStatus());
        assertTrue(specificResponse.getBodyString().contains("specific"));

        // Test catch-all
        HttpRequest other = createRequest("GET", "/unknown");
        HttpResponse otherResponse = handler.apply(other);
        assertEquals(404, otherResponse.getStatus());
        assertTrue(otherResponse.getBodyString().contains("catch-all"));
    }

    @Test
    void testStatefulMock() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Background:
              * def counter = { value: 0 }

            Scenario: pathMatches('/increment')
              * counter.value = counter.value + 1
              * def response = { count: counter.value }
            """);

        MockHandler handler = new MockHandler(feature);

        // First call
        HttpRequest request1 = createRequest("GET", "/increment");
        HttpResponse response1 = handler.apply(request1);
        assertEquals(200, response1.getStatus());
        assertTrue(response1.getBodyString().contains("1"));

        // Second call
        HttpRequest request2 = createRequest("GET", "/increment");
        HttpResponse response2 = handler.apply(request2);
        assertTrue(response2.getBodyString().contains("2"));

        // Third call
        HttpRequest request3 = createRequest("GET", "/increment");
        HttpResponse response3 = handler.apply(request3);
        assertTrue(response3.getBodyString().contains("3"));
    }

    @Test
    void testCorsConfiguration() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Background:
              * configure cors = true

            Scenario: pathMatches('/api')
              * def response = { data: 'test' }
            """);

        MockHandler handler = new MockHandler(feature);

        // Test OPTIONS preflight
        HttpRequest options = createRequest("OPTIONS", "/api");
        HttpResponse optionsResponse = handler.apply(options);
        assertEquals(200, optionsResponse.getStatus());
        assertEquals("*", optionsResponse.getHeader("Access-Control-Allow-Origin"));

        // Test normal request includes CORS header
        HttpRequest get = createRequest("GET", "/api");
        HttpResponse getResponse = handler.apply(get);
        assertEquals(200, getResponse.getStatus());
        assertEquals("*", getResponse.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void testConfigureResponseHeaders() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Background:
              * configure responseHeaders = { 'Content-Type': 'application/json', 'X-Custom': 'value' }

            Scenario: pathMatches('/api')
              * def response = { data: 'test' }
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/api");
        HttpResponse response = handler.apply(request);

        assertEquals(200, response.getStatus());
        assertEquals("value", response.getHeader("X-Custom"));
    }

    @Test
    void testTypeContains() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/api') && typeContains('json')
              * def response = { type: 'json' }

            Scenario: pathMatches('/api') && typeContains('xml')
              * def response = { type: 'xml' }
            """);

        MockHandler handler = new MockHandler(feature);

        // Test JSON content type
        HttpRequest jsonRequest = createRequest("POST", "/api", "{}".getBytes(), "application/json");
        HttpResponse jsonResponse = handler.apply(jsonRequest);
        assertEquals(200, jsonResponse.getStatus());
        assertTrue(jsonResponse.getBodyString().contains("json"));
    }

    @Test
    void testRequestVariables() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/echo')
              * def response = { method: requestMethod, path: requestPath }
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("POST", "/echo");
        HttpResponse response = handler.apply(request);

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("POST"));
        assertTrue(response.getBodyString().contains("/echo"));
    }

    @Test
    void testNoMatchReturns404() {
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/specific')
              * def response = { found: true }
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/other");
        HttpResponse response = handler.apply(request);

        assertEquals(404, response.getStatus());
    }

}
