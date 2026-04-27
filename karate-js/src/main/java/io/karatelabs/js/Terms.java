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

import io.karatelabs.common.Xml;
import io.karatelabs.parser.Token;
import org.w3c.dom.Node;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Terms {

    // JsUndefined singleton for undefined - used for identity comparison
    public static final JsUndefined UNDEFINED = JsUndefined.INSTANCE;

    static final Number POSITIVE_ZERO = 0;
    static final Number NEGATIVE_ZERO = -0.0;

    static final Object NAN = Double.NaN;

    final Number lhs;
    final Number rhs;

    Terms(Object lhsObject, Object rhsObject) {
        lhs = objectToNumber(lhsObject);
        rhs = objectToNumber(rhsObject);
    }

    // True iff either operand is BigInt. Fast path: most call sites have
    // plain Number operands and this returns false on the first instanceof.
    private boolean isBigIntOp() {
        return lhs instanceof BigInteger || rhs instanceof BigInteger;
    }

    // Spec: arithmetic ops require both operands to be BigInt or both Number;
    // mixing throws TypeError. Centralized check fires only on the rare path.
    private void requireBothBigInt(String opName) {
        if (!(lhs instanceof BigInteger) || !(rhs instanceof BigInteger)) {
            throw JsErrorException.typeError(
                "Cannot mix BigInt and other types, use explicit conversions (" + opName + ")");
        }
    }

    static Number parseInt(String str, int radix) {
        if (str == null) {
            return Double.NaN;
        }
        str = str.trim();
        if (str.isEmpty()) {
            return Double.NaN;
        }
        boolean negative = false;
        int index = 0;
        if (str.charAt(0) == '-') {
            negative = true;
            index++;
        } else if (str.charAt(0) == '+') {
            index++;
        }
        // auto-detect radix from prefix if not specified
        if (radix == 0) {
            if (index + 1 < str.length() && str.charAt(index) == '0'
                    && (str.charAt(index + 1) == 'x' || str.charAt(index + 1) == 'X')) {
                radix = 16;
                index += 2;
            } else {
                radix = 10;
            }
        }
        if (radix < 2 || radix > 36) {
            return Double.NaN;
        }
        long result = 0;
        boolean foundDigit = false;
        while (index < str.length()) {
            char ch = str.charAt(index);
            int digit;
            if (ch >= '0' && ch <= '9') {
                digit = ch - '0';
            } else if (ch >= 'a' && ch <= 'z') {
                digit = ch - 'a' + 10;
            } else if (ch >= 'A' && ch <= 'Z') {
                digit = ch - 'A' + 10;
            } else {
                break; // stop at first invalid char
            }
            if (digit >= radix) {
                break;
            }
            result = result * radix + digit;
            foundDigit = true;
            index++;
        }
        if (!foundDigit) {
            return Double.NaN;
        }
        double value = negative ? -result : result;
        return narrow(value);
    }

    static Number parseFloat(String str, boolean asInt) {
        if (str == null) {
            return Double.NaN;
        }
        str = str.trim();
        if (str.isEmpty()) {
            return Double.NaN;
        }
        int index = 0;
        boolean negative = false;
        if (str.charAt(index) == '-') {
            negative = true;
            index++;
        } else if (str.charAt(index) == '+') {
            index++;
        }
        Number radix = fromRadixPrefix(str);
        if (radix != null) {
            return narrow(radix.doubleValue());
        }
        long intPart = 0;
        double fracPart = 0;
        double divisor = 1.0;
        boolean foundDigit = false;
        boolean seenDot = false;
        while (index < str.length()) {
            char ch = str.charAt(index);
            if (ch == '.' && !asInt && !seenDot) {
                seenDot = true;
                index++;
                continue;
            }
            if (ch < '0' || ch > '9') {
                break; // stop at first invalid char
            }
            int digit = ch - '0';
            if (!seenDot) {
                intPart = intPart * 10 + digit;
            } else {
                divisor *= 10;
                fracPart += digit / divisor;
            }
            foundDigit = true;
            index++;
        }
        if (!foundDigit) {
            return Double.NaN;
        }
        double value = intPart + fracPart;
        if (negative) {
            value = -value;
        }
        return narrow(value);
    }

    static Number objectToNumber(Object o) {
        // Unwrap JsValue first using getJsValue()
        if (o instanceof JsValue jv) {
            o = jv.getJsValue();
        }
        return switch (o) {
            case Number n -> n;
            case Boolean b -> b ? 1 : 0;
            case Date d -> d.getTime();
            case String s -> stringToNumber(s);
            case null -> 0;
            // includes undefined
            default -> Double.NaN;
        };
    }

    /**
     * Spec ToNumber for built-ins that must invoke a host object's
     * {@code @@toPrimitive} / {@code valueOf} / {@code toString} (e.g. every
     * Math.* method that takes a number arg, per spec). Errors raised by those
     * dispatches propagate via {@code context.isError()} — callers must check
     * and bail with {@link #UNDEFINED} so the surrounding call frame can pick
     * the abrupt completion up.
     */
    static Number toNumberCoerce(Object o, CoreContext context) {
        Object prim = toPrimitive(o, "number", context);
        if (context != null && context.isError()) {
            return Double.NaN;
        }
        return objectToNumber(prim);
    }

    public static Number toNumber(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return narrow(Double.parseDouble(text));
        } catch (Exception e) {
            Number radix = fromRadixPrefix(text);
            return radix == null ? Double.NaN : narrow(radix.doubleValue());
        }
    }

    /**
     * Spec ToNumber for string runtime input (StringNumericLiteral §7.1.4.1.1).
     * Strips spec whitespace (StrWhiteSpace + LineTerminator), rejects numeric
     * separators (`_`), accepts `0b`/`0o`/`0x` radix prefixes, returns NaN on
     * malformed input — never throws. The lexer's literal path uses
     * {@link #literalToNumber(String)} which permits `_`.
     */
    public static Number stringToNumber(String text) {
        text = stripJsWhiteSpace(text);
        if (text.isEmpty()) {
            return 0;
        }
        // `_` is a literal-only separator; spec StringNumericLiteral rejects it.
        if (text.indexOf('_') >= 0) {
            return Double.NaN;
        }
        return toNumber(text);
    }

    static Number literalToNumber(String text) {
        // Lexer-validated NUMBER token: no leading/trailing/doubled `_`. Strip
        // separators only when present (skips allocation in the common case).
        if (text.indexOf('_') >= 0) {
            text = text.replace("_", "");
        }
        return toNumber(text);
    }

    private static String stripJsWhiteSpace(String s) {
        int len = s.length();
        int start = 0;
        while (start < len && isJsWhiteSpace(s.charAt(start))) start++;
        int end = len;
        while (end > start && isJsWhiteSpace(s.charAt(end - 1))) end--;
        return (start == 0 && end == len) ? s : s.substring(start, end);
    }

    private static boolean isJsWhiteSpace(char c) {
        // Spec WhiteSpace + LineTerminator: ES §12.2 / §12.3.
        // Java's Character.isWhitespace excludes NBSP and ZWNBSP/BOM.
        return Character.isWhitespace(c) || c == ' ' || c == '﻿';
    }

    public static Object literalValue(Token token) {
        return switch (token.type) {
            case S_STRING, D_STRING -> {
                String text = token.getText();
                yield text.substring(1, text.length() - 1);
            }
            case NUMBER -> literalToNumber(token.getText());
            case BIGINT -> toBigInt(token.getText());
            case TRUE -> true;
            case FALSE -> false;
            default -> null; // includes NULL
        };
    }

    // Parse a BIGINT literal token. The token text always ends with `n`; it may
    // contain `_` separators between digits; it may have an `0x`/`0X`, `0b`/`0B`,
    // or `0o`/`0O` radix prefix. Plain decimal integer otherwise (no `.`, no
    // exponent — those are forbidden by the lexer for BIGINT).
    private static BigInteger toBigInt(String text) {
        // strip trailing `n`
        String s = text.substring(0, text.length() - 1);
        // strip separators only if any are present (avoids allocation on the common case)
        if (s.indexOf('_') >= 0) {
            s = s.replace("_", "");
        }
        if (s.length() > 2 && s.charAt(0) == '0') {
            char p = s.charAt(1);
            if (p == 'x' || p == 'X') return new BigInteger(s.substring(2), 16);
            if (p == 'b' || p == 'B') return new BigInteger(s.substring(2), 2);
            if (p == 'o' || p == 'O') return new BigInteger(s.substring(2), 8);
        }
        return new BigInteger(s);
    }

    static Number fromRadixPrefix(String text) {
        if (text.length() > 2 && text.charAt(0) == '0') {
            char p = text.charAt(1);
            int radix = (p == 'x' || p == 'X') ? 16
                    : (p == 'b' || p == 'B') ? 2
                    : (p == 'o' || p == 'O') ? 8 : 0;
            if (radix != 0) {
                try {
                    return narrow(Long.parseLong(text.substring(2), radix));
                } catch (NumberFormatException nfe) {
                    return Double.NaN;
                }
            }
        }
        return null;
    }

    /**
     * Spec {@code SameValue} (§7.2.10) — like {@code ===} except
     * {@code SameValue(NaN, NaN) === true} and
     * {@code SameValue(+0, -0) === false}. Used by descriptor-redefinition
     * checks ({@code [[DefineOwnProperty]]} on a non-configurable
     * non-writable data property must SameValue-match the existing value
     * to avoid TypeError) and by {@code Object.is}.
     */
    public static boolean sameValue(Object lhs, Object rhs) {
        if (lhs instanceof Number ln && rhs instanceof Number rn
                && !(lhs instanceof BigInteger) && !(rhs instanceof BigInteger)) {
            double a = ln.doubleValue();
            double b = rn.doubleValue();
            if (Double.isNaN(a) && Double.isNaN(b)) return true;
            // +0 / -0 distinguished via raw bit pattern.
            if (a == 0 && b == 0) return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
            return a == b;
        }
        return eq(lhs, rhs, true);
    }

    static boolean eq(Object lhs, Object rhs, boolean strict) {
        if (lhs == null) {
            return rhs == null || !strict && rhs == UNDEFINED;
        }
        if (lhs == UNDEFINED) {
            return rhs == UNDEFINED || !strict && rhs == null;
        }
        // Per JS spec: null/undefined are only loosely equal to each other, not to anything else
        if (rhs == null || rhs == UNDEFINED) {
            return false;
        }
        if (lhs == rhs) { // instance equality !
            return true;
        }
        // Check for plain List/Map BEFORE JsObject (JsObject implements Map)
        // JsPrimitive includes JsNumber, JsString, JsBoolean which extend JsObject
        if (!(lhs instanceof JsPrimitive) && (lhs instanceof List || lhs instanceof Map)) {
            return false;
        }
        if (lhs.equals(rhs)) {
            return true;
        }
        if (strict) {
            // BigInt + BigInt was handled by lhs.equals(rhs) above; reaching here with
            // a BigInt operand means the other is non-BigInt — different type → false.
            if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
                return false;
            }
            if (lhs instanceof Number && rhs instanceof Number) {
                return ((Number) lhs).doubleValue() == ((Number) rhs).doubleValue();
            }
            return false;
        }
        // loose equality: unwrap boxed primitives
        if (lhs instanceof JsPrimitive jp) {
            lhs = jp.getJavaValue();
        }
        if (rhs instanceof JsPrimitive jp) {
            rhs = jp.getJavaValue();
        }
        if (lhs.equals(rhs)) {
            return true;
        }
        // BigInt vs Number / String: compare mathematical values per spec 7.2.14
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            return bigIntLooseEq(lhs, rhs);
        }
        if (lhs instanceof Number || rhs instanceof Number) { // coerce to number
            Terms terms = new Terms(lhs, rhs);
            return terms.lhs.equals(terms.rhs);
        }
        return false;
    }

    private static boolean bigIntLooseEq(Object lhs, Object rhs) {
        BigInteger bi;
        Object other;
        if (lhs instanceof BigInteger b) { bi = b; other = rhs; }
        else { bi = (BigInteger) rhs; other = lhs; }
        // BigInt vs String: try parse string as BigInt
        if (other instanceof String s) {
            try {
                return bi.equals(new BigInteger(s.trim()));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (other instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) return false;
            if (d != Math.floor(d)) return false; // fractional part — not equal to any BigInt
            // Use BigDecimal to convert exactly (handles values beyond long range)
            return bi.equals(new java.math.BigDecimal(d).toBigInteger());
        }
        if (other instanceof Boolean b) {
            return bi.equals(b ? BigInteger.ONE : BigInteger.ZERO);
        }
        return false;
    }

    static boolean lt(Object lhs, Object rhs) {
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            return bigIntCompare(lhs, rhs) < 0;
        }
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() < terms.rhs.doubleValue();
    }

    static boolean gt(Object lhs, Object rhs) {
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            return bigIntCompare(lhs, rhs) > 0;
        }
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() > terms.rhs.doubleValue();
    }

    static boolean ltEq(Object lhs, Object rhs) {
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            return bigIntCompare(lhs, rhs) <= 0;
        }
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() <= terms.rhs.doubleValue();
    }

    static boolean gtEq(Object lhs, Object rhs) {
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            return bigIntCompare(lhs, rhs) >= 0;
        }
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() >= terms.rhs.doubleValue();
    }

    // Returns Integer.MIN_VALUE for "incomparable" (NaN-like — surfaces via the
    // < / > / <= / >= callers as `false`, which is the spec result).
    private static int bigIntCompare(Object lhs, Object rhs) {
        if (lhs instanceof BigInteger && rhs instanceof BigInteger) {
            return ((BigInteger) lhs).compareTo((BigInteger) rhs);
        }
        BigInteger bi;
        Object other;
        boolean swapped;
        if (lhs instanceof BigInteger b) { bi = b; other = rhs; swapped = false; }
        else { bi = (BigInteger) rhs; other = lhs; swapped = true; }
        // BigInt vs String: parse, fall back to NaN-like incomparable
        if (other instanceof String s) {
            try {
                int c = bi.compareTo(new BigInteger(s.trim()));
                return swapped ? -c : c;
            } catch (NumberFormatException e) {
                return Integer.MIN_VALUE;
            }
        }
        if (other instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d)) return Integer.MIN_VALUE;
            if (d == Double.POSITIVE_INFINITY) return swapped ? 1 : -1;
            if (d == Double.NEGATIVE_INFINITY) return swapped ? -1 : 1;
            int c = new java.math.BigDecimal(d).compareTo(new java.math.BigDecimal(bi));
            // c was Number.compareTo(BigInt); swap sign so result is BigInt-relative
            c = -c;
            return swapped ? -c : c;
        }
        return Integer.MIN_VALUE;
    }

    Object bitAnd() {
        if (isBigIntOp()) {
            requireBothBigInt("&");
            return narrowBigInt(((BigInteger) lhs).and((BigInteger) rhs));
        }
        return lhs.intValue() & rhs.intValue();
    }

    Object bitOr() {
        if (isBigIntOp()) {
            requireBothBigInt("|");
            return narrowBigInt(((BigInteger) lhs).or((BigInteger) rhs));
        }
        return lhs.intValue() | rhs.intValue();
    }

    Object bitXor() {
        if (isBigIntOp()) {
            requireBothBigInt("^");
            return narrowBigInt(((BigInteger) lhs).xor((BigInteger) rhs));
        }
        return lhs.intValue() ^ rhs.intValue();
    }

    Object bitShiftRight() {
        if (isBigIntOp()) {
            requireBothBigInt(">>");
            return narrowBigInt(((BigInteger) lhs).shiftRight(((BigInteger) rhs).intValueExact()));
        }
        return lhs.intValue() >> rhs.intValue();
    }

    Object bitShiftLeft() {
        if (isBigIntOp()) {
            requireBothBigInt("<<");
            return narrowBigInt(((BigInteger) lhs).shiftLeft(((BigInteger) rhs).intValueExact()));
        }
        return lhs.intValue() << rhs.intValue();
    }

    Object bitShiftRightUnsigned() {
        if (isBigIntOp()) {
            // spec: unsigned right shift on BigInt always TypeError, even when both operands are BigInt
            throw JsErrorException.typeError("BigInts have no unsigned right shift, use >> instead");
        }
        return narrow((lhs.intValue() & 0xFFFFFFFFL) >>> rhs.intValue());
    }

    static Object bitNot(Object value) {
        Number number = objectToNumber(value);
        if (number instanceof BigInteger bi) {
            return narrowBigInt(bi.not());
        }
        return ~number.intValue();
    }

    Object mul() {
        if (isBigIntOp()) {
            requireBothBigInt("*");
            return narrowBigInt(((BigInteger) lhs).multiply((BigInteger) rhs));
        }
        double result = lhs.doubleValue() * rhs.doubleValue();
        return narrow(result);
    }

    Object div() {
        if (isBigIntOp()) {
            requireBothBigInt("/");
            BigInteger r = (BigInteger) rhs;
            if (r.signum() == 0) {
                throw JsErrorException.rangeError("Division by zero");
            }
            return narrowBigInt(((BigInteger) lhs).divide(r));
        }
        if (rhs.equals(POSITIVE_ZERO)) {
            return lhs.doubleValue() > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        if (rhs.equals(NEGATIVE_ZERO)) {
            return lhs.doubleValue() < 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        if (rhs.equals(Double.POSITIVE_INFINITY)) {
            return lhs.doubleValue() > 0 ? POSITIVE_ZERO : NEGATIVE_ZERO;
        }
        if (rhs.equals(Double.NEGATIVE_INFINITY)) {
            return lhs.doubleValue() < 0 ? POSITIVE_ZERO : NEGATIVE_ZERO;
        }
        double result = lhs.doubleValue() / rhs.doubleValue();
        return narrow(result);
    }

    Object min() {
        if (isBigIntOp()) {
            requireBothBigInt("-");
            return narrowBigInt(((BigInteger) lhs).subtract((BigInteger) rhs));
        }
        double result = lhs.doubleValue() - rhs.doubleValue();
        return narrow(result);
    }

    Object mod() {
        if (isBigIntOp()) {
            requireBothBigInt("%");
            BigInteger r = (BigInteger) rhs;
            if (r.signum() == 0) {
                throw JsErrorException.rangeError("Division by zero");
            }
            // Java BigInteger.remainder matches JS BigInt % semantics (sign follows dividend)
            return narrowBigInt(((BigInteger) lhs).remainder(r));
        }
        double result = lhs.doubleValue() % rhs.doubleValue();
        return narrow(result);
    }

    Object exp() {
        if (isBigIntOp()) {
            requireBothBigInt("**");
            BigInteger r = (BigInteger) rhs;
            if (r.signum() < 0) {
                throw JsErrorException.rangeError("Exponent must be non-negative");
            }
            return narrowBigInt(((BigInteger) lhs).pow(r.intValueExact()));
        }
        double result = Math.pow(lhs.doubleValue(), rhs.doubleValue());
        return narrow(result);
    }

    static Object add(Object lhs, Object rhs, CoreContext context) {
        // Spec evaluation of binary +: ToPrimitive both operands first (default hint),
        // then string-or-number dispatch on the *primitives*. ObjectLike on either side
        // is the rare case — primitives short-circuit through the existing fast path.
        if (lhs instanceof ObjectLike) {
            lhs = toPrimitive(lhs, "default", context);
            if (context != null && context.isError()) return UNDEFINED;
        }
        if (rhs instanceof ObjectLike) {
            rhs = toPrimitive(rhs, "default", context);
            if (context != null && context.isError()) return UNDEFINED;
        }
        if (lhs instanceof String || rhs instanceof String) {
            return lhs + "" + rhs;
        }
        // BigInt branch — pulled into a fast type test that fails on the common case
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            if (!(lhs instanceof BigInteger) || !(rhs instanceof BigInteger)) {
                throw JsErrorException.typeError(
                    "Cannot mix BigInt and other types, use explicit conversions (+)");
            }
            return narrowBigInt(((BigInteger) lhs).add((BigInteger) rhs));
        }
        Number lhsNum = objectToNumber(lhs);
        Number rhsNum = objectToNumber(rhs);
        double result = lhsNum.doubleValue() + rhsNum.doubleValue();
        return narrow(result);
    }

    // BigInt does NOT participate in `narrow` (which collapses to int/long/double).
    // Returning the BigInteger as-is preserves the bigint type identity through
    // arithmetic; downstream `typeOf` continues to report "bigint".
    static BigInteger narrowBigInt(BigInteger value) {
        return value;
    }

    // The step value for `++` / `--` of an operand. Plain Number gets the
    // Integer 1; BigInt gets BigInteger.ONE so the BigInt arithmetic path
    // is reached and `i++` doesn't TypeError on mixing types.
    static Object incDecStep(Object operand) {
        return operand instanceof BigInteger ? BigInteger.ONE : 1;
    }

    public static Number narrow(double d) {
        if (NEGATIVE_ZERO.equals(d)) {
            return d;
        }
        if (d % 1 != 0) {
            return d;
        }
        // Both bounds matter: a negative value < Integer.MIN_VALUE was previously
        // narrowed to int via `d <= MAX_VALUE`, which silently overflowed. Same for long.
        if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
            return (int) d;
        }
        if (d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
            return (long) d;
        }
        return d;
    }

    static JsValue toJsValue(Object o) {
        if (o == null) {
            return null;
        }
        return switch (o) {
            case String s -> new JsString(s);
            // BigInteger before Number — BigInteger extends Number; if we fell through
            // to JsNumber the prototype lookup would route to JsNumberPrototype.
            case BigInteger bi -> new JsBigInt(bi);
            case Number n -> new JsNumber(n);
            case Boolean b -> new JsBoolean(b);
            case Date d -> new JsDate(d);
            case Instant i -> new JsDate(i);
            case LocalDateTime ldt -> new JsDate(ldt);
            case LocalDate ld -> new JsDate(ld);
            case ZonedDateTime zdt -> new JsDate(zdt);
            case byte[] bytes -> new JsUint8Array(bytes);
            default -> null;
        };
    }

    // Convert Java native arrays (String[], int[], Object[], etc.) to JsArray
    // Note: byte[] is excluded as it has special handling (JsUint8Array)
    static JsArray toJsArray(Object o) {
        if (o != null && o.getClass().isArray() && !(o instanceof byte[])) {
            int length = Array.getLength(o);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(o, i));
            }
            return new JsArray(list);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static ObjectLike toObjectLike(Object o) {
        if (o instanceof ObjectLike ol) {
            return ol;
        }
        if (o instanceof List<?> list) {
            return new JsArray((List<Object>) list);
        }
        // Java native arrays (String[], int[], Object[], etc.)
        JsArray jsArray = toJsArray(o);
        if (jsArray != null) {
            return jsArray;
        }
        // XML Node: convert to Map structure for JS-style property access
        if (o instanceof Node node) {
            Object converted = Xml.toObject(node);
            if (converted instanceof Map<?, ?> map) {
                return new JsObject((Map<String, Object>) map);
            }
        }
        JsValue jsValue = toJsValue(o);
        return jsValue instanceof ObjectLike ol ? ol : null;
    }

    static Iterable<KeyValue> toIterable(Object o) {
        return toIterable(o, null);
    }

    /**
     * Spec-correct iteration variant — when {@code ctx} is non-null, accessor
     * descriptors invoke their getters during iteration. Used by
     * {@code Object.keys / values / entries / assign} so an accessor entry
     * surfaces its evaluated value (not {@code null}) in the resulting list.
     * Java-interop callers pass {@code null} (or use the no-arg overload) to
     * keep accessors as null at the host boundary.
     */
    @SuppressWarnings("unchecked")
    static Iterable<KeyValue> toIterable(Object o, CoreContext ctx) {
        // TODO strictly Objects are not iterable
        // Check JsArray first - it implements List but has its own jsEntries
        if (o instanceof JsArray jsArray) {
            return jsArray.jsEntries(ctx);
        }
        if (o instanceof JsObject jsObject) {
            return jsObject.jsEntries(ctx);
        }
        if (o instanceof List) {
            return new JsArray((List<Object>) o).jsEntries(ctx);
        }
        // Java native arrays (String[], int[], Object[], etc.)
        JsArray jsArray = toJsArray(o);
        if (jsArray != null) {
            return jsArray.jsEntries(ctx);
        }
        if (o instanceof Map) {
            return new JsObject((Map<String, Object>) o).jsEntries(ctx);
        }
        if (o instanceof String) {
            return new JsString((String) o).jsEntries(ctx);
        }
        return new JsObject().jsEntries(ctx);
    }

    public static boolean isTruthy(Object value) {
        if (value == null || value.equals(UNDEFINED) || value.equals(Double.NaN)) {
            return false;
        }
        // boxed primitives are always truthy (they are objects)
        if (value instanceof JsPrimitive) {
            return true;
        }
        if (value instanceof JsValue jv) {
            value = jv.getJavaValue();
        }
        return switch (value) {
            case Boolean b -> b;
            case Number number -> number.doubleValue() != 0;
            case String s -> !s.isEmpty();
            default -> true;
        };
    }

    static boolean isPrimitive(Object value) {
        if (value instanceof String
                || (value instanceof Number)
                || value instanceof Boolean) {
            return true;
        }
        if (value == null) {
            return true;
        }
        return value == UNDEFINED;
    }

    public static String typeOf(Object value) {
        if (value instanceof String) {
            return "string";
        }
        // Raw JsInvokable lambdas (parseInt, eval, Math.max, ...).
        if (value instanceof JsInvokable) {
            return "function";
        }
        // JsFunction + built-in constructor singletons (Error / TypeError /
        // … globals) self-report via isJsFunction() override.
        if (value instanceof JsObject jo && jo.isJsFunction()) {
            return "function";
        }
        // Raw JsCallable method refs exposed by Prototype.getBuiltinProperty
        // ((JsCallable) this::map, etc.). After R2, plain JsObject is no longer
        // JsCallable; JsArray and the boxed primitive wrappers (JsString /
        // JsNumber) still are, but they're handled by the
        // primitive/object-ish branches around this one.
        if (value instanceof JsCallable && !(value instanceof ObjectLike)) {
            return "function";
        }
        // Boxed primitives are objects
        if (value instanceof JsPrimitive) {
            return "object";
        }
        // BigInt before generic Number — BigInteger extends Number
        if (value instanceof BigInteger) {
            return "bigint";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value == UNDEFINED) {
            return "undefined";
        }
        return "object";
    }

    static boolean instanceOf(Object lhs, Object rhs) {
        // Handle built-in constructors by class comparison
        // JsArray doesn't extend JsObject, so handle it first
        if (rhs instanceof JsArray && lhs instanceof JsArray) {
            return true;
        }
        // For built-in types that implement JsCallable but aren't JsFunction
        // (JsRegex, JsDate — registered globals that double as instances).
        // Same-class comparison covers `new RegExp() instanceof RegExp` etc.
        // Error types no longer hit this — JsErrorConstructor IS a JsFunction
        // and instances walk the prototype chain at the bottom of this method.
        if (rhs instanceof JsCallable && !(rhs instanceof JsFunction)) {
            if (lhs != null && lhs.getClass().equals(rhs.getClass())) {
                return true;
            }
        }
        // JsValue: same class means same type
        if (lhs instanceof JsValue && rhs instanceof JsValue) {
            return lhs.getClass().equals(rhs.getClass());
        }
        // Walk prototype chain for any ObjectLike (JsObject, JsArray, JsString, etc.)
        if (lhs instanceof ObjectLike objectLhs && rhs instanceof ObjectLike objectRhs) {
            Object target = objectRhs.getMember("prototype");
            ObjectLike current = objectLhs.getPrototype();
            while (current != null) {
                if (current == target) {
                    return true;
                }
                current = current.getPrototype();
            }
        }
        return false;
    }

    /**
     * ECMAScript {@code ToString} abstract operation. Converts a value to its spec-defined
     * JavaScript string representation.
     * <ul>
     *   <li>{@code null} → {@code "null"}</li>
     *   <li>{@code undefined} → {@code "undefined"}</li>
     *   <li>primitives and {@link JsValue} → their natural string form</li>
     *   <li>{@link ObjectLike} → invokes {@code toString} via the prototype chain. The default
     *       {@link JsObjectPrototype} returns {@code "[object Object]"}; arrays return
     *       {@code this.join(",")}; functions / dates / regex return their specific forms;
     *       user-overridden {@code toString} is honored.</li>
     * </ul>
     * When {@code context} is {@code null} and the value is an {@link ObjectLike}, falls back
     * to {@code "[object Object]"} (the user-visible override cannot be invoked without one).
     */
    /**
     * ECMAScript {@code ToPrimitive} abstract operation. Coerces an object to a
     * primitive value by invoking {@code valueOf} / {@code toString} via the
     * prototype chain.
     * <p>
     * Hint is {@code "number"} (default — try valueOf first) or {@code "string"}
     * (try toString first). Spec rule: the first method that returns a non-object
     * wins; if both return objects, throws TypeError.
     * <p>
     * Errors raised by {@code valueOf} / {@code toString} flow through the supplied
     * {@code context} (same pattern as {@link #toStringCoerce}); callers must check
     * {@code context.isError()} after invoking. When error state is set, returns
     * {@link #UNDEFINED} as a placeholder — the caller should bail.
     * <p>
     * Hot-path note: every call site already had to dispatch on type for primitives;
     * this method only enters the ObjectLike branch on the rare case where the input
     * is genuinely an object.
     */
    static Object toPrimitive(Object value, String hint, CoreContext context) {
        if (value == null || value == UNDEFINED) {
            return value;
        }
        // Boxed primitives unwrap directly — equivalent to spec valueOf for these,
        // but cheaper than a method dispatch.
        if (value instanceof JsPrimitive jp) {
            return jp.getJavaValue();
        }
        if (value instanceof BigInteger || isPrimitive(value)) {
            return value;
        }
        // ObjectLike (or Java-native types we wrap): run OrdinaryToPrimitive.
        ObjectLike ol = (value instanceof ObjectLike) ? (ObjectLike) value : toObjectLike(value);
        if (ol == null || context == null) {
            // No prototype dispatch possible — return as-is and let the caller cope.
            return value;
        }
        // Spec: @@toPrimitive (the well-known Symbol.toPrimitive method) takes precedence
        // over OrdinaryToPrimitive's valueOf/toString dispatch. Hint passed verbatim
        // ("string" | "number" | "default"). Result must be a primitive; an object result
        // is a TypeError per spec. Set-but-not-callable is also a TypeError (GetMethod).
        Object exotic = ol.getMember("@@toPrimitive");
        if (exotic != null && exotic != UNDEFINED) {
            if (!(exotic instanceof JsCallable jsc)) {
                throw JsErrorException.typeError("Symbol.toPrimitive method is not callable");
            }
            CoreContext callCtx = new CoreContext(context, null, null);
            callCtx.thisObject = ol;
            String hintArg = hint == null ? "default" : hint;
            Object r = jsc.call(callCtx, new Object[]{hintArg});
            if (callCtx.isError()) {
                context.updateFrom(callCtx);
                return UNDEFINED;
            }
            if (r == null || r == UNDEFINED || isPrimitive(r) || r instanceof BigInteger) {
                return r;
            }
            throw JsErrorException.typeError("Cannot convert object to primitive value");
        }
        return ordinaryToPrimitive(ol, hint, context);
    }

    /**
     * Spec §7.1.1.1 OrdinaryToPrimitive — the valueOf/toString-only dispatch
     * without the @@toPrimitive check. Used by {@link #toPrimitive} as the
     * fallback path, and by built-in @@toPrimitive methods (e.g. Date) that
     * need to invoke OrdinaryToPrimitive on themselves without re-entering
     * the @@toPrimitive lookup.
     */
    static Object ordinaryToPrimitive(ObjectLike ol, String hint, CoreContext context) {
        String[] order = "string".equals(hint)
                ? new String[]{"toString", "valueOf"}
                : new String[]{"valueOf", "toString"};
        for (String methodName : order) {
            Object fn = ol.getMember(methodName);
            if (!(fn instanceof JsCallable jsc)) {
                continue;
            }
            CoreContext callCtx = new CoreContext(context, null, null);
            callCtx.thisObject = ol;
            Object r = jsc.call(callCtx, new Object[0]);
            if (callCtx.isError()) {
                context.updateFrom(callCtx);
                return UNDEFINED;
            }
            // Spec: a primitive (or BigInt) wins; an object falls through to the next method.
            if (r == null || r == UNDEFINED || isPrimitive(r) || r instanceof BigInteger) {
                return r;
            }
        }
        throw JsErrorException.typeError("Cannot convert object to primitive value");
    }

    /**
     * Spec {@code ToPropertyKey} (§7.1.18) — converts an arbitrary value to
     * the canonical string form used as a property name. Most callers pass
     * an already-string key, so the common path is a single instanceof.
     * The number cases follow {@code Number::toString} (§6.1.6.1.13):
     * {@code -0} → {@code "0"}, {@code NaN} → {@code "NaN"}, infinities,
     * and integer-valued doubles drop the {@code ".0"} (so
     * {@code Double.toString(30.0)}'s {@code "30.0"} doesn't leak into
     * property-key comparisons).
     * <p>
     * {@code BigInt} keys, accessor-descriptor receivers, and
     * {@code Symbol.toPrimitive} are out of scope here — symbol keys are
     * the broader Slice #7 work.
     */
    public static String toPropertyKey(Object o) {
        return toPropertyKey(o, null);
    }

    /**
     * Context-aware {@code ToPropertyKey} (§7.1.19). Adds the spec's
     * {@code ToPrimitive(hint=string) → ToString} dispatch for {@link ObjectLike}
     * receivers — so a callback that returns {@code {toString(){return 1}}}
     * coerces to {@code "1"}, and a {@code toString} that throws propagates the
     * error via {@code ctx.error} (matching IfAbruptCloseIterator semantics).
     * Without ctx, ObjectLike values fall back to {@link Object#toString} as
     * before.
     */
    public static String toPropertyKey(Object o, CoreContext ctx) {
        if (o == null) return "null";
        if (o instanceof String s) return s;
        if (o == UNDEFINED) return "undefined";
        if (o instanceof JsString js) return js.toString();
        if (o instanceof Number n) return numberToPropertyKey(n);
        if (o instanceof Boolean) return o.toString();
        if (o instanceof ObjectLike && ctx != null) {
            return toStringCoerce(o, ctx);
        }
        return o.toString();
    }

    private static String numberToPropertyKey(Number n) {
        if (n instanceof BigInteger bi) return bi.toString();
        double d = n.doubleValue();
        if (Double.isNaN(d)) return "NaN";
        if (d == 0) return "0";
        if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
        double abs = Math.abs(d);
        // Plain decimal range per spec Number::toString (§6.1.6.1.13):
        // 1e-6 <= |d| < 1e21 emits without exponential notation.
        if (abs < 1e21 && abs >= 1e-6) {
            if (d == Math.floor(d)) {
                // Integer-valued: drop fractional part. Long fits up to 2^63;
                // BigDecimal handles the [2^63, 1e21) tail (where (long) d
                // saturates and Double.toString switches to "1.0E20").
                long l = (long) d;
                if ((double) l == d) return Long.toString(l);
                return java.math.BigDecimal.valueOf(d).toBigInteger().toString();
            }
            return java.math.BigDecimal.valueOf(d).toPlainString();
        }
        // Exponential range: ES form is <mantissa>e<sign><exp>. Java's
        // Double.toString uses 'E', no '+' sign, and a trailing ".0" on
        // integer-valued mantissas — reshape to spec form.
        String s = Double.toString(d);
        int eIdx = s.indexOf('E');
        if (eIdx < 0) return s;
        String mantissa = s.substring(0, eIdx);
        String exp = s.substring(eIdx + 1);
        if (mantissa.endsWith(".0")) mantissa = mantissa.substring(0, mantissa.length() - 2);
        if (exp.charAt(0) != '+' && exp.charAt(0) != '-') exp = "+" + exp;
        return mantissa + "e" + exp;
    }

    /**
     * Spec {@code RequireObjectCoercible} (§7.2.1) — gate at the top of every
     * built-in whose receiver feeds {@code ToObject} / {@code ToString} (e.g.
     * {@code String.prototype.*}). null / undefined throw TypeError; everything
     * else passes through. {@code methodName} is woven into the message so the
     * thrown error reads like a JS engine's, not a generic NPE.
     */
    public static void requireObjectCoercible(Object value, String methodName) {
        if (value == null || value == UNDEFINED) {
            throw JsErrorException.typeError(methodName + " called on null or undefined");
        }
    }

    public static String toStringCoerce(Object o, CoreContext context) {
        if (o == null) {
            return "null";
        }
        if (o == UNDEFINED) {
            return "undefined";
        }
        if (isPrimitive(o) || o instanceof JsValue) {
            return o.toString();
        }
        // Java-native types (Map, List, raw arrays, XML Node, Date) are wrapped so
        // their JS toString dispatches via the correct prototype.
        ObjectLike ol = (o instanceof ObjectLike) ? (ObjectLike) o : toObjectLike(o);
        if (ol != null) {
            if (context != null) {
                Object fn = ol.getMember("toString");
                if (fn instanceof JsCallable jsc) {
                    CoreContext callCtx = new CoreContext(context, null, null);
                    callCtx.thisObject = ol;
                    Object r = jsc.call(callCtx, new Object[0]);
                    // Propagate a throw from the callee via the context so that
                    // the original JS value (including custom classes like
                    // Test262Error) retains its identity when a surrounding JS
                    // try/catch reads `thrown.constructor`. A Java-exception
                    // conversion here would flatten the value to a generic Error.
                    if (callCtx.isError()) {
                        context.updateFrom(callCtx);
                        return "";
                    }
                    if (r instanceof String s) {
                        return s;
                    }
                    if (r != null && r != UNDEFINED && !(r instanceof ObjectLike)) {
                        return r.toString();
                    }
                }
            }
            return "[object Object]";
        }
        return o.toString();
    }

}
