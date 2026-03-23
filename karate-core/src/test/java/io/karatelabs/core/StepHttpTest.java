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
package io.karatelabs.core;

import io.karatelabs.common.Json;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static io.karatelabs.core.InMemoryHttpClient.*;
import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class StepHttpTest {

    @Test
    void testSimpleGet() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{ \"id\": 1 }"));

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method get
            * match response == { id: 1 }
            """);
        assertPassed(sr);
    }

    @Test
    void testGetWithPath() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            // Verify path was set correctly
            String path = req.getPath();
            if (path.endsWith("/users/123")) {
                return json("{ \"id\": 123 }");
            }
            return status(404);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * path 'users', '123'
            * method get
            * match response.id == 123
            """);
        assertPassed(sr);
    }

    @Test
    void testGetWithParams() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String page = req.getParam("page");
            String size = req.getParam("size");
            if ("1".equals(page) && "10".equals(size)) {
                return json("{ \"page\": 1, \"size\": 10 }");
            }
            return json("{ \"error\": \"missing params\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/items'
            * param page = 1
            * param size = 10
            * method get
            * match response == { page: 1, size: 10 }
            """);
        assertPassed(sr);
    }

    @Test
    void testStatusAssertion() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> status(404));

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method get
            * status 200
            """);
        assertFailed(sr);
    }

    @Test
    void testStatusAssertionSuccess() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> status(201));

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method post
            * status 201
            """);
        assertPassed(sr);
    }

    @Test
    void testPostWithJsonBody() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            // Echo back request body
            Object body = req.getBodyConverted();
            if (body != null) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * request { name: 'test' }
            * method post
            * status 200
            * match response.received == true
            """);
        assertPassed(sr);
    }

    @Test
    void testRequestWithEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            // Echo back the request body to verify embedded expressions were resolved
            Object body = req.getBodyConverted();
            return json(Json.stringifyStrict(body));
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def myName = 'John'
            * def myAge = 30
            * request { name: '#(myName)', age: '#(myAge)' }
            * method post
            * status 200
            * match response == { name: 'John', age: 30 }
            """);
        assertPassed(sr);
    }

    @Test
    void testHeaders() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String auth = req.getHeader("Authorization");
            if ("Bearer token123".equals(auth)) {
                return json("{ \"authorized\": true }");
            }
            return status(401);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * header Authorization = 'Bearer token123'
            * method get
            * status 200
            * match response.authorized == true
            """);
        assertPassed(sr);
    }

    @Test
    void testResponseStatus() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> status(204));

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method delete
            * status 204
            """);
        assertPassed(sr);
        assertEquals(204, get(sr, "responseStatus"));
    }

    @Test
    void testResponseHeaders() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            var resp = json("{}");
            resp.setHeader("X-Custom-Header", "custom-value");
            return resp;
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method get
            * status 200
            """);
        assertPassed(sr);
        Object headers = get(sr, "responseHeaders");
        assertNotNull(headers);
    }

    @Test
    void testUrlFromVariable() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{ \"ok\": true }"));

        ScenarioRuntime sr = run(client, """
            * def baseUrl = 'http://test'
            * url baseUrl
            * method get
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testPutMethod() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            if ("PUT".equals(req.getMethod())) {
                return json("{ \"updated\": true }");
            }
            return status(405);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/item/1'
            * request { name: 'updated' }
            * method put
            * status 200
            * match response.updated == true
            """);
        assertPassed(sr);
    }

    @Test
    void testPatchMethod() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            if ("PATCH".equals(req.getMethod())) {
                return json("{ \"patched\": true }");
            }
            return status(405);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/item/1'
            * request { status: 'active' }
            * method patch
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testFormParams() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("form")) {
                return json("{ \"form\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/login'
            * form field username = 'admin'
            * form field password = 'secret'
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testCookie() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("session=abc123")) {
                return json("{ \"session\": 'valid' }");
            }
            return status(401);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * cookie session = 'abc123'
            * method get
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testCookieWithMapValue() {
        // V1 supports cookie with map value: cookie foo = { value: 'bar' }
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("foo=bar")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"cookie\": \"" + cookie + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * cookie foo = { value: 'bar' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testResponseCookies() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            HttpResponse resp = json("{ \"ok\": true }");
            resp.setHeader("Set-Cookie", java.util.List.of(
                "session=abc123; Path=/; HttpOnly",
                "token=xyz789; Domain=test.com"
            ));
            resp.setStartTime(1234567890L);
            return resp;
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method get
            * status 200
            * match responseCookies.session.value == 'abc123'
            * match responseCookies.session.path == '/'
            * match responseCookies.token.value == 'xyz789'
            * match responseCookies.token.domain == 'test.com'
            * match requestTimeStamp == 1234567890
            """);
        assertPassed(sr);
    }

    @Test
    void testPrevRequest() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{ \"ok\": true }"));

        ScenarioRuntime sr = run(client, """
            * url 'http://test/users'
            * request { name: 'John' }
            * method post
            * status 200
            * def prev = karate.prevRequest
            * match prev.method == 'POST'
            * match prev.url contains '/users'
            """);
        assertPassed(sr);
    }

    @Test
    void testPrevRequestBodyIsBytes() {
        // V1 compatibility: karate.prevRequest.body returns byte[], not parsed body
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{ \"ok\": true }"));

        ScenarioRuntime sr = run(client, """
            * url 'http://test/users'
            * request { name: 'Billie' }
            * method post
            * status 200
            * def requestBody = karate.prevRequest.body
            # convert byte array to string
            * def requestString = new java.lang.String(requestBody, 'utf-8')
            * match requestString == '{"name":"Billie"}'
            """);
        assertPassed(sr);
    }

    @Test
    void testPrevRequestUpdatesAfterEachCall() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{ \"ok\": true }"));

        ScenarioRuntime sr = run(client, """
            * url 'http://test/first'
            * method get
            * status 200
            * match karate.prevRequest.method == 'GET'
            * match karate.prevRequest.url contains '/first'

            * url 'http://test/second'
            * request { data: 123 }
            * method post
            * status 200
            * match karate.prevRequest.method == 'POST'
            * match karate.prevRequest.url contains '/second'
            """);
        assertPassed(sr);
    }

    // ========== Embedded Expression Tests ==========
    // Tests for V1 compatibility: embedded expressions like #(varName) in JSON literals

    @Test
    void testParamsWithEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String page = req.getParam("page");
            String size = req.getParam("size");
            if ("5".equals(page) && "20".equals(size)) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"page\": \"" + page + "\", \"size\": \"" + size + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def myPage = 5
            * def mySize = 20
            * params { page: '#(myPage)', size: '#(mySize)' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testHeadersWithEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String auth = req.getHeader("Authorization");
            String custom = req.getHeader("X-Custom");
            if ("Bearer secret123".equals(auth) && "myvalue".equals(custom)) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"auth\": \"" + auth + "\", \"custom\": \"" + custom + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def token = 'secret123'
            * def customVal = 'myvalue'
            * headers { Authorization: 'Bearer #(token)', 'X-Custom': '#(customVal)' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testCookiesWithEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("session=abc123") && cookie.contains("user=john")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"cookie\": \"" + cookie + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def sessionId = 'abc123'
            * def username = 'john'
            * cookies { session: '#(sessionId)', user: '#(username)' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testFormFieldsWithEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            Object body = req.getBodyConverted();
            // For form data, body is typically a string like "username=admin&password=secret"
            String bodyStr = body != null ? body.toString() : "";
            if (contentType != null && contentType.contains("form")
                && bodyStr.contains("admin") && bodyStr.contains("secret123")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"body\": \"" + bodyStr + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def user = 'admin'
            * def pass = 'secret123'
            * form fields { username: '#(user)', password: '#(pass)' }
            * method post
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testRequestWithNestedEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            Object body = req.getBodyConverted();
            return json(Json.stringifyStrict(body));
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def user = { name: 'John', age: 30 }
            * def items = ['a', 'b', 'c']
            * request { user: '#(user)', items: '#(items)', count: '#(items.length)' }
            * method post
            * status 200
            * match response.user == { name: 'John', age: 30 }
            * match response.items == ['a', 'b', 'c']
            * match response.count == 3
            """);
        assertPassed(sr);
    }

    @Test
    void testRequestWithOptionalEmbeddedExpressions() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            Object body = req.getBodyConverted();
            return json(Json.stringifyStrict(body));
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def name = 'John'
            * def missing = null
            * request { name: '#(name)', optional: '##(missing)' }
            * method post
            * status 200
            * match response == { name: 'John' }
            """);
        assertPassed(sr);
    }

    // ========== Header Name Tests ==========

    @Test
    void testHeaderWithHyphenatedName() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if ("application/json".equals(contentType)) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"contentType\": \"" + contentType + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * header Content-Type = 'application/json'
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testHeaderWithLowercaseHyphenatedName() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("content-type");
            if ("application/json".equals(contentType)) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"contentType\": \"" + contentType + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * header content-type = 'application/json'
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchResponseHeaderExpression() {
        // V1 supports "match header <name>" to access response headers
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            HttpResponse resp = json("{ \"ok\": true }");
            resp.setHeader("Content-Type", "application/json; charset=utf-8");
            return resp;
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method get
            * status 200
            * match header Content-Type contains 'application/json'
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchResponseHeaderCaseInsensitive() {
        // V1 behavior: header name lookup is case-insensitive
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            HttpResponse resp = json("{ \"ok\": true }");
            resp.setHeader("Content-Type", "application/json");
            return resp;
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * method get
            * status 200
            * match header content-type contains 'application/json'
            """);
        assertPassed(sr);
    }

    @Test
    void testParamsWithNullValues() {
        // V1 behavior: params with null values should be skipped
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String name = req.getParam("name");
            String country = req.getParam("country");
            // country should be null/missing since it was set to null
            if ("foo".equals(name) && country == null) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"error\": \"country should be null\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/search'
            * def query = { name: 'foo', country: null }
            * params query
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testParamsWithNullValuesInList() {
        // V1 behavior: null items in list should be skipped
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            // Should only have non-null values
            java.util.List<String> values = req.getParams().get("items");
            if (values != null && values.size() == 2
                && "a".equals(values.get(0)) && "b".equals(values.get(1))) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"error\": \"unexpected items\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/search'
            * def query = { items: ['a', null, 'b'] }
            * params query
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    // ========== Configure Headers/Cookies with JS Function Tests ==========

    @Test
    void testConfigureHeadersWithJsFunction() {
        // V1 supports: configure headers = read('headers.js')
        // The JS function should be called before each request and return headers
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String auth = req.getHeader("Authorization");
            String requestId = req.getHeader("X-Request-Id");
            if ("Bearer secret-token".equals(auth) && requestId != null) {
                return json("{ \"ok\": true, \"requestId\": \"" + requestId + "\" }");
            }
            return json("{ \"error\": \"missing headers\", \"auth\": \"" + auth + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def token = 'secret-token'
            * def headersFn = function(){ return { 'Authorization': 'Bearer ' + token, 'X-Request-Id': java.util.UUID.randomUUID() + '' } }
            * configure headers = headersFn
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureHeadersWithJsFunctionMultipleCalls() {
        // Headers function should be called for each request
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String requestId = req.getHeader("X-Request-Id");
            return json("{ \"requestId\": \"" + requestId + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def counter = { value: 0 }
            * def headersFn = function(){ counter.value++; return { 'X-Request-Id': 'req-' + counter.value } }
            * configure headers = headersFn

            * method get
            * status 200
            * match response.requestId == 'req-1'

            * url 'http://test'
            * method get
            * status 200
            * match response.requestId == 'req-2'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureHeadersAccessesKarateVariables() {
        // V1 behavior: headers function can access variables via karate.get()
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.contains("token123") && auth.contains("time456")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"auth\": \"" + auth + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def token = 'token123'
            * def time = 'time456'
            * def headersFn = function(){ var t = karate.get('token'); var tm = karate.get('time'); return { 'Authorization': t + tm } }
            * configure headers = headersFn
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureCookiesWithJsFunction() {
        // V1 supports: configure cookies = read('cookies.js')
        // The JS function should be called before each request and return cookies
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("session=abc123") && cookie.contains("tracking=")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"cookie\": \"" + cookie + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def sessionId = 'abc123'
            * def cookiesFn = function(){ return { 'session': sessionId, 'tracking': java.util.UUID.randomUUID() + '' } }
            * configure cookies = cookiesFn
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureCookiesWithJsFunctionMultipleCalls() {
        // Cookies function should be called for each request
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            // Extract counter value from cookie
            if (cookie != null && cookie.contains("counter=")) {
                int start = cookie.indexOf("counter=") + 8;
                int end = cookie.indexOf(";", start);
                if (end == -1) end = cookie.length();
                String counterValue = cookie.substring(start, end);
                return json("{ \"counter\": \"" + counterValue + "\" }");
            }
            return json("{ \"cookie\": \"" + cookie + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def counter = { value: 0 }
            * def cookiesFn = function(){ counter.value++; return { 'counter': counter.value + '' } }
            * configure cookies = cookiesFn

            * method get
            * status 200
            * match response.counter == '1'

            * url 'http://test'
            * method get
            * status 200
            * match response.counter == '2'
            """);
        assertPassed(sr);
    }

    @Test
    void testResponseCookiesAutoSend() {
        // V1 behavior: responseCookies should be auto-sent on subsequent requests
        // When server sends Set-Cookie, the cookie should be sent back automatically
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String path = req.getPath();
            if (path.endsWith("/login")) {
                // First request: set a session cookie
                HttpResponse resp = json("{ \"token\": \"abc123\" }");
                resp.setHeader("Set-Cookie", java.util.List.of("session=xyz789; Path=/"));
                return resp;
            } else if (path.endsWith("/protected")) {
                // Second request: check if session cookie was sent
                String cookie = req.getHeader("Cookie");
                if (cookie != null && cookie.contains("session=xyz789")) {
                    return json("{ \"ok\": true }");
                }
                return json("{ \"error\": \"no session cookie\", \"cookie\": \"" + cookie + "\" }");
            }
            return status(404);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/login'
            * method get
            * status 200
            * match responseCookies.session.value == 'xyz789'

            * url 'http://test/protected'
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testResponseCookiesAutoSendAcrossMultipleRequests() {
        // V1 behavior: cookies set by client and echoed back by server should persist
        // This mirrors the karate-demo cookies.feature test
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("foo=bar")) {
                // Echo back the cookies as response
                HttpResponse resp = json("[{ \"name\": \"foo\", \"value\": \"bar\" }]");
                resp.setHeader("Set-Cookie", java.util.List.of("foo=bar; Path=/"));
                return resp;
            }
            return json("[]");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/cookies'
            * cookie foo = 'bar'
            * method get
            * status 200
            * match response == '#[1]'
            * match response[0] contains { name: 'foo', value: 'bar' }

            # Second request - cookie should be auto-sent
            * url 'http://test/cookies'
            * request {}
            * method post
            * status 200
            * match response == '#[1]'
            * match response[0] contains { name: 'foo', value: 'bar' }
            """);
        assertPassed(sr);
    }

    @Test
    void testCookieResetWithConfigureNull() {
        // V1 behavior: configure cookies = null should clear all cookies
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("foo=bar")) {
                HttpResponse resp = json("[{ \"name\": \"foo\", \"value\": \"bar\" }]");
                resp.setHeader("Set-Cookie", java.util.List.of("foo=bar; Path=/"));
                return resp;
            }
            return json("[]");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/cookies'
            * cookie foo = 'bar'
            * method get
            * status 200
            * match response == '#[1]'

            # Reset cookies
            * configure cookies = null
            * url 'http://test/cookies'
            * method get
            * status 200
            * match response == []
            """);
        assertPassed(sr);
    }

    @Test
    void testCookieOverrideOnSubsequentRequest() {
        // V1 behavior: explicit cookie should override persisted cookies
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("foo=blah")) {
                HttpResponse resp = json("[{ \"name\": \"foo\", \"value\": \"blah\" }]");
                resp.setHeader("Set-Cookie", java.util.List.of("foo=blah; Path=/"));
                return resp;
            } else if (cookie != null && cookie.contains("foo=bar")) {
                HttpResponse resp = json("[{ \"name\": \"foo\", \"value\": \"bar\" }]");
                resp.setHeader("Set-Cookie", java.util.List.of("foo=bar; Path=/"));
                return resp;
            }
            return json("[]");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/cookies'
            * cookie foo = 'bar'
            * method get
            * status 200
            * match response[0].value == 'bar'

            # Reset and send different cookie
            * configure cookies = null
            * url 'http://test/cookies'
            * cookie foo = 'blah'
            * request {}
            * method post
            * status 200
            * match response[0].value == 'blah'
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipleResponseCookiesPersisted() {
        // V1 behavior: multiple Set-Cookie headers should all be persisted
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String path = req.getPath();
            if (path.endsWith("/login")) {
                HttpResponse resp = json("{ \"ok\": true }");
                resp.setHeader("Set-Cookie", java.util.List.of(
                    "session=abc123; Path=/",
                    "token=xyz789; Path=/",
                    "user=john; Path=/"
                ));
                return resp;
            } else if (path.endsWith("/check")) {
                String cookie = req.getHeader("Cookie");
                boolean hasSession = cookie != null && cookie.contains("session=abc123");
                boolean hasToken = cookie != null && cookie.contains("token=xyz789");
                boolean hasUser = cookie != null && cookie.contains("user=john");
                if (hasSession && hasToken && hasUser) {
                    return json("{ \"allCookies\": true }");
                }
                return json("{ \"cookie\": \"" + cookie + "\" }");
            }
            return status(404);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/login'
            * method get
            * status 200
            * match responseCookies.session.value == 'abc123'
            * match responseCookies.token.value == 'xyz789'
            * match responseCookies.user.value == 'john'

            # All three cookies should be sent on next request
            * url 'http://test/check'
            * method get
            * status 200
            * match response.allCookies == true
            """);
        assertPassed(sr);
    }

    @Test
    void testResponseCookiesFromPostSentInGet() {
        // V1 behavior: cookies from POST response should be sent in subsequent GET
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String path = req.getPath();
            if (path.endsWith("/login") && "POST".equals(req.getMethod())) {
                HttpResponse resp = json("{ \"loggedIn\": true }");
                resp.setHeader("Set-Cookie", java.util.List.of("auth=secret123; Path=/"));
                return resp;
            } else if (path.endsWith("/profile") && "GET".equals(req.getMethod())) {
                String cookie = req.getHeader("Cookie");
                if (cookie != null && cookie.contains("auth=secret123")) {
                    return json("{ \"profile\": \"visible\" }");
                }
                return json("{ \"error\": \"unauthorized\" }");
            }
            return status(404);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/login'
            * request { username: 'admin', password: 'secret' }
            * method post
            * status 200
            * match responseCookies.auth.value == 'secret123'

            # Cookie from POST should be sent in GET
            * url 'http://test/profile'
            * method get
            * status 200
            * match response.profile == 'visible'
            """);
        assertPassed(sr);
    }

    @Test
    void testHeadersFeatureFlow() {
        // This simulates the v1 demo/headers/headers.feature flow:
        // 1. GET /headers -> returns token and sets 'time' cookie
        // 2. GET /headers/{token}?url=baseUrl with Authorization header
        //    - Server validates time cookie + Authorization = token + time + url
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String path = req.getPath();
            if (path.equals("/headers")) {
                // Sign-in: return token and set time cookie
                String token = "my-token-123";
                String time = System.currentTimeMillis() + "";
                HttpResponse resp = json("\"" + token + "\""); // Response is the token as a string
                resp.setHeader("Set-Cookie", java.util.List.of("time=" + time + "; Path=/"));
                return resp;
            } else if (path.contains("/headers/")) {
                // Validate token, time cookie, and Authorization header
                String cookie = req.getHeader("Cookie");
                String auth = req.getHeader("Authorization");
                String url = req.getParam("url");

                // Extract token from path (e.g., /headers/my-token-123)
                String pathToken = path.substring(path.lastIndexOf("/") + 1);

                // Check if time cookie is present
                boolean hasTimeCookie = cookie != null && cookie.contains("time=");

                // Check if Authorization matches expected format: token + time + url
                // We can't check exact value since time is dynamic, just check it contains the token
                boolean hasAuth = auth != null && auth.contains(pathToken);

                if (hasTimeCookie && hasAuth) {
                    return json("{ \"ok\": true }");
                }
                return status(400);
            }
            return status(404);
        });

        ScenarioRuntime sr = run(client, """
            * def demoBaseUrl = 'http://test'
            * url demoBaseUrl
            * path 'headers'
            * method get
            * status 200
            * def token = response
            * def time = responseCookies.time.value
            * match responseCookies contains { time: '#notnull' }

            # Use configure headers with JS function
            * def headersFn = function(){ var t = karate.get('token'); var tm = karate.get('time'); var url = karate.get('demoBaseUrl'); return { 'Authorization': t + tm + url } }
            * configure headers = headersFn
            * path 'headers', token
            * param url = demoBaseUrl
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureHeadersWithEmbeddedExpressions() {
        // V1 behavior: configure headers = { key: '#(variable)' } should evaluate embedded expressions
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String auth = req.getHeader("Authorization");
            if ("token123time456".equals(auth)) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"auth\": \"" + auth + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def token = 'token123'
            * def time = 'time456'
            * configure headers = { Authorization: '#(token + time)' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureCookiesWithEmbeddedExpressions() {
        // V1 behavior: configure cookies = { key: '#(variable)' } should evaluate embedded expressions
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            if (cookie != null && cookie.contains("session=abc123") && cookie.contains("time=456")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"cookie\": \"" + cookie + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def sessionId = 'abc123'
            * def timeValue = '456'
            * configure cookies = { session: '#(sessionId)', time: '#(timeValue)' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureHeadersAndCookiesWithEmbeddedExpressions() {
        // Combined test: both headers and cookies with embedded expressions
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String cookie = req.getHeader("Cookie");
            String auth = req.getHeader("Authorization");
            boolean hasTimeCookie = cookie != null && cookie.contains("time=time789");
            boolean hasAuth = auth != null && auth.contains("token123");
            if (hasTimeCookie && hasAuth) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"cookie\": \"" + cookie + "\", \"auth\": \"" + auth + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * def token = 'token123'
            * def time = 'time789'
            * configure headers = { Authorization: '#(token + time)' }
            * configure cookies = { time: '#(time)' }
            * method get
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testRequestWithInlineXml() {
        // Tests V1 syntax: And request <name>value</name>
        // Inline XML should be parsed and sent as body
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            // Check raw bytes for XML content
            byte[] bodyBytes = req.getBody();
            String bodyStr = bodyBytes != null ? new String(bodyBytes) : "";
            if (bodyStr.contains("Müller") || bodyStr.contains("M\\u00FCller")) {
                return json("{ \"ok\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/echo'
            * request <name>Müller</name>
            * method post
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testRequestWithXmlDocstring() {
        // Tests XML in docstring
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            byte[] bodyBytes = req.getBody();
            String bodyStr = bodyBytes != null ? new String(bodyBytes) : "";
            if (bodyStr.contains("Add") && bodyStr.contains("intA")) {
                return json("{ \"result\": 5 }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/soap'
            * request
            \"\"\"
            <?xml version="1.0"?>
            <Add>
              <intA>2</intA>
              <intB>3</intB>
            </Add>
            \"\"\"
            * method post
            * status 200
            * match response.result == 5
            """);
        assertPassed(sr);
    }

    @Test
    void testSoapAction() {
        // V1 syntax: When soap action 'http://tempuri.org/Add'
        // Sets SOAPAction header, Content-Type: text/xml, and does POST
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String method = req.getMethod();
            String soapAction = req.getHeader("SOAPAction");
            String contentType = req.getHeader("Content-Type");
            if ("POST".equals(method)
                && "http://tempuri.org/Add".equals(soapAction)
                && contentType != null && contentType.contains("text/xml")) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"method\": \"" + method + "\", \"soapAction\": \"" + soapAction + "\", \"contentType\": \"" + contentType + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/soap'
            * request
            \"\"\"
            <?xml version="1.0"?>
            <Add>
              <intA>2</intA>
              <intB>3</intB>
            </Add>
            \"\"\"
            * soap action 'http://tempuri.org/Add'
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testSoapActionWithExpression() {
        // Test that soap action evaluates expressions
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String soapAction = req.getHeader("SOAPAction");
            if ("http://example.com/MyAction".equals(soapAction)) {
                return json("{ \"ok\": true }");
            }
            return json("{ \"soapAction\": \"" + soapAction + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/soap'
            * def action = 'http://example.com/MyAction'
            * request <test/>
            * soap action action
            * status 200
            * match response.ok == true
            """);
        assertPassed(sr);
    }

    @Test
    void testDefaultCharsetAddedToContentType() {
        // V1 behavior: charset=utf-8 is auto-added to JSON content-type
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            return json("{ \"contentType\": \"" + contentType + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * request { foo: 'bar' }
            * method post
            * status 200
            * match response.contentType contains 'application/json'
            * match response.contentType contains 'charset=UTF-8'
            """);
        assertPassed(sr);
    }

    @Test
    void testCharsetAddedToCustomContentType() {
        // V1 behavior: charset=utf-8 is auto-added even for custom JSON content-types
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            return json("{ \"contentType\": \"" + contentType + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * header Content-Type = 'application/vnd.app.test+json;version=1'
            * request { foo: 'bar' }
            * method post
            * status 200
            * match response.contentType contains 'application/vnd.app.test+json'
            * match response.contentType contains 'charset=UTF-8'
            * match response.contentType contains 'version=1'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureCharsetNull() {
        // V1 behavior: configure charset = null disables auto-charset
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            return json("{ \"contentType\": \"" + contentType + "\" }");
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test'
            * configure charset = null
            * header Content-Type = 'application/json'
            * request { foo: 'bar' }
            * method post
            * status 200
            * match response.contentType == 'application/json'
            * match response.contentType !contains 'charset'
            """);
        assertPassed(sr);
    }

}
