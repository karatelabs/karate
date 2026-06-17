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
import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.Test;

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
}
