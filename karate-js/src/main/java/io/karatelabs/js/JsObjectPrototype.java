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

    static final JsObjectPrototype INSTANCE = new JsObjectPrototype();

    /**
     * The default {@code Object.prototype.toString} implementation. Exposed so
     * pretty-printers (e.g. {@link JsConsole}) can detect a user-overridden
     * {@code toString} vs the default by reference identity.
     * <p>
     * Returns {@code "[object <Tag>]"} where {@code <Tag>} is derived from the
     * receiver type — {@code Array}/{@code Date}/{@code RegExp}/{@code Error}/
     * {@code Map}/{@code Set}/{@code Boolean}/{@code Number}/{@code String}/
     * {@code Function}/{@code Null}/{@code Undefined}, or {@code Object} for plain
     * objects. Spec uses {@code @@toStringTag} for the user-visible tag; this is
     * a pragmatic fixed-table substitute keyed by the host wrapper class.
     */
    static final JsCallable DEFAULT_TO_STRING = (context, args) -> {
        Object o = context.getThisObject();
        return "[object " + builtinTag(o) + "]";
    };

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
        // Function tag only applies to actual function objects, NOT plain JsObjects
        // (which also implement JsCallable as a side-effect of the host hierarchy).
        if (o instanceof JsFunction) return "Function";
        if (o instanceof JsObject jo && jo.isJsFunction()) return "Function";
        if (!(o instanceof ObjectLike) && o instanceof JsCallable) return "Function";
        return "Object";
    }

    private JsObjectPrototype() {
        super(null); // Object.prototype.__proto__ === null
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "toString" -> DEFAULT_TO_STRING;
            case "valueOf" -> (JsCallable) (context, args) -> context.getThisObject();
            case "hasOwnProperty" -> (JsCallable) this::hasOwnProperty;
            case "isPrototypeOf" -> (JsCallable) this::isPrototypeOf;
            case "propertyIsEnumerable" -> (JsCallable) this::propertyIsEnumerable;
            default -> null;
        };
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
        // We don't model attribute slots; treat any own property as enumerable.
        // Same lookup as hasOwnProperty.
        return hasOwnProperty(context, args);
    }

    private Object hasOwnProperty(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return false;
        }
        String prop = args[0].toString();
        Object thisObj = context.getThisObject();
        // Built-in prototype itself: include own built-in methods (e.g.
        // Date.prototype.hasOwnProperty('toString') === true). Walks neither
        // userProps via toMap() nor the __proto__ chain.
        if (thisObj instanceof Prototype proto) {
            return proto.hasOwnMember(prop);
        }
        if (thisObj instanceof JsObject jo) {
            return jo.toMap().containsKey(prop) || jo.hasOwnIntrinsic(prop);
        }
        if (thisObj instanceof ObjectLike ol) {
            return ol.toMap().containsKey(prop);
        }
        if (thisObj instanceof Map<?, ?> map) {
            return map.containsKey(prop);
        }
        return false;
    }

}
