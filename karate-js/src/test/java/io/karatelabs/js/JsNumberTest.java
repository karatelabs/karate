package io.karatelabs.js;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class JsNumberTest extends EvalBase {

    @BeforeAll
    static void setLocale() {
        Locale.setDefault(Locale.US);
    }

    @Test
    void testConstructor() {
        assertEquals(0, eval("var a = Number()"));
        match(get("a"), "0");
        assertEquals(100, eval("var a = Number(100)"));
        match(get("a"), "100");
        assertEquals(100, eval("var a = Number('100')"));
        match(get("a"), "100");
    }

    @Test
    void testApi() {
        assertEquals("0.100", eval("var a = 0.1; a.toFixed(3)"));
        assertEquals(Math.ulp(1.0), eval("Number.EPSILON"));
    }

    @Test
    void testConstants() {
        assertEquals(Double.MAX_VALUE, eval("Number.MAX_VALUE"));
        assertEquals(Double.MIN_VALUE, eval("Number.MIN_VALUE"));
        assertEquals(9007199254740991L, eval("Number.MAX_SAFE_INTEGER"));
        assertEquals(-9007199254740991L, eval("Number.MIN_SAFE_INTEGER"));
        assertEquals(Double.POSITIVE_INFINITY, eval("Number.POSITIVE_INFINITY"));
        assertEquals(Double.NEGATIVE_INFINITY, eval("Number.NEGATIVE_INFINITY"));
        // NaN is tricky: NaN != NaN, so use JS isNaN
        assertEquals(true, eval("Number.isNaN(Number.NaN)"));
    }

    @Test
    void testIsFinite() {
        assertEquals(true, eval("Number.isFinite(123)"));
        assertEquals(false, eval("Number.isFinite(Infinity)"));
        assertEquals(false, eval("Number.isFinite(NaN)"));
        assertEquals(false, eval("Number.isFinite('123')"));
    }

    @Test
    void testIsInteger() {
        assertEquals(true, eval("Number.isInteger(42)"));
        assertEquals(false, eval("Number.isInteger(42.1)"));
        assertEquals(false, eval("Number.isInteger(NaN)"));
        assertEquals(false, eval("Number.isInteger(Infinity)"));
    }

    @Test
    void testIsNaN() {
        assertEquals(true, eval("Number.isNaN(NaN)"));
        assertEquals(false, eval("Number.isNaN('NaN')"));
        assertEquals(false, eval("Number.isNaN(123)"));
    }

    @Test
    void testNaNInequality() {
        // NaN != anything (including numbers) should be true
        assertEquals(true, eval("NaN != 5"));
        assertEquals(true, eval("NaN !== 5"));
        assertEquals(true, eval("5 != NaN"));
        assertEquals(true, eval("5 !== NaN"));
        // NaN != NaN should also be true
        assertEquals(true, eval("NaN != NaN"));
        assertEquals(true, eval("NaN !== NaN"));
    }

    @Test
    void testIsSafeInteger() {
        assertEquals(true, eval("Number.isSafeInteger(9007199254740991)"));  // MAX_SAFE_INTEGER
        assertEquals(false, eval("Number.isSafeInteger(9007199254740992)")); // just over
        assertEquals(false, eval("Number.isSafeInteger(1.5)"));              // not an int
        assertEquals(false, eval("Number.isSafeInteger(Infinity)"));
    }

    @Test
    void testToPrecision() {
        // basic rounding
        assertEquals("123", eval("(123.456).toPrecision(3)"));
        assertEquals("123.5", eval("(123.456).toPrecision(4)"));
        assertEquals("123.46", eval("(123.456).toPrecision(5)"));

        // fewer significant digits
        assertEquals("1e+3", eval("(1234.5).toPrecision(1)"));    // rounds to 1000
        assertEquals("1.2e+3", eval("(1234.5).toPrecision(2)"));  // 1200

        // small numbers (fixed-point in your impl)
        assertEquals("0.00012", eval("(0.0001234).toPrecision(2)"));
        assertEquals("0.00012340", eval("(0.0001234).toPrecision(5)"));

        // zero handling — spec §21.1.3.4: precision p produces 1 leading 0 + (p-1) trailing zeros after the decimal
        assertEquals("0", eval("(0).toPrecision(1)"));
        assertEquals("0.00", eval("(0).toPrecision(3)"));

        // negative numbers
        assertEquals("-123", eval("(-123.456).toPrecision(3)"));
        assertEquals("-123.5", eval("(-123.456).toPrecision(4)"));
        assertEquals("-0.00012", eval("(-0.0001234).toPrecision(2)"));

        try {
            eval("(123.456).toPrecision(0)");
            fail("Expected RangeError for precision 0");
        } catch (RuntimeException e) {
            // ok
        }
        try {
            eval("(123.456).toPrecision(101)");
            fail("Expected RangeError for precision 101");
        } catch (RuntimeException e) {
            // ok
        }
    }

    @Test
    void testToLocaleString() {
        // Basic usage - no arguments (uses US locale formatting)
        String result = (String) eval("(1234.567).toLocaleString()");
        assertEquals("1,234.567", result);

        // With options - minimumFractionDigits
        result = (String) eval("(1234.5).toLocaleString('en-US', {minimumFractionDigits: 3})");
        assertEquals("1,234.500", result);

        // With options - maximumFractionDigits
        result = (String) eval("(1234.56789).toLocaleString('en-US', {maximumFractionDigits: 2})");
        assertEquals("1,234.57", result);

        // Both min and max
        result = (String) eval("(1234.5).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 4})");
        assertEquals("1,234.50", result);

        // Zero
        result = (String) eval("(0).toLocaleString()");
        assertEquals("0", result);

        // Negative number
        result = (String) eval("(-1234.5).toLocaleString()");
        assertEquals("-1,234.5", result);

        // Large number
        result = (String) eval("(1234567.89).toLocaleString()");
        assertEquals("1,234,567.89", result);
    }

    @Test
    void testNumberToStringSpecForm() {
        assertEquals("123", eval("String(123)"));
        assertEquals("1.5", eval("String(1.5)"));
        assertEquals("0", eval("String(-0)"));
        assertEquals("0.30000000000000004", eval("(0.1 + 0.2) + ''"));
        assertEquals("NaN", eval("String(NaN)"));
        assertEquals("Infinity", eval("String(Infinity)"));
        assertEquals("-Infinity", eval("String(-Infinity)"));
        // spec Number::toString plain-decimal band is 1e-6 <= |d| < 1e21
        assertEquals("0.000001", eval("String(1e-6)"));
        assertEquals("1e-7", eval("(1e-7) + ''"));
        assertEquals("1e+21", eval("String(1e21)"));
        assertEquals("1e+21", eval("(1e21).toString()"));
        assertEquals("1e+21", eval("`${1e21}`"));
        assertEquals("-1e+21", eval("String(-1e21)"));
        assertEquals("1e+21", eval("String(new Number(1e21))"));
    }

    @Test
    void testParseFloatExponent() {
        assertEquals(1e21, eval("parseFloat('1e21')"));
        assertEquals(0.0015, eval("parseFloat('1.5e-3')"));
        assertEquals(1500, eval("parseFloat('1.5E3')"));
        assertEquals(2.5, eval("parseFloat('2.5abc')"));
        assertEquals(1, eval("parseFloat('1e')"));
        // a Number arg must round-trip through the spec string form
        assertEquals(1e21, eval("parseFloat(1e21)"));
    }

    @Test
    void testParseFloatInfinityAndRadixPrefix() {
        assertEquals(Double.POSITIVE_INFINITY, eval("parseFloat('Infinity')"));
        assertEquals(Double.NEGATIVE_INFINITY, eval("parseFloat('-Infinity')"));
        assertEquals(Double.POSITIVE_INFINITY, eval("parseFloat('Infinity123')"));
        // radix prefixes are parseInt-only: parseFloat reads the 0, stops at 'x'
        assertEquals(0, eval("parseFloat('0x10')"));
        assertEquals(16, eval("parseInt('0x10')"));
    }

}
