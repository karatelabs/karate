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

import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.core.MockHandler;
import io.karatelabs.core.MockServer;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.TestUtils;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Mock Server must not execute Karate embedded expressions found in attacker-controlled request
 * data. A mock that assigns request-derived values ({@code def body = request}) used to recursively
 * evaluate the body's {@code #(...)} strings; combined with Java interop that was remote code
 * execution via {@code #(Java.type(...))}.
 *
 * <p>Two independent, default-on protections cover this, each with an opt-out for trusted mocks:
 * <ul>
 *   <li>request-derived data is treated as inert data ({@code requestExpressionsEnabled});</li>
 *   <li>Java interop is disabled ({@code javaBridgeEnabled}).</li>
 * </ul>
 *
 * <p>The first protection has to survive <em>extraction</em>: a mock that reads a scalar out of
 * the request ({@code request.poc}, {@code bodyPath('$.poc')}, {@code headerValue('x')}) and
 * places it into a JS-built object hands the runtime a value the request container never held,
 * so marking containers by identity alone is not enough — see the extraction tests below.
 */
class MockServerSecurityTest {

    // Needs the Java bridge - stands in for an exec payload so the test proves evaluation
    // happened without doing anything dangerous.
    private static final String JAVA_EXPR =
            "#(Java.type('java.lang.System').getProperty('java.specification.version'))";
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");
    // Pure JS - isolates the request-expression layer from the Java-bridge layer.
    private static final String JS_EXPR = "#(1 + 1)";

    private final HttpClient client = new ApacheHttpClient();

    private static final String ECHO =
            "Feature: echo\nScenario: pathMatches('/echo')\n* def body = request\n* def response = body\n";

    private MockServer.Builder echoMock(String configure) {
        String feature = configure == null ? ECHO
                : "Feature: echo\nBackground:\n" + configure + "\nScenario: pathMatches('/echo')\n"
                  + "* def body = request\n* def response = body\n";
        return MockServer.featureString(feature).port(0);
    }

    private Object roundTrip(MockServer server, String payloadExpr) {
        try {
            HttpResponse res = new HttpRequestBuilder(client)
                    .url(server.getUrl()).path("/echo").method("POST")
                    .body(Map.of("poc", payloadExpr))
                    .invoke();
            @SuppressWarnings("unchecked")
            Map<String, Object> echoed = (Map<String, Object>) res.getBodyConverted();
            return echoed.get("poc");
        } finally {
            server.stopAsync();
        }
    }

    // ---- request-expression layer (independent of the Java bridge) ----

    @Test
    void testRequestExpressionInertByDefault() {
        // a plain JS expression in request data is NOT evaluated - survives verbatim as data
        assertEquals(JS_EXPR, roundTrip(echoMock(null).start(), JS_EXPR));
    }

    @Test
    void testRequestExpressionEvaluatedWhenOptedInViaConfigure() {
        Object result = roundTrip(echoMock("* configure requestExpressionsEnabled = true").start(), JS_EXPR);
        assertEquals(2, result); // no Java bridge needed for pure JS
    }

    @Test
    void testRequestExpressionEvaluatedWhenOptedInViaBuilder() {
        Object result = roundTrip(echoMock(null).requestExpressionsEnabled(true).start(), JS_EXPR);
        assertEquals(2, result);
    }

    // ---- Java-bridge layer ----

    @Test
    void testJavaRcePayloadInertByDefault() {
        // the documented RCE vector: blocked by both layers, stays literal
        assertEquals(JAVA_EXPR, roundTrip(echoMock(null).start(), JAVA_EXPR));
    }

    @Test
    void testJavaPayloadStillInertWithOnlyRequestExpressionsOptedIn() {
        // request expressions allowed, but the Java bridge is still off: Java.type fails and the
        // expression is left as data rather than executing - the two opt-outs are independent
        Object result = roundTrip(echoMock("* configure requestExpressionsEnabled = true").start(), JAVA_EXPR);
        assertEquals(JAVA_EXPR, result);
    }

    @Test
    void testJavaPayloadEvaluatesWithBothOptedInViaConfigure() {
        String cfg = "* configure requestExpressionsEnabled = true\n* configure javaBridgeEnabled = true";
        assertEquals(JAVA_VERSION, roundTrip(echoMock(cfg).start(), JAVA_EXPR));
    }

    @Test
    void testJavaPayloadEvaluatesWithBothOptedInViaBuilder() {
        MockServer server = echoMock(null).javaBridgeEnabled(true).requestExpressionsEnabled(true).start();
        assertEquals(JAVA_VERSION, roundTrip(server, JAVA_EXPR));
    }

    // karate.exec starts a process without the Java bridge, so it must follow the same switch.
    // `java -version` is harmless; a run that happened returns its version banner.
    private static final String EXEC_EXPR = "#(karate.exec(['"
            + Path.of(System.getProperty("java.home"), "bin", "java").toString().replace("\\", "/")
            + "', '-version']))";

    @Test
    void testExecPayloadStillInertWithOnlyRequestExpressionsOptedIn() {
        Object result = roundTrip(echoMock("* configure requestExpressionsEnabled = true").start(), EXEC_EXPR);
        assertEquals(EXEC_EXPR, result);
    }

    @Test
    void testExecPayloadEvaluatesWithBothOptedIn() {
        String cfg = "* configure requestExpressionsEnabled = true\n* configure javaBridgeEnabled = true";
        assertTrue(String.valueOf(roundTrip(echoMock(cfg).start(), EXEC_EXPR)).contains("version"));
    }

    @Test
    void testMockStepCannotExecByDefault() {
        String feature = "Feature: exec\nScenario: pathMatches('/exec')\n"
                + "* def response = karate.exec(['java', '-version'])\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            HttpResponse res = new HttpRequestBuilder(client)
                    .url(server.getUrl()).path("/exec").method("GET").invoke();
            assertEquals(500, res.getStatus());
            assertTrue(res.getBodyString().contains("javaBridgeEnabled"), res.getBodyString());
        } finally {
            server.stopAsync();
        }
    }

    // ---- extraction: a scalar pulled out of the request is a different object than the container ----

    /** A mock that builds its response in JS from a value it read out of the request. */
    private MockServer.Builder extractMock(String reader, String configure) {
        String feature = "Feature: echo\n"
                + (configure == null ? "" : "Background:\n" + configure + "\n")
                + "Scenario: pathMatches('/echo')\n"
                + "* def response = ({ poc: " + reader + " })\n";
        return MockServer.featureString(feature).port(0);
    }

    @Test
    void testExtractedBodyScalarInertByDefault() {
        assertEquals(JS_EXPR, roundTrip(extractMock("request.poc", null).start(), JS_EXPR));
    }

    @Test
    void testExtractedBodyPathScalarInertByDefault() {
        assertEquals(JS_EXPR, roundTrip(extractMock("bodyPath('$.poc')", null).start(), JS_EXPR));
    }

    @Test
    void testExtractedJavaPayloadInertByDefault() {
        assertEquals(JAVA_EXPR, roundTrip(extractMock("request.poc", null).start(), JAVA_EXPR));
    }

    @Test
    void testExtractedBodyScalarEvaluatedWhenOptedIn() {
        MockServer server = extractMock("request.poc", "* configure requestExpressionsEnabled = true").start();
        assertEquals(2, roundTrip(server, JS_EXPR));
    }

    /** A header the attacker controls, read back out with headerValue(). */
    @Test
    void testExtractedHeaderInertByDefault() {
        MockServer server = extractMock("headerValue('x-poc')", null).start();
        try {
            HttpResponse res = new HttpRequestBuilder(client)
                    .url(server.getUrl()).path("/echo").method("POST")
                    .header("x-poc", JS_EXPR)
                    .body(Map.of("ignored", true))
                    .invoke();
            @SuppressWarnings("unchecked")
            Map<String, Object> echoed = (Map<String, Object>) res.getBodyConverted();
            assertEquals(JS_EXPR, echoed.get("poc"));
        } finally {
            server.stopAsync();
        }
    }

    /** A query parameter the attacker controls, read back out with paramValue(). */
    @Test
    void testExtractedParamInertByDefault() {
        MockServer server = extractMock("paramValue('poc')", null).start();
        try {
            HttpResponse res = new HttpRequestBuilder(client)
                    .url(server.getUrl()).path("/echo").method("POST")
                    .param("poc", JS_EXPR)
                    .body(Map.of("ignored", true))
                    .invoke();
            @SuppressWarnings("unchecked")
            Map<String, Object> echoed = (Map<String, Object>) res.getBodyConverted();
            assertEquals(JS_EXPR, echoed.get("poc"));
        } finally {
            server.stopAsync();
        }
    }

    /** The body read through the karate.request accessor instead of the request variable. */
    @Test
    void testKarateRequestAccessorInertByDefault() {
        String feature = "Feature: echo\nScenario: pathMatches('/echo')\n"
                + "* def body = karate.request\n* def response = body\n";
        assertEquals(JS_EXPR, roundTrip(MockServer.featureString(feature).port(0).start(), JS_EXPR));
    }

    /** A path segment the attacker controls, read back out of pathParams. */
    @Test
    void testExtractedPathParamInertByDefault() {
        String feature = "Feature: echo\nScenario: pathMatches('/echo/{id}')\n"
                + "* def response = ({ poc: pathParams.id })\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            HttpResponse res = new HttpRequestBuilder(client)
                    .url(server.getUrl() + "/echo/%23(1%20%2B%201)").method("GET")
                    .invoke();
            @SuppressWarnings("unchecked")
            Map<String, Object> echoed = (Map<String, Object>) res.getBodyConverted();
            assertEquals(JS_EXPR, echoed.get("poc"));
        } finally {
            server.stopAsync();
        }
    }

    /** The Host header the attacker controls, read back out of requestUrlBase. */
    @Test
    void testRequestUrlBaseInertByDefault() {
        String feature = "Feature: echo\nScenario: pathMatches('/echo')\n"
                + "* def response = ({ poc: requestUrlBase })\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            HttpResponse res = new HttpRequestBuilder(client)
                    .url(server.getUrl()).path("/echo").method("GET")
                    .header("Host", JS_EXPR)
                    .invoke();
            @SuppressWarnings("unchecked")
            Map<String, Object> echoed = (Map<String, Object>) res.getBodyConverted();
            assertEquals("http://" + JS_EXPR, echoed.get("poc"));
        } finally {
            server.stopAsync();
        }
    }

    // ---- match: an expected-side marker sees the mock's variables only when written in the feature ----

    private MockServer.Builder matchMock(String actual, String expected, String configure) {
        String feature = "Feature: match\n"
                + (configure == null ? "" : "Background:\n" + configure + "\n")
                + "Scenario: pathMatches('/echo')\n* def secret = 'x'\n"
                + "* def response = ({ poc: karate.match(" + actual + ", " + expected + ").pass })\n";
        return MockServer.featureString(feature).port(0);
    }

    @Test
    void testKarateMatchRequestMarkerInertByDefault() {
        assertEquals(false, roundTrip(matchMock("'string'", "request.poc", null).start(), "#(typeof secret)"));
        assertEquals(false, roundTrip(matchMock("{ a: 'string' }", "{ a: request.poc }", null).start(), "#(typeof secret)"));
    }

    @Test
    void testKarateMatchRequestMarkerEvaluatedWhenOptedIn() {
        MockServer server = matchMock("'string'", "request.poc", "* configure requestExpressionsEnabled = true").start();
        assertEquals(true, roundTrip(server, "#(typeof secret)"));
    }

    // A marker that reaches the match engine runs as JS, so one that came off the wire - whatever
    // read it out, however it was reshaped - must run where it sees neither the mock's variables
    // nor karate. The probe mutates the mock if it runs; a second request reports whether it did.
    private static final String MUTATING_MARKER = "#? karate.set('leaked', secret) || true";

    private MockServer probeMock(String matchStep, String configure) {
        String feature = "Feature: match\n"
                + (configure == null ? "" : "Background:\n" + configure + "\n")
                + "Scenario: pathMatches('/probe')\n* def secret = 'x'\n"
                + "* " + matchStep + "\n* def response = 'ok'\n"
                + "Scenario: pathMatches('/leaked')\n* def response = ({ leaked: typeof leaked })\n";
        return MockServer.featureString(feature).port(0).start();
    }

    private HttpRequestBuilder probe(MockServer server) {
        return new HttpRequestBuilder(client).url(server.getUrl()).path("/probe").method("POST")
                .body(Map.of("poc", MUTATING_MARKER, "nested", Map.of("poc", MUTATING_MARKER)));
    }

    private Object leaked(MockServer server) {
        HttpResponse res = new HttpRequestBuilder(client).url(server.getUrl()).path("/leaked").method("GET").invoke();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBodyConverted();
        return body.get("leaked");
    }

    private void assertInert(MockServer server, HttpRequestBuilder probe) {
        try {
            HttpResponse res = probe.invoke();
            assertEquals(500, res.getStatus(), res.getBodyString());
            assertTrue(res.getBodyString().contains("requestExpressionsEnabled"), res.getBodyString());
            assertEquals("undefined", leaked(server));
        } finally {
            server.stopAsync();
        }
    }

    private void assertEvaluated(MockServer server, HttpRequestBuilder probe) {
        try {
            HttpResponse res = probe.invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
            assertEquals("string", leaked(server));
        } finally {
            server.stopAsync();
        }
    }

    @Test
    void testKarateMatchTwoArgsRequestMarkerCannotRunByDefault() {
        MockServer server = probeMock("def res = karate.match('string', request.poc)", null);
        assertInert(server, probe(server));
    }

    @Test
    void testMatchKeywordRequestMarkerCannotRunByDefault() {
        MockServer server = probeMock("match 'string' == request.poc", null);
        assertInert(server, probe(server));
    }

    @Test
    void testKarateMatchOneArgRequestMarkerCannotRunByDefault() {
        MockServer server = probeMock("def res = karate.match(\"'string' == request.poc\")", null);
        assertInert(server, probe(server));
    }

    @Test
    void testMatchNestedRequestMarkerInAuthorLiteralCannotRunByDefault() {
        MockServer server = probeMock("match { a: 'string' } == { a: '#(request.nested.poc)' }", null);
        assertInert(server, probe(server));
    }

    @Test
    void testMatchTransformedRequestMarkerCannotRunByDefault() {
        // .trim() yields a string the request never held, so value provenance alone cannot catch it
        MockServer server = probeMock("match 'string' == request.poc.trim()", null);
        assertInert(server, probe(server).body(Map.of("poc", " " + MUTATING_MARKER + " ")));
    }

    @Test
    void testMatchArrayMarkerFromRequestCannotRunByDefault() {
        MockServer server = probeMock("def res = karate.match([1], request.poc)", null);
        assertInert(server, probe(server).body(Map.of("poc", "#[] karate.set('leaked', secret)")));
    }

    @Test
    void testKarateMatchJsonStringFromRequestCannotRunByDefault() {
        // the two-arg form parses a JSON string expected, so the marker is one level down
        MockServer server = probeMock("def res = karate.match({ a: 'string' }, request.poc)", null);
        assertInert(server, probe(server).body(Map.of("poc", "{\"a\":\"" + MUTATING_MARKER + "\"}")));
    }

    @Test
    void testMatchHeaderMarkerCannotRunByDefault() {
        MockServer server = probeMock("match 'string' == headerValue('x-poc')", null);
        assertInert(server, probe(server).header("x-poc", MUTATING_MARKER));
    }

    @Test
    void testMatchParamMarkerCannotRunByDefault() {
        MockServer server = probeMock("match 'string' == paramValue('poc')", null);
        assertInert(server, probe(server).param("poc", MUTATING_MARKER));
    }

    @Test
    void testMatchRequestMarkerRunsWhenOptedIn() {
        MockServer server = probeMock("match 'string' == request.poc", "* configure requestExpressionsEnabled = true");
        assertEvaluated(server, probe(server));
    }

    @Test
    void testMatchMarkersWrittenInFeatureStillSeeMockVariables() {
        String feature = "Feature: match\nScenario: pathMatches('/probe')\n"
                + "* def secret = 'x'\n* def schemas = { item: { id: '#number' } }\n"
                + "* match request == { items: '#[] schemas.item', tag: '#? _ == secret', name: '##(schemas.name)' }\n"
                + "* def res = karate.match(request.items, '#[] schemas.item')\n"
                + "* if (!res.pass) karate.fail(res.message)\n"
                + "* def response = 'ok'\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            HttpResponse res = probe(server).body(Map.of("items", List.of(Map.of("id", 1)), "tag", "x")).invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
        } finally {
            server.stopAsync();
        }
    }

    @Test
    void testMatchMarkerBuiltAtRuntimeNeedsOptIn() {
        // the marker text is not in the feature, so the mock cannot tell it from one off the wire
        String step = "def marker = '#[] ' + 'schemas.item'\n* def schemas = { item: { id: '#number' } }\n"
                + "* match request.items == marker";
        Map<String, Object> body = Map.of("items", List.of(Map.of("id", 1)));
        MockServer server = probeMock(step, null);
        try {
            HttpResponse res = probe(server).body(body).invoke();
            assertEquals(500, res.getStatus(), res.getBodyString());
            assertTrue(res.getBodyString().contains("requestExpressionsEnabled"), res.getBodyString());
        } finally {
            server.stopAsync();
        }
        server = probeMock(step, "* configure requestExpressionsEnabled = true");
        try {
            assertEquals(200, probe(server).body(body).invoke().getStatus());
        } finally {
            server.stopAsync();
        }
    }

    // ---- second order: what an author-written marker yields off the wire is itself a marker ----

    @Test
    void testMarkerYieldedByAuthorMarkerCannotRunByDefault() {
        for (String step : List.of(
                "def res = karate.match('string', '#(request.poc)')",
                "def res = karate.match('string', '##(request.poc)')",
                "def res = karate.match(['string'], '#[] request.poc')",
                "match 'string' == '#(request.poc)'",
                "match 'string' == '#(^request.poc)'",
                "match 'string' == '#string? karate.match(_, request.poc).pass'")) {
            MockServer server = probeMock(step, null);
            assertInert(server, probe(server));
        }
    }

    @Test
    void testMarkerYieldedByAuthorMarkerRunsWhenOptedIn() {
        MockServer server = probeMock("def res = karate.match('string', '#(request.poc)')",
                "* configure requestExpressionsEnabled = true");
        assertEvaluated(server, probe(server));
    }

    @Test
    void testMarkerQuotedInCommentCannotRunByDefault() {
        // a comment is not the author writing the marker into a step
        MockServer server = probeMock("match 'string' == request.poc\n# a rejected example: " + MUTATING_MARKER, null);
        assertInert(server, probe(server));
    }

    @Test
    void testMatchMarkersInDocStringAndTableStillSeeMockVariables() {
        String feature = "Feature: match\nScenario: pathMatches('/probe')\n"
                + "* def secret = 'x'\n* def schemas = { item: { id: '#number' } }\n"
                + "* table expected\n| tag |\n| '#? _ == secret' |\n"
                + "* match request.tags == expected\n"
                + "* match request ==\n\"\"\"\n{ items: '#[] schemas.item', tags: '#[] #? _.tag == secret' }\n\"\"\"\n"
                + "* def response = 'ok'\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            HttpResponse res = probe(server)
                    .body(Map.of("items", List.of(Map.of("id", 1)), "tags", List.of(Map.of("tag", "x")))).invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
        } finally {
            server.stopAsync();
        }
    }

    // ---- an XML request body is request data like a JSON one ----

    private static final String XML_TEXT_PROBE = "<root>#(karate.set('leaked', secret) || 'ok')</root>";
    private static final String XML_ATTR_PROBE = "<root a=\"prefix #(karate.set('leaked', secret)) suffix\"/>";

    private void assertXmlInert(String step, String xml) {
        MockServer server = probeMock(step, null);
        try {
            HttpResponse res = probe(server).contentType("application/xml").body(xml).invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
            assertEquals("undefined", leaked(server));
        } finally {
            server.stopAsync();
        }
    }

    @Test
    void testXmlRequestBodyInertByDefault() {
        assertXmlInert("def wrapped = ({ body: request })", XML_TEXT_PROBE);
        assertXmlInert("def wrapped = ({ body: request })", XML_ATTR_PROBE);
        assertXmlInert("json asJson = request\n* def wrapped = ({ body: asJson })", XML_TEXT_PROBE);
        assertXmlInert("xml asXml = request\n* def wrapped = ({ body: asXml })", XML_TEXT_PROBE);
    }

    @Test
    void testXmlRequestBodyEvaluatedWhenOptedIn() {
        MockServer server = probeMock("def wrapped = ({ body: request })", "* configure requestExpressionsEnabled = true");
        try {
            HttpResponse res = probe(server).contentType("application/xml").body(XML_TEXT_PROBE).invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
            assertEquals("string", leaked(server));
        } finally {
            server.stopAsync();
        }
    }

    // ---- a feature called from a mock inherits the mock's trust posture ----

    private static final String HELPERS = "classpath:io/karatelabs/core/mock/";

    /** A mock that hands the request to a helper feature and serves what the helper made of it. */
    private MockServer.Builder callMock(String helper, String configure) {
        String feature = "Feature: call\n"
                + (configure == null ? "" : "Background:\n" + configure + "\n")
                + "Scenario: pathMatches('/echo')\n* def secret = 'x'\n"
                + "* def result = call read('" + HELPERS + helper + "') request\n"
                + "* def response = result.echoed\n";
        return MockServer.featureString(feature).port(0);
    }

    @Test
    void testCalledFeatureLeavesRequestDataInert() {
        assertEquals(JS_EXPR, roundTrip(callMock("mock-call-copy.feature", null).start(), JS_EXPR));
        assertEquals(JAVA_EXPR, roundTrip(callMock("mock-call-copy.feature", null).start(), JAVA_EXPR));
    }

    @Test
    void testCalledFeatureEvaluatesRequestDataWhenOptedIn() {
        String expressions = "* configure requestExpressionsEnabled = true";
        assertEquals(2, roundTrip(callMock("mock-call-copy.feature", expressions).start(), JS_EXPR));
        assertEquals(JAVA_EXPR, roundTrip(callMock("mock-call-copy.feature", expressions).start(), JAVA_EXPR));
        String both = expressions + "\n* configure javaBridgeEnabled = true";
        assertEquals(JAVA_VERSION, roundTrip(callMock("mock-call-copy.feature", both).start(), JAVA_EXPR));
    }

    private HttpResponse callJavaHelper(String tag, String configure) {
        String feature = "Feature: call\n"
                + (configure == null ? "" : "Background:\n" + configure + "\n")
                + "Scenario: pathMatches('/probe')\n"
                + "* def response = karate.call('" + HELPERS + "mock-call-java.feature@" + tag + "')\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            return new HttpRequestBuilder(client).url(server.getUrl()).path("/probe").method("GET").invoke();
        } finally {
            server.stopAsync();
        }
    }

    @Test
    void testCalledFeatureCannotReachJavaOrExecByDefault() {
        for (String tag : List.of("java", "exec")) {
            HttpResponse res = callJavaHelper(tag, null);
            assertEquals(500, res.getStatus(), res.getBodyString());
            assertTrue(res.getBodyString().contains("not enabled"), res.getBodyString());
        }
    }

    @Test
    void testCalledFeatureReachesJavaWhenOptedIn() {
        HttpResponse res = callJavaHelper("java", "* configure javaBridgeEnabled = true");
        assertEquals(200, res.getStatus(), res.getBodyString());
        assertTrue(res.getBodyString().contains(JAVA_VERSION), res.getBodyString());
    }

    @Test
    void testCalledFeatureMatchRequestMarkerCannotRunByDefault() {
        for (String tag : List.of("js", "keyword")) {
            MockServer server = probeMock("call read('" + HELPERS + "mock-call-match.feature@" + tag + "') request", null);
            assertInert(server, probe(server));
        }
    }

    @Test
    void testCalledFeatureMatchRequestMarkerRunsWhenOptedIn() {
        MockServer server = probeMock("call read('" + HELPERS + "mock-call-match.feature@js') request",
                "* configure requestExpressionsEnabled = true");
        assertEvaluated(server, probe(server));
    }

    // ---- request text reshaped by JS is still request data ----

    private static final String EMBEDDED_MARKER = "#(karate.set('leaked', secret) || 'x')";

    @Test
    void testRequestTextReshapedByJsCannotRunByDefault() {
        // a string JS built out of request text carries no mark, so the token itself is judged
        for (String reshaped : List.of(
                "'Hello ' + request.poc",
                "`Hi ${request.poc}`",
                "request.poc.replace('@', '')",
                "'v=' + headerValue('x-poc')")) {
            MockServer server = probeMock("def reshaped = ({ poc: " + reshaped + " })", null);
            try {
                HttpResponse res = probe(server).header("x-poc", EMBEDDED_MARKER)
                        .body(Map.of("poc", EMBEDDED_MARKER)).invoke();
                assertEquals(200, res.getStatus(), res.getBodyString());
                assertEquals("undefined", leaked(server));
            } finally {
                server.stopAsync();
            }
        }
    }

    private MockServer.Builder reshapeMock(String configure) {
        String feature = "Feature: echo\n"
                + (configure == null ? "" : "Background:\n" + configure + "\n")
                + "Scenario: pathMatches('/echo')\n* def response = ({ poc: 'Hello ' + request.poc })\n";
        return MockServer.featureString(feature).port(0);
    }

    @Test
    void testRequestTextReshapedByJsIsServedVerbatim() {
        assertEquals("Hello " + JS_EXPR, roundTrip(reshapeMock(null).start(), JS_EXPR));
    }

    @Test
    void testRequestTextReshapedByJsEvaluatedWhenOptedIn() {
        assertEquals("Hello 2", roundTrip(reshapeMock("* configure requestExpressionsEnabled = true").start(), JS_EXPR));
    }

    @Test
    void testCalledFeatureReshapedRequestTextStaysInert() {
        assertEquals("Hello " + JS_EXPR, roundTrip(callMock("mock-call-concat.feature", null).start(), JS_EXPR));
    }

    @Test
    void testEmbeddedExpressionsWrittenInFeatureOrReadFileStillRun() {
        String feature = "Feature: template\nScenario: pathMatches('/probe')\n"
                + "* def secret = 'x'\n* def greeting = 'hi'\n* def name = 'bob'\n"
                + "* def fromFile = read('" + HELPERS + "mock-template.json')\n"
                + "* def response = ({ file: fromFile, text: karate.readAsString('" + HELPERS + "mock-template.txt'),"
                + " inline: 'Hello #(secret)', whole: '#(secret)' })\n";
        MockServer server = MockServer.featureString(feature).port(0).start();
        try {
            HttpResponse res = probe(server).invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
            Map<String, Object> expected = Map.of(
                    "file", Map.of("greeting", "hi", "inline", "Hello bob!"),
                    "text", "Hello bob!\n", "inline", "Hello x", "whole", "x");
            assertEquals(expected, res.getBodyConverted());
        } finally {
            server.stopAsync();
        }
    }

    // ---- a mock answering on a thread where another scenario is live still applies its own policy ----

    /** In-JVM glue: a plain scenario that drives a mock handler directly, as a Java step would. */
    public static class Glue {

        static MockHandler handler;

        public static int apply() {
            HttpRequest request = new HttpRequest();
            request.setMethod("POST");
            request.setPath("/probe");
            request.putHeader("Content-Type", "application/json");
            request.setBody(Json.stringifyStrict(Map.of("poc", "#? karate.set('leaked', 'yes') || true"))
                    .getBytes(StandardCharsets.UTF_8));
            return handler.apply(request).getStatus();
        }
    }

    @Test
    void testKarateMatchInMockIgnoresTheScenarioLiveOnTheThread() {
        String mock = "Feature: match\nScenario: pathMatches('/probe')\n"
                + "* def res = karate.match('string', request.poc)\n* def response = 'ok'\n";
        Glue.handler = new MockHandler(Feature.read(Resource.text(mock)));
        ScenarioRuntime sr = TestUtils.run("* def Glue = Java.type('io.karatelabs.core.mock.MockServerSecurityTest$Glue')\n"
                + "* def status = Glue.apply()\n");
        TestUtils.assertPassed(sr);
        assertEquals(500, TestUtils.get(sr, "status"));
        assertNull(TestUtils.get(sr, "leaked"));
        assertNull(Glue.handler.getVariable("leaked"));
    }

    @Test
    void testCalledFeatureOwnMarkersStillSeeItsVariables() {
        String step = "def result = call read('" + HELPERS + "mock-call-schema.feature') { items: '#(request.items)', expectedId: 1 }";
        MockServer server = probeMock(step, null);
        try {
            HttpResponse res = probe(server).body(Map.of("items", List.of(Map.of("id", 1)))).invoke();
            assertEquals(200, res.getStatus(), res.getBodyString());
        } finally {
            server.stopAsync();
        }
    }
}
