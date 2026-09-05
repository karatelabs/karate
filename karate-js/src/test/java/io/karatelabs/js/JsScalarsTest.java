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

import static org.junit.jupiter.api.Assertions.*;

class JsScalarsTest {

    @Test
    void roundIsTheSpecCasesNotFloorOfXPlusHalf() {
        assertEquals(0.0, JsScalars.round(0.0));
        assertEquals(3.0, JsScalars.round(3.0));
        // the ulp is already 1 here, so x + 0.5 would advance the value
        assertEquals(4503599627370497d, JsScalars.round(4503599627370497d));
        assertEquals(0.0, JsScalars.round(0.49999999999999994));
        assertEquals(0.0, JsScalars.round(0.1));
        assertEquals(1.0, JsScalars.round(0.5));
        assertEquals(-0.0, JsScalars.round(-0.5));
        assertEquals(Double.NEGATIVE_INFINITY, 1 / JsScalars.round(-0.5), "-0.5 rounds to -0");
        assertEquals(-0.0, JsScalars.round(-0.1));
        assertEquals(3.0, JsScalars.round(2.5));
        assertEquals(-1.0, JsScalars.round(-1.5), "round half toward +Infinity");
        assertEquals(-2.0, JsScalars.round(-2.5));
        assertTrue(Double.isNaN(JsScalars.round(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, JsScalars.round(Double.POSITIVE_INFINITY));
    }

    @Test
    void toFixedFormatsAndRangeChecks() {
        assertEquals("1", JsScalars.toFixed(1, 0));
        assertEquals("1.00", JsScalars.toFixed(1, 2));
        assertEquals("1.01", JsScalars.toFixed(1.005, 2));
        assertEquals("1e+21", JsScalars.toFixed(1e21, 2));
        JsErrorException e = assertThrows(JsErrorException.class, () -> JsScalars.toFixed(1, 101));
        assertTrue(e.getMessage().contains("toFixed() digits argument must be between 0 and 100"), e.getMessage());
        assertThrows(JsErrorException.class, () -> JsScalars.toFixed(1, -1));
    }

    @Test
    void theInstalledMethodsDelegate() {
        Engine engine = new Engine();
        assertEquals(0, engine.eval("Math.round(0.49999999999999994)"));
        assertEquals("1.00", engine.eval("(1).toFixed(2)"));
    }
}
