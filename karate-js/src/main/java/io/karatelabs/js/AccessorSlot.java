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
 * Accessor PropertyDescriptor — ES 6.2.5.1. Holds {@link #getter} and
 * {@link #setter} callables. {@link #read} invokes the getter (or returns
 * {@code undefined} if get-only is missing); {@link #write} invokes the
 * setter (or fails per strict-mode policy if set-only is missing).
 */
final class AccessorSlot extends PropertySlot {

    JsCallable getter;
    JsCallable setter;

    AccessorSlot(String name) {
        super(name);
    }

    AccessorSlot(String name, JsCallable getter, JsCallable setter) {
        super(name);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    boolean isAccessor() {
        return true;
    }

    @Override
    Object read(Object receiver, CoreContext ctx) {
        if (getter == null || ctx == null) {
            return Terms.UNDEFINED;
        }
        return Interpreter.invokeGetter(getter, receiver, ctx);
    }

    @Override
    void write(Object receiver, Object newValue, CoreContext ctx, boolean strict) {
        if (setter == null) {
            if (strict) {
                throw JsErrorException.typeError(
                        "Cannot set property '" + name + "' which has only a getter");
            }
            return;
        }
        if (ctx != null) {
            Interpreter.invokeSetter(setter, receiver, newValue, ctx);
        }
    }

    @Override
    public String toString() {
        return name + "={get=" + getter + ", set=" + setter + "}";
    }

}
