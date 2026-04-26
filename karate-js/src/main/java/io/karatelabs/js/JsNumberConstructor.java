/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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
 * JavaScript Number constructor function.
 * <p>
 * Static methods (isFinite / isInteger / isNaN / isSafeInteger) are wrapped in
 * {@link JsBuiltinMethod} so they expose spec {@code length} and {@code name}
 * as own properties. Method instances are cached per-Engine in {@code methodCache}
 * so {@code Number.isFinite === Number.isFinite} holds within a session and
 * tombstones from {@code delete Number.isFinite} are applied to a stable instance.
 * The cache is wiped per-Engine via {@link #clearEngineState()} (see the
 * {@code ENGINE_RESET_LIST} mechanism on {@link JsObject}).
 * <p>
 * {@link #hasOwnIntrinsic} and {@link #getOwnAttrs} declare each method, constant
 * and the {@code prototype} slot per spec so {@code getOwnPropertyDescriptor}
 * reports the correct attribute bits — constants are
 * {@code {writable: false, enumerable: false, configurable: false}}, methods are
 * {@code {writable: true, enumerable: false, configurable: true}}, and a built-in
 * constructor's {@code prototype} is all-false.
 */
class JsNumberConstructor extends JsFunction {

    static final JsNumberConstructor INSTANCE = new JsNumberConstructor();

    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final long MIN_SAFE_INTEGER = -9007199254740991L;

    private java.util.Map<String, JsBuiltinMethod> methodCache;

    private JsNumberConstructor() {
        this.name = "Number";
        this.length = 1;
        registerForEngineReset();
    }

    @Override
    public Object getMember(String name) {
        // User-set values + tombstones take precedence over intrinsic resolution.
        if (isTombstoned(name) || ownContainsKey(name)) {
            return super.getMember(name);
        }
        // Cache hit: stable identity for the wrapped method instance.
        if (methodCache != null) {
            JsBuiltinMethod cached = methodCache.get(name);
            if (cached != null) return cached;
        }
        Object result = resolveMember(name);
        if (result instanceof JsBuiltinMethod jbm) {
            if (methodCache == null) {
                methodCache = new java.util.HashMap<>();
            }
            methodCache.put(name, jbm);
        }
        return result;
    }

    private Object resolveMember(String name) {
        return switch (name) {
            case "isFinite" -> method(name, 1, (JsInvokable) this::isFinite);
            case "isInteger" -> method(name, 1, (JsInvokable) this::isInteger);
            case "isNaN" -> method(name, 1, (JsInvokable) this::isNaN);
            case "isSafeInteger" -> method(name, 1, (JsInvokable) this::isSafeInteger);
            case "EPSILON" -> Math.ulp(1.0);
            case "MAX_VALUE" -> Double.MAX_VALUE;
            case "MIN_VALUE" -> Double.MIN_VALUE;
            case "MAX_SAFE_INTEGER" -> MAX_SAFE_INTEGER;
            case "MIN_SAFE_INTEGER" -> MIN_SAFE_INTEGER;
            case "POSITIVE_INFINITY" -> Double.POSITIVE_INFINITY;
            case "NEGATIVE_INFINITY" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            case "prototype" -> JsNumberPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        return isNumberMethod(name) || isNumberConstant(name)
                || super.hasOwnIntrinsic(name); // length / name / prototype / constructor
    }

    @Override
    public byte getOwnAttrs(String name) {
        if (isNumberConstant(name)) {
            // Constants: { writable: false, enumerable: false, configurable: false }
            return 0;
        }
        if (isNumberMethod(name)) {
            // Methods: { writable: true, enumerable: false, configurable: true }
            return WRITABLE | CONFIGURABLE;
        }
        if ("prototype".equals(name)) {
            // Built-in constructor prototype: all-false (overrides JsFunction's
            // user-function default of WRITABLE).
            return 0;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        if (methodCache != null) methodCache.clear();
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsNumber.getObject(context, args);
    }

    private static boolean isNumberConstant(String n) {
        return switch (n) {
            case "EPSILON", "MAX_VALUE", "MIN_VALUE",
                 "MAX_SAFE_INTEGER", "MIN_SAFE_INTEGER",
                 "POSITIVE_INFINITY", "NEGATIVE_INFINITY", "NaN" -> true;
            default -> false;
        };
    }

    private static boolean isNumberMethod(String n) {
        return switch (n) {
            case "isFinite", "isInteger", "isNaN", "isSafeInteger" -> true;
            default -> false;
        };
    }

    // Static methods

    private Object isFinite(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            return Double.isFinite(n.doubleValue());
        }
        return false;
    }

    private Object isInteger(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) && Math.floor(d) == d;
        }
        return false;
    }

    private Object isNaN(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            return Double.isNaN(n.doubleValue());
        }
        return false;
    }

    private Object isSafeInteger(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                return false;
            }
            long l = (long) d;
            return (d == l) && (l >= MIN_SAFE_INTEGER) && (l <= MAX_SAFE_INTEGER);
        }
        return false;
    }

}
