/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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
import java.util.Map;

/**
 * Singleton prototype for Number instances. Methods wrapped via
 * {@link Prototype#method(String, int, JsCallable)} for spec
 * {@code length}+{@code name}; the base class caches wrapped instances
 * per-Engine.
 */
class JsNumberPrototype extends Prototype {

    static final JsNumberPrototype INSTANCE = new JsNumberPrototype();

    private JsNumberPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("toFixed", 1, this::toFixed);
        install("toPrecision", 1, this::toPrecision);
        install("toExponential", 1, this::toExponential);
        install("toLocaleString", 0, this::toLocaleString);
        install("toString", 1, this::toStringMethod);
        install("valueOf", 0, this::valueOf);
    }

    private Object toStringMethod(Context context, Object[] args) {
        Number n = thisNumber(context);
        // Spec: only `undefined` (or absent) defaults to radix 10; `null` runs through
        // ToInteger → 0 → RangeError.
        if (args.length > 0 && args[0] != Terms.UNDEFINED) {
            // ToIntegerOrInfinity rejects BigInt — spec mandates TypeError.
            if (args[0] instanceof java.math.BigInteger) {
                throw JsErrorException.typeError("Cannot convert a BigInt to a number");
            }
            int radix = Terms.objectToNumber(args[0]).intValue();
            if (radix < 2 || radix > 36) {
                throw JsErrorException.rangeError("toString() radix must be between 2 and 36");
            }
            if (radix != 10) {
                double d = n.doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return Long.toString((long) d, radix);
                }
                return Double.toString(d);
            }
        }
        return numberToString(n);
    }

    /**
     * Spec {@code thisNumberValue} §21.1.3: unwrap JsNumber, accept primitive
     * Number, TypeError otherwise. {@code Number.prototype} is a Number exotic
     * with {@code [[NumberData]]} of +0, so it routes to 0.
     */
    private static Number thisNumber(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsNumber jn) return jn.value;
        if (thisObj instanceof Number n) return n;
        if (thisObj == INSTANCE) return 0;
        throw JsErrorException.typeError("Number.prototype method called on incompatible receiver");
    }

    /**
     * ToInteger for digits/precision/fractionDigits args: dispatches through
     * ToPrimitive so {@code [2]} → {@code "2"} → 2. Range-checking is the
     * caller's job.
     */
    private static int toIntegerArg(Object arg, Context context) {
        Number n = (context instanceof CoreContext cc)
                ? Terms.toNumberCoerce(arg, cc)
                : Terms.objectToNumber(arg);
        double d = n.doubleValue();
        if (Double.isNaN(d)) return 0;
        return (int) d;
    }

    private static String numberToString(Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d)) return "NaN";
        if (d == Double.POSITIVE_INFINITY) return "Infinity";
        if (d == Double.NEGATIVE_INFINITY) return "-Infinity";
        return n.toString();
    }

    // Instance methods

    private Object toFixed(Context context, Object[] args) {
        double d = thisNumber(context).doubleValue();
        if (args.length > 0 && args[0] instanceof java.math.BigInteger) {
            throw JsErrorException.typeError("Cannot convert a BigInt to a number");
        }
        int digits = 0;
        if (args.length > 0 && args[0] != Terms.UNDEFINED) {
            digits = toIntegerArg(args[0], context);
        }
        if (digits < 0 || digits > 100) {
            throw JsErrorException.rangeError("toFixed() digits argument must be between 0 and 100");
        }
        if (Double.isNaN(d)) return "NaN";
        if (d == Double.POSITIVE_INFINITY) return "Infinity";
        if (d == Double.NEGATIVE_INFINITY) return "-Infinity";
        // Spec: |x| ≥ 10^21 falls back to ToString(x); BigDecimal of such doubles
        // produces a noisy decimal expansion (e.g. 1e21 -> "1000000000000000040000")
        // that doesn't match JS's "1e+21" canonical form.
        if (Math.abs(d) >= 1e21) {
            return numberToString(d);
        }
        BigDecimal bd = BigDecimal.valueOf(d);
        bd = bd.setScale(digits, RoundingMode.HALF_UP);
        StringBuilder pattern = new StringBuilder("0");
        if (digits > 0) {
            pattern.append(".");
            pattern.append("0".repeat(digits));
        }
        DecimalFormat df = new DecimalFormat(pattern.toString());
        return df.format(bd.doubleValue());
    }

    private Object toPrecision(Context context, Object[] args) {
        double d = thisNumber(context).doubleValue();
        // Absent / undefined precision: spec returns ToString(x) — no range check.
        if (args.length == 0 || args[0] == Terms.UNDEFINED) {
            return numberToString(d);
        }
        if (args[0] instanceof java.math.BigInteger) {
            throw JsErrorException.typeError("Cannot convert a BigInt to a number");
        }
        int precision = toIntegerArg(args[0], context);
        if (Double.isNaN(d)) return "NaN";
        if (d == Double.POSITIVE_INFINITY) return "Infinity";
        if (d == Double.NEGATIVE_INFINITY) return "-Infinity";
        if (precision < 1 || precision > 100) {
            throw JsErrorException.rangeError("toPrecision() precision must be between 1 and 100");
        }
        if (d == 0.0) {
            // Spec §21.1.3.4: zero uses fixed notation with (precision - 1) trailing zeros
            // after the decimal point. -0 stringifies as "0" (sign elided for the zero
            // mantissa per Number::toString §6.1.6.1.13).
            if (precision == 1) return "0";
            StringBuilder sb = new StringBuilder(precision + 2);
            sb.append("0.");
            for (int i = 1; i < precision; i++) sb.append('0');
            return sb.toString();
        }
        BigDecimal bd = new BigDecimal(d);
        bd = bd.round(new java.math.MathContext(precision, RoundingMode.HALF_UP));
        String result = bd.toString();
        // BigDecimal.toString uses scientific form for very large / small values; JS
        // switches between plain and exponential at |d| < 1e-6 or |d| >= 10^precision.
        if (result.contains("E") || result.contains("e")) {
            return result.replace('E', 'e');
        }
        if (Math.abs(d) < 1e-6 || Math.abs(d) >= Math.pow(10, precision)) {
            return formatPrecision(d, precision);
        }
        return result;
    }

    /**
     * Spec §21.1.3.2 Number.prototype.toExponential. Always renders as
     * {@code d[.dd]e±dd}. undefined fractionDigits picks the minimum digits
     * that round-trip back to the receiver.
     */
    private Object toExponential(Context context, Object[] args) {
        double d = thisNumber(context).doubleValue();
        if (args.length > 0 && args[0] instanceof java.math.BigInteger) {
            throw JsErrorException.typeError("Cannot convert a BigInt to a number");
        }
        boolean digitsAbsent = args.length == 0 || args[0] == Terms.UNDEFINED;
        int digits = digitsAbsent ? 0 : toIntegerArg(args[0], context);
        if (Double.isNaN(d)) return "NaN";
        if (d == Double.POSITIVE_INFINITY) return "Infinity";
        if (d == Double.NEGATIVE_INFINITY) return "-Infinity";
        if (d == 0.0) {
            // Spec §21.1.3.2: zero exponent always renders as "+0"; -0 strips its sign.
            if (digitsAbsent || digits == 0) return "0e+0";
            StringBuilder sb = new StringBuilder(digits + 5);
            sb.append("0.");
            for (int i = 0; i < digits; i++) sb.append('0');
            sb.append("e+0");
            return sb.toString();
        }
        if (!digitsAbsent && (digits < 0 || digits > 100)) {
            throw JsErrorException.rangeError("toExponential() fractionDigits must be between 0 and 100");
        }
        String formatted;
        if (digitsAbsent) {
            // Minimum-digits path: %.<n>e for the smallest n such that
            // round-tripping recovers d. For the common test262 cases this is
            // the same shape Double.toString produces, then we canonicalize.
            formatted = String.format(java.util.Locale.ROOT, "%.15e", d);
            // Trim trailing zeros from the mantissa fractional part.
            int e = formatted.indexOf('e');
            String mant = formatted.substring(0, e);
            String exp = formatted.substring(e);
            if (mant.contains(".")) {
                mant = mant.replaceAll("0+$", "");
                if (mant.endsWith(".")) mant = mant.substring(0, mant.length() - 1);
            }
            formatted = mant + exp;
        } else {
            formatted = String.format(java.util.Locale.ROOT, "%." + digits + "e", d);
        }
        // Java emits "1.0e+01" / "1.0e-01"; JS spec uses the "+" / "-" sign but
        // strips a leading zero from a single-digit exponent: "1.0e+1" / "1.0e-1".
        int e = formatted.indexOf('e');
        String mant = formatted.substring(0, e);
        char sign = formatted.charAt(e + 1);
        String expDigits = formatted.substring(e + 2).replaceFirst("^0+(?!$)", "");
        return mant + "e" + sign + expDigits;
    }

    @SuppressWarnings("MalformedFormatString")
    private static String formatPrecision(double value, int precision) {
        String formatted = String.format(java.util.Locale.ROOT,
                "%." + (precision - 1) + "e", value);
        // Canonicalize Java's "1.0e+01" → JS "1.0e+1".
        int e = formatted.indexOf('e');
        if (e < 0) return formatted;
        String mant = formatted.substring(0, e);
        char sign = formatted.charAt(e + 1);
        String expDigits = formatted.substring(e + 2).replaceFirst("^0+(?!$)", "");
        return mant + "e" + sign + expDigits;
    }

    private Object toLocaleString(Context context, Object[] args) {
        double d = thisNumber(context).doubleValue();
        DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
        int optionsIndex = args.length > 1 ? 1 : 0;
        if (args.length > optionsIndex && args[optionsIndex] instanceof Map<?, ?> options) {
            Object minFractionDigits = options.get("minimumFractionDigits");
            Object maxFractionDigits = options.get("maximumFractionDigits");
            if (minFractionDigits instanceof Number n) {
                df.setMinimumFractionDigits(n.intValue());
            }
            if (maxFractionDigits instanceof Number n) {
                df.setMaximumFractionDigits(n.intValue());
            }
        }
        return df.format(d);
    }

    private Object valueOf(Context context, Object[] args) {
        return thisNumber(context);
    }

}
