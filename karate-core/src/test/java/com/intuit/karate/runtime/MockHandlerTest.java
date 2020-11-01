package com.intuit.karate.runtime;

import com.intuit.karate.data.Json;
import com.intuit.karate.server.*;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import java.util.Map;
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
       
    HttpClient client = new DummyClient();
    MockHandler handler;
    FeatureBuilder feature;
    HttpRequestBuilder request;
    Response response;

    @BeforeEach
    void beforeEach() {
        request = new HttpRequestBuilder(client).method("GET");
    }

    FeatureBuilder background(String... lines) {
        feature = FeatureBuilder.background(lines);
        return feature;
    }

    private Response handle() {
        handler = new MockHandler(feature.build());
        response = handler.handle(request.build().toRequest());
        request = new HttpRequestBuilder(client).method("GET");
        return response;
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    private Map<String, Object> json(String raw) {
        return new Json(raw).asMap();
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

    @Test
    void testMultiPart() {
        background().scenario(
                "pathMatches('/hello')",
                "def foo = paramValue('foo')",
                "string bar = requestFiles.bar[0].value",
                "def response = { foo: '#(foo)', bar: '#(bar)' }"
        );
        request.path("/hello")
                .multiPart(json("{ name: 'foo', value: 'hello world' }"))
                .multiPart(json("{ name: 'bar', value: 'some bytes', filename: 'bar.txt' }"))
                .method("POST");
        handle();
        match(response.getBodyConverted(), "{ foo: 'hello world', bar: 'some bytes' }");
    }
    
    @Test
    void testAbort() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'before'",
                "karate.abort()",
                "def response = 'after'"
        );
        request.path("/hello");
        handle();
        match(response.getBodyAsString(), "before");
    }    
    
    @Test
    void testUrlWithSpecialCharacters() {
        background().scenario(
                "pathMatches('/hello/{raw}')",
                "def response = pathParams.raw"
        );
        request.path("/hello/�Ill~Formed@RequiredString!");
        handle();
        match(response.getBodyAsString(), "�Ill~Formed@RequiredString!");        
    }

}
