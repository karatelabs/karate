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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface JavaCallable extends JsCallable,
        Function<Object, Object>, Predicate<Object>, Consumer<Object>,
        Supplier<Object>, Runnable {

    /**
     * Override with varargs for external Java client convenience.
     * Callers can use: callable.call(ctx, arg1, arg2)
     */
    @Override
    Object call(Context context, Object... args);

    @Override
    default boolean isExternal() {
        return true;
    }

    // Functional-interface adapters: let JS functions satisfy Function /
    // Predicate / Consumer / Supplier / Runnable parameters on Java methods
    // (issue #2837). v1 got this for free via Graal interop coercion; the v2
    // engine has none, so we route through call(). Lazy bindings are the
    // separate {@link JsLazy} marker — Supplier here is purely for parameter
    // coercion and never appears as a binding sentinel.

    @Override
    default Object apply(Object t) {
        return Engine.toJava(call(null, new Object[]{t}));
    }

    @Override
    default boolean test(Object t) {
        return Terms.isTruthy(call(null, new Object[]{t}));
    }

    @Override
    default void accept(Object t) {
        call(null, new Object[]{t});
    }

    @Override
    default Object get() {
        return Engine.toJava(call(null, new Object[0]));
    }

    @Override
    default void run() {
        call(null, new Object[0]);
    }

}
