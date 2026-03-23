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
 * JavaScript Number constructor function.
 * Provides static methods like Number.isFinite, Number.isInteger, Number.isNaN, etc.
 * Also provides static constants like Number.EPSILON, Number.MAX_VALUE, etc.
 */
class JsNumberConstructor extends JsFunction {

    static final JsNumberConstructor INSTANCE = new JsNumberConstructor();

    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final long MIN_SAFE_INTEGER = -9007199254740991L;

    private JsNumberConstructor() {
        this.name = "Number";
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "isFinite" -> (JsInvokable) this::isFinite;
            case "isInteger" -> (JsInvokable) this::isInteger;
            case "isNaN" -> (JsInvokable) this::isNaN;
            case "isSafeInteger" -> (JsInvokable) this::isSafeInteger;
            case "EPSILON" -> Math.ulp(1.0);
            case "MAX_VALUE" -> Double.MAX_VALUE;
            case "MIN_VALUE" -> Double.MIN_VALUE;
            case "MAX_SAFE_INTEGER" -> MAX_SAFE_INTEGER;
            case "MIN_SAFE_INTEGER" -> MIN_SAFE_INTEGER;
            case "POSITIVE_INFINITY" -> Double.POSITIVE_INFINITY;
            case "NEGATIVE_INFINITY" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            case "prototype" -> JsNumberPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsNumber.getObject(context, args);
    }

    // Static methods

    private Object isFinite(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            return Double.isFinite(n.doubleValue());
        }
        return false;
    }

    private Object isInteger(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) && Math.floor(d) == d;
        }
        return false;
    }

    private Object isNaN(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            return Double.isNaN(n.doubleValue());
        }
        return false;
    }

    private Object isSafeInteger(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                return false;
            }
            long l = (long) d;
            return (d == l) && (l >= MIN_SAFE_INTEGER) && (l <= MAX_SAFE_INTEGER);
        }
        return false;
    }

}
