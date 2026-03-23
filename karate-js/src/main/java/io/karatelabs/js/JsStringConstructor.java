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
 * JavaScript String constructor function.
 * Provides static methods like String.fromCharCode, String.fromCodePoint, etc.
 */
class JsStringConstructor extends JsFunction {

    static final JsStringConstructor INSTANCE = new JsStringConstructor();

    private JsStringConstructor() {
        this.name = "String";
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "fromCharCode" -> (JsInvokable) this::fromCharCode;
            case "fromCodePoint" -> (JsInvokable) this::fromCodePoint;
            case "prototype" -> JsStringPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsString.getObject(context, args);
    }

    // Static methods

    private Object fromCharCode(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof Number num) {
                sb.append((char) num.intValue());
            }
        }
        return sb.toString();
    }

    private Object fromCodePoint(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof Number num) {
                int n = num.intValue();
                if (n < 0 || n > 0x10FFFF) {
                    throw new RuntimeException("invalid code point: " + num);
                }
                sb.appendCodePoint(n);
            }
        }
        return sb.toString();
    }

}
