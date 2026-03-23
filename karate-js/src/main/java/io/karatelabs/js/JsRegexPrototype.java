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

/**
 * Singleton prototype for RegExp instances.
 * Contains instance methods like test, exec.
 * Inherits from JsObjectPrototype.
 */
class JsRegexPrototype extends Prototype {

    static final JsRegexPrototype INSTANCE = new JsRegexPrototype();

    private JsRegexPrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "test" -> (JsCallable) this::test;
            case "exec" -> (JsCallable) this::exec;
            case "toString" -> (JsCallable) this::toStringMethod;
            default -> null;
        };
    }

    // Helper to get JsRegex from this context
    private static JsRegex asRegex(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsRegex regex) {
            return regex;
        }
        return new JsRegex("(?:)");
    }

    // Instance methods

    private Object test(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return false;
        }
        return asRegex(context).test(args[0].toString());
    }

    private Object exec(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return null;
        }
        return asRegex(context).exec(args[0].toString());
    }

    private Object toStringMethod(Context context, Object[] args) {
        return asRegex(context).toString();
    }

}
