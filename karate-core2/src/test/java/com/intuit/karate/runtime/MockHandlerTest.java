package com.intuit.karate.runtime;

import com.intuit.karate.data.Json;
import com.intuit.karate.server.*;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(MockHandlerTest.class);

    MockHandler handler;
    FeatureBuilder feature;
    HttpRequestBuilder request;
    Response response;

    @BeforeEach
    void beforeEach() {
        request = new HttpRequestBuilder(null).method("GET");
    }

    FeatureBuilder background(String... lines) {
        feature = FeatureBuilder.background(lines);
        return feature;
    }

    private Response handle() {
        handler = new MockHandler(feature.build());
        response = handler.handle(request.build().toRequest());
        request = new HttpRequestBuilder(null).method("GET");
        return response;
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    private Object json(String raw) {
        return new Json(raw).asMapOrList();
    }

    @Test
    void testSimpleResponse() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello world'"
        );
        request.path("/hello");
        handle();
        match(response.getBodyAsString(), "hello world");
    }

    @Test
    void testRequestMethod() {
        background().scenario(
                "pathMatches('/hello')",
                "def isPost = methodIs('post')",
                "def method = requestMethod",
                "def response = { isPost: '#(isPost)', method: '#(method)' }"
        );
        request.path("/hello").method("POST");
        handle();
        match(response.getBodyConverted(), "{ isPost: true, method: 'POST' }");
    }

    @Test
    void testPathParams() {
        background().scenario(
                "pathMatches('/hello/{name}')",
                "def response = 'hello ' + pathParams.name"
        );
        request.path("/hello/john");
        handle();
        match(response.getBodyAsString(), "hello john");
    }

    @Test
    void testQueryParams() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello ' + paramValue('foo')"
        );
        request.path("/hello").param("foo", "world");
        handle();
        match(response.getBodyAsString(), "hello world");
    }

    @Test
    void testFormFieldsRequestPost() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = request"
        );
        request.path("/hello").formField("foo", "hello world").method("POST");
        handle();
        match(response.getBodyAsString(), "foo=hello+world");
    }

    @Test
    void testFormFieldsRequestGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def exists = paramExists('foo')",
                "def value = paramValue('foo')",
                "def response = { exists: '#(exists)', value: '#(value)' }"
        );
        request.path("/hello").formField("foo", "hello world").method("GET");
        handle();
        match(response.getBodyConverted(), "{ exists: true, value: 'hello world' }");
    }

    @Test
    void testTypeContains() {
        background().scenario(
                "pathMatches('/hello') && typeContains('json')",
                "def response = { success: true }"
        );
        request.path("/hello").contentType("application/json").method("GET");
        handle();
        match(response.getBodyConverted(), "{ success: true }");
    }

    @Test
    void testAcceptContains() {
        background().scenario(
                "pathMatches('/hello') && acceptContains('json')",
                "def response = requestHeaders"
        );
        request.path("/hello").header("accept", "application/json").method("GET");
        handle();
        match(response.getBodyConverted(), "{ accept: ['application/json'] }");
    }

    @Test
    void testHeaderContains() {
        background().scenario(
                "pathMatches('/hello') && headerContains('foo', 'bar')",
                "def response = { success: true }"
        );
        request.path("/hello").header("foo", "baabarbaa").method("GET");
        handle();
        match(response.getBodyConverted(), "{ success: true }");
    }

    @Test
    void testRequestHeaders() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders"
        );
        request.path("/hello").header("foo", "bar").method("GET");
        handle();
        match(response.getBodyConverted(), "{ foo: ['bar'] }");
    }

    @Test
    void testBodyPath() {
        background().scenario(
                "pathMatches('/hello') && bodyPath('$.foo') == 'bar'",
                "def response = { success: true }"
        );
        request.path("/hello").body(json("{ foo: 'bar' }"));
        handle();
        match(response.getBodyConverted(), "{ success: true }");
    }

    @Test
    void testResponseStatus() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = { success: false }",
                "def responseStatus = 404"
        );
        request.path("/hello");
        handle();
        match(response.getBodyConverted(), "{ success: false }");
        match(response.getStatus(), 404);
    }

    @Test
    void testResponseHeaders() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = { success: false }",
                "def responseHeaders = { foo: 'bar' }"
        );
        request.path("/hello");
        handle();
        match(response.getBodyConverted(), "{ success: false }");
        match(response.getHeader("foo"), "bar");
    }

}
