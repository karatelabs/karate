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
 */
class JsMath extends JsObject {

    @Override
    public Object getMember(String name) {
        // Math built-in properties and methods
        return switch (name) {
            case "E" -> Math.E;
            case "LN10" -> Math.log(10);
            case "LN2" -> Math.log(2);
            case "LOG2E" -> 1 / Math.log(2);
            case "PI" -> Math.PI;
            case "SQRT1_2" -> Math.sqrt(0.5);
            case "SQRT2" -> Math.sqrt(2);
            case "abs" -> math(Math::abs);
            case "acos" -> math(Math::acos);
            case "acosh" -> math(x -> {
                if (x < 1) {
                    throw new RuntimeException("value must be >= 1");
                }
                return Math.log(x + Math.sqrt(x * x - 1));
            });
            case "asin" -> math(Math::asin);
            case "asinh" -> math(x -> Math.log(x + Math.sqrt(x * x + 1)));
            case "atan" -> math(Math::atan);
            case "atan2" -> math(Math::atan2);
            case "atanh" -> math(x -> {
                if (x <= -1 || x >= 1) {
                    throw new RuntimeException("value must be between -1 and 1 (exclusive)");
                }
                return 0.5 * Math.log((1 + x) / (1 - x));
            });
            case "cbrt" -> math(Math::cbrt);
            case "ceil" -> math(Math::ceil);
            case "clz32" -> (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                return Integer.numberOfLeadingZeros(x.intValue());
            };
            case "cos" -> math(Math::cos);
            case "cosh" -> math(Math::cosh);
            case "exp" -> math(Math::exp);
            case "expm1" -> math(Math::expm1);
            case "floor" -> math(Math::floor);
            case "fround" -> (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                float y = (float) x.doubleValue();
                return (double) y;
            };
            case "hypot" -> math(Math::hypot);
            case "imul" -> (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                Number y = Terms.objectToNumber(args[1]);
                return x.intValue() * y.intValue();
            };
            case "log" -> math(Math::log);
            case "log10" -> math(Math::log10);
            case "log1p" -> math(Math::log1p);
            case "log2" -> math(x -> Math.log(x) / Math.log(2));
            case "max" -> math(Math::max);
            case "min" -> math(Math::min);
            case "pow" -> math(Math::pow);
            case "random" -> (JsInvokable) args -> Math.random();
            case "round" -> (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                double value = x.doubleValue();
                // js Math.round is "half-away from zero"
                if (value < 0) {
                    return Terms.narrow(Math.ceil(value - 0.5));
                } else {
                    return Terms.narrow(Math.floor(value + 0.5));
                }
            };
            case "sign" -> (JsInvokable) args -> {
                Number x = Terms.objectToNumber(args[0]);
                if (Terms.NEGATIVE_ZERO.equals(x)) {
                    return Terms.NEGATIVE_ZERO;
                }
                if (Terms.POSITIVE_ZERO.equals(x)) {
                    return Terms.POSITIVE_ZERO;
                }
                return x.doubleValue() > 0 ? 1 : -1;
            };
            case "sin" -> math(Math::sin);
            case "sinh" -> math(Math::sinh);
            case "sqrt" -> math(Math::sqrt);
            case "tan" -> math(Math::tan);
            case "tanh" -> math(Math::tanh);
            case "trunc" -> math(x -> x > 0 ? Math.floor(x) : Math.ceil(x));
            default -> null;
        };
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
