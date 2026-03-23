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
 * Wrapper for JsFunction that auto-converts return values via Engine.toJava().
 * <p>
 * When Java code holds a reference to a JS function and invokes it directly,
 * this wrapper ensures the return value is converted (undefined → null,
 * JsDate → Date, etc.) so Java code never sees raw JS types.
 */
public class JsFunctionWrapper extends JsFunction {

    private final JsFunction delegate;

    public JsFunctionWrapper(JsFunction delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object call(Context ctx, Object[] args) {
        Object result = delegate.call(ctx, args);
        return Engine.toJava(result);
    }

    @Override
    public String getSource() {
        return delegate.getSource();
    }

    @Override
    public Object getMember(String name) {
        return delegate.getMember(name);
    }

    @Override
    public void putMember(String name, Object value) {
        delegate.putMember(name, value);
    }

}
