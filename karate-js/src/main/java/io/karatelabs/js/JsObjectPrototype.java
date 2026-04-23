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
     * Spec note: {@code Object.prototype.toString} returns {@code "[object " + tag + "]"}
     * where {@code tag} comes from {@code @@toStringTag} or the internal slot. karate-js
     * returns {@code "[object Object]"} for plain objects — the tag variants for
     * arrays/dates/etc. are produced by those types' own {@code toString} overrides
     * in their prototypes, not here.
     */
    static final JsCallable DEFAULT_TO_STRING = (context, args) -> "[object Object]";

    private JsObjectPrototype() {
        super(null); // Object.prototype.__proto__ === null
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "toString" -> DEFAULT_TO_STRING;
            case "valueOf" -> (JsCallable) (context, args) -> context.getThisObject();
            case "hasOwnProperty" -> (JsCallable) this::hasOwnProperty;
            default -> null;
        };
    }

    private Object hasOwnProperty(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return false;
        }
        String prop = args[0].toString();
        Object thisObj = context.getThisObject();
        if (thisObj instanceof ObjectLike ol) {
            return ol.toMap().containsKey(prop);
        }
        if (thisObj instanceof Map<?, ?> map) {
            return map.containsKey(prop);
        }
        return false;
    }

}
