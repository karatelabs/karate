package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JsMathTest extends EvalBase {

    @Test
    void testMath() {
        assertEquals(3, eval("1 + 2"));
        assertEquals(1, eval("3 - 2"));
        assertEquals(6, eval("1 + 2 + 3"));
        assertEquals(0, eval("1 + 2 - 3"));
        assertEquals(1.5d, eval("1 + 0.5"));
        assertEquals(6, eval("3 * 2"));
        assertEquals(3, eval("6 / 2"));
        assertEquals(1.5d, eval("3 / 2"));
        assertEquals(0, eval("8 % 2"));
        assertEquals(2, eval("11 % 3"));
        assertEquals(7, eval("1 + 3 * 2"));
        assertEquals(8, eval("2 * 3 + 2"));
        assertEquals(8, eval("(1 + 3) * 2"));
        assertEquals(8, eval("2 * (1 + 3)"));
        assertEquals(8, eval("2 ** 3"));
    }

    @Test
    void testMathSpecial() {
        assertEquals(Double.POSITIVE_INFINITY, eval("5 / 0"));
        assertEquals(0, eval("5 / Infinity"));
        assertEquals(3, eval("true + 2"));
        assertEquals(5, eval("null + 5"));
        assertEquals(Double.NaN, eval("undefined + 5"));
    }

    @Test
    void testMathApi() {
        assertEquals(Math.E, eval("Math.E"));
        assertEquals(2.302585092994046, eval("Math.LN10"));
        assertEquals(0.6931471805599453, eval("Math.LN2"));
        assertEquals(1.4426950408889634, eval("Math.LOG2E"));
        assertEquals(Math.PI, eval("Math.PI"));
        assertEquals(0.7071067811865476, eval("Math.SQRT1_2"));
        assertEquals(1.4142135623730951, eval("Math.SQRT2"));
        assertEquals(5, eval("Math.abs(-5)"));
        assertEquals(Math.PI, eval("Math.acos(-1)"));
        assertEquals(Double.NaN, eval("Math.acosh(0.5)"));
        assertEquals(1.5667992369724109, (double) eval("Math.acosh(2.5)"), 0.01);
        assertEquals(1.5707963267948966, eval("Math.asin(1)"));
        assertEquals(0.8813735870195429, eval("Math.asinh(1)"));
        assertEquals(0.7853981633974483, eval("Math.atan(1)"));
        assertEquals(1.4056476493802699, eval("Math.atan2(90, 15)"));
        assertEquals(0.5493061443340548, (double) eval("Math.atanh(0.5)"), 0.01);
        assertEquals(4, eval("Math.cbrt(64)"));
        assertEquals(1, eval("Math.ceil(0.95)"));
        assertEquals(22, eval("Math.clz32(1000)"));
        assertEquals(0.5403023058681398, eval("Math.cos(1)"));
        assertEquals(1.543080634815244, eval("Math.cosh(1)"));
        assertEquals(7.38905609893065, eval("Math.exp(2)"));
        assertEquals(1.718281828459045, eval("Math.expm1(1)"));
        assertEquals(5, eval("Math.floor(5.05)"));
        assertEquals(1.3370000123977661, eval("Math.fround(1.337)"));
        assertEquals(13, eval("Math.hypot(5, 12)"));
        assertEquals(-5, eval("Math.imul(0xffffffff, 5)"));
        assertEquals(2.302585092994046, eval("Math.log(10)"));
        assertEquals(5, eval("Math.log10(100000)"));
        assertEquals(0.6931471805599453, eval("Math.log1p(1)"));
        assertEquals(1.584962500721156, (double) eval("Math.log2(3)"), 0.01);
        assertEquals(6, eval("Math.max(3, 6)"));
        assertEquals(3, eval("Math.min(3, 6)"));
        assertEquals(343, eval("Math.pow(7, 3)"));
        assertEquals(343, eval("Math.pow(7, 3)"));
        assertInstanceOf(Number.class, eval("Math.random()"));
        assertEquals(1, eval("Math.round(0.9)"));
        assertEquals(-0.0, eval("Math.sign(-0)"));
        assertEquals(0, eval("Math.sign(0)"));
        assertEquals(1, eval("Math.sign(100)"));
        assertEquals(-1, eval("Math.sign(-20)"));
        assertEquals(0.8414709848078965, eval("Math.sin(1)"));
        assertEquals(1.1752011936438014, eval("Math.sinh(1)"));
        assertEquals(1.4142135623730951, eval("Math.sqrt(2)"));
        assertEquals(1.5574077246549023, eval("Math.tan(1)"));
        assertEquals(0.7615941559557649, eval("Math.tanh(1)"));
        assertEquals(1, eval("Math.trunc(1.9)"));
        assertEquals(-1, eval("Math.trunc(-1.9)"));
        assertEquals(-0.0, eval("Math.trunc(-0.9)"));
    }

    @Test
    void testMathRounding() {
        // Test basic Math.round behavior
        assertEquals(19, eval("Math.round(18.865)"));
        assertEquals(19, eval("Math.round(18.5)"));
        assertEquals(18, eval("Math.round(18.4)"));
        assertEquals(19, eval("Math.round(18.6)"));

        // Test rounding with negative numbers
        assertEquals(-19, eval("Math.round(-18.5)"));
        assertEquals(-18, eval("Math.round(-18.4)"));
        assertEquals(-19, eval("Math.round(-18.6)"));

        // Test edge cases with 0.5
        assertEquals(3, eval("Math.round(2.5)"));  // rounds to 3 (away from 0)
        assertEquals(4, eval("Math.round(3.5)"));  // rounds to 4 (away from 0)
        assertEquals(-3, eval("Math.round(-2.5)")); // rounds to -3 (away from 0) - THIS IS CORRECT!
        assertEquals(-4, eval("Math.round(-3.5)")); // rounds to -4 (away from 0)

        // Test Math.floor behavior
        assertEquals(18, eval("Math.floor(18.865)"));
        assertEquals(18, eval("Math.floor(18.5)"));
        assertEquals(18, eval("Math.floor(18.9)"));
        assertEquals(-19, eval("Math.floor(-18.1)"));
        assertEquals(-19, eval("Math.floor(-18.5)"));

        // Test Math.ceil behavior
        assertEquals(19, eval("Math.ceil(18.1)"));
        assertEquals(19, eval("Math.ceil(18.5)"));
        assertEquals(19, eval("Math.ceil(18.865)"));
        assertEquals(-18, eval("Math.ceil(-18.1)"));
        assertEquals(-18, eval("Math.ceil(-18.5)"));

        // Test special floating point cases
        assertEquals(0, eval("Math.round(0.4999999999999999)"));  // Actually less than 0.5 (15 9s)
        assertEquals(1, eval("Math.round(0.49999999999999994)"));  // This becomes 0.5 due to float precision (16 9s)
        assertEquals(1, eval("Math.round(0.5)"));  // Exactly 0.5
        assertEquals(1, eval("Math.round(0.50000000000000001)"));  // Just over 0.5 (actually equals 0.5 in double)

        // Test with very small numbers
        assertEquals(0, eval("Math.round(Number.EPSILON)"));
        assertEquals(Terms.NEGATIVE_ZERO, eval("Math.round(-Number.EPSILON)"));
    }

    @Test
    void testMatchRoundingSpecial() {
        // Test rounding behavior that would be used in roundHalfUp function
        // Note: 18.865 * 100 = 1886.4999999999998 due to floating point precision
        assertEquals(1886, eval("Math.floor(18.865 * 100 + 0.5)"));  // Actually gives 1886 due to float precision
        assertEquals(18.86, eval("Math.floor(18.865 * 100 + 0.5) / 100"));  // Results in 18.86

        // Math.round also affected by floating point precision
        assertEquals(1886, eval("Math.round(18.865 * 100)"));  // Actually 1886 due to float precision
        assertEquals(18.86, eval("Math.round(18.865 * 100) / 100"));  // Results in 18.86

        // Adding epsilon to fix floating point issues
        assertEquals(1887, eval("Math.round(18.865 * 100 + 0.00001)"));  // Epsilon fixes it
        assertEquals(18.87, eval("Math.round(18.865 * 100 + 0.00001) / 100"));  // Gives 18.87 correctly

        // More decimal rounding simulation tests
        assertEquals(1886, eval("Math.floor(18.864 * 100 + 0.5)"));  // Should give 1886
        assertEquals(18.86, eval("Math.floor(18.864 * 100 + 0.5) / 100"));  // Should give 18.86

        // 18.8650 has same float precision issue as 18.865
        assertEquals(1886, eval("Math.floor(18.8650 * 100 + 0.5)"));  // Actually 1886
        assertEquals(18.86, eval("Math.floor(18.8650 * 100 + 0.5) / 100"));  // Actually 18.86

        // Test edge case with 10.005 - this one actually works!
        assertEquals(1001, eval("Math.floor(10.005 * 100 + 0.5)"));  // Actually gives 1001 (no precision issue here)
        assertEquals(10.01, eval("Math.floor(10.005 * 100 + 0.5) / 100"));  // Gives 10.01 correctly
    }

    @Test
    void testNumberEpsilon() {
        // Test that Number.EPSILON is available and has the correct value
        assertEquals(2.220446049250313e-16, eval("Number.EPSILON"));

        // Test basic arithmetic with EPSILON
        assertEquals(1.0000000000000002, eval("1 + Number.EPSILON"));
        assertEquals(1, eval("1 + Number.EPSILON / 2"));  // Too small to affect
    }

    @Test
    void testHalfRoundUp() {
        Engine engine = new Engine();
        engine.eval("""
            function roundHalfUp(value, places) {
                if (typeof places !== 'number') {
                    places = 0;
                }
                const multiplier = Math.pow(10, places);
                const epsilon = Math.pow(10, - (places + 5));
                return Math.floor((value + epsilon) * multiplier + 0.5) / multiplier;
            }
            """);

        assertEquals(1886.4999999999998, engine.eval("18.865 * 100"));
        assertEquals(2.220446049250313e-16, engine.eval("Number.EPSILON"));
        assertEquals(18.865, engine.eval("18.865 + Number.EPSILON"));

        // whole number rounding
        assertEquals(2, engine.eval("roundHalfUp(1.5)"));
        assertEquals(3, engine.eval("roundHalfUp(2.5)"));
        assertEquals(2, engine.eval("roundHalfUp(2.4)"));
        assertEquals(3, engine.eval("roundHalfUp(2.6)"));

        // negative numbers without places
        assertEquals(-1, engine.eval("roundHalfUp(-1.5)"));
        assertEquals(-2, engine.eval("roundHalfUp(-2.5)"));
        assertEquals(-2, engine.eval("roundHalfUp(-2.4)"));
        assertEquals(-3, engine.eval("roundHalfUp(-2.6)"));

        // with 2 decimal places
        assertEquals(18.87, engine.eval("roundHalfUp(18.865, 2)"));
        assertEquals(18.87, engine.eval("roundHalfUp(18.8650, 2)"));
        assertEquals(18.86, engine.eval("roundHalfUp(18.864, 2)"));
        assertEquals(18.87, engine.eval("roundHalfUp(18.866, 2)"));

        // common problematic floating point cases
        assertEquals(10.01, engine.eval("roundHalfUp(10.005, 2)"));
        assertEquals(1.01, engine.eval("roundHalfUp(1.005, 2)"));
        assertEquals(0.01, engine.eval("roundHalfUp(0.005, 2)"));

        // 1 decimal place
        assertEquals(1.3, engine.eval("roundHalfUp(1.25, 1)"));
        assertEquals(1.4, engine.eval("roundHalfUp(1.35, 1)"));
        assertEquals(1.5, engine.eval("roundHalfUp(1.45, 1)"));

        // 3 decimal places
        assertEquals(1.236, engine.eval("roundHalfUp(1.2355, 3)"));
        assertEquals(1.235, engine.eval("roundHalfUp(1.2345, 3)"));
        assertEquals(1.235, engine.eval("roundHalfUp(1.2349, 3)"));

        // 0 decimal places (should round to integer)
        assertEquals(19, engine.eval("roundHalfUp(18.5, 0)"));
        assertEquals(19, engine.eval("roundHalfUp(18.865, 0)"));
        assertEquals(18, engine.eval("roundHalfUp(18.4, 0)"));

        // edge cases with very small numbers
        assertEquals(0.01, engine.eval("roundHalfUp(0.005, 2)"));
        assertEquals(0, engine.eval("roundHalfUp(0.004, 2)"));
        assertEquals(0.01, engine.eval("roundHalfUp(0.006, 2)"));

        // large numbers
        assertEquals(1234567.89, engine.eval("roundHalfUp(1234567.885, 2)"));
        assertEquals(1234567.88, engine.eval("roundHalfUp(1234567.884, 2)"));
    }

}
