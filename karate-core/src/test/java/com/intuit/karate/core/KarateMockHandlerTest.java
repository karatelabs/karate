package com.intuit.karate.core;

import static com.intuit.karate.TestUtils.*;
import static com.intuit.karate.TestUtils.runScenario;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 *
 * @author pthomas3
 */
class KarateMockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(KarateMockHandlerTest.class);

    String URL_STEP = "url 'http://localhost:8080'";
    MockHandler handler;
    FeatureBuilder mock;
    ScenarioRuntime runtime;
    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss z");

    FeatureBuilder background(String... lines) {
        mock = FeatureBuilder.background(lines);
        return mock;
    }

    Object get(String name) {
        return runtime.engine.vars.get(name).getValue();
    }

    ScenarioRuntime run(String... lines) {
        handler = new MockHandler(mock.build());
        MockClient client = new MockClient(handler);
        runtime = runScenario(e -> client, lines);
        assertFalse(runtime.isFailed(), runtime.result.getFailureMessageForDisplay());
        return runtime;
    }

    private void matchVar(String name, Object expected) {
        match(get(name), expected);
    }

    @Test
    void testSimpleGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello world'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get"
        );
        matchVar("response", "hello world");
    }

    @Test
    void testSimplePost() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "path 'hello'",
                "request { foo: 'bar' }",
                "method post"
        );
        matchVar("response", "{ 'Content-Type': ['application/json; charset=UTF-8'] }");
    }

    @Test
    void testPathSubstitution() {
        background().scenario(
                "pathMatches('/hello/{id}')",
                "def response = pathParams");
        run(
                URL_STEP,
                "def id = 42",
                "path 'hello', id",
                "method get"
        );
        matchVar("response", "{ id: '42' }");
    }

    @Test
    void testParam() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "param foo = 'bar'",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }

    @Test
    void testParams() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "params { foo: 'bar' }",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }

    @Test
    void testParamWithEmbeddedCommas() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "param foo = 'bar,baz'",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar,baz'] }");
    }

    @Test
    void testParamMultiValue() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "param foo = ['bar', 'baz']",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar', 'baz'] }");
    }

    @Test
    void testRequestBodyAsInteger() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = request");
        run(
                URL_STEP,
                "path '/hello'",
                "request 42",
                "method post"
        );
        matchVar("response", "42");
    }

    @Test
    void testHeaders() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "path 'hello'",
                "header foo = 'bar'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }

    @Test
    void testHeaderMultiValue() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "path 'hello'",
                "def fun = function(arg){ return [arg.first, arg.second] }",
                "header Authorization = call fun { first: 'foo', second: 'bar' }",
                "method get"
        );
        matchVar("response", "{ Authorization: ['foo', 'bar'] }");
    }

    @Test
    void testRequestContentTypeForJson() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "path 'hello'",
                "request { foo: 'bar' }",
                "method post"
        );
        matchVar("response", "{ 'Content-Type': ['application/json; charset=UTF-8'] }");
    }

    @Test
    void testResponseContentTypeForJson() {
        background().scenario(
                "pathMatches('/hello')",
                "def responseHeaders = { 'Content-Type': 'application/json' }",
                "def response = '{ \"foo\": \"bar\"}'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get",
                "match responseHeaders == { 'Content-Type': ['application/json'] }",
                "match header content-type == 'application/json'",
                "match responseType == 'json'"
        );
    }

    @Test
    void testCookie() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = 'bar'",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ Cookie: ['foo=bar'] }");
    }

    @Test
    void testCookieWithDateInThePast() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(java.util.Calendar.DATE, -1);
        String pastDate = sdf.format(calendar.getTime());
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = {value:'bar', expires: '" + pastDate + "'}",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ Cookie: ['foo=bar'] }");
    }

    @Test
    void testCookieWithDateInTheFuture() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(java.util.Calendar.DATE, +1);
        String futureDate = sdf.format(calendar.getTime());
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = { value: 'bar', expires: '" + futureDate + "' }",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ Cookie: ['foo=bar'] }");
    }

    @Test
    void testCookieWithMaxAgeZero() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = { value: 'bar', max-age: '0' }",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ Cookie: ['#string'] }");
    }

    @Test
    void testFormFieldGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "form field foo = 'bar'",
                "path 'hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }

    @Test
    void testFormFieldPost() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = request");
        run(
                URL_STEP,
                "form field foo = 'bar'",
                "path 'hello'",
                "method post"
        );
        matchVar("response", "foo=bar");
    }

    @Test
    void testMultiPartField() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "multipart field foo = 'bar'",
                "path 'hello'",
                "method post"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }

    @Test
    void testMultiPartFile() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParts");
        run(
                URL_STEP,
                "multipart file foo = { filename: 'foo.txt', value: 'hello' }",
                "path 'hello'",
                "method post"
        );
        matchVar("response", "{ foo: [{ name: 'foo', value: '#notnull', contentType: 'text/plain', charset: 'UTF-8', filename: 'foo.txt', transferEncoding: '7bit' }] }");
    }

    @Test
    void testConfigureResponseHeaders() {
        background("configure responseHeaders = { 'Content-Type': 'text/html' }")
                .scenario(
                        "pathMatches('/hello')",
                        "def response = ''");
        run(
                URL_STEP,
                "path 'hello'",
                "method get"
        );
        matchVar("responseHeaders", "{ 'Content-Type': ['text/html'] }");
    }

    @Test
    void testConfigureLowerCaseResponseHeaders() {
        background().scenario(
                "pathMatches('/hello')",
                "def responseHeaders = { 'Content-Type': 'text/html' }",
                "def response = ''");
        run(
                "configure lowerCaseResponseHeaders = true",
                URL_STEP,
                "path 'hello'",
                "method get"
        );
        matchVar("responseHeaders", "{ 'content-type': ['text/html'] }");
    }

    @Test
    void testResponseContentTypeForXml() {
        background().scenario(
                "pathMatches('/hello')",
                "def responseHeaders = { 'Content-Type': 'application/xml' }",
                "def response = '<hello>world</hello>'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get",
                "match header content-type == 'application/xml'",
                "match responseType == 'xml'",
                "match response.hello == 'world'"
        );
    }

    @Test
    void testNoResponseAutoConversionForUnknownContentType() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = '<hello>world</hello>'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get",
                "match header content-type == 'text/plain'",
                "match responseType == 'string'"
        );
    }

    @Test
    void testResponseAutoConversionForJsonAsPlainText() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = '{ \"foo\": \"bar\"}'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get",
                "match header content-type == 'text/plain'",
                "match responseType == 'json'",
                "match response.foo == 'bar'"
        );
    }

    @Test
    void testResponseAutoConversionForTextWithTags() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = '<http://example.org/#hello> a <http://example.org/#greeting> .'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get",
                "match header content-type == 'text/plain'",
                "match responseType == 'string'",
                "match response == '<http://example.org/#hello> a <http://example.org/#greeting> .'"
        );
    }

    @Test
    void testResponseContentTypeForNonXmlWithTags() {
        background().scenario(
                "pathMatches('/hello')",
                "def responseHeaders = { 'Content-Type': 'text/turtle' }",
                "def response = '<http://example.org/#hello> a <http://example.org/#greeting> .'");
        run(
                URL_STEP,
                "path 'hello'",
                "method get",
                "match header content-type == 'text/turtle'",
                "match responseType == 'string'",
                "match response == '<http://example.org/#hello> a <http://example.org/#greeting> .'"
        );
    }

    @Test
    void testWildcardLikePathMatch() {
        background().scenario(
                "requestUri.startsWith('hello/')",
                "def response = requestUri");
        run(
                URL_STEP,
                "path 'hello', 'foo', 'bar'",
                "method get",
                "match response == 'hello/foo/bar'"
        );
    }

    @Test
    void testPathFromStringVariable() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestUri");
        run(
                URL_STEP,
                " def temp = 'hello'",
                "path temp",
                "method get",
                "match response == 'hello'"
        );
    }

    @Test
    void testPathFromArrayVariable() {
        background().scenario(
                "pathMatches('/hello/world')",
                "def response = requestUri");
        run(
                URL_STEP,
                " def temp = ['hello', 'world']",
                "path temp",
                "method get",
                "match response == 'hello/world'"
        );
    }

    @Test
    void testPathWithForwardSlashes() {
        background().scenario(
                "pathMatches('/hello/world')",
                "def response = requestUri");
        run(
                URL_STEP,
                "path '/hello/world'",
                "method get",
                "match response == 'hello/world'"
        );
    }

    @Test
    void testPathWithEscapedSlashes() {
        background().scenario(
                "pathMatches('/hello/world')",
                "def response = requestUri");
        run(
                URL_STEP,
                "path '/hello\\\\/world'",
                "method get",
                "match response == 'hello/world'"
        );
    }  

}
