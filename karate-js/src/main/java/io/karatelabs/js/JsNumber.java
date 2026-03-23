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

/**
 * JavaScript Number wrapper that provides Number prototype methods.
 */
non-sealed class JsNumber extends JsObject implements JsPrimitive {

    final Number value;

    JsNumber() {
        this(0);
    }

    JsNumber(Number value) {
        super(null, JsNumberPrototype.INSTANCE);
        this.value = value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public Object getJavaValue() {
        return value;
    }

    @Override
    public Object call(Context context, Object[] args) {
        return getObject(context, args);
    }

    static Object getObject(Context context, Object[] args) {
        Number temp = 0;
        if (args.length > 0) {
            temp = Terms.objectToNumber(args[0]);
        }
        CallInfo callInfo = context.getCallInfo();
        if (callInfo != null && callInfo.constructor) {
            return new JsNumber(temp);
        }
        return temp;
    }

}
