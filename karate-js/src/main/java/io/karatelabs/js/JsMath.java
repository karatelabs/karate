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

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The {@code Math} global — allocated per-Engine via
 * {@code ContextRoot.initGlobal}. Eight numeric constants and ~35 methods
 * are installed at construction time as own properties with spec-correct
 * attributes:
 * <ul>
 *   <li>Constants ({@code E}, {@code PI}, …): all-false attrs (non-writable,
 *       non-enumerable, non-configurable). Per ES §21.3.</li>
 *   <li>Methods ({@code cos}, {@code abs}, …): {@code W | C} (writable,
 *       configurable; non-enumerable). Wrapped in {@link JsBuiltinMethod}
 *       so {@code Math.cos.length === 1} works.</li>
 * </ul>
 * <p>
 * No {@code methodCache} machinery; method identity follows from the
 * single Slot per name installed at construction. {@code Math.cos === Math.cos}
 * holds because both reads return the same Slot's value.
 */
class JsMath extends JsObject {

    private static final byte CONSTANT_ATTRS = PropertySlot.INTRINSIC;
    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    JsMath() {
        // Constants — ES §21.3.1.
        defineOwn("E", Math.E, CONSTANT_ATTRS);
        defineOwn("LN10", Math.log(10), CONSTANT_ATTRS);
        defineOwn("LN2", Math.log(2), CONSTANT_ATTRS);
        defineOwn("LOG2E", 1 / Math.log(2), CONSTANT_ATTRS);
        defineOwn("LOG10E", 1 / Math.log(10), CONSTANT_ATTRS);
        defineOwn("PI", Math.PI, CONSTANT_ATTRS);
        defineOwn("SQRT1_2", Math.sqrt(0.5), CONSTANT_ATTRS);
        defineOwn("SQRT2", Math.sqrt(2), CONSTANT_ATTRS);

        // Methods — ES §21.3.2.
        installMethod("abs", 1, math(Math::abs));
        installMethod("acos", 1, math(Math::acos));
        installMethod("acosh", 1, math(x -> {
            if (x < 1) {
                throw JsErrorException.rangeError("value must be >= 1");
            }
            return Math.log(x + Math.sqrt(x * x - 1));
        }));
        installMethod("asin", 1, math(Math::asin));
        installMethod("asinh", 1, math(x -> {
            // Spec §21.3.2.5: ±0 / ±Inf / NaN return the argument unchanged.
            // The naive `log(x + sqrt(x*x + 1))` form yields NaN for -Inf
            // (Inf + (-Inf)) and loses the sign of zero.
            if (Double.isNaN(x) || x == 0 || Double.isInfinite(x)) return x;
            return Math.log(x + Math.sqrt(x * x + 1));
        }));
        installMethod("atan", 1, math(Math::atan));
        installMethod("atan2", 2, math(Math::atan2));
        installMethod("atanh", 1, math(x -> {
            // Spec §21.3.2.7: ±1 → ±Infinity; |x| > 1 → NaN; ±0 preserved.
            if (Double.isNaN(x)) return Double.NaN;
            if (x > 1 || x < -1) return Double.NaN;
            if (x == 1) return Double.POSITIVE_INFINITY;
            if (x == -1) return Double.NEGATIVE_INFINITY;
            if (x == 0) return x;
            return 0.5 * Math.log((1 + x) / (1 - x));
        }));
        installMethod("cbrt", 1, math(Math::cbrt));
        installMethod("ceil", 1, math(Math::ceil));
        installMethod("clz32", 1, (JsInvokable) args -> {
            Number x = Terms.objectToNumber(args.length == 0 ? Terms.UNDEFINED : args[0]);
            double d = x.doubleValue();
            // Spec §21.3.2.11 ToUint32: NaN, ±0, ±Infinity all map to +0.
            if (Double.isNaN(d) || Double.isInfinite(d) || d == 0) return 32;
            double t = (d < 0) ? Math.ceil(d) : Math.floor(d);
            double mod = t - Math.floor(t / 4294967296.0) * 4294967296.0;
            int n = (int) (long) mod;
            return Integer.numberOfLeadingZeros(n);
        });
        installMethod("cos", 1, math(Math::cos));
        installMethod("cosh", 1, math(Math::cosh));
        installMethod("exp", 1, math(Math::exp));
        installMethod("expm1", 1, math(Math::expm1));
        installMethod("floor", 1, math(Math::floor));
        installMethod("fround", 1, (JsInvokable) args -> {
            Number x = Terms.objectToNumber(args[0]);
            float y = (float) x.doubleValue();
            return (double) y;
        });
        installMethod("hypot", 2, (JsCallable) (context, args) -> {
            // Spec §21.3.2.18: coerce ALL args left-to-right via ToNumber
            // before scanning (so an Infinity later in the list cannot
            // short-circuit a valueOf abrupt-completion earlier in the list).
            CoreContext cc = (CoreContext) context;
            double[] coerced = new double[args.length];
            for (int i = 0; i < args.length; i++) {
                Number n = Terms.toNumberCoerce(args[i], cc);
                if (cc != null && cc.isError()) return Terms.UNDEFINED;
                coerced[i] = n.doubleValue();
            }
            boolean hasNaN = false;
            for (double d : coerced) {
                if (Double.isInfinite(d)) return Double.POSITIVE_INFINITY;
                if (Double.isNaN(d)) hasNaN = true;
            }
            if (hasNaN) return Double.NaN;
            if (coerced.length == 0) return 0;
            if (coerced.length == 1) return Terms.narrow(Math.abs(coerced[0]));
            double r = Math.hypot(coerced[0], coerced[1]);
            for (int i = 2; i < coerced.length; i++) {
                r = Math.hypot(r, coerced[i]);
            }
            return Terms.narrow(r);
        });
        installMethod("imul", 2, (JsInvokable) args -> {
            Number x = Terms.objectToNumber(args[0]);
            Number y = Terms.objectToNumber(args[1]);
            return x.intValue() * y.intValue();
        });
        installMethod("log", 1, math(Math::log));
        installMethod("log10", 1, math(Math::log10));
        installMethod("log1p", 1, math(Math::log1p));
        installMethod("log2", 1, math(x -> Math.log(x) / Math.log(2)));
        installMethod("max", 2, (JsCallable) (context, args) -> {
            // Spec §21.3.2.24: empty -> -Infinity; coerce all args first
            // (so a later valueOf abrupt completion isn't lost), then
            // reduce. Java's Math.max already treats -0 < +0 per spec.
            CoreContext cc = (CoreContext) context;
            if (args.length == 0) return Double.NEGATIVE_INFINITY;
            double[] coerced = new double[args.length];
            for (int i = 0; i < args.length; i++) {
                Number n = Terms.toNumberCoerce(args[i], cc);
                if (cc != null && cc.isError()) return Terms.UNDEFINED;
                coerced[i] = n.doubleValue();
            }
            double r = Double.NEGATIVE_INFINITY;
            for (double d : coerced) {
                if (Double.isNaN(d)) return Double.NaN;
                r = Math.max(r, d);
            }
            return Terms.narrow(r);
        });
        installMethod("min", 2, (JsCallable) (context, args) -> {
            // Spec §21.3.2.25: empty -> +Infinity; mirror of max.
            CoreContext cc = (CoreContext) context;
            if (args.length == 0) return Double.POSITIVE_INFINITY;
            double[] coerced = new double[args.length];
            for (int i = 0; i < args.length; i++) {
                Number n = Terms.toNumberCoerce(args[i], cc);
                if (cc != null && cc.isError()) return Terms.UNDEFINED;
                coerced[i] = n.doubleValue();
            }
            double r = Double.POSITIVE_INFINITY;
            for (double d : coerced) {
                if (Double.isNaN(d)) return Double.NaN;
                r = Math.min(r, d);
            }
            return Terms.narrow(r);
        });
        installMethod("pow", 2, math(Math::pow));
        installMethod("random", 0, (JsInvokable) args -> Math.random());
        installMethod("round", 1, math(x -> {
            // Spec §21.3.2.28: NaN/±Inf/±0/integer unchanged; (0, 0.5) -> +0;
            // [-0.5, 0) -> -0; otherwise floor(x + 0.5). Note this is
            // "round half toward +Infinity", NOT "round half away from zero":
            // Math.round(-1.5) === -1, NOT -2. The integer short-circuit is
            // load-bearing near MAX_SAFE_INTEGER (ulp ≥ 1), where x + 0.5
            // rounds to a different integer than x.
            if (Double.isNaN(x) || Double.isInfinite(x) || x == 0) return x;
            if (x == Math.floor(x)) return x;
            if (x > 0 && x < 0.5) return 0.0;
            if (x < 0 && x >= -0.5) return -0.0;
            return Math.floor(x + 0.5);
        }));
        installMethod("sign", 1, math(x -> {
            if (Double.isNaN(x)) return Double.NaN;
            if (x == 0) return x; // ±0 preserved
            return x > 0 ? 1.0 : -1.0;
        }));
        installMethod("sin", 1, math(Math::sin));
        installMethod("sinh", 1, math(Math::sinh));
        installMethod("sqrt", 1, math(Math::sqrt));
        installMethod("tan", 1, math(Math::tan));
        installMethod("tanh", 1, math(Math::tanh));
        installMethod("trunc", 1, math(x -> x > 0 ? Math.floor(x) : Math.ceil(x)));
    }

    private void installMethod(String name, int length, JsCallable delegate) {
        defineOwn(name, new JsBuiltinMethod(name, length, delegate), METHOD_ATTRS);
    }

    private static JsInvokable math(Function<Double, Double> fn) {
        return args -> {
            try {
                Number x = Terms.objectToNumber(args[0]);
                Double y = fn.apply(x.doubleValue());
                return Terms.narrow(y);
            } catch (Exception e) {
                return Double.NaN;
            }
        };
    }

    private static JsInvokable math(BiFunction<Double, Double, Double> fn) {
        return args -> {
            try {
                Number x = Terms.objectToNumber(args[0]);
                Number y = Terms.objectToNumber(args[1]);
                Double r = fn.apply(x.doubleValue(), y.doubleValue());
                return Terms.narrow(r);
            } catch (Exception e) {
                return Double.NaN;
            }
        };
    }

}
