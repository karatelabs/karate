/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JsonParserTest {

    // -------------------------------------------------------------------------
    // primitive values
    // -------------------------------------------------------------------------

    @Test
    void literalNull() {
        assertNull(JsonParser.parse("null"));
    }

    @Test
    void literalTrue() {
        assertEquals(Boolean.TRUE, JsonParser.parse("true"));
    }

    @Test
    void literalFalse() {
        assertEquals(Boolean.FALSE, JsonParser.parse("false"));
    }

    @Test
    void stringEmpty() {
        assertEquals("", JsonParser.parse("\"\""));
    }

    @Test
    void stringPlain() {
        assertEquals("hello", JsonParser.parse("\"hello\""));
    }

    @Test
    void stringEscapes() {
        assertEquals("\"\\/\b\f\n\r\t",
                JsonParser.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\""));
    }

    @Test
    void stringUnicodeEscape() {
        assertEquals("é", JsonParser.parse("\"\\u00E9\""));
        assertEquals("A", JsonParser.parse("\"\\u0041\""));
    }

    @Test
    void stringSurrogatePair() {
        // U+1F600 GRINNING FACE encoded as UTF-16 surrogate pair
        assertEquals("😀", JsonParser.parse("\"\\uD83D\\uDE00\""));
    }

    // -------------------------------------------------------------------------
    // numbers — narrowing contract (mirrors JsonNumberContractTest)
    // -------------------------------------------------------------------------

    @Test
    void numberZero() {
        Object o = JsonParser.parse("0");
        assertEquals(Integer.class, o.getClass());
        assertEquals(0, o);
    }

    @Test
    void numberSmallInteger() {
        assertEquals(Integer.class, JsonParser.parse("127").getClass());
        assertEquals(127, JsonParser.parse("127"));
        assertEquals(Integer.class, JsonParser.parse("-127").getClass());
        assertEquals(-127, JsonParser.parse("-127"));
    }

    @Test
    void numberIntegerBoundary() {
        assertEquals(Integer.class, JsonParser.parse("2147483647").getClass());
        assertEquals(Integer.MAX_VALUE, JsonParser.parse("2147483647"));
        assertEquals(Integer.class, JsonParser.parse("-2147483648").getClass());
        assertEquals(Integer.MIN_VALUE, JsonParser.parse("-2147483648"));
    }

    @Test
    void numberJustOverIntegerMax() {
        assertEquals(Long.class, JsonParser.parse("2147483648").getClass());
        assertEquals(2147483648L, JsonParser.parse("2147483648"));
        assertEquals(Long.class, JsonParser.parse("-2147483649").getClass());
        assertEquals(-2147483649L, JsonParser.parse("-2147483649"));
    }

    @Test
    void numberLongBoundary() {
        assertEquals(Long.class, JsonParser.parse("9223372036854775807").getClass());
        assertEquals(Long.MAX_VALUE, JsonParser.parse("9223372036854775807"));
        assertEquals(Long.class, JsonParser.parse("-9223372036854775808").getClass());
        assertEquals(Long.MIN_VALUE, JsonParser.parse("-9223372036854775808"));
    }

    @Test
    void numberBeyondLong_isBigInteger() {
        Object o = JsonParser.parse("99999999999999999999");
        assertEquals(BigInteger.class, o.getClass());
        assertEquals(new BigInteger("99999999999999999999"), o);
    }

    @Test
    void numberFractional_isDouble() {
        assertEquals(Double.class, JsonParser.parse("1.5").getClass());
        assertEquals(1.5, JsonParser.parse("1.5"));
        assertEquals(Double.class, JsonParser.parse("-1.5").getClass());
        assertEquals(-1.5, JsonParser.parse("-1.5"));
    }

    @Test
    void numberOneDotZero_isDouble() {
        // contract pinned by JsonNumberContractTest: fractional-form forces Double
        // even when value is integer-valued.
        assertEquals(Double.class, JsonParser.parse("1.0").getClass());
        assertEquals(1.0, JsonParser.parse("1.0"));
        assertEquals(Double.class, JsonParser.parse("0.0").getClass());
        assertEquals(0.0, JsonParser.parse("0.0"));
    }

    @Test
    void numberNegativeZero_integerForm() {
        // matches json-smart: -0 integer form loses the sign and returns Integer 0.
        Object o = JsonParser.parse("-0");
        assertEquals(Integer.class, o.getClass());
        assertEquals(0, o);
    }

    @Test
    void numberNegativeZero_fractionalForm() {
        Object o = JsonParser.parse("-0.0");
        assertEquals(Double.class, o.getClass());
        assertEquals(0, Double.compare(-0.0, (Double) o));
    }

    @Test
    void numberExponent_isDouble() {
        // exponent form forces Double, even when result is integer-valued.
        assertEquals(Double.class, JsonParser.parse("1e2").getClass());
        assertEquals(100.0, JsonParser.parse("1e2"));
        assertEquals(Double.class, JsonParser.parse("1E2").getClass());
        assertEquals(100.0, JsonParser.parse("1E2"));
        assertEquals(Double.class, JsonParser.parse("1.5e2").getClass());
        assertEquals(150.0, JsonParser.parse("1.5e2"));
        assertEquals(Double.class, JsonParser.parse("1e-2").getClass());
        assertEquals(0.01, JsonParser.parse("1e-2"));
        assertEquals(Double.class, JsonParser.parse("1e+2").getClass());
        assertEquals(100.0, JsonParser.parse("1e+2"));
    }

    // -------------------------------------------------------------------------
    // arrays and objects
    // -------------------------------------------------------------------------

    @Test
    void arrayEmpty() {
        assertEquals(List.of(), JsonParser.parse("[]"));
        assertEquals(List.of(), JsonParser.parse("[ ]"));
    }

    @Test
    void arrayPrimitives() {
        assertEquals(List.of(1, 2, 3), JsonParser.parse("[1,2,3]"));
        assertEquals(List.of(1, 2, 3), JsonParser.parse("[ 1 , 2 , 3 ]"));
    }

    @Test
    void arrayMixed() {
        List<?> list = (List<?>) JsonParser.parse("[1,\"x\",true,null,[2]]");
        assertEquals(5, list.size());
        assertEquals(1, list.get(0));
        assertEquals("x", list.get(1));
        assertEquals(Boolean.TRUE, list.get(2));
        assertNull(list.get(3));
        assertEquals(List.of(2), list.get(4));
    }

    @Test
    void objectEmpty() {
        assertEquals(new LinkedHashMap<>(), JsonParser.parse("{}"));
        assertEquals(new LinkedHashMap<>(), JsonParser.parse("{ }"));
    }

    @Test
    void objectPreservesInsertionOrder() {
        Map<?, ?> m = (Map<?, ?>) JsonParser.parse("{\"b\":1,\"a\":2,\"c\":3}");
        assertEquals(List.of("b", "a", "c"), new java.util.ArrayList<>(m.keySet()));
    }

    @Test
    void objectNested() {
        Map<?, ?> m = (Map<?, ?>) JsonParser.parse("{\"a\":{\"b\":[1,2]}}");
        assertEquals(Map.of("b", List.of(1, 2)), m.get("a"));
    }

    @Test
    void duplicateKeys_lastWins() {
        // RFC 8259: behavior of duplicate keys is implementation-defined.
        // We match json-smart parseKeepingOrder: last value wins.
        Map<?, ?> m = (Map<?, ?>) JsonParser.parse("{\"a\":1,\"a\":2}");
        assertEquals(1, m.size());
        assertEquals(2, m.get("a"));
    }

    // -------------------------------------------------------------------------
    // whitespace
    // -------------------------------------------------------------------------

    @Test
    void leadingTrailingWhitespace() {
        assertEquals(42, JsonParser.parse("   42   "));
        assertEquals(42, JsonParser.parse("\t\n\r 42 \r\n\t"));
    }

    @Test
    void rejectsNonStandardWhitespace() {
        // RFC 8259 only allows space / tab / LF / CR as JSON whitespace.
        // Reject VT, FF, NBSP, etc. — matches test262 expectations.
        assertSyntaxError("42");      // vertical tab
        assertSyntaxError("42");      // form feed
        assertSyntaxError(" 42");      // NBSP
        assertSyntaxError(" 42");      // EN QUAD (Unicode whitespace)
        assertSyntaxError(" 42");      // LINE SEPARATOR
        assertSyntaxError(" 42");      // PARAGRAPH SEPARATOR
        assertSyntaxError("﻿42");      // BOM
    }

    // -------------------------------------------------------------------------
    // negative cases
    // -------------------------------------------------------------------------

    @Test
    void rejectsEmptyInput() {
        assertSyntaxError("");
        assertSyntaxError("   ");
    }

    @Test
    void rejectsTrailingGarbage() {
        assertSyntaxError("123 garbage");
        assertSyntaxError("{} garbage");
        assertSyntaxError("[1,2,3]extra");
    }

    @Test
    void rejectsUnquotedKey() {
        assertSyntaxError("{a:1}");
    }

    @Test
    void rejectsTrailingCommaInObject() {
        assertSyntaxError("{\"a\":1,}");
    }

    @Test
    void rejectsTrailingCommaInArray() {
        assertSyntaxError("[1,2,]");
    }

    @Test
    void rejectsSingleQuotes() {
        assertSyntaxError("'hello'");
        assertSyntaxError("{'a':1}");
    }

    @Test
    void rejectsNaN() {
        assertSyntaxError("NaN");
    }

    @Test
    void rejectsInfinity() {
        assertSyntaxError("Infinity");
        assertSyntaxError("-Infinity");
    }

    @Test
    void rejectsCommentsLineAndBlock() {
        assertSyntaxError("// line\n42");
        assertSyntaxError("/* block */ 42");
        assertSyntaxError("{\"a\":1/*c*/}");
    }

    @Test
    void rejectsMixedCaseLiterals() {
        assertSyntaxError("True");
        assertSyntaxError("FALSE");
        assertSyntaxError("Null");
        assertSyntaxError("nil");
    }

    @Test
    void rejectsLeadingPlus() {
        assertSyntaxError("+1");
    }

    @Test
    void rejectsLeadingZero() {
        assertSyntaxError("01");
        assertSyntaxError("00");
        assertSyntaxError("-01");
    }

    @Test
    void rejectsBareDecimalPoint() {
        assertSyntaxError(".5");
        assertSyntaxError("1.");
        assertSyntaxError("-.5");
    }

    @Test
    void rejectsBareExponent() {
        assertSyntaxError("1e");
        assertSyntaxError("1e+");
        assertSyntaxError("1e-");
    }

    @Test
    void rejectsBareMinus() {
        assertSyntaxError("-");
    }

    @Test
    void rejectsUnterminatedString() {
        assertSyntaxError("\"abc");
    }

    @Test
    void rejectsRawControlInString() {
        assertSyntaxError("\"a\nb\"");
        assertSyntaxError("\"ab\"");
        assertSyntaxError("\"a\tb\"");
    }

    @Test
    void rejectsInvalidStringEscape() {
        assertSyntaxError("\"\\x41\"");
        assertSyntaxError("\"\\v\"");
        assertSyntaxError("\"\\\"");          // backslash then end
    }

    @Test
    void rejectsInvalidUnicodeEscape() {
        assertSyntaxError("\"\\u00\"");        // short
        assertSyntaxError("\"\\uXXXX\"");       // non-hex
        assertSyntaxError("\"\\u\"");
    }

    @Test
    void rejectsUnclosedObject() {
        assertSyntaxError("{");
        assertSyntaxError("{\"a\"");
        assertSyntaxError("{\"a\":");
        assertSyntaxError("{\"a\":1");
        assertSyntaxError("{\"a\":1,");
    }

    @Test
    void rejectsUnclosedArray() {
        assertSyntaxError("[");
        assertSyntaxError("[1");
        assertSyntaxError("[1,");
    }

    @Test
    void rejectsObjectKeyNotString() {
        assertSyntaxError("{1:2}");
        assertSyntaxError("{true:1}");
        assertSyntaxError("{null:1}");
    }

    // -------------------------------------------------------------------------
    // pathological / robustness
    // -------------------------------------------------------------------------

    @Test
    void deeplyNestedArray_500levels() {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            open.append('[');
            close.append(']');
        }
        Object result = JsonParser.parse(open + "1" + close);
        // walk down to confirm it parsed
        Object cur = result;
        for (int i = 0; i < 500; i++) {
            cur = ((List<?>) cur).get(0);
        }
        assertEquals(1, cur);
    }

    @Test
    void deeplyNestedObject_500levels() {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            open.append("{\"a\":");
            close.append('}');
        }
        Object result = JsonParser.parse(open + "1" + close);
        Object cur = result;
        for (int i = 0; i < 500; i++) {
            cur = ((Map<?, ?>) cur).get("a");
        }
        assertEquals(1, cur);
    }

    @Test
    void nullInput_throwsSyntaxError() {
        assertThrows(JsErrorException.class, () -> JsonParser.parse(null));
    }

    // -------------------------------------------------------------------------
    // thread safety
    // -------------------------------------------------------------------------

    @Test
    void concurrentParses_doNotInterfere() throws Exception {
        // R10 in the migration plan: confirm the parser holds no shared state.
        // 4 threads, 1000 parses each, different inputs.
        final int threads = 4;
        final int parsesPerThread = 1000;
        final String[] inputs = {
                "{\"a\":1,\"b\":[1,2,3],\"c\":\"hello\"}",
                "[1.5, -2.7, 1e10, true, null]",
                "{\"nested\":{\"deep\":{\"value\":42}}}",
                "[\"x\",\"y\",\"z\",\"\\u00e9\\u00e8\"]"
        };
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final String input = inputs[t];
            final Object expected = JsonParser.parse(input);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < parsesPerThread; i++) {
                        Object actual = JsonParser.parse(input);
                        if (!expected.equals(actual)) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (Throwable th) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent parses did not complete in time");
        pool.shutdownNow();
        assertEquals(0, failures.get(), "concurrent parse produced inconsistent results");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static void assertSyntaxError(String input) {
        try {
            Object out = JsonParser.parse(input);
            fail("expected SyntaxError for input " + display(input) + " but got: " + out);
        } catch (JsErrorException e) {
            // expected — the payload is a JS SyntaxError
            assertSame(JsErrorPrototype.SYNTAX_ERROR, e.payload.getPrototype());
        }
    }

    private static String display(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) sb.append(c);
            else sb.append(String.format("\\u%04x", (int) c));
        }
        sb.append("\"");
        return sb.toString();
    }

}
