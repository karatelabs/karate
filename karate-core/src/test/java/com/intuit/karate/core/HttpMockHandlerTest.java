package com.intuit.karate.core;

import com.intuit.karate.Constants;
import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.http.ApacheHttpClient;
import com.intuit.karate.http.HttpClientFactory;
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
class HttpMockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(HttpMockHandlerTest.class);

    MockHandler handler;
    HttpServer server;
    FeatureBuilder mock;
    HttpRequestBuilder http;
    Response response;

    HttpRequestBuilder handle() {
        handler = new MockHandler(mock.build());
        server = HttpServer.handler(handler).build();
        ScenarioEngine se = ScenarioEngine.forTempUse(HttpClientFactory.DEFAULT);
        ApacheHttpClient client = new ApacheHttpClient(se);
        http = new HttpRequestBuilder(client);
        http.url("http://localhost:" + server.getPort());
        return http;
    }

    HttpRequestBuilder handleWithOriginalHeaders() {
        handler = new MockHandler(mock.build());
        server = HttpServer.handler(handler).keepOriginalHeaders(true).build();
        ScenarioEngine se = ScenarioEngine.forTempUse(HttpClientFactory.DEFAULT);
        ApacheHttpClient client = new ApacheHttpClient(se);
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
    void testSimpleGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello world'");
        response = handle().path("/hello").invoke("get");
        match(response.getBodyAsString(), "hello world");
    }

    @Test
    void testUrlWithSpecialCharacters() {
        background().scenario(
                "pathMatches('/hello/{raw}')",
                "def response = { success: true }"
        );
        response = handle().path("/hello/�Ill~Formed@RequiredString!").invoke("get");
        match(response.getBodyConverted(), "{ success: true }");
    }

    @Test
    void testGraalJavaClassLoading() {
        background().scenario(
                "pathMatches('/hello')",
                "def Utils = Java.type('com.intuit.karate.core.MockUtils')",
                "def response = Utils.testBytes"
        );
        response = handle().path("/hello").invoke("get");
        match(response.getBody(), MockUtils.testBytes);
    }

    @Test
    void testEmptyResponse() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = null"
        );
        response = handle().path("/hello").invoke("get");
        match(response.getBody(), Constants.ZERO_BYTES);
    }

    @Test
    void testConfigureResponseHeaders() {
        background("configure responseHeaders = { 'Content-Type': 'text/html' }")
                .scenario(
                        "pathMatches('/hello')",
                        "def response = ''");
        response = handle().path("/hello").invoke("get");
        match(response.getHeader("Content-Type"), "text/html");
    }

    @Test
    void testKeepOriginalResponseHeaders() {
        background("configure responseHeaders = { 'X-Special-Header': 'test1' }")
                .scenario(
                        "pathMatches('/hello')",
                        "def responseHeaders = { 'X-Custom-Header': 'test2' }",
                        "def response = ''");
        response = handleWithOriginalHeaders().path("/hello").invoke("get");
        match(response.getHeader("X-Special-Header"), "test1");
        match(response.getHeader("X-Custom-Header"), "test2");

        matchContains(response.getHeaders().keySet(), "X-Special-Header");
        matchContains(response.getHeaders().keySet(), "X-Custom-Header");
    }

    @Test
    void testResponseHavingForwardSlashes() {
        background()
                .scenario(
                        "pathMatches('/hello')",
                        "def response = { 'url': 'https://test123' }");
        response = handle().path("/hello").invoke("get");
        match(response.getBody(), "{\"url\":\"https://test123\"}".getBytes());
    }
}
