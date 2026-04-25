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
 * JavaScript Math object - a singleton with static math methods.
 * <p>
 * Methods are wrapped in {@link JsBuiltinMethod} so they expose spec
 * {@code length} and {@code name} as own properties (read by test262 via
 * {@code Math.cos.length === 1}). {@link #hasOwnIntrinsic} and
 * {@link #getOwnAttrs} declare each method/constant per spec so
 * {@code getOwnPropertyDescriptor} reports correct attribute bits
 * ({@code {writable: false, enumerable: false, configurable: false}} for
 * the seven constants; {@code {writable: true, enumerable: false,
 * configurable: true}} for methods).
 * <p>
 * Method instances are cached per-{@link Engine} (the {@code Math} object
 * itself is allocated fresh per-Engine via {@code ContextRoot.initGlobal}),
 * so {@code Math.cos === Math.cos} holds within a session and tombstones
 * from {@code delete Math.cos.length} persist across reads. The cache
 * resets on Engine creation along with the rest of per-engine state.
 */
class JsMath extends JsObject {

    private java.util.Map<String, JsBuiltinMethod> _methodCache;

    @Override
    public Object getMember(String name) {
        // User-set values + tombstones take precedence over intrinsic resolution.
        // (`Math.cos = 5` and `delete Math.cos` need to win.)
        if (isTombstoned(name) || ownContainsKey(name)) {
            return super.getMember(name);
        }
        // Cache hit: stable identity for the wrapped method instance.
        if (_methodCache != null) {
            JsBuiltinMethod cached = _methodCache.get(name);
            if (cached != null) return cached;
        }
        Object result = resolveMember(name);
        if (result instanceof JsBuiltinMethod jbm) {
            if (_methodCache == null) {
                _methodCache = new java.util.HashMap<>();
            }
            _methodCache.put(name, jbm);
        }
        return result;
    }

    private Object resolveMember(String name) {
        // Math built-in properties and methods
        return switch (name) {
            case "E" -> Math.E;
            case "LN10" -> Math.log(10);
            case "LN2" -> Math.log(2);
            case "LOG2E" -> 1 / Math.log(2);
            case "LOG10E" -> 1 / Math.log(10);
            case "PI" -> Math.PI;
            case "SQRT1_2" -> Math.sqrt(0.5);
            case "SQRT2" -> Math.sqrt(2);
            case "abs" -> wrap("abs", 1, math(Math::abs));
            case "acos" -> wrap("acos", 1, math(Math::acos));
            case "acosh" -> wrap("acosh", 1, math(x -> {
                if (x < 1) {
                    throw JsErrorException.rangeError("value must be >= 1");
                }
                return Math.log(x + Math.sqrt(x * x - 1));
            }));
            case "asin" -> wrap("asin", 1, math(Math::asin));
            case "asinh" -> wrap("asinh", 1, math(x -> Math.log(x + Math.sqrt(x * x + 1))));
            case "atan" -> wrap("atan", 1, math(Math::atan));
            case "atan2" -> wrap("atan2", 2, math(Math::atan2));
            case "atanh" -> wrap("atanh", 1, math(x -> {
                if (x <= -1 || x >= 1) {
                    throw JsErrorException.rangeError("value must be between -1 and 1 (exclusive)");
                }
                return 0.5 * Math.log((1 + x) / (1 - x));
            }));
            case "cbrt" -> wrap("cbrt", 1, math(Math::cbrt));
            case "ceil" -> wrap("ceil", 1, math(Math::ceil));
            case "clz32" -> wrap("clz32", 1, (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                return Integer.numberOfLeadingZeros(x.intValue());
            });
            case "cos" -> wrap("cos", 1, math(Math::cos));
            case "cosh" -> wrap("cosh", 1, math(Math::cosh));
            case "exp" -> wrap("exp", 1, math(Math::exp));
            case "expm1" -> wrap("expm1", 1, math(Math::expm1));
            case "floor" -> wrap("floor", 1, math(Math::floor));
            case "fround" -> wrap("fround", 1, (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                float y = (float) x.doubleValue();
                return (double) y;
            });
            case "hypot" -> wrap("hypot", 2, math(Math::hypot));
            case "imul" -> wrap("imul", 2, (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                Number y = Terms.objectToNumber(args[1]);
                return x.intValue() * y.intValue();
            });
            case "log" -> wrap("log", 1, math(Math::log));
            case "log10" -> wrap("log10", 1, math(Math::log10));
            case "log1p" -> wrap("log1p", 1, math(Math::log1p));
            case "log2" -> wrap("log2", 1, math(x -> Math.log(x) / Math.log(2)));
            case "max" -> wrap("max", 2, math(Math::max));
            case "min" -> wrap("min", 2, math(Math::min));
            case "pow" -> wrap("pow", 2, math(Math::pow));
            case "random" -> wrap("random", 0, (JsInvokable) args -> Math.random());
            case "round" -> wrap("round", 1, (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                double value = x.doubleValue();
                // js Math.round is "half-away from zero"
                if (value < 0) {
                    return Terms.narrow(Math.ceil(value - 0.5));
                } else {
                    return Terms.narrow(Math.floor(value + 0.5));
                }
            });
            case "sign" -> wrap("sign", 1, (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                if (Terms.NEGATIVE_ZERO.equals(x)) {
                    return Terms.NEGATIVE_ZERO;
                }
                if (Terms.POSITIVE_ZERO.equals(x)) {
                    return Terms.POSITIVE_ZERO;
                }
                return x.doubleValue() > 0 ? 1 : -1;
            });
            case "sin" -> wrap("sin", 1, math(Math::sin));
            case "sinh" -> wrap("sinh", 1, math(Math::sinh));
            case "sqrt" -> wrap("sqrt", 1, math(Math::sqrt));
            case "tan" -> wrap("tan", 1, math(Math::tan));
            case "tanh" -> wrap("tanh", 1, math(Math::tanh));
            case "trunc" -> wrap("trunc", 1, math(x -> x > 0 ? Math.floor(x) : Math.ceil(x)));
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        return isMathConstant(name) || isMathMethod(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        if (isMathConstant(name)) {
            // Constants: { writable: false, enumerable: false, configurable: false }
            return 0;
        }
        if (isMathMethod(name)) {
            // Methods: { writable: true, enumerable: false, configurable: true }
            return WRITABLE | CONFIGURABLE;
        }
        return super.getOwnAttrs(name);
    }

    private static boolean isMathConstant(String n) {
        return switch (n) {
            case "E", "LN10", "LN2", "LOG2E", "LOG10E",
                 "PI", "SQRT1_2", "SQRT2" -> true;
            default -> false;
        };
    }

    private static boolean isMathMethod(String n) {
        return switch (n) {
            case "abs", "acos", "acosh", "asin", "asinh",
                 "atan", "atan2", "atanh", "cbrt", "ceil",
                 "clz32", "cos", "cosh", "exp", "expm1",
                 "floor", "fround", "hypot", "imul",
                 "log", "log10", "log1p", "log2",
                 "max", "min", "pow", "random", "round",
                 "sign", "sin", "sinh", "sqrt",
                 "tan", "tanh", "trunc" -> true;
            default -> false;
        };
    }

    private static JsBuiltinMethod wrap(String name, int length, JsCallable delegate) {
        return new JsBuiltinMethod(name, length, delegate);
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
