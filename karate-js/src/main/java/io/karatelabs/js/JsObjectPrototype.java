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

import java.util.Map;

/**
 * Singleton prototype for Object instances.
 * Contains instance methods like toString, valueOf, hasOwnProperty.
 * This is the root of the prototype chain (its __proto__ is null).
 */
class JsObjectPrototype extends Prototype {

    /**
     * The default {@code Object.prototype.toString} implementation. Exposed so
     * pretty-printers (e.g. {@link JsConsole}) can detect a user-overridden
     * {@code toString} vs the default by reference identity.
     * <p>
     * Returns {@code "[object <Tag>]"} where {@code <Tag>} is — in spec order —
     * the value of {@code @@toStringTag} on the receiver (or its prototype chain)
     * if a string, otherwise the host-class-derived tag from {@link #builtinTag}:
     * {@code Array}/{@code Date}/{@code RegExp}/{@code Error}/{@code Map}/{@code Set}/
     * {@code Boolean}/{@code Number}/{@code String}/{@code Function}/{@code Null}/
     * {@code Undefined}, or {@code Object} for plain objects.
     * <p>
     * Declared BEFORE {@link #INSTANCE} so static-init order initializes it
     * first — the constructor reads it via {@code install("toString", DEFAULT_TO_STRING)}.
     */
    static final JsCallable DEFAULT_TO_STRING = (context, args) -> {
        Object o = context.getThisObject();
        if (o instanceof ObjectLike ol) {
            Object tag = ol.getMember("@@toStringTag");
            if (tag instanceof String s) {
                return "[object " + s + "]";
            }
        }
        return "[object " + builtinTag(o) + "]";
    };

    static final JsObjectPrototype INSTANCE = new JsObjectPrototype();

    private static String builtinTag(Object o) {
        if (o == null) return "Null";
        if (o == Terms.UNDEFINED) return "Undefined";
        if (o instanceof JsArray || o instanceof java.util.List) return "Array";
        if (o instanceof JsDate || o instanceof java.util.Date) return "Date";
        if (o instanceof JsRegex) return "RegExp";
        if (o instanceof JsError) return "Error";
        if (o instanceof JsMap) return "Map";
        if (o instanceof JsSet) return "Set";
        if (o instanceof JsBoolean || o instanceof Boolean) return "Boolean";
        if (o instanceof JsString || o instanceof String) return "String";
        if (o instanceof JsNumber || o instanceof Number) return "Number";
        if (o instanceof JsFunction) return "Function";
        if (o instanceof JsObject jo && jo.isJsFunction()) return "Function";
        // Bare JsCallable (e.g. JsInvokable lambdas in initGlobal) — not an
        // ObjectLike, so the spec's [[Call]] check applies. After R2 this also
        // wouldn't catch the JsString/JsNumber/JsBoolean/etc. wrappers (they
        // implement JsCallable AND ObjectLike), but they're already tagged
        // by the typed branches above.
        if (!(o instanceof ObjectLike) && o instanceof JsCallable) return "Function";
        return "Object";
    }

    private JsObjectPrototype() {
        super(null); // Object.prototype.__proto__ === null
        // toString stays unwrapped — JsConsole compares against DEFAULT_TO_STRING
        // by reference identity to detect a user-overridden toString.
        install("toString", DEFAULT_TO_STRING);
        install("valueOf", 0, (context, args) -> context.getThisObject());
        install("hasOwnProperty", 1, this::hasOwnProperty);
        install("isPrototypeOf", 1, this::isPrototypeOf);
        install("propertyIsEnumerable", 1, this::propertyIsEnumerable);
    }

    private Object isPrototypeOf(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof ObjectLike target)) {
            return false;
        }
        Object thisObj = context.getThisObject();
        if (!(thisObj instanceof ObjectLike)) {
            return false;
        }
        ObjectLike cur = target.getPrototype();
        while (cur != null) {
            if (cur == thisObj) {
                return true;
            }
            cur = cur.getPrototype();
        }
        return false;
    }

    private Object propertyIsEnumerable(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return false;
        }
        String prop = args[0].toString();
        Object thisObj = context.getThisObject();
        // Reuse hasOwnProperty's own-key check, then layer the enumerable bit on top.
        // Returns false for non-own keys (including missing) and for own keys
        // whose attribute byte has ENUMERABLE cleared.
        if (!Terms.isTruthy(hasOwnProperty(context, args))) {
            return false;
        }
        if (thisObj instanceof JsObject jo) {
            return jo.isEnumerable(prop);
        }
        return true;
    }

    private Object hasOwnProperty(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return false;
        }
        String prop = args[0].toString();
        Object thisObj = context.getThisObject();
        // Single dispatch through ObjectLike.isOwnProperty — JsObject /
        // JsArray / Prototype each override with the storage-specific check
        // (own-slot non-tombstoned, intrinsic-installed, or numeric-index
        // non-HOLE), and the interface default falls back to toMap for any
        // future ObjectLike. Raw Java Maps fall through to the Map branch.
        if (thisObj instanceof ObjectLike ol) {
            return ol.isOwnProperty(prop);
        }
        if (thisObj instanceof Map<?, ?> map) {
            return map.containsKey(prop);
        }
        return false;
    }

}
