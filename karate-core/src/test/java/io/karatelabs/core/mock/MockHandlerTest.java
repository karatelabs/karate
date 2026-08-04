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
              * def response = ({ id: pathParams.id })
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/users/123");
        HttpResponse response = handler.apply(request);

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("123"));
    }

    @Test
    void testHeaderValueCaseInsensitive() {
        // headerValue(name) should be case-insensitive on name and return the value as a String
        // (vs. raw requestHeaders[name] which is case-sensitive and returns List<String>).
        Feature feature = parseFeature("""
            Feature: Test Mock

            Scenario: pathMatches('/echo')
              * def auth = headerValue('Authorization')
              * def lower = headerValue('authorization')
              * def upper = headerValue('AUTHORIZATION')
              * def missing = headerValue('X-Missing')
              * def response = ({ auth: auth, lower: lower, upper: upper, missing: missing })
            """);

        MockHandler handler = new MockHandler(feature);
        HttpRequest request = createRequest("GET", "/echo");
        // Put the header in mixed-case; lookups in any case must find it.
        request.putHeader("Authorization", "Bearer abc123");

        HttpResponse response = handler.apply(request);
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("\"auth\":\"Bearer abc123\""), body);
        assertTrue(body.contains("\"lower\":\"Bearer abc123\""), body);
        assertTrue(body.contains("\"upper\":\"Bearer abc123\""), body);
        // Absent header -> null, which serializes as JSON null
        assertTrue(body.contains("\"missing\":null"), body);
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
              * def response = ({ count: counter.value })
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
              * def response = ({ method: requestMethod, path: requestPath })
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

    @Test
    void everyFabricatedResponseDisclosesThatAMockAnsweredIt() {
        // The responder has to say so itself: an address proves nothing (a real service on localhost is
        // ordinary), so without this header a consumer — above all the coverage graph — cannot tell a
        // stand-in from the real system. Presence is certainty; absence proves nothing.
        Feature feature = parseFeature("""
            Feature: disclosure

            Scenario: pathMatches('/hello')
              * def response = { message: 'world' }
            """);

        MockHandler handler = new MockHandler(feature);
        HttpResponse response = handler.apply(createRequest("GET", "/hello"));
        assertEquals("true", response.getHeader(MockHandler.KARATE_MOCK_HEADER));

        // it rides every response the mock fabricates, including the no-scenario-matched 404
        HttpResponse notFound = handler.apply(createRequest("GET", "/nope"));
        assertEquals(404, notFound.getStatus());
        assertEquals("true", notFound.getHeader(MockHandler.KARATE_MOCK_HEADER));
    }

    @Test
    void theDisclosureHeaderCanBeSuppressedForAMockThatMustImpersonateExactly() {
        Feature feature = parseFeature("""
            Feature: disclosure off

            Scenario: pathMatches('/hello')
              * def response = { message: 'world' }
            """);

        MockHandler handler = new MockHandler(feature);
        handler.getConfig().setMockHeaderEnabled(false);
        HttpResponse response = handler.apply(createRequest("GET", "/hello"));

        assertNull(response.getHeader(MockHandler.KARATE_MOCK_HEADER),
                "a mock asserting on an exact header set can opt out");
    }

    @Test
    void anEmbeddedExpressionThatThrowsNeverReachesTheWireAsItsOwnSourceText() {
        // a `#(expr)` that fails is left as its source text for the match engine to retry — but nothing
        // ever matches a mock body, so without this guard the literal placeholder is served AS DATA
        Feature feature = parseFeature("""
            Feature: broken helper

            Background:
              * def uuid = function(){ return java.util.UUID.randomUUID() + '' }

            Scenario: pathMatches('/quotes')
              * def responseStatus = 201
              * def response = { id: '#(uuid())', currency: 'USD' }
            """);

        HttpResponse response = new MockHandler(feature).apply(createRequest("GET", "/quotes"));

        assertEquals(500, response.getStatus(), "silently serving the placeholder is the defect");
        String body = new String(response.getBodyBytes());
        assertTrue(body.contains("#(uuid())"), "names the placeholder that was stranded: " + body);
        assertTrue(body.contains("/id"), "names WHERE in the body it stranded: " + body);
        assertTrue(body.contains("java"), "names the real cause, not just the symptom: " + body);
    }

    @Test
    void anXmlBodyIsCoveredToo() {
        // an XML response is text under the hood, so the placeholder reaches the wire the same way —
        // and this is the body type SOAP-style mocks use
        Feature feature = parseFeature("""
            Feature: broken helper, xml

            Background:
              * def uuid = function(){ return java.util.UUID.randomUUID() + '' }

            Scenario: pathMatches('/soap')
              * def response = <root><id>#(uuid())</id></root>
            """);

        HttpResponse response = new MockHandler(feature).apply(createRequest("GET", "/soap"));

        assertEquals(500, response.getStatus(), "" + new String(response.getBodyBytes()));
    }

    /**
     * <b>The guard may never be something a caller can trigger.</b> A mock that echoes a path segment
     * back is ordinary and the value is entirely client-chosen, so a check that fired on anything merely
     * SHAPED like a placeholder would hand any caller a 500 — turning an inertness guarantee into a
     * denial of service. The test is whether an expression was evaluated and threw, which this was not.
     */
    @Test
    void aClientChosenValueThatLooksLikeAPlaceholderIsServedNotRefused() {
        Feature feature = parseFeature("""
            Feature: echo

            Scenario: pathMatches('/policies/{id}')
              * def responseStatus = 404
              * def response = { error: 'no such policy', id: '#(pathParams.id)' }
            """);

        HttpResponse response = new MockHandler(feature).apply(createRequest("GET", "/policies/#(boom)"));

        assertEquals(404, response.getStatus(), "the client picked that text; it is data: "
                + new String(response.getBodyBytes()));
        assertTrue(new String(response.getBodyBytes()).contains("#(boom)"), "and it is echoed inert");
    }

    /** The same, one transformation removed — the case value-identity provenance cannot see. */
    @Test
    void aDerivedValueThatLooksLikeAPlaceholderIsServedNotRefused() {
        Feature feature = parseFeature("""
            Feature: derived echo

            Scenario: pathMatches('/echo')
              * def shout = headerValue('X-Thing').toUpperCase()
              * def response = { got: '#(shout)' }
            """);

        HttpRequest request = createRequest("GET", "/echo");
        request.putHeader("X-Thing", "#(boom)");
        HttpResponse response = new MockHandler(feature).apply(request);

        assertEquals(200, response.getStatus(), "" + new String(response.getBodyBytes()));
        assertTrue(new String(response.getBodyBytes()).contains("#(BOOM)"), "" + new String(response.getBodyBytes()));
    }

    /** {@code ##(...)} means "may be absent" — a lenient failure there is the feature, not a defect. */
    @Test
    void anOptionalEmbeddedExpressionIsNotAnError() {
        Feature feature = parseFeature("""
            Feature: optional

            Scenario: pathMatches('/opt')
              * def response = { a: 1, b: '##(nope())' }
            """);

        HttpResponse response = new MockHandler(feature).apply(createRequest("GET", "/opt"));

        assertEquals(200, response.getStatus(), "" + new String(response.getBodyBytes()));
    }

    @Test
    void aWorkingEmbeddedExpressionIsUnaffected() {
        Feature feature = parseFeature("""
            Feature: working helper

            Background:
              * def label = function(){ return 'ok' }

            Scenario: pathMatches('/hello')
              * def response = { a: '#(label())', b: '#(1 + 1)', c: 'plain #(not-a-whole-value) text' }
            """);

        HttpResponse response = new MockHandler(feature).apply(createRequest("GET", "/hello"));

        assertEquals(200, response.getStatus());
        String body = new String(response.getBodyBytes());
        assertTrue(body.contains("\"a\":\"ok\""), body);
        assertTrue(body.contains("\"b\":2"), body);
    }

}
