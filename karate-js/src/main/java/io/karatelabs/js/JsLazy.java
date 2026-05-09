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
 * Marker for a lazily-resolved binding value. When stored as a binding's
 * value, the engine invokes {@link #get()} on each read instead of returning
 * the binding object itself.
 * <p>
 * Use this — not {@code java.util.function.Supplier} — to register lazy
 * properties (e.g. mock-server {@code request*}, scenario-scoped session
 * accessors). {@code Supplier} is a generic functional interface that any
 * callable host object (including JS functions via {@link JavaCallable})
 * can satisfy, which would conflate "this is a value to compute lazily"
 * with "this is a callable somebody passed in."
 */
@FunctionalInterface
public interface JsLazy {

    Object get();

}
