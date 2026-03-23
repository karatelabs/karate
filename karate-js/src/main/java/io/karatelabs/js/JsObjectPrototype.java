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

    private JsObjectPrototype() {
        super(null); // Object.prototype.__proto__ === null
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "toString" -> (JsCallable) (context, args) -> Terms.TO_STRING(context.getThisObject());
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
