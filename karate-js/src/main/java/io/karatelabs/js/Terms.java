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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Terms {

    // JsUndefined singleton for undefined - used for identity comparison
    public static final JsUndefined UNDEFINED = JsUndefined.INSTANCE;

    static final Number NEGATIVE_ZERO = -0.0;

    private Terms() {
        // static holder - the binary operators take (lhs, rhs) directly
    }

    // True iff either operand is BigInt. Fast path: most call sites have
    // plain Number operands and this returns false on the first instanceof.
    private static boolean isBigIntOp(Number lhs, Number rhs) {
        return lhs instanceof BigInteger || rhs instanceof BigInteger;
    }

    // Spec: arithmetic ops require both operands to be BigInt or both Number;
    // mixing throws TypeError. Centralized check fires only on the rare path.
    private static void requireBothBigInt(Number lhs, Number rhs, String opName) {
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
        if (radix != 0 && (radix < 2 || radix > 36)) {
            return Double.NaN;
        }
        // Spec §19.2.5 step 10: the `0x`/`0X` prefix is stripped both when the
        // radix is unspecified (it then implies 16) and when 16 was passed
        // explicitly — `parseInt('0x1f', 16)` is 31, not 0. Any other radix
        // leaves the prefix alone, so `parseInt('0x1f', 10)` stops at the `x`.
        if ((radix == 0 || radix == 16) && radixPrefix(str, index) == 16) {
            radix = 16;
            index += 2;
        } else if (radix == 0) {
            radix = 10;
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
        if (str.charAt(index) == '-' || str.charAt(index) == '+') {
            index++;
        }
        // §19.2.4: a signed "Infinity" prefix is a valid StrDecimalLiteral for
        // parseFloat; radix prefixes (0x…) are NOT — parseFloat("0x10") reads
        // the leading 0 and stops at 'x'.
        if (!asInt && str.startsWith("Infinity", index)) {
            return str.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (asInt) {
            Number radix = fromRadixPrefix(str);
            if (radix != null) {
                return narrow(radix.doubleValue());
            }
        }
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
            foundDigit = true;
            index++;
        }
        if (!foundDigit) {
            return Double.NaN;
        }
        // ExponentPart is only consumed when it is well-formed: a lone trailing
        // 'e' (or an 'e' with no digits after the sign) is not part of the
        // literal, so `parseFloat('1e')` is 1 rather than NaN.
        if (!asInt && index < str.length() && (str.charAt(index) == 'e' || str.charAt(index) == 'E')) {
            int p = index + 1;
            if (p < str.length() && (str.charAt(p) == '+' || str.charAt(p) == '-')) {
                p++;
            }
            int digitsStart = p;
            while (p < str.length() && str.charAt(p) >= '0' && str.charAt(p) <= '9') {
                p++;
            }
            if (p > digitsStart) {
                index = p;
            }
        }
        // Delegate the digits → double rounding to the JDK: the earlier
        // hand-rolled accumulation lost precision on long fractions and
        // overflowed the integer part past 2^63.
        return narrow(Double.parseDouble(str.substring(0, index)));
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

    /**
     * Value of a literal token, memoized on the token. A literal inside a loop body is evaluated
     * on every iteration, and without this each evaluation re-ran {@code Double.parseDouble} (or
     * the BigInt / string-unquoting path) over text that cannot have changed.
     */
    public static Object literalValue(Token token) {
        Object cached = token.getCachedLiteral();
        if (cached != null) {
            return cached;
        }
        Object value = computeLiteralValue(token);
        if (value != null) { // `null` is only ever the NULL keyword, whose branch parses nothing
            token.cacheLiteral(value);
        }
        return value;
    }

    private static Object computeLiteralValue(Token token) {
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
        int radix = radixPrefix(s);
        return radix == 0 ? new BigInteger(s) : new BigInteger(s.substring(2), radix);
    }

    // Radix implied by an `0x`/`0b`/`0o` prefix at {@code from}, 0 when there is
    // none. A bare prefix with no digits after it still counts — the callers
    // then fail to parse the empty remainder, which is the spec answer
    // (`parseInt('0x', 16)` and `Number('0x')` are both NaN).
    static int radixPrefix(String s, int from) {
        if (s.length() > from + 1 && s.charAt(from) == '0') {
            char p = s.charAt(from + 1);
            if (p == 'x' || p == 'X') return 16;
            if (p == 'b' || p == 'B') return 2;
            if (p == 'o' || p == 'O') return 8;
        }
        return 0;
    }

    private static int radixPrefix(String s) {
        return radixPrefix(s, 0);
    }

    /**
     * Spec {@code ToUint32} (§7.1.7) — for the spec arguments typed as a 32-bit
     * unsigned count, where a negative input wraps to a huge positive one
     * ({@code 'a,b'.split(',', -1)} keeps every segment).
     */
    static long toUint32(Object value) {
        double d = objectToNumber(value).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 0;
        }
        double truncated = d < 0 ? Math.ceil(d) : Math.floor(d);
        return (long) (truncated % 4294967296.0) & 0xFFFFFFFFL;
    }

    static Number fromRadixPrefix(String text) {
        int radix = radixPrefix(text);
        if (radix == 0) {
            return null;
        }
        try {
            return narrow(Long.parseLong(text.substring(2), radix));
        } catch (NumberFormatException nfe) {
            return Double.NaN;
        }
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
            // doubleValue, not equals: the boxes can be different Number types
            // (Integer 0 vs Double -0.0) for the same mathematical value
            return objectToNumber(lhs).doubleValue() == objectToNumber(rhs).doubleValue();
        }
        return false;
    }

    private static boolean bigIntLooseEq(Object lhs, Object rhs) {
        BigInteger bi;
        Object other;
        if (lhs instanceof BigInteger b) { bi = b; other = rhs; }
        else { bi = (BigInteger) rhs; other = lhs; }
        if (other instanceof String s) {
            BigInteger parsed = stringToBigInt(s);
            return parsed != null && bi.equals(parsed);
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

    // Spec StringToBigInt (§7.1.14): a whitespace-only string is 0n, anything
    // that is not a well-formed integer literal is undefined — null here, which
    // the BigInt-vs-String legs surface as "not equal" / "not comparable".
    private static BigInteger stringToBigInt(String s) {
        String trimmed = stripJsWhiteSpace(s);
        if (trimmed.isEmpty()) {
            return BigInteger.ZERO;
        }
        int radix = radixPrefix(trimmed);
        try {
            return radix == 0 ? new BigInteger(trimmed) : new BigInteger(trimmed.substring(2), radix);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNotANumber(Object o) {
        return o instanceof Double d && d.isNaN();
    }

    /**
     * Spec {@code IsStrictlyEqual} (§7.2.15) for the {@code ===} / {@code !==}
     * operators. {@link #eq}{@code (lhs, rhs, true)} is SameValueZero — Java's
     * {@code Double.equals} holds {@code NaN} equal to itself, which Map / Set
     * key lookup wants and the operator does not.
     */
    static boolean strictEq(Object lhs, Object rhs) {
        if (isNotANumber(lhs) || isNotANumber(rhs)) {
            return false;
        }
        return eq(lhs, rhs, true);
    }

    /**
     * Spec {@code IsLooselyEqual} (§7.2.14) for {@code ==} / {@code !=}. An
     * ObjectLike operand facing a primitive one is ToPrimitive'd with the
     * default hint and the ladder re-entered on the result; two objects compare
     * by reference. Errors raised by the user's {@code @@toPrimitive} /
     * {@code valueOf} / {@code toString} flow through {@code context.isError()},
     * so the returned value is meaningless once that is set.
     */
    static boolean looseEq(Object lhs, Object rhs, CoreContext context) {
        // a symbol is a primitive that equals only itself — never ToPrimitive'd,
        // so `sym == 'x'` is false rather than the coercion TypeError
        if (lhs instanceof JsSymbol || rhs instanceof JsSymbol) {
            return lhs == rhs;
        }
        if (lhs instanceof ObjectLike) {
            if (rhs instanceof ObjectLike) {
                return lhs == rhs;
            }
            if (rhs == null || rhs == UNDEFINED) {
                return false;
            }
            lhs = toPrimitive(lhs, "default", context);
            if (context != null && context.isError()) {
                return false;
            }
        } else if (rhs instanceof ObjectLike) {
            if (lhs == null || lhs == UNDEFINED) {
                return false;
            }
            rhs = toPrimitive(rhs, "default", context);
            if (context != null && context.isError()) {
                return false;
            }
        }
        if (isNotANumber(lhs) || isNotANumber(rhs)) {
            return false;
        }
        // §7.2.14 steps 8-9: a Boolean operand becomes a Number before the ladder
        // is re-entered, which is what puts `'' == false` on the String~Number leg
        if (lhs instanceof Boolean b) {
            lhs = b ? 1 : 0;
        }
        if (rhs instanceof Boolean b) {
            rhs = b ? 1 : 0;
        }
        return eq(lhs, rhs, false);
    }

    // Spec IsLessThan yields undefined when either operand is NaN; every
    // relational operator maps that to false, so it needs a third state.
    static final int LESS_UNDEFINED = -1;

    /**
     * Spec {@code IsLessThan} (§7.2.13), returning {@code 1} / {@code 0} /
     * {@link #LESS_UNDEFINED}. {@code leftFirst} is the spec's LeftFirst flag:
     * {@code x > y} is defined as {@code IsLessThan(y, x, false)}, and clearing
     * the flag is what keeps the two ToPrimitive calls in source order.
     * Two string primitives compare as strings; everything else numerically.
     */
    static int isLessThan(Object lhs, Object rhs, boolean leftFirst, CoreContext context) {
        if (lhs instanceof Number ln && rhs instanceof Number rn && !isBigIntOp(ln, rn)) {
            double a = ln.doubleValue();
            double b = rn.doubleValue();
            if (Double.isNaN(a) || Double.isNaN(b)) {
                return LESS_UNDEFINED;
            }
            return a < b ? 1 : 0;
        }
        if (lhs instanceof ObjectLike || rhs instanceof ObjectLike) {
            if (leftFirst) {
                lhs = toPrimitive(lhs, "number", context);
                if (context != null && context.isError()) {
                    return LESS_UNDEFINED;
                }
                rhs = toPrimitive(rhs, "number", context);
            } else {
                rhs = toPrimitive(rhs, "number", context);
                if (context != null && context.isError()) {
                    return LESS_UNDEFINED;
                }
                lhs = toPrimitive(lhs, "number", context);
            }
            if (context != null && context.isError()) {
                return LESS_UNDEFINED;
            }
        }
        if (lhs instanceof String ls && rhs instanceof String rs) {
            return ls.compareTo(rs) < 0 ? 1 : 0;
        }
        if (lhs instanceof BigInteger || rhs instanceof BigInteger) {
            // a non-BigInt, non-String operand is ToNumeric'd first so the
            // mixed-type compare below sees a magnitude it can order
            int c = bigIntCompare(
                    lhs instanceof BigInteger || lhs instanceof String ? lhs : objectToNumber(lhs),
                    rhs instanceof BigInteger || rhs instanceof String ? rhs : objectToNumber(rhs));
            return c == Integer.MIN_VALUE ? LESS_UNDEFINED : (c < 0 ? 1 : 0);
        }
        double a = objectToNumber(lhs).doubleValue();
        double b = objectToNumber(rhs).doubleValue();
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return LESS_UNDEFINED;
        }
        return a < b ? 1 : 0;
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
        if (other instanceof String s) {
            BigInteger parsed = stringToBigInt(s);
            if (parsed == null) {
                return Integer.MIN_VALUE;
            }
            int c = bi.compareTo(parsed);
            return swapped ? -c : c;
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

    /**
     * Spec ToNumeric for a numeric-operator operand: an ObjectLike routes
     * through {@code @@toPrimitive} / {@code valueOf} / {@code toString} with
     * the live ctx — an abrupt completion in those surfaces via
     * {@code context.isError()}, and callers must check and bail with
     * {@link #UNDEFINED} so nothing evaluates past the throw. Everything else
     * keeps the {@link #objectToNumber} fast path untouched.
     */
    private static Number toNumericOperand(Object o, CoreContext context) {
        if (o instanceof ObjectLike) {
            Object prim = toPrimitive(o, "number", context);
            if (context != null && context.isError()) {
                return Double.NaN;
            }
            return objectToNumber(prim);
        }
        return objectToNumber(o);
    }

    static Object bitAnd(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "&");
            return narrowBigInt(((BigInteger) lhs).and((BigInteger) rhs));
        }
        return lhs.intValue() & rhs.intValue();
    }

    static Object bitOr(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "|");
            return narrowBigInt(((BigInteger) lhs).or((BigInteger) rhs));
        }
        return lhs.intValue() | rhs.intValue();
    }

    static Object bitXor(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "^");
            return narrowBigInt(((BigInteger) lhs).xor((BigInteger) rhs));
        }
        return lhs.intValue() ^ rhs.intValue();
    }

    static Object bitShiftRight(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, ">>");
            return narrowBigInt(((BigInteger) lhs).shiftRight(((BigInteger) rhs).intValueExact()));
        }
        return lhs.intValue() >> rhs.intValue();
    }

    static Object bitShiftLeft(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "<<");
            return narrowBigInt(((BigInteger) lhs).shiftLeft(((BigInteger) rhs).intValueExact()));
        }
        return lhs.intValue() << rhs.intValue();
    }

    static Object bitShiftRightUnsigned(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            // spec: unsigned right shift on BigInt always TypeError, even when both operands are BigInt
            throw JsErrorException.typeError("BigInts have no unsigned right shift, use >> instead");
        }
        return narrow((lhs.intValue() & 0xFFFFFFFFL) >>> rhs.intValue());
    }

    static Object bitNot(Object value, CoreContext context) {
        Number number = toNumericOperand(value, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (number instanceof BigInteger bi) {
            return narrowBigInt(bi.not());
        }
        return ~number.intValue();
    }

    static Object mul(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "*");
            return narrowBigInt(((BigInteger) lhs).multiply((BigInteger) rhs));
        }
        double result = lhs.doubleValue() * rhs.doubleValue();
        return narrow(result);
    }

    static Object div(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "/");
            BigInteger r = (BigInteger) rhs;
            if (r.signum() == 0) {
                throw JsErrorException.rangeError("Division by zero");
            }
            return narrowBigInt(((BigInteger) lhs).divide(r));
        }
        // no special-casing for zero / Infinity operands: IEEE 754 double division is
        // exactly what the spec's Number::divide mandates, and narrow() preserves -0.0
        double result = lhs.doubleValue() / rhs.doubleValue();
        return narrow(result);
    }

    static Object min(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "-");
            return narrowBigInt(((BigInteger) lhs).subtract((BigInteger) rhs));
        }
        double result = lhs.doubleValue() - rhs.doubleValue();
        return narrow(result);
    }

    static Object mod(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "%");
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

    static Object exp(Object lhsObject, Object rhsObject, CoreContext context) {
        Number lhs = toNumericOperand(lhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        Number rhs = toNumericOperand(rhsObject, context);
        if (context != null && context.isError()) return UNDEFINED;
        if (isBigIntOp(lhs, rhs)) {
            requireBothBigInt(lhs, rhs, "**");
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
            return concatOperand(lhs) + concatOperand(rhs);
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

    private static String concatOperand(Object o) {
        if (o instanceof String s) return s;
        if (o instanceof Number n) return numberToString(n);
        return String.valueOf(o);
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
        // One long-roundtrip compare replaces the old NEGATIVE_ZERO.equals
        // (which autoboxed a Double per call — this method runs after every
        // arithmetic op) and the `d % 1 != 0` fmod: (long) d != d rejects
        // fractional values, NaN, ±Infinity, AND out-of-long-range magnitudes
        // in one test. It is also exact at the ±2^63 boundary, where the old
        // `d <= Long.MAX_VALUE` promoted the bound to 2^63 and (long)-cast
        // 2^63 to Long.MAX_VALUE — a silent off-by-one.
        long l = (long) d;
        if (l != d) {
            return d;
        }
        if (l == 0 && Double.doubleToRawLongBits(d) != 0) {
            return d; // -0.0 stays a double (observable via 1/x)
        }
        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return (int) l;
        }
        return l;
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
        if (o instanceof ObjectLike obj) {
            // Generic ObjectLike host bridges (e.g. fall-through underscore
            // views, lazy proxies, custom Map adapters). Read via toMap()
            // so Object.keys/values/entries and for-in see the live state.
            return new JsObject(obj.toMap()).jsEntries(ctx);
        }
        return new JsObject().jsEntries(ctx);
    }

    /**
     * Spec §7.3.26 CopyDataProperties — the single seam behind
     * {@code Object.assign}, object spread {@code {...src}} and destructuring
     * rest {@code {a, ...rest}}. Own enumerable string keys only, in
     * OrdinaryOwnPropertyKeys order; an accessor's getter is invoked at copy
     * time and its result stored as a *data* property. A null/undefined source
     * is a no-op; primitives contribute whatever their ToObject wrapper
     * exposes (a string's characters by index, nothing for number/boolean).
     * {@code excluded} is the rest pattern's already-bound key set, or null.
     * <p>
     * Own enumerable <i>symbol</i> keys copy too (the spec copies both
     * partitions) — only reachable when both sides are {@link JsObject}, which
     * is where the symbol store lives.
     */
    static void copyDataProperties(Map<String, Object> target, Object source, Set<String> excluded, CoreContext ctx) {
        if (source == null || source == UNDEFINED || (ctx != null && ctx.isError())) {
            return;
        }
        for (KeyValue kv : toIterable(source, ctx)) {
            if (ctx != null && ctx.isError()) { // a getter threw — nothing copies past it
                return;
            }
            if (excluded == null || !excluded.contains(kv.key())) {
                target.put(kv.key(), kv.value());
            }
        }
        if (source instanceof JsObject from && target instanceof JsObject to) {
            // CreateDataProperty, not Set — spread never runs a target setter
            for (JsSymbol sym : from.ownEnumerableSymbols()) {
                if (ctx != null && ctx.isError()) {
                    return;
                }
                Object v = from.getSymbol(sym, ctx);
                if (ctx != null && ctx.isError()) { // a getter threw — nothing copies past it
                    return;
                }
                to.defineOwnSymbol(sym, v, PropertySlot.ATTRS_DEFAULT);
            }
        }
    }

    /**
     * Spec §14.7.5.6 EnumerateObjectProperties — back-end of {@code for...in}.
     * Walks the {@code [[GetPrototypeOf]]} chain rooted at {@code o},
     * yielding each enumerable own string key once, dedup'd by name (closer
     * receiver wins). Receiver-level entries carry their resolved value (so
     * existing for-in callers that read the {@code KeyValue#value()} keep
     * working); inherited entries carry {@code null} since for-in binds
     * keys only.
     * <p>
     * Distinct from {@link #toIterable}, which yields own enumerable
     * properties only — the back-end of {@code Object.keys / values /
     * entries / assign}. Only for-in walks the chain.
     * <p>
     * Limitation vs spec: a non-enumerable own key at a closer level does
     * not currently shadow a same-named inherited enumerable key. None of
     * the test262 paths exercising for-in over inherited properties reach
     * this edge case today; revisit when one surfaces.
     */
    static Iterable<KeyValue> forInIterable(Object o, CoreContext ctx) {
        if (o == null || o == UNDEFINED) {
            return Collections.emptyList();
        }
        return () -> {
            List<KeyValue> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int yieldCount = 0;
            // Receiver-level: full toIterable so values resolve via the
            // existing seam (accessor getters fire when ctx is non-null).
            for (KeyValue kv : toIterable(o, ctx)) {
                if (seen.add(kv.key())) {
                    out.add(new KeyValue(o, yieldCount++, kv.key(), kv.value()));
                }
            }
            // Inherited levels: keys only. Walks ObjectLike.getPrototype
            // chain — terminates at null (top of Object.prototype chain or
            // a null-proto object made via Object.create(null)).
            ObjectLike cur = (o instanceof ObjectLike ol) ? ol.getPrototype() : null;
            while (cur != null) {
                for (String key : enumerableOwnKeysAtLevel(cur)) {
                    if (seen.add(key)) {
                        out.add(new KeyValue(o, yieldCount++, key, null));
                    }
                }
                cur = cur.getPrototype();
            }
            return out.iterator();
        };
    }

    /** Enumerable own string keys at a single chain level — used by
     *  {@link #forInIterable} during the inherited-level walk where values
     *  aren't needed. JsObject / JsArray reuse their {@code jsEntries}
     *  iteration (already filtered + spec-ordered);
     *  {@link Prototype} consults {@code userProps} via the enumerable bit
     *  on {@link Prototype#getOwnAttrs}. */
    private static Iterable<String> enumerableOwnKeysAtLevel(ObjectLike ol) {
        if (ol instanceof JsArray ja) {
            Set<String> out = new LinkedHashSet<>();
            for (KeyValue kv : ja.jsEntries(null)) out.add(kv.key());
            return out;
        }
        if (ol instanceof JsObject jo) {
            Set<String> out = new LinkedHashSet<>();
            for (KeyValue kv : jo.jsEntries(null)) out.add(kv.key());
            return out;
        }
        if (ol instanceof Prototype p) {
            Set<String> out = new LinkedHashSet<>();
            // Prototype.toMap surfaces userProps; built-in methods aren't in
            // toMap and default to non-enumerable, so they're correctly
            // excluded. Userland defineProperty entries with enumerable:true
            // surface here.
            for (String name : p.toMap().keySet()) {
                if ((p.getOwnAttrs(name) & PropertySlot.ENUMERABLE) != 0) {
                    out.add(name);
                }
            }
            return out;
        }
        return Collections.emptyList();
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
        // Before the object branches — a symbol is a JsObject underneath.
        if (value instanceof JsSymbol) {
            return "symbol";
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
     * ECMA relational {@code in} — returns {@code true} iff {@code rhs} (or its
     * prototype chain) has a property named {@code ToPropertyKey(lhs)}.
     * Throws {@link JsErrorException#typeError} when {@code rhs} is not an
     * object (per spec §13.10.1 step 7), since the {@code [[HasProperty]]}
     * internal method is only defined on objects.
     */
    static boolean in(Object lhs, Object rhs) {
        if (!(rhs instanceof ObjectLike obj)) {
            throw JsErrorException.typeError(
                    "Cannot use 'in' operator to search for '"
                            + String.valueOf(lhs) + "' in " + String.valueOf(rhs));
        }
        JsSymbol sym = JsSymbol.keyedBy(lhs);
        if (sym != null) {
            ObjectLike walk = obj;
            while (walk != null) {
                if (walk instanceof JsObject jo && jo.hasSymbol(sym)) {
                    return true;
                }
                walk = walk.getPrototype();
            }
            return false;
        }
        String key = toPropertyKey(lhs);
        ObjectLike current = obj;
        while (current != null) {
            if (current.isOwnProperty(key)) {
                return true;
            }
            current = current.getPrototype();
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
        // §7.1.1: a symbol IS a primitive, so it never runs OrdinaryToPrimitive.
        // Every arithmetic / string coercion that reaches it throws (`sym + ''`).
        if (value instanceof JsSymbol) {
            throw JsErrorException.typeError("Cannot convert a Symbol value to a string");
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
        // A well-known symbol IS its engine-internal string key. A minted one has
        // none — every property site routes it to the symbol store before here,
        // so this is only reached by a non-property coercion, where the spec's
        // SymbolDescriptiveString is the least surprising answer.
        if (o instanceof JsSymbol sym) return sym.toString();
        if (o == UNDEFINED) return "undefined";
        if (o instanceof JsString js) return js.toString();
        if (o instanceof Number n) return numberToString(n);
        if (o instanceof Boolean) return o.toString();
        if (ctx != null) {
            // Spec §7.1.18 ToPropertyKey: ToPrimitive(hint string) → ToString.
            // ordinaryToPrimitive tries toString then valueOf, throws TypeError
            // when neither yields a primitive (test262
            // Object/defineProperty/15.2.3.6-2-47). Recurse on the primitive so
            // numbers/booleans go through their own spec-shaped string path.
            Object prim = toPrimitive(o, "string", ctx);
            if (ctx.isError()) return "";
            if (prim != o) return toPropertyKey(prim, ctx);
        }
        return o.toString();
    }

    /**
     * Spec {@code Number::toString} (§6.1.6.1.13) — the single seam every
     * user-visible number → string site routes through (ToPropertyKey,
     * ToString coercion, {@code Number.prototype.toString}, JSON output).
     * Java's {@code Double.toString} disagrees at both ends of the range
     * ({@code 1.0E21} vs {@code 1e+21}, {@code -0.0} vs {@code 0},
     * {@code 30.0} vs {@code 30}).
     * <p>
     * On the interpreter hot path (property keys, string concat), so the
     * int / long cases short-circuit ahead of any double inspection —
     * {@link #narrow} collapses most engine values to those types.
     */
    public static String numberToString(Number n) {
        if (n instanceof Integer i) return Integer.toString(i);
        if (n instanceof Long l) return Long.toString(l);
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
            // stripTrailingZeros so e.g. 0.000001 yields "0.000001" rather
            // than "0.0000010" (Double.toString → "1.0E-6" → BigDecimal scale 7
            // with unscaled 10; trailing zero on the mantissa leaks through
            // toPlainString without the strip).
            return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
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
        if (o instanceof String s) {
            return s;
        }
        // §7.1.17 ToString throws for a symbol. String(sym) is the one operation
        // that does not (JsString.getObject special-cases it), and ToPropertyKey
        // has its own branch above, so both stay reachable.
        if (o instanceof JsSymbol) {
            throw JsErrorException.typeError("Cannot convert a Symbol value to a string");
        }
        if (o instanceof Number n) {
            return numberToString(n);
        }
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
