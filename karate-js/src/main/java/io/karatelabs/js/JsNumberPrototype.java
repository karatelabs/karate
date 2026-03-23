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
 * Singleton prototype for Number instances.
 * Contains instance methods like toFixed, toPrecision, toLocaleString.
 * Inherits from JsObjectPrototype.
 */
class JsNumberPrototype extends Prototype {

    static final JsNumberPrototype INSTANCE = new JsNumberPrototype();

    private JsNumberPrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "toFixed" -> (JsCallable) this::toFixed;
            case "toPrecision" -> (JsCallable) this::toPrecision;
            case "toLocaleString" -> (JsCallable) this::toLocaleString;
            case "valueOf" -> (JsCallable) this::valueOf;
            default -> null;
        };
    }

    // Helper method to get number from this context
    private static Number asNumber(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsNumber jn) {
            return jn.value;
        }
        if (thisObj instanceof Number n) {
            return n;
        }
        return 0;
    }

    // Instance methods

    private Object toFixed(Context context, Object[] args) {
        int digits = 0;
        if (args.length > 0) {
            digits = Terms.objectToNumber(args[0]).intValue();
        }
        double doubleValue = asNumber(context).doubleValue();
        BigDecimal bd = BigDecimal.valueOf(doubleValue);
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
        double d = asNumber(context).doubleValue();
        if (args.length == 0) {
            // same as toString()
            return Double.toString(d);
        }
        int precision = Terms.objectToNumber(args[0]).intValue();
        if (precision < 1 || precision > 100) {
            throw new RuntimeException("RangeError: precision must be between 1 and 100");
        }
        // Use BigDecimal for rounding
        BigDecimal bd = new BigDecimal(d);
        bd = bd.round(new java.math.MathContext(precision, RoundingMode.HALF_UP));
        String result = bd.toString();
        // Ensure formatting matches JS behavior
        // JS switches between plain and exponential depending on magnitude
        if (result.contains("E") || result.contains("e")) {
            // Already in scientific notation, return as-is
            return result.replace('E', 'e');
        } else {
            // If scientific notation is needed for very large/small values
            if ((d != 0.0 && (Math.abs(d) < 1e-6 || Math.abs(d) >= 1e21))) {
                return formatPrecision(d, precision);
            }
            return result;
        }
    }

    @SuppressWarnings("MalformedFormatString")
    private static String formatPrecision(double value, int precision) {
        return String.format("%." + (precision - 1) + "e", value);
    }

    private Object toLocaleString(Context context, Object[] args) {
        double d = asNumber(context).doubleValue();
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
        return asNumber(context);
    }

}
