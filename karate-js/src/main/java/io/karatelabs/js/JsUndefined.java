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
 * Singleton representing JavaScript's undefined value.
 * <p>
 * Implements JsValue so it's handled uniformly with other JS types.
 * {@link #getJavaValue()} returns null for Java interop.
 */
public final class JsUndefined implements JsValue {

    public static final JsUndefined INSTANCE = new JsUndefined();

    private JsUndefined() {
        // singleton
    }

    @Override
    public Object getJavaValue() {
        return null;
    }

    @Override
    public Object getJsValue() {
        // Return self so JS operations can distinguish undefined from null
        // e.g., undefined + 5 = NaN, but null + 5 = 5
        return this;
    }

    @Override
    public String toString() {
        return "undefined";
    }

}
