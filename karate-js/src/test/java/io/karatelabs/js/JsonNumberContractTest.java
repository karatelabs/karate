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

import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the JSON number-type contract that the future {@code JsonParser}
 * (replacing {@code net.minidev:json-smart}) must match. Documents the
 * concrete return types {@code JSONValue.parseKeepingOrder} produces today
 * — several karate-core call sites do {@code instanceof Integer} checks
 * (e.g. {@code OAuth2Token.fromMap}, {@code W3cDriver.tab/frame}), so the
 * narrowing contract is observable behavior.
 *
 * <p>Each test runs json-smart and asserts the type returned. Once
 * {@code JsonParser.parse} exists, extend each test to assert the same
 * type for the new parser. The migration is "done" when this test class
 * runs both implementations and they agree on every input.
 *
 * <p>The expected contract mirrors {@link Terms#narrow(double)}:
 * <ul>
 *   <li>Integer numbers in {@code [Integer.MIN_VALUE, Integer.MAX_VALUE]} → {@link Integer}
 *   <li>Integer numbers fitting in long but outside int range → {@link Long}
 *   <li>Numbers larger than {@code Long.MAX_VALUE} (precision loss) → {@link Double}
 *   <li>Fractional numbers (including {@code 1.0}) → {@link Double}
 *   <li>Negative zero ({@code -0}) — behavior is preserved as observed; see {@link #negativeZero_isInteger_zero()}
 * </ul>
 */
class JsonNumberContractTest {

    // -------------------------------------------------------------------------
    // Integers within int range
    // -------------------------------------------------------------------------

    @Test
    void zero_isInteger() {
        assertSameType(Integer.class, JSONValue.parseKeepingOrder("0"));
        assertEquals(0, JSONValue.parseKeepingOrder("0"));
    }

    @Test
    void smallPositive_isInteger() {
        assertSameType(Integer.class, JSONValue.parseKeepingOrder("127"));
        assertEquals(127, JSONValue.parseKeepingOrder("127"));
    }

    @Test
    void smallNegative_isInteger() {
        assertSameType(Integer.class, JSONValue.parseKeepingOrder("-127"));
        assertEquals(-127, JSONValue.parseKeepingOrder("-127"));
    }

    @Test
    void integerMaxValue_isInteger() {
        assertSameType(Integer.class, JSONValue.parseKeepingOrder("2147483647"));
        assertEquals(Integer.MAX_VALUE, JSONValue.parseKeepingOrder("2147483647"));
    }

    @Test
    void integerMinValue_isInteger() {
        assertSameType(Integer.class, JSONValue.parseKeepingOrder("-2147483648"));
        assertEquals(Integer.MIN_VALUE, JSONValue.parseKeepingOrder("-2147483648"));
    }

    // -------------------------------------------------------------------------
    // Just over int range → Long
    // -------------------------------------------------------------------------

    @Test
    void justOverIntegerMax_isLong() {
        assertSameType(Long.class, JSONValue.parseKeepingOrder("2147483648"));
        assertEquals(2147483648L, JSONValue.parseKeepingOrder("2147483648"));
    }

    @Test
    void justUnderIntegerMin_isLong() {
        assertSameType(Long.class, JSONValue.parseKeepingOrder("-2147483649"));
        assertEquals(-2147483649L, JSONValue.parseKeepingOrder("-2147483649"));
    }

    @Test
    void longMaxValue_isLong() {
        assertSameType(Long.class, JSONValue.parseKeepingOrder("9223372036854775807"));
        assertEquals(Long.MAX_VALUE, JSONValue.parseKeepingOrder("9223372036854775807"));
    }

    @Test
    void longMinValue_isLong() {
        assertSameType(Long.class, JSONValue.parseKeepingOrder("-9223372036854775808"));
        assertEquals(Long.MIN_VALUE, JSONValue.parseKeepingOrder("-9223372036854775808"));
    }

    // -------------------------------------------------------------------------
    // Beyond long range — json-smart's actual behavior pinned by probe.
    // Could be BigInteger or Double depending on version; we'll capture and
    // assert exactly what 2.6.0 does so the new parser must match.
    // -------------------------------------------------------------------------

    @Test
    void beyondLongMax_isBigInteger() {
        // json-smart 2.6.0 actual (probed 2026-05-12): returns BigInteger.
        // Surprising — the reviewer guessed Double-with-precision-loss; json-smart
        // is actually arbitrary-precision for integer values that overflow long.
        // The new JsonParser must match this contract: integers > Long.MAX_VALUE → BigInteger.
        assertSameType(BigInteger.class, JSONValue.parseKeepingOrder("99999999999999999999"));
        assertEquals(new BigInteger("99999999999999999999"),
                JSONValue.parseKeepingOrder("99999999999999999999"));
    }

    // -------------------------------------------------------------------------
    // Fractional → Double (per ECMA-262 §25.5.1 numeric output is IEEE 754)
    // -------------------------------------------------------------------------

    @Test
    void fractional_isDouble() {
        assertSameType(Double.class, JSONValue.parseKeepingOrder("1.5"));
        assertEquals(1.5, JSONValue.parseKeepingOrder("1.5"));
    }

    @Test
    void negativeFractional_isDouble() {
        assertSameType(Double.class, JSONValue.parseKeepingOrder("-1.5"));
        assertEquals(-1.5, JSONValue.parseKeepingOrder("-1.5"));
    }

    @Test
    void oneDotZero_isDouble() {
        // json-smart 2.6.0 actual: `1.0` → Double 1.0 (presence of fractional dot
        // forces Double even when the integer-valued result would fit in int).
        // The new parser must match.
        assertSameType(Double.class, JSONValue.parseKeepingOrder("1.0"));
        assertEquals(1.0, JSONValue.parseKeepingOrder("1.0"));
    }

    @Test
    void zeroDotZero_isDouble() {
        assertSameType(Double.class, JSONValue.parseKeepingOrder("0.0"));
        assertEquals(0.0, JSONValue.parseKeepingOrder("0.0"));
    }

    // -------------------------------------------------------------------------
    // Negative zero — surprising edge; pinned to document.
    // -------------------------------------------------------------------------

    @Test
    void negativeZero_isInteger_zero() {
        // json-smart 2.6.0 actual: `-0` → Integer 0 (negative-zero sign lost).
        // ECMA-262 §25.5.1 says `JSON.parse("-0")` should produce -0 (distinct from
        // +0 under Object.is). The new parser may CHOOSE to fix this (return -0.0
        // as Double) — but that's an observable behavior change. Decision deferred
        // to the implementation session; this test pins the json-smart behavior.
        assertSameType(Integer.class, JSONValue.parseKeepingOrder("-0"));
        assertEquals(0, JSONValue.parseKeepingOrder("-0"));
    }

    @Test
    void negativeZeroFractional_isDouble() {
        // `-0.0` → Double -0.0 (sign preserved via fractional path).
        Object o = JSONValue.parseKeepingOrder("-0.0");
        assertSameType(Double.class, o);
        // Confirm the sign is preserved:
        assertEquals(0, Double.compare(-0.0, ((Number) o).doubleValue()));
    }

    // -------------------------------------------------------------------------
    // Exponent form
    // -------------------------------------------------------------------------

    @Test
    void exponentForm_isDouble() {
        // json-smart 2.6.0: `1e2` → Double 100.0. Exponent form forces Double
        // even when the value is integer-valued. The new parser must match.
        assertSameType(Double.class, JSONValue.parseKeepingOrder("1e2"));
        assertEquals(100.0, JSONValue.parseKeepingOrder("1e2"));
    }

    @Test
    void exponentWithFraction_isDouble() {
        assertSameType(Double.class, JSONValue.parseKeepingOrder("1.5e2"));
        assertEquals(150.0, JSONValue.parseKeepingOrder("1.5e2"));
    }

    @Test
    void negativeExponent_isDouble() {
        assertSameType(Double.class, JSONValue.parseKeepingOrder("1e-2"));
        assertEquals(0.01, JSONValue.parseKeepingOrder("1e-2"));
    }

    // -------------------------------------------------------------------------
    // Non-numeric values — for completeness
    // -------------------------------------------------------------------------

    @Test
    void booleanTrue_isBoolean() {
        assertSameType(Boolean.class, JSONValue.parseKeepingOrder("true"));
        assertEquals(true, JSONValue.parseKeepingOrder("true"));
    }

    @Test
    void nullLiteral_isNull() {
        assertEquals(null, JSONValue.parseKeepingOrder("null"));
    }

    @Test
    void stringLiteral_isString() {
        assertSameType(String.class, JSONValue.parseKeepingOrder("\"hello\""));
        assertEquals("hello", JSONValue.parseKeepingOrder("\"hello\""));
    }

    // -------------------------------------------------------------------------
    // Sanity: Terms.narrow matches the same contract for double inputs.
    // The new JsonParser will likely route fractional/exponent-parse results
    // through Terms.narrow; this test confirms the rule produces matching types
    // for the integer-valued cases.
    // -------------------------------------------------------------------------

    @Test
    void termsNarrow_agreesOnInteger() {
        assertSameType(Integer.class, Terms.narrow(127.0));
        assertSameType(Integer.class, Terms.narrow(0.0));
        assertSameType(Integer.class, Terms.narrow(Integer.MAX_VALUE));
        assertSameType(Integer.class, Terms.narrow((double) Integer.MIN_VALUE));
    }

    @Test
    void termsNarrow_agreesOnLong() {
        assertSameType(Long.class, Terms.narrow(2147483648.0));
        assertSameType(Long.class, Terms.narrow(-2147483649.0));
    }

    @Test
    void termsNarrow_agreesOnDouble() {
        assertSameType(Double.class, Terms.narrow(1.5));
        assertSameType(Double.class, Terms.narrow(-1.5));
    }

    @Test
    void termsNarrow_preservesNegativeZero() {
        // Terms.narrow returns -0.0 as Double (preserves the sign).
        Object o = Terms.narrow(-0.0);
        assertSameType(Double.class, o);
        // -0.0 == 0.0 is true in Java, but Double.compare distinguishes them.
        assertEquals(0, Double.compare(-0.0, ((Number) o).doubleValue()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void assertSameType(Class<?> expected, Object actual) {
        if (actual == null) {
            throw new AssertionError("expected " + expected.getSimpleName() + ", got null");
        }
        if (!expected.equals(actual.getClass())) {
            throw new AssertionError("expected " + expected.getSimpleName()
                    + ", got " + actual.getClass().getSimpleName()
                    + " (value=" + actual + ")");
        }
    }

}
