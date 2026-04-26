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
import java.math.BigInteger;

/**
 * BigInt global constructor (per spec, NOT callable with `new` — but karate-js
 * accepts both forms for ergonomics; tests rely only on the call form).
 * Provides {@code BigInt(value)} conversion plus {@code asIntN}/{@code asUintN}.
 */
class JsBigIntConstructor extends JsFunction {

    static final JsBigIntConstructor INSTANCE = new JsBigIntConstructor();

    private JsBigIntConstructor() {
        this.name = "BigInt";
        this.length = 1;
        registerForEngineReset();
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "asIntN" -> (JsCallable) this::asIntN;
            case "asUintN" -> (JsCallable) this::asUintN;
            case "prototype" -> JsBigIntPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        if (args.length == 0) {
            throw JsErrorException.typeError("Cannot convert undefined to a BigInt");
        }
        return toBigInt(args[0], (CoreContext) context);
    }

    /**
     * Spec ToBigInt: ToPrimitive with hint "number", then convert. ToPrimitive
     * fires only on the rare ObjectLike path — primitive inputs (Number, String,
     * Boolean, BigInt) skip it entirely.
     */
    static BigInteger toBigInt(Object value, CoreContext context) {
        // Rare path: object → call valueOf / toString to get a primitive
        if (value instanceof ObjectLike) {
            value = Terms.toPrimitive(value, "number", context);
            if (context != null && context.isError()) {
                // Caller will surface the propagated error; sentinel return is OK
                // since the host will throw before reading it.
                return BigInteger.ZERO;
            }
        } else if (value instanceof JsValue jv) {
            value = jv.getJavaValue();
        }
        return primitiveToBigInt(value);
    }

    // No-context overload for code paths that have only a primitive in hand
    // (e.g. asIntN/asUintN second arg already coerced). Matches spec when the
    // input is guaranteed primitive.
    static BigInteger toBigInt(Object value) {
        return toBigInt(value, null);
    }

    private static BigInteger primitiveToBigInt(Object value) {
        if (value instanceof BigInteger bi) {
            return bi;
        }
        if (value instanceof Boolean b) {
            return b ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                throw JsErrorException.rangeError("Cannot convert non-finite number to BigInt");
            }
            if (d != Math.floor(d)) {
                throw JsErrorException.rangeError("Cannot convert non-integer number to BigInt");
            }
            return new BigDecimal(d).toBigInteger();
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return BigInteger.ZERO;
            }
            try {
                if (trimmed.length() > 2 && trimmed.charAt(0) == '0') {
                    char c = trimmed.charAt(1);
                    if (c == 'x' || c == 'X') return new BigInteger(trimmed.substring(2), 16);
                    if (c == 'o' || c == 'O') return new BigInteger(trimmed.substring(2), 8);
                    if (c == 'b' || c == 'B') return new BigInteger(trimmed.substring(2), 2);
                }
                return new BigInteger(trimmed);
            } catch (NumberFormatException e) {
                throw JsErrorException.syntaxError("Cannot convert " + s + " to a BigInt");
            }
        }
        if (value == null || value == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert " + (value == null ? "null" : "undefined") + " to a BigInt");
        }
        throw JsErrorException.typeError("Cannot convert object to a BigInt");
    }

    private Object asIntN(Context context, Object[] args) {
        int bits = toIndex(args.length > 0 ? args[0] : Terms.UNDEFINED, (CoreContext) context);
        BigInteger bi = toBigInt(args.length > 1 ? args[1] : Terms.UNDEFINED, (CoreContext) context);
        if (bits == 0) return BigInteger.ZERO;
        // mod 2^bits, then signed reinterpret
        BigInteger mod = BigInteger.ONE.shiftLeft(bits);
        BigInteger r = bi.mod(mod);
        BigInteger half = BigInteger.ONE.shiftLeft(bits - 1);
        if (r.compareTo(half) >= 0) {
            r = r.subtract(mod);
        }
        return r;
    }

    private Object asUintN(Context context, Object[] args) {
        int bits = toIndex(args.length > 0 ? args[0] : Terms.UNDEFINED, (CoreContext) context);
        BigInteger bi = toBigInt(args.length > 1 ? args[1] : Terms.UNDEFINED, (CoreContext) context);
        if (bits == 0) return BigInteger.ZERO;
        BigInteger mod = BigInteger.ONE.shiftLeft(bits);
        return bi.mod(mod);
    }

    // Spec ToIndex: ToPrimitive("number") → ToNumber → ToInteger, then RangeError if
    // negative. Truncate fires *before* the negative check, so -0.9 → 0 (not RangeError).
    // NaN / undefined / null / false / "" all collapse to 0.
    private static int toIndex(Object value, CoreContext context) {
        if (value instanceof ObjectLike) {
            value = Terms.toPrimitive(value, "number", context);
            if (context != null && context.isError()) return 0;
        }
        Number n = Terms.objectToNumber(value);
        double d = n.doubleValue();
        if (Double.isNaN(d) || d == 0) return 0;
        long truncated = (long) d; // Java cast: truncate toward zero
        if (truncated < 0) {
            throw JsErrorException.rangeError("ToIndex: value must be non-negative");
        }
        return (int) truncated;
    }

}
