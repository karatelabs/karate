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

import java.util.Iterator;

/**
 * JavaScript String wrapper that provides String prototype methods.
 */
non-sealed class JsString extends JsObject implements JsPrimitive {

    final String text;

    JsString() {
        this("");
    }

    JsString(String text) {
        super(null, JsStringPrototype.INSTANCE);
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public Object getJavaValue() {
        return text;
    }

    @Override
    public Object getMember(String name) {
        // Check own properties first
        Object own = super.getMember(name);
        if (own != null) {
            return own;
        }
        // Special case: length property
        if ("length".equals(name)) {
            return text.length();
        }
        return null;
    }

    public Iterable<KeyValue> jsEntries() {
        return () -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < text.length();
            }

            @Override
            public KeyValue next() {
                int i = index++;
                String c = String.valueOf(text.charAt(i));
                return new KeyValue(JsString.this, i, i + "", c);
            }
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return getObject(context, args);
    }

    static Object getObject(Context context, Object[] args) {
        String temp = "";
        if (args.length > 0 && args[0] != null) {
            temp = args[0].toString();
        }
        CallInfo callInfo = context.getCallInfo();
        if (callInfo != null && callInfo.constructor) {
            return new JsString(temp);
        }
        return temp;
    }

}
