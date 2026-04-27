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
    public Object getMember(String name, Object receiver, CoreContext ctx) {
        // Symmetric with the 1-arg forward — without this, accessor
        // descriptors installed on the wrapped function (rare, but reachable
        // via {@code Object.defineProperty(fn, key, {get: …})} when the
        // JS-side reference is a wrapper) would surface as {@code null}
        // because the inherited JsObject path can't see the delegate's slots.
        return delegate.getMember(name, receiver, ctx);
    }

    @Override
    protected Object resolveOwnIntrinsic(String name) {
        // Forward intrinsic resolution to the delegate so the wrapper's
        // hasOwnIntrinsic / isOwnProperty / getOwnPropertyDescriptor reflect
        // the wrapped function's surface (its name / length / prototype),
        // not the wrapper's empty-by-default JsFunction state.
        return delegate.resolveOwnIntrinsic(name);
    }

    @Override
    public void putMember(String name, Object value) {
        delegate.putMember(name, value);
    }

}
