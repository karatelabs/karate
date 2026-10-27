/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;

/**
 * The one classifier for raw Java values that have a JS mirror: which mirror
 * (JsDateView / JsArray / JsUint8Array), whether lookup on it is host-first, and
 * java.time's coercion policy.
 * Classification never allocates; a caller builds the mirror only when it needs one.
 * Classification is by Java type alone — callers test JsValue / ObjectLike first
 * where that matters (a JsArray is also a List).
 */
final class HostMirror {

    enum Kind {
        NONE,
        /** java.util.Date and its java.sql subclasses: JS-first JsDateView. */
        UTIL_DATE,
        /** Instant, LocalDate, LocalDateTime, ZonedDateTime, OffsetDateTime: host-first JsDateView. */
        TEMPORAL,
        LIST,
        /** A Java array other than byte[]. */
        JAVA_ARRAY,
        /** byte[]: a JsUint8Array. */
        BYTES
    }

    private HostMirror() {
        // static only
    }

    static Kind kind(Object o) {
        if (o == null) {
            return Kind.NONE;
        }
        Class<?> c = o.getClass();
        if (c.isArray()) {
            return o instanceof byte[] ? Kind.BYTES : Kind.JAVA_ARRAY;
        }
        if (o instanceof List) {
            return Kind.LIST;
        }
        if (o instanceof Date) {
            return Kind.UTIL_DATE;
        }
        return isTemporal(c) ? Kind.TEMPORAL : Kind.NONE;
    }

    // the java.time classes are final: an identity test is exact
    private static boolean isTemporal(Class<?> c) {
        return c == Instant.class || c == LocalDate.class || c == LocalDateTime.class
                || c == ZonedDateTime.class || c == OffsetDateTime.class;
    }

    static boolean isDate(Kind k) {
        return k == Kind.UTIL_DATE || k == Kind.TEMPORAL;
    }

    /**
     * The Java value whose own members win over the JS prototype, or null: a raw
     * java.time value, or the JsDateView the call path wrapped around one. A
     * java.util.Date is not host-first — it is the JS Date's own Java mirror.
     */
    static Temporal hostFirstSource(Object o) {
        if (o instanceof JsObject) {
            return o instanceof JsDateView v ? v.hostFirstSource() : null;
        }
        return o != null && isTemporal(o.getClass()) ? (Temporal) o : null;
    }

    /** The JsDate a raw Java date ({@link #isDate}) presents to JS: a live view of it. */
    static JsDate dateView(Object raw) {
        return new JsDateView(raw);
    }

    /** [[DateValue]] of a raw Java date ({@link #isDate}), without building a view. */
    static double timeValue(Object raw) {
        return JsDateView.timeValueOf(raw);
    }

    /**
     * ToPrimitive of a java.time value, raw or viewed: a JS Date's @@toPrimitive
     * with the Java toString in place of Date.prototype.toString — epoch ms (NaN
     * past a long) for the number hint, the Java toString for string and default.
     */
    static Object temporalToPrimitive(Temporal t, String hint) {
        return "number".equals(hint) ? Terms.narrow(timeValue(t)) : t.toString();
    }

    /**
     * ToString of a raw host value ({@link #kind} not NONE) where no context can
     * run JS, e.g. a bracket key: what the mirror's built-in toString gives.
     */
    static String jsString(Object raw) {
        switch (kind(raw)) {
            case UTIL_DATE:
                return new JsDate(timeValue(raw)).toString();
            case LIST, JAVA_ARRAY, BYTES:
                StringJoiner sj = new StringJoiner(",");
                for (Object v : ((JsArray) Terms.toObjectLike(raw)).toList()) {
                    sj.add(v == null || v == Terms.UNDEFINED ? "" : Terms.toPropertyKey(v));
                }
                return sj.toString();
            default:
                return String.valueOf(raw);
        }
    }

}
