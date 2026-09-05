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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * The scalar bodies behind {@code Math.round} and {@code Number.prototype.toFixed}, reachable
 * without an Engine so a caller that must agree with the interpreter can run the same code rather
 * than re-derive it. {@code JsMath} and {@code JsNumberPrototype} are the installed methods and
 * delegate here; the argument coercion, the {@code this} binding and the BigInt rejection stay
 * theirs.
 */
public final class JsScalars {

    private JsScalars() {
    }

    /**
     * Spec §21.3.2.28: NaN/±Inf/±0/integer unchanged; (0, 0.5) -&gt; +0; [-0.5, 0) -&gt; -0;
     * otherwise floor(x + 0.5). Note this is "round half toward +Infinity", NOT "round half away
     * from zero": Math.round(-1.5) === -1, NOT -2. The integer short-circuit is load-bearing near
     * MAX_SAFE_INTEGER (ulp &ge; 1), where x + 0.5 rounds to a different integer than x.
     */
    public static double round(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x) || x == 0) return x;
        if (x == Math.floor(x)) return x;
        if (x > 0 && x < 0.5) return 0.0;
        if (x < 0 && x >= -0.5) return -0.0;
        return Math.floor(x + 0.5);
    }

    /** Spec §21.1.3.3, with the same RangeError text the installed method throws. */
    public static String toFixed(double value, int digits) {
        if (digits < 0 || digits > 100) {
            throw JsErrorException.rangeError("toFixed() digits argument must be between 0 and 100");
        }
        if (Double.isNaN(value)) return "NaN";
        if (value == Double.POSITIVE_INFINITY) return "Infinity";
        if (value == Double.NEGATIVE_INFINITY) return "-Infinity";
        // Spec: |x| ≥ 10^21 falls back to ToString(x); BigDecimal of such doubles
        // produces a noisy decimal expansion (e.g. 1e21 -> "1000000000000000040000")
        // that doesn't match JS's "1e+21" canonical form.
        if (Math.abs(value) >= 1e21) {
            return Terms.numberToString(value);
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(digits, RoundingMode.HALF_UP);
        StringBuilder pattern = new StringBuilder("0");
        if (digits > 0) {
            pattern.append(".");
            pattern.append("0".repeat(digits));
        }
        DecimalFormat df = new DecimalFormat(pattern.toString());
        return df.format(bd.doubleValue());
    }
}
