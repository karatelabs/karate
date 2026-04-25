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
 * Wraps a built-in callable lambda with spec {@code .name} and {@code .length}
 * — the values test262 reads back via {@code Date.now.length === 0} etc. Without
 * this wrapper, raw {@code (JsCallable) this::foo} method refs report no name
 * and no length, breaking the {@code length.js} / {@code name.js} suites.
 * <p>
 * Built-in methods aren't constructable per spec — overrides
 * {@link #isConstructable} back to {@code false} so {@code new Date.now()}
 * throws TypeError, even though the parent {@link JsFunction} would otherwise
 * opt in.
 */
final class JsBuiltinMethod extends JsFunction {

    private final JsCallable delegate;

    JsBuiltinMethod(String name, int length, JsCallable delegate) {
        this.name = name;
        this.length = length;
        this.delegate = delegate;
    }

    @Override
    public Object call(Context context, Object[] args) {
        return delegate.call(context, args);
    }

    @Override
    public boolean isConstructable() {
        return false;
    }

}
