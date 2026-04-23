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

/**
 * Singleton prototype for Boolean instances.
 * Inherits from JsObjectPrototype.
 */
class JsBooleanPrototype extends Prototype {

    static final JsBooleanPrototype INSTANCE = new JsBooleanPrototype();

    private JsBooleanPrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "toString" -> (JsCallable) JsBooleanPrototype::toStringMethod;
            case "valueOf" -> (JsCallable) JsBooleanPrototype::valueOfMethod;
            default -> null;
        };
    }

    private static boolean asBoolean(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsBoolean jb) {
            return jb.value;
        }
        if (thisObj instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static Object toStringMethod(Context context, Object[] args) {
        return Boolean.toString(asBoolean(context));
    }

    private static Object valueOfMethod(Context context, Object[] args) {
        return asBoolean(context);
    }

}
