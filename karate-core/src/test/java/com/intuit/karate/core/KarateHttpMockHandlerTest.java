package com.intuit.karate.core;

import static com.intuit.karate.TestUtils.*;
import static com.intuit.karate.TestUtils.runScenario;
import com.intuit.karate.http.HttpServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class KarateHttpMockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(KarateHttpMockHandlerTest.class);

    MockHandler handler;
    HttpServer server;
    FeatureBuilder mock;
    ScenarioRuntime runtime;

    String urlStep() {
        return "url 'http://localhost:" + server.getPort() + "'";
    }

    void startMockServer() {
        handler = new MockHandler(mock.build());
        server = HttpServer.handler(handler).build();
    }

    FeatureBuilder background(String... lines) {
        mock = FeatureBuilder.background(lines);
        return mock;
    }

    Object get(String name) {
        return runtime.engine.vars.get(name).getValue();
    }

    ScenarioRuntime run(String... lines) {
        runtime = runScenario(null, lines);
        return runtime;
    }

    private void matchVar(String name, Object expected) {
        match(get(name), expected);
    }

    private void matchVarContains(String name, Object expected) {
        matchContains(get(name), expected);
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
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "method get"
        );
        matchVar("response", "hello world");
    }

    @Test
    void testUrlWithTrailingSlashAndPath() {
        background().scenario(
                "pathMatches('/hello/world')",
                "def response = requestUri");
        startMockServer();
        run(
                "url 'http://localhost:" + server.getPort() + "/hello'",
                "path '/world/'",
                "method get",
                "match response == 'hello/world'"
        );
        matchVar("response", "hello/world");
    }

    @Test
    void testRequestBodyAsInteger() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(),
                "path '/hello'",
                "request 42",
                "method post"
        );
        // for some reason, apache assumes this encoding if the request body is raw bytes TODO
        matchVarContains("response", "{ 'content-type': ['application/octet-stream'] }");
    }

    @Test
    void testThatCookieIsPartOfRequest() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "cookie foo = 'bar'",
                "method get"
        );
        matchVarContains("response", "{ cookie: ['foo=bar'] }");
    }

    @Test
    void testSameSiteSecureCookieRequest() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "cookie foo = { value: 'bar', samesite: 'Strict', secure: true }",
                "method get"
        );
        matchVarContains("response", "{ cookie: ['foo=bar'] }");
    }

    @Test
    void testSameSiteSecureCookieResponse() {
        background().scenario(
                "pathMatches('/hello')",
                "def responseHeaders = { 'Set-Cookie': 'foo=bar; expires=Wed, 30-Dec-20 09:25:45 GMT; path=/; domain=.example.com; HttpOnly; SameSite=Lax; Secure' }");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "method get"
        );
        matchVarContains("responseHeaders", "{ set-cookie: ['foo=bar; expires=Wed, 30-Dec-20 09:25:45 GMT; path=/; domain=.example.com; HttpOnly; SameSite=Lax; Secure'] }");
    }

    @Test
    void testMultipleCookies() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "cookie cookie1 = 'foo'",
                "cookie cookie2 = 'bar'",
                "method get"
        );
        matchVarContains("response", "{ cookie: ['cookie1=foo; cookie2=bar'] }");
    }

    @Test
    void testThatExoticContentTypeIsPreserved() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "header Content-Type = 'application/xxx.pingixxxxxx.checkUsernamePassword+json'",
                "method post"
        );
        matchVarContains("response", "{ 'content-type': ['application/xxx.pingixxxxxx.checkUsernamePassword+json'] }");
    }

    @Test
    void testInspectRequestInHeadersFunction() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(),
                "configure headers = function(request){ return { 'api-key': request.bodyAsString } }",
                "path 'hello'",
                "request 'some text'",
                "method post"
        );
        matchVarContains("response", "{ 'api-key': ['some text'] }");
    }

    @Test
    void testKarateRemove() {
        background().scenario(
                "pathMatches('/hello/{id}')",
                "def temp = { '1': 'foo', '2': 'bar' }",
                "karate.remove('temp', pathParams.id)",
                "def response = temp");
        startMockServer();
        run(
                urlStep(),
                "path 'hello', '1'",
                "method get"
        );
        matchVarContains("response", "{ '2': 'bar' }");
    }

    @Test
    void testTransferEncoding() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = request");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "header Transfer-Encoding = 'chunked'",
                "request { foo: 'bar' }",
                "method post"
        );
        matchVarContains("response", "{ foo: 'bar' }");
    }

    @Test
    void testMalformedMockResponse() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = '{ \"id\" \"123\" }'");
        startMockServer();
        run(
                urlStep(),
                "path 'hello'",
                "method get",
                "match response == '{ \"id\" \"123\" }'",
                "match responseType == 'string'"
        );
        Object response = get("response");
        assertEquals(response, "{ \"id\" \"123\" }");
    }

    @Test
    void testRedirectAfterPostWithCookie() {
        background()
                .scenario("pathMatches('/first')",
                        "def responseHeaders = { 'Set-Cookie': 'foo1=bar1', Location: '/second' }",
                        "def responseStatus = 302")
                .scenario("pathMatches('/second')",
                        "def response = requestHeaders",
                        "def responseHeaders = { 'Set-Cookie': 'foo2=bar2' }");
        startMockServer();
        run(
                urlStep(),
                "path 'first'",
                "form fields { username: 'blah', password: 'blah' }",
                "method post"
        );
        matchVarContains("response", "{ cookie: ['foo1=bar1'] }");
        Map<String, Object> map = (Map) get("responseHeaders");
        List<String> list = (List) map.get("Set-Cookie");
        matchContains(list, "['foo1=bar1; Domain=localhost', 'foo2=bar2; Domain=localhost']");
    }

}
