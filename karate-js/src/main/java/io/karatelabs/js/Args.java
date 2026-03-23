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

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class Args {

    public static final Arg<String> STR = new Arg<>(String.class, null, false);
    public static final Arg<Integer> INT = new Arg<>(Integer.class, null, false);
    public static final Arg<Double> NUM = new Arg<>(Double.class, null, false);
    public static final Arg<Boolean> BOOL = new Arg<>(Boolean.class, null, false);
    public static final Arg<Map> MAP = new Arg<>(Map.class, null, false);
    public static final Arg<List> LIST = new Arg<>(List.class, null, false);

    public static <T> Arg<T> type(Class<T> type) {
        return new Arg<>(type, null, false);
    }

    public static JavaCallable call(JavaCallable fn) {
        return fn;
    }

    public static JavaCallable invoke(JavaInvokable fn) {
        return fn;
    }

    public static JavaCallable invoke(Supplier<?> fn) {
        return (ctx, args) -> fn.get();
    }

    public static <A> JavaCallable invoke(Arg<A> a, Function<A, ?> fn) {
        return (ctx, args) -> fn.apply(a.extract(args, 0));
    }

    public static <A, B> JavaCallable invoke(Arg<A> a, Arg<B> b, BiFunction<A, B, ?> fn) {
        return (ctx, args) -> fn.apply(a.extract(args, 0), b.extract(args, 1));
    }

    public static <A, B, C> JavaCallable invoke(Arg<A> a, Arg<B> b, Arg<C> c, Fn3<A, B, C, ?> fn) {
        return (ctx, args) -> fn.apply(a.extract(args, 0), b.extract(args, 1), c.extract(args, 2));
    }

    @FunctionalInterface
    public interface Fn3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    public static class Arg<T> {

        final Class<T> type;
        final T defaultValue;
        final boolean optional;

        Arg(Class<T> type, T defaultValue, boolean optional) {
            this.type = type;
            this.defaultValue = defaultValue;
            this.optional = optional;
        }

        public Arg<T> optional(T defaultValue) {
            return new Arg<>(type, defaultValue, true);
        }

        @SuppressWarnings("unchecked")
        T extract(Object[] args, int index) {
            if (index >= args.length) {
                if (optional) return defaultValue;
                throw new IllegalArgumentException("missing argument " + index);
            }
            Object raw = args[index];
            if (type == String.class) return (T) raw.toString();
            if (type == Integer.class) return (T) Integer.valueOf(((Number) raw).intValue());
            if (type == Double.class) return (T) Double.valueOf(((Number) raw).doubleValue());
            return type.cast(raw);
        }
    }

}
