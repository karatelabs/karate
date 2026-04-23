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
        Number hex = fromHex(str);
        if (hex != null) {
            return narrow(hex.doubleValue());
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
            case String s -> toNumber(s.trim());
            case null -> 0;
            // includes undefined
            default -> Double.NaN;
        };
    }

    public static Number toNumber(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return narrow(Double.parseDouble(text));
        } catch (Exception e) {
            Number hex = fromHex(text);
            return hex == null ? Double.NaN : narrow(hex.doubleValue());
        }
    }

    public static Object literalValue(Token token) {
        return switch (token.type) {
            case S_STRING, D_STRING -> {
                String text = token.getText();
                yield text.substring(1, text.length() - 1);
            }
            case NUMBER -> toNumber(token.getText());
            case TRUE -> true;
            case FALSE -> false;
            default -> null; // includes NULL
        };
    }

    static Number fromHex(String text) {
        if (text.charAt(0) == '0') {
            char second = text.charAt(1);
            if (second == 'x' || second == 'X') { // hex
                long longValue = Long.parseLong(text.substring(2), 16);
                return narrow(longValue);
            }
        }
        return null;
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
        if (lhs instanceof Number || rhs instanceof Number) { // coerce to number
            Terms terms = new Terms(lhs, rhs);
            return terms.lhs.equals(terms.rhs);
        }
        return false;
    }

    static boolean lt(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() < terms.rhs.doubleValue();
    }

    static boolean gt(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() > terms.rhs.doubleValue();
    }

    static boolean ltEq(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() <= terms.rhs.doubleValue();
    }

    static boolean gtEq(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() >= terms.rhs.doubleValue();
    }

    Object bitAnd() {
        return lhs.intValue() & rhs.intValue();
    }

    Object bitOr() {
        return lhs.intValue() | rhs.intValue();
    }

    Object bitXor() {
        return lhs.intValue() ^ rhs.intValue();
    }

    Object bitShiftRight() {
        return lhs.intValue() >> rhs.intValue();
    }

    Object bitShiftLeft() {
        return lhs.intValue() << rhs.intValue();
    }

    Object bitShiftRightUnsigned() {
        return narrow((lhs.intValue() & 0xFFFFFFFFL) >>> rhs.intValue());
    }

    static Object bitNot(Object value) {
        Number number = objectToNumber(value);
        return ~number.intValue();
    }

    Object mul() {
        double result = lhs.doubleValue() * rhs.doubleValue();
        return narrow(result);
    }

    Object div() {
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
        double result = lhs.doubleValue() - rhs.doubleValue();
        return narrow(result);
    }

    Object mod() {
        double result = lhs.doubleValue() % rhs.doubleValue();
        return narrow(result);
    }

    Object exp() {
        double result = Math.pow(lhs.doubleValue(), rhs.doubleValue());
        return narrow(result);
    }

    static Object add(Object lhs, Object rhs) {
        if (lhs instanceof String || rhs instanceof String) {
            return lhs + "" + rhs;
        }
        Number lhsNum = objectToNumber(lhs);
        Number rhsNum = objectToNumber(rhs);
        double result = lhsNum.doubleValue() + rhsNum.doubleValue();
        return narrow(result);
    }

    public static Number narrow(double d) {
        if (NEGATIVE_ZERO.equals(d)) {
            return d;
        }
        if (d % 1 != 0) {
            return d;
        }
        if (d <= Integer.MAX_VALUE) {
            return (int) d;
        }
        if (d <= Long.MAX_VALUE) {
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

    @SuppressWarnings("unchecked")
    static Iterable<KeyValue> toIterable(Object o) {
        // TODO strictly Objects are not iterable
        // Check JsArray first - it implements List but has its own jsEntries
        if (o instanceof JsArray jsArray) {
            return jsArray.jsEntries();
        }
        if (o instanceof JsObject jsObject) {
            return jsObject.jsEntries();
        }
        if (o instanceof List) {
            return new JsArray((List<Object>) o).jsEntries();
        }
        // Java native arrays (String[], int[], Object[], etc.)
        JsArray jsArray = toJsArray(o);
        if (jsArray != null) {
            return jsArray.jsEntries();
        }
        if (o instanceof Map) {
            return new JsObject((Map<String, Object>) o).jsEntries();
        }
        if (o instanceof String) {
            return new JsString((String) o).jsEntries();
        }
        return new JsObject().jsEntries();
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
        // JsFunction + built-in constructor singletons (Boolean/RegExp/Error
        // globals) self-report via isJsFunction() override. Must come before
        // the JsPrimitive check because JsBoolean is both primitive AND the
        // global Boolean constructor — the latter sets builtinConstructor=true.
        if (value instanceof JsObject jo && jo.isJsFunction()) {
            return "function";
        }
        // Raw JsCallable method refs exposed by Prototype.getBuiltinProperty
        // ((JsCallable) this::map, etc.). JsObject / JsArray also implement
        // JsCallable but are excluded here by the ObjectLike guard, so they
        // fall through to "object" below.
        if (value instanceof JsCallable && !(value instanceof ObjectLike)) {
            return "function";
        }
        // Boxed primitives are objects
        if (value instanceof JsPrimitive) {
            return "object";
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
        // Error hierarchy: all NativeError types (TypeError, RangeError, ...) share the
        // JsError Java class but differ by .name. Compare names, with "Error" as the
        // implicit base class so `new RangeError() instanceof Error` is true.
        if (lhs instanceof JsError lhsErr && rhs instanceof JsError rhsErr) {
            String rhsName = rhsErr.getName();
            return "Error".equals(rhsName) || rhsName.equals(lhsErr.getName());
        }
        // For other built-in types that implement JsCallable (JsRegex, JsDate, etc.)
        // Check if lhs is the same type as rhs constructor
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
