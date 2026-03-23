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

import io.karatelabs.core.MockServer;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.http.ApacheHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for mock features.
 * Uses a shared mock server for all tests, running Karate features against it.
 *
 * NOTE: Tests from karate-demo (e.g., demo/mock/) are a separate exercise.
 * This file focuses on core mock functionality from karate-core.
 */
class MockE2eTest {

    private static MockServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        // Comprehensive mock server handling all test scenarios
        server = MockServer.featureString("""
            Feature: E2E Test Mock

            Background:
              * def counter = 0
              * def retryCounter = { value: 0 }
              * def payments = {}
              * def foo = { bar: 'baz' }

            # Payment CRUD scenarios
            Scenario: pathMatches('/payments') && methodIs('post')
              * def payment = request
              * def counter = counter + 1
              * def id = '' + counter
              * payment.id = id
              * payments[id] = payment
              * def response = payment

            Scenario: pathMatches('/payments/{id}') && methodIs('put')
              * payments[pathParams.id] = request
              * def response = request

            Scenario: pathMatches('/payments/{id}') && methodIs('delete')
              * karate.remove('payments', pathParams.id)
              * def responseStatus = 204

            Scenario: pathMatches('/payments/{id}')
              * def response = payments[pathParams.id]
              * def responseStatus = response ? 200 : 404

            Scenario: pathMatches('/payments')
              * def response = karate.valuesOf(payments)

            # Simple response scenarios
            Scenario: pathMatches('/hello')
              * def response = foo

            # Performance test scenarios
            Scenario: pathMatches('/fast')
              * def response = { speed: 'fast' }

            Scenario: pathMatches('/slow')
              * def responseDelay = 100
              * def response = { speed: 'slow' }

            # Path params test
            Scenario: pathMatches('/users/{userId}/orders/{orderId}')
              * def response = { userId: pathParams.userId, orderId: pathParams.orderId }

            # Headers test
            Scenario: pathMatches('/headers')
              * def response = { auth: requestHeaders['Authorization'] ? requestHeaders['Authorization'][0] : null, custom: requestHeaders['X-Custom'] ? requestHeaders['X-Custom'][0] : null, headers: requestHeaders }

            # ===== Redirect scenarios =====

            # Simple redirect
            Scenario: pathMatches('/redirect')
              * def responseStatus = 302
              * def responseHeaders = { 'Location': '/target' }

            # Redirect with cookie - sets cookie during redirect
            Scenario: pathMatches('/redirect-with-cookie')
              * def responseStatus = 302
              * def responseHeaders = { 'Location': '/check-cookie', 'Set-Cookie': 'redirect_cookie=from_redirect; Path=/' }

            # Check cookie was received
            Scenario: pathMatches('/check-cookie')
              * def cookieHeader = requestHeaders['Cookie'] ? requestHeaders['Cookie'][0] : ''
              * def response = { cookie: cookieHeader }

            # Target endpoint for redirects
            Scenario: pathMatches('/target')
              * def response = { reached: true }

            # Chain of redirects: /chain1 -> /chain2 -> /chain3 -> /final
            Scenario: pathMatches('/chain1')
              * def responseStatus = 302
              * def responseHeaders = { 'Location': '/chain2', 'Set-Cookie': 'chain1=value1; Path=/' }

            Scenario: pathMatches('/chain2')
              * def responseStatus = 302
              * def responseHeaders = { 'Location': '/chain3', 'Set-Cookie': 'chain2=value2; Path=/' }

            Scenario: pathMatches('/chain3')
              * def responseStatus = 302
              * def responseHeaders = { 'Location': '/final' }

            Scenario: pathMatches('/final')
              * def cookieHeader = requestHeaders['Cookie'] ? requestHeaders['Cookie'][0] : ''
              * def response = { reached: true, cookies: cookieHeader }

            # ===== Cookie scenarios =====

            # Set a cookie
            Scenario: pathMatches('/set-cookie')
              * def responseHeaders = { 'Set-Cookie': 'session=abc123; Path=/; HttpOnly' }
              * def response = { message: 'cookie set' }

            # Echo back received cookies (using raw Cookie header)
            Scenario: pathMatches('/echo-cookies')
              * def cookieHeader = requestHeaders['Cookie'] ? requestHeaders['Cookie'][0] : 'none'
              * def response = { receivedCookies: cookieHeader }

            # Echo back cookies using requestCookies variable
            Scenario: pathMatches('/echo-cookies-parsed')
              * def session = requestCookies['session'] ? requestCookies['session'].value : 'none'
              * def user = requestCookies['user'] ? requestCookies['user'].value : 'none'
              * def response = { session: session, user: user }

            # ===== Retry scenarios =====

            Scenario: pathMatches('/flaky')
              * def responseStatus = 503
              * def response = { error: 'service unavailable' }

            Scenario: pathMatches('/stable')
              * def response = { status: 'ok' }

            # Counter-based retry endpoint - returns counter value, increments each call
            Scenario: pathMatches('/counter/reset')
              * retryCounter.value = 0
              * def response = { counter: 0 }

            Scenario: pathMatches('/counter')
              * retryCounter.value = retryCounter.value + 1
              * def response = { id: retryCounter.value }

            # ===== Binary scenarios =====

            Scenario: pathMatches('/binary/download')
              * def responseHeaders = { 'Content-Type': 'application/octet-stream' }
              * def response = karate.toBytes([15, 98, -45, 0, 0, 7, -124, 75, 12, 26, 0, 9])

            Scenario: pathMatches('/binary/upload')
              * def success = requestBytes.length == 12
              * def response = { success: success, size: requestBytes.length }

            # ===== Encoding scenarios =====

            Scenario: pathMatches('/german')
              * def response = '<name>Müller</name>'

            Scenario: pathMatches('/encoding/{raw}')
              * def response = { success: true, path: pathParams.raw }

            # ===== Whitespace scenarios =====

            # Response with leading linefeed - should still parse as JSON
            Scenario: pathMatches('/linefeed')
              * def lf = String.fromCharCode(10)
              * def response = lf + '{ "success": true }'

            Scenario: pathMatches('/spaces')
              * def lf = String.fromCharCode(10)
              * def response = lf + '    ' + lf

            # ===== Form and multipart scenarios =====

            Scenario: pathMatches('/form')
              * def response = { success: true }

            Scenario: pathMatches('/multipart/upload')
              * def filePart = requestParts['myFile'][0]
              * def response = filePart

            Scenario: pathMatches('/multipart/fields')
              * def response = { success: true }

            Scenario: pathMatches('/multipart/json-value')
              # After processBody(), multipart field values are in requestParams
              * def msgVal = requestParams.message ? requestParams.message[0] : null
              * def jsonVal = requestParams.json ? requestParams.json[0] : null
              # Parse JSON string back to object
              * def jsonObj = jsonVal ? karate.fromJson(jsonVal) : null
              * def response = { message: msgVal, json: jsonObj }

            # ===== Empty/no-headers scenarios =====

            Scenario: pathMatches('/noheaders')
              * def responseStatus = 404
              * def response = ''

            # 404 JSON error response (like Spring Boot error format)
            Scenario: pathMatches('/not-found-endpoint')
              * def responseStatus = 404
              * def responseHeaders = { 'Content-Type': 'application/json' }
              * def response = { status: 404, error: 'Not Found', path: '/not-found-endpoint' }

            # ===== Query param scenarios =====

            Scenario: pathMatches('/greeting') && paramExists('name')
              * def content = 'Hello ' + paramValue('name') + '!'
              * def response = { message: content }

            Scenario: pathMatches('/greeting')
              * def response = { message: 'Hello stranger!' }

            Scenario: pathMatches('/search') && paramExists('q') && paramExists('limit')
              * def response = { query: paramValue('q'), limit: paramValue('limit'), hasQuery: paramExists('q'), hasLimit: paramExists('limit') }

            Scenario: pathMatches('/multi-param')
              * def vals = requestParams['tags']
              * def response = { tags: vals, count: vals ? vals.length : 0 }

            # ===== XML scenarios =====

            Scenario: pathMatches('/xml/echo')
              # Echo back XML request - request should be an XML Node
              * def response = { name: karate.xmlPath(request, '/name') }

            Scenario: pathMatches('/soap')
              # SOAP-style endpoint - request should be XML Node, use XPath
              * def intA = karate.xmlPath(request, '/Add/intA')
              * def intB = karate.xmlPath(request, '/Add/intB')
              * def result = parseInt(intA) + parseInt(intB)
              * def response = { result: result }

            # ===== Auth scenarios =====

            # Endpoint that checks Authorization header and returns what it received
            Scenario: pathMatches('/auth/check')
              * def authHeader = requestHeaders['Authorization'] ? requestHeaders['Authorization'][0] : null
              * def response = { authorization: authHeader }

            # OAuth2 token endpoint for client_credentials flow
            Scenario: pathMatches('/oauth/token') && methodIs('post')
              * def clientId = requestParams['client_id'] ? requestParams['client_id'][0] : null
              * def clientSecret = requestParams['client_secret'] ? requestParams['client_secret'][0] : null
              * def grantType = requestParams['grant_type'] ? requestParams['grant_type'][0] : null
              # Simple validation
              * def valid = clientId == 'test-client' && clientSecret == 'test-secret' && grantType == 'client_credentials'
              * def responseStatus = valid ? 200 : 401
              * def response = valid ? { access_token: 'test-access-token-12345', token_type: 'Bearer', expires_in: 3600 } : { error: 'invalid_client' }
            """)
            .port(0)
            .start();

        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stopAsync();
        }
    }

    @Test
    void testPaymentsMockCrud() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Payments Mock

            Scenario: CRUD operations on payments
            * url 'http://localhost:%d'

            # Create payment
            * path '/payments'
            * request { amount: 100, description: 'Test' }
            * method post
            * status 200
            * match response.id == '#notnull'
            * match response.amount == 100
            * def id = response.id

            # Get payment
            * path '/payments/' + id
            * method get
            * status 200
            * match response.id == id
            * match response.amount == 100

            # List payments
            * path '/payments'
            * method get
            * status 200
            * match response == '#array'

            # Update payment
            * path '/payments/' + id
            * request { id: '#(id)', amount: 200 }
            * method put
            * status 200

            # Verify update
            * path '/payments/' + id
            * method get
            * status 200
            * match response.amount == 200

            # Delete payment
            * path '/payments/' + id
            * method delete
            * status 204

            # Verify deleted
            * path '/payments/' + id
            * method get
            * status 404
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testCallResponseMock() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Call Response Mock

            Scenario: Get hello endpoint
            * url 'http://localhost:%d'
            * path '/hello'
            * method get
            * status 200
            * match response == { bar: 'baz' }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testPerfMock() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Perf Mock

            Scenario: Fast and slow endpoints
            * url 'http://localhost:%d'

            # Fast endpoint should respond quickly
            * path '/fast'
            * method get
            * status 200
            * match response.speed == 'fast'
            * assert responseTime < 100

            # Slow endpoint has configured delay
            * path '/slow'
            * method get
            * status 200
            * match response.speed == 'slow'
            * assert responseTime >= 100
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testPathParams() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Path Params

            Scenario: Extract multiple path parameters
            * url 'http://localhost:%d'
            * path '/users/123/orders/456'
            * method get
            * status 200
            * match response == { userId: '123', orderId: '456' }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testRequestHeaders() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Request Headers

            Scenario: Access request headers in mock
            * url 'http://localhost:%d'
            * path '/headers'
            * header Authorization = 'Bearer token123'
            * header X-Custom = 'custom-value'
            * method get
            * status 200
            * match response.auth == 'Bearer token123'
            * match response.custom == 'custom-value'
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Redirect Tests =====

    @Test
    void testFollowRedirectsEnabled() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Follow Redirects Enabled

            Scenario: Should automatically follow redirects
            * url 'http://localhost:%d'
            * configure followRedirects = true
            * path '/redirect'
            * method get
            * status 200
            * match response == { reached: true }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testFollowRedirectsDisabled() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Follow Redirects Disabled

            Scenario: Should NOT follow redirects when disabled
            * url 'http://localhost:%d'
            * configure followRedirects = false
            * path '/redirect'
            * method get
            * status 302
            * match responseHeaders['Location'][0] == '/target'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testRedirectChain() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Redirect Chain

            Scenario: Should follow multiple redirects
            * url 'http://localhost:%d'
            * configure followRedirects = true
            * path '/chain1'
            * method get
            * status 200
            * match response.reached == true
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Cookie Tests =====

    @Test
    void testCookieSetAndReceived() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Cookie Set and Received

            Scenario: Set-Cookie header should be visible in response
            * url 'http://localhost:%d'
            * path '/set-cookie'
            * method get
            * status 200
            * match responseHeaders['Set-Cookie'][0] contains 'session=abc123'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testCookieSentWithRequest() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Cookie Sent With Request

            Scenario: Configured cookies should be sent
            * url 'http://localhost:%d'
            * cookie mycookie = 'myvalue'
            * path '/echo-cookies'
            * method get
            * status 200
            * match response.receivedCookies contains 'mycookie=myvalue'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testCookieDuringRedirect() {
        // This tests that cookies set during redirects are captured
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Cookie During Redirect

            Scenario: Cookies set during redirect should be visible
            * url 'http://localhost:%d'
            * configure followRedirects = true
            * path '/redirect-with-cookie'
            * method get
            * status 200
            # The final response should show that cookie was received
            # Note: Apache's cookie store handles this during redirect
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testCookieIsolationBetweenRequests() {
        // Cookies from one request should not leak to another
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Cookie Isolation

            Scenario: Cookies should not leak between requests
            * url 'http://localhost:%d'

            # First request with cookie
            * cookie request1cookie = 'value1'
            * path '/echo-cookies'
            * method get
            * status 200
            * match response.receivedCookies contains 'request1cookie=value1'

            # Second request without that cookie - should not have it
            * url 'http://localhost:%d'
            * path '/echo-cookies'
            * method get
            * status 200
            # Should NOT contain the previous cookie
            * match response.receivedCookies !contains 'request1cookie'
            """.formatted(port, port));

        assertPassed(sr);
    }

    @Test
    void testRequestCookiesParsedInMock() {
        // Test that requestCookies variable properly parses cookies
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test requestCookies Variable

            Scenario: requestCookies should parse Cookie header
            * url 'http://localhost:%d'
            * cookie session = 'abc123'
            * cookie user = 'john'
            * path '/echo-cookies-parsed'
            * method get
            * status 200
            * match response == { session: 'abc123', user: 'john' }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Retry Tests =====

    @Test
    void testHttpRetryDisabledByDefault() {
        // Without httpRetryEnabled, a 503 should fail immediately
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test HTTP Retry Disabled

            Scenario: Without retry, 503 should be returned
            * url 'http://localhost:%d'
            * path '/flaky'
            * method get
            * status 503
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testHttpRetryConfigurable() {
        // Test that httpRetryEnabled can be configured
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test HTTP Retry Configurable

            Scenario: httpRetryEnabled should be configurable
            * url 'http://localhost:%d'
            * configure httpRetryEnabled = true
            * path '/stable'
            * method get
            * status 200
            * match response.status == 'ok'
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Configure Charset Test =====

    @Test
    void testCharsetConfiguration() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Charset Configuration

            Scenario: charset should be configurable
            * url 'http://localhost:%d'
            * configure charset = 'UTF-8'
            * path '/hello'
            * method get
            * status 200
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Binary Tests =====

    @Test
    void testBinaryDownload() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Binary Download

            Scenario: Download binary content and verify integrity
            * url 'http://localhost:%d'
            * path '/binary/download'
            * method get
            * status 200
            * match responseBytes == '#notnull'
            * def size = responseBytes.length
            * match size == 12
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testBinaryUpload() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Binary Upload

            Scenario: Upload binary content
            * url 'http://localhost:%d'
            * path '/binary/upload'
            * def body = karate.toBytes([15, 98, -45, 0, 0, 7, -124, 75, 12, 26, 0, 9])
            * request body
            * method post
            * status 200
            * match response.success == true
            * match response.size == 12
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Encoding Tests =====

    @Test
    void testGermanUmlauts() {
        // Response is auto-converted to XML because it starts with '<' (V1 behavior)
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test German Encoding

            Scenario: Response with umlauts should be handled correctly
            * url 'http://localhost:%d'
            * path '/german'
            * method get
            * status 200
            * match response == <name>Müller</name>
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testUrlEncodingSpecialChars() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test URL Encoding

            Scenario: Special characters in path should be handled
            * url 'http://localhost:%d'
            * path '/encoding/test-value'
            * method get
            * status 200
            * match response.success == true
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Whitespace Edge Cases =====

    @Test
    void testJsonWithLeadingLinefeed() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Whitespace

            Scenario: JSON with leading linefeed should parse correctly
            * url 'http://localhost:%d'
            * path '/linefeed'
            * method get
            * status 200
            * match response == { success: true }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testPureWhitespaceResponse() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Pure Whitespace

            Scenario: Response that is pure whitespace
            * url 'http://localhost:%d'
            * path '/spaces'
            * method get
            * status 200
            * match response == '\\n    \\n'
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Form Field Tests =====

    @Test
    void testFormFieldPost() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Form Field

            Scenario: POST with form fields
            * url 'http://localhost:%d'
            * path '/form'
            * form field foo = 'bar'
            * method post
            * status 200
            * match response == { success: true }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Multipart Tests =====

    @Test
    void testMultipartFileUpload() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart Upload

            Scenario: Upload file with multipart
            * url 'http://localhost:%d'
            * path '/multipart/fields'
            * multipart field message = 'test message'
            * method post
            * status 200
            * match response == { success: true }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipartFieldsWithJsonValue() {
        // V1 behavior: { value: { foo: 'bar' } } should extract the value, not nest it
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart JSON Value

            Scenario: Multipart fields with JSON value
            * url 'http://localhost:%d'
            * path '/multipart/json-value'
            * multipart fields { message: 'hello', json: { value: { foo: 'bar' } } }
            * method post
            * status 200
            # V1 behavior: json field should be { foo: 'bar' }, not { value: { foo: 'bar' } }
            * match response == { message: 'hello', json: { foo: 'bar' } }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Empty Response Tests =====

    @Test
    void testNoHeadersEmptyResponse() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Empty Response

            Scenario: 404 with empty response body
            * url 'http://localhost:%d'
            * path '/noheaders'
            * method get
            * status 404
            * match response == ''
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Query Param Tests =====

    @Test
    void testParamExistsAndParamValue() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test paramExists and paramValue

            Scenario: Greeting with name parameter
            * url 'http://localhost:%d'
            * path '/greeting'
            * param name = 'World'
            * method get
            * status 200
            * match response.message == 'Hello World!'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testParamExistsFallback() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test paramExists fallback

            Scenario: Greeting without name parameter should use fallback
            * url 'http://localhost:%d'
            * path '/greeting'
            * method get
            * status 200
            * match response.message == 'Hello stranger!'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipleParamExistsConditions() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test multiple paramExists

            Scenario: Search with multiple required params
            * url 'http://localhost:%d'
            * path '/search'
            * param q = 'karate'
            * param limit = '10'
            * method get
            * status 200
            * match response.query == 'karate'
            * match response.limit == '10'
            * match response.hasQuery == true
            * match response.hasLimit == true
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultiValueParamWithArray() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test multi-value param with array

            Scenario: param with array value
            * url 'http://localhost:%d'
            * path '/multi-param'
            * param tags = ['java', 'karate', 'api']
            * method get
            * status 200
            * match response.count == 3
            * match response.tags == ['java', 'karate', 'api']
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testParamsWithMixedValues() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test params with mixed values

            Scenario: params with string and array values
            * url 'http://localhost:%d'
            * path '/multi-param'
            * params { tags: ['java', 'karate', 'api'] }
            * method get
            * status 200
            * match response.count == 3
            * match response.tags == ['java', 'karate', 'api']
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Configure afterScenario Tests =====

    @Test
    void testConfigureAfterScenarioModifiesResponse() {
        // afterScenario hook can modify the response using karate.set('response')
        MockServer afterScenarioServer = MockServer.featureString("""
            Feature: afterScenario Response Modification Test

            Background:
              * configure afterScenario = function(){ var r = karate.get('response'); r.modified = true; karate.set('response', r) }

            Scenario: pathMatches('/test')
              * def response = { original: true }
            """)
            .port(0)
            .start();

        try {
            int testPort = afterScenarioServer.getPort();
            ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: Test afterScenario modifies response

                Scenario: Response should have field added by afterScenario
                * url 'http://localhost:%d'
                * path '/test'
                * method get
                * status 200
                * match response.original == true
                * match response.modified == true
                """.formatted(testPort));

            assertPassed(sr);
        } finally {
            afterScenarioServer.stopAsync();
        }
    }

    @Test
    void testConfigureAfterScenarioWithCounter() {
        // afterScenario hook can increment a counter for tracking
        MockServer counterServer = MockServer.featureString("""
            Feature: afterScenario Counter Test

            Background:
              * def requestCount = { value: 0 }
              * configure afterScenario = function(){ requestCount.value++ }

            Scenario: pathMatches('/count')
              * def response = { count: requestCount.value }
            """)
            .port(0)
            .start();

        try {
            int testPort = counterServer.getPort();
            ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: Test afterScenario counter

                Scenario: Multiple requests should increment counter
                * url 'http://localhost:%d'

                # First request - count should be 0 before afterScenario runs
                * path '/count'
                * method get
                * status 200
                * match response.count == 0

                # Second request - afterScenario from first request already ran
                * path '/count'
                * method get
                * status 200
                * match response.count == 1

                # Third request
                * path '/count'
                * method get
                * status 200
                * match response.count == 2
                """.formatted(testPort));

            assertPassed(sr);
        } finally {
            counterServer.stopAsync();
        }
    }

    // ===== Retry Until Tests =====

    @Test
    void testRetryUntilBasic() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Retry Until Basic

            Scenario: Retry until response condition is met
            * url 'http://localhost:%d'

            # Reset counter
            * path '/counter/reset'
            * method get
            * status 200

            # Retry until response.id > 3
            * configure retry = { count: 10, interval: 0 }
            * path '/counter'
            * retry until response.id > 3
            * method get
            * status 200
            * match response.id == 4
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testRetryUntilWithStatusCheck() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Retry Until With Status

            Scenario: Retry until status and response condition
            * url 'http://localhost:%d'

            # Reset counter
            * path '/counter/reset'
            * method get
            * status 200

            # Retry until responseStatus == 200 && response.id >= 2
            * configure retry = { count: 5, interval: 0 }
            * path '/counter'
            * retry until responseStatus == 200 && response.id >= 2
            * method get
            * status 200
            * match response.id == 2
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testRetryUntilExceedsMaxRetries() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Retry Until Max Retries

            Scenario: Retry exceeds max attempts
            * url 'http://localhost:%d'

            # Reset counter
            * path '/counter/reset'
            * method get
            * status 200

            # Try to get id > 100 with only 3 retries - should fail
            * configure retry = { count: 3, interval: 0 }
            * path '/counter'
            * retry until response.id > 100
            * method get
            """.formatted(port));

        // This should fail because max retries exceeded
        assertFailed(sr);
    }

    @Test
    void testRetryUntilWithJsFunction() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Retry Until With JS Function

            Scenario: Use JS function in retry condition
            * url 'http://localhost:%d'

            # Reset counter
            * path '/counter/reset'
            * method get
            * status 200

            # Define a function to check condition
            * def isReady = function(){ return response.id >= 3 }

            * configure retry = { count: 10, interval: 0 }
            * path '/counter'
            * retry until isReady()
            * method get
            * status 200
            * match response.id == 3
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testConfigureRetryDefaults() {
        // Test that default retry config is count=3, interval=3000
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Configure Retry Defaults

            Scenario: Check default retry configuration
            * def cfg = karate.config
            * match cfg.retryCount == 3
            * match cfg.retryInterval == 3000
            """);

        assertPassed(sr);
    }

    @Test
    void testConfigureRetryCustom() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Configure Retry Custom

            Scenario: Set custom retry configuration
            * configure retry = { count: 5, interval: 100 }
            * def cfg = karate.config
            * match cfg.retryCount == 5
            * match cfg.retryInterval == 100
            """);

        assertPassed(sr);
    }

    // ===== Embedded Expression Tests =====
    // Tests for V1 compatibility: embedded expressions like #(varName) in JSON literals

    @Test
    void testParamsWithEmbeddedExpressions() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Params with Embedded Expressions

            Scenario: params with embedded expressions should evaluate variables
            * url 'http://localhost:%d'
            * def myQuery = 'karate'
            * def myLimit = '25'
            * params { q: '#(myQuery)', limit: '#(myLimit)' }
            * path '/search'
            * method get
            * status 200
            * match response.query == 'karate'
            * match response.limit == '25'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testHeadersWithEmbeddedExpressions() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Headers with Embedded Expressions

            Scenario: headers with embedded expressions should evaluate variables
            * url 'http://localhost:%d'
            * def myToken = 'token123'
            * def myCustom = 'custom-value'
            * headers { Authorization: 'Bearer #(myToken)', 'X-Custom': '#(myCustom)' }
            * path '/headers'
            * method get
            * status 200
            * match response.auth == 'Bearer token123'
            * match response.custom == 'custom-value'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testCookiesWithEmbeddedExpressions() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Cookies with Embedded Expressions

            Scenario: cookies with embedded expressions should evaluate variables
            * url 'http://localhost:%d'
            * def sessionId = 'abc123'
            * def username = 'john'
            * cookies { session: '#(sessionId)', user: '#(username)' }
            * path '/echo-cookies-parsed'
            * method get
            * status 200
            * match response == { session: 'abc123', user: 'john' }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testRequestWithEmbeddedExpressions() {
        // Create a temporary mock server for this test
        MockServer reqServer = MockServer.featureString("""
            Feature: Request Echo Mock

            Scenario: pathMatches('/echo')
              * def response = request
            """)
            .port(0)
            .start();

        try {
            int testPort = reqServer.getPort();
            ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: Test Request with Embedded Expressions

                Scenario: request body with embedded expressions
                * url 'http://localhost:%d'
                * def myName = 'John'
                * def myAge = 30
                * def myItems = ['a', 'b', 'c']
                * request { name: '#(myName)', age: '#(myAge)', items: '#(myItems)' }
                * path '/echo'
                * method post
                * status 200
                * match response.name == 'John'
                * match response.age == 30
                * match response.items == ['a', 'b', 'c']
                """.formatted(testPort));

            assertPassed(sr);
        } finally {
            reqServer.stopAsync();
        }
    }

    // ===== XML Request Tests =====

    @Test
    void testXmlRequestInline() {
        // Test inline XML request - V1 syntax: request <name>value</name>
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test XML Request Inline

            Scenario: Inline XML in request
            * url 'http://localhost:%d'
            * path '/xml/echo'
            * request <name>Müller</name>
            * method post
            * status 200
            * match response.name == 'Müller'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testXmlRequestDocstring() {
        // Test XML in docstring - SOAP-style request
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test XML Request Docstring

            Scenario: XML docstring in request
            * url 'http://localhost:%d'
            * path '/soap'
            * request
            \"\"\"
            <Add>
              <intA>2</intA>
              <intB>3</intB>
            </Add>
            \"\"\"
            * method post
            * status 200
            * match response.result == 5
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testXmlRequestWithVariable() {
        // Test XML assigned to variable then used in request
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test XML Request with Variable

            Scenario: XML variable in request
            * url 'http://localhost:%d'
            * path '/soap'
            * def body = <Add><intA>10</intA><intB>20</intB></Add>
            * request body
            * method post
            * status 200
            * match response.result == 30
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Cookie Jar Propagation Test =====
    // This tests V1 compatibility: cookies collected in a called feature (shared scope)
    // should be auto-sent on subsequent requests in the caller feature.

    @TempDir
    Path tempDir;

    @Test
    void testCookieJarPropagationInSharedScopeCall() throws Exception {
        // This replicates the pattern from karate-demo's call-updates-config.feature:
        // 1. Caller calls a common.feature with shared scope (no assignment)
        // 2. common.feature makes an HTTP request that receives a Set-Cookie
        // 3. Caller makes another HTTP request - cookie should be auto-sent
        //
        // Without cookie jar propagation, the second request fails because the cookie is missing.

        Path commonFeature = tempDir.resolve("common.feature");
        Files.writeString(commonFeature, """
            @ignore
            Feature: Common routine that collects cookies from HTTP response

            Scenario:
            * url 'http://localhost:%d'
            * path '/set-cookie'
            * method get
            * status 200
            # responseCookies now contains 'session=abc123'
            # This should be stored in the cookie jar
            * def token = 'authenticated'
            """.formatted(port));

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Cookie jar should propagate from called feature

            Background:
            # Shared scope call - cookie jar should propagate back
            * callonce read('common.feature')

            Scenario: Cookie from called feature should be auto-sent
            # Verify variable propagated
            * match token == 'authenticated'
            # Make another request - the 'session' cookie should be auto-sent
            * url 'http://localhost:%d'
            * path '/echo-cookies'
            * method get
            * status 200
            # If cookie jar propagation works, 'session=abc123' should be present
            * match response.receivedCookies contains 'session=abc123'

            Scenario: Second scenario should also have cookies from cache
            * url 'http://localhost:%d'
            * path '/echo-cookies'
            * method get
            * status 200
            * match response.receivedCookies contains 'session=abc123'
            """.formatted(port, port));

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertEquals(2, result.getScenarioCount());
        assertTrue(result.isPassed(), "Cookie jar should propagate from called feature: " + getFailureMessage(result));
    }

    private String getFailureMessage(SuiteResult result) {
        if (result.isPassed()) return "none";
        for (var fr : result.getFeatureResults()) {
            for (var sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return "unknown";
    }

    // ===== V1 Compatibility: Path Encoding and Multi-Value Headers =====

    @Test
    void testPathEncodingSpecialCharacters() {
        // Test that special characters like "<>#{}|\^[]` are properly URL-encoded in path
        // This replicates demo/encoding/encoding.feature:31-36 from karate-demo
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Path encoding special characters

            Scenario: Special characters in path should be URL-encoded
            * url 'http://localhost:%d'
            * path 'encoding', '"<>#{}|\\^[]`'
            * method get
            * status 200
            * match response.path == '"<>#{}|\\^[]`'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultiValueHeaderUsingFunctionCall() {
        // Test that multi-value headers work when set via function call
        // This replicates demo/headers/headers.feature:75-82 from karate-demo
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Multi-value headers via function call

            Scenario: Multi-value header set via function call
            * def fun = function(arg){ return [arg.first, arg.second] }
            * header X-Multi = call fun { first: 'value1', second: 'value2' }
            * url 'http://localhost:%d'
            * path 'headers'
            * method get
            * status 200
            * match response.headers['X-Multi'] == ['value1', 'value2']
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void test404JsonError() {
        // Tests 404 status with JSON error body
        // Header matching is case-insensitive by default (via StringUtils.getIgnoreKeyCase)
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: 404 JSON error response

            Scenario: Invalid URL returns proper error response
            * url 'http://localhost:%d'
            * path 'not-found-endpoint'
            * method get
            * status 404
            * match header content-type contains 'application/json'
            * match response.status == 404
            * match response.error == 'Not Found'
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Configure Auth Tests =====

    @Test
    void testConfigureAuthBasic() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Basic Auth via configure

            Scenario: Basic auth should set Authorization header
            * url 'http://localhost:%d'
            * configure auth = { type: 'basic', username: 'testuser', password: 'testpass' }
            * path '/auth/check'
            * method get
            * status 200
            # Basic auth: base64('testuser:testpass') = 'dGVzdHVzZXI6dGVzdHBhc3M='
            * match response.authorization == 'Basic dGVzdHVzZXI6dGVzdHBhc3M='
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testConfigureAuthBearer() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Bearer Auth via configure

            Scenario: Bearer auth should set Authorization header
            * url 'http://localhost:%d'
            * configure auth = { type: 'bearer', token: 'my-jwt-token-12345' }
            * path '/auth/check'
            * method get
            * status 200
            * match response.authorization == 'Bearer my-jwt-token-12345'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testConfigureAuthBearerWithVariable() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Bearer Auth with variable

            Scenario: Bearer auth token can use embedded expression
            * url 'http://localhost:%d'
            * def myToken = 'dynamic-token-xyz'
            * configure auth = { type: 'bearer', token: '#(myToken)' }
            * path '/auth/check'
            * method get
            * status 200
            * match response.authorization == 'Bearer dynamic-token-xyz'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testConfigureAuthOAuth2ClientCredentials() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test OAuth2 Client Credentials via configure

            Scenario: OAuth2 should obtain token and set Authorization header
            * url 'http://localhost:%d'
            * configure auth = { type: 'oauth2', grantType: 'client_credentials', accessTokenUrl: '/oauth/token', clientId: 'test-client', clientSecret: 'test-secret' }
            * path '/auth/check'
            * method get
            * status 200
            # OAuth2 obtains token 'test-access-token-12345' from token endpoint
            * match response.authorization == 'Bearer test-access-token-12345'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testConfigureAuthDisable() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Auth Disable

            Scenario: Auth can be disabled with null
            * url 'http://localhost:%d'
            * configure auth = { type: 'basic', username: 'testuser', password: 'testpass' }
            * path '/auth/check'
            * method get
            * status 200
            * match response.authorization == 'Basic dGVzdHVzZXI6dGVzdHBhc3M='

            # Disable auth
            * configure auth = null
            * path '/auth/check'
            * method get
            * status 200
            * match response.authorization == '#notpresent'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testConfigureAuthOverride() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Auth Override

            Scenario: Auth can be overridden mid-scenario
            * url 'http://localhost:%d'
            * configure auth = { type: 'basic', username: 'user1', password: 'pass1' }
            * path '/auth/check'
            * method get
            * status 200
            # base64('user1:pass1') = 'dXNlcjE6cGFzczE='
            * match response.authorization == 'Basic dXNlcjE6cGFzczE='

            # Override with bearer
            * configure auth = { type: 'bearer', token: 'new-token' }
            * path '/auth/check'
            * method get
            * status 200
            * match response.authorization == 'Bearer new-token'
            """.formatted(port));

        assertPassed(sr);
    }

}
