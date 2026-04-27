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
non-sealed class JsString extends JsObject implements JsPrimitive, JsCallable {

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
    protected Object resolveOwnIntrinsic(String name) {
        if ("length".equals(name)) {
            return text.length();
        }
        // Spec: exotic string objects expose indexed character access as own
        // properties — `HasProperty(strObj, "0")` is true and
        // `Get(strObj, "0")` returns the char. Required for
        // {@code Array.prototype.{forEach, every, …}.call(strObj, …)} to
        // iterate the characters via the spec HasProperty + Get loop
        // (test262 `15.4.4.18-1-8.js` and the cluster of -1-8 tests).
        int i = parseIndex(name);
        if (i >= 0 && i < text.length()) {
            return String.valueOf(text.charAt(i));
        }
        return null;
    }

    /** Strict canonical-integer parse for the indexed-char fast path —
     *  rejects {@code "01"}, {@code "+1"}, {@code "1.0"}; accepts
     *  {@code "0"} through 10-digit integers. Mirrors {@code JsArray.parseIndex}. */
    private static int parseIndex(String s) {
        int n = s.length();
        if (n == 0 || n > 10) return -1;
        int v = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
        }
        if (n > 1 && s.charAt(0) == '0') return -1;
        return v;
    }

    private static final java.util.List<String> INTRINSIC_NAMES = java.util.List.of("length");

    @Override
    protected Iterable<String> ownIntrinsicNames() {
        return INTRINSIC_NAMES;
    }

    @Override
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
    public Iterable<KeyValue> jsEntries(CoreContext ctx) {
        // Strings expose their characters by index — no accessor descriptors
        // possible at this level; ctx-aware variant has no extra work.
        return jsEntries();
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
