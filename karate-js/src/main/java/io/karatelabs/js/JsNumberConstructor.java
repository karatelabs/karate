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

    private static final byte CONSTANT_ATTRS = PropertySlot.INTRINSIC;
    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    private JsNumberConstructor() {
        this.name = "Number";
        this.length = 1;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("EPSILON", Math.ulp(1.0), CONSTANT_ATTRS);
        defineOwn("MAX_VALUE", Double.MAX_VALUE, CONSTANT_ATTRS);
        defineOwn("MIN_VALUE", Double.MIN_VALUE, CONSTANT_ATTRS);
        defineOwn("MAX_SAFE_INTEGER", MAX_SAFE_INTEGER, CONSTANT_ATTRS);
        defineOwn("MIN_SAFE_INTEGER", MIN_SAFE_INTEGER, CONSTANT_ATTRS);
        defineOwn("POSITIVE_INFINITY", Double.POSITIVE_INFINITY, CONSTANT_ATTRS);
        defineOwn("NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY, CONSTANT_ATTRS);
        defineOwn("NaN", Double.NaN, CONSTANT_ATTRS);
        defineOwn("isFinite", new JsBuiltinMethod("isFinite", 1, (JsInvokable) this::isFinite), METHOD_ATTRS);
        defineOwn("isInteger", new JsBuiltinMethod("isInteger", 1, (JsInvokable) this::isInteger), METHOD_ATTRS);
        defineOwn("isNaN", new JsBuiltinMethod("isNaN", 1, (JsInvokable) this::isNaN), METHOD_ATTRS);
        defineOwn("isSafeInteger", new JsBuiltinMethod("isSafeInteger", 1, (JsInvokable) this::isSafeInteger), METHOD_ATTRS);
        // Spec: Number.parseInt === parseInt and Number.parseFloat === parseFloat.
        // Share the singleton with ContextRoot so identity holds.
        defineOwn("parseInt", ContextRoot.PARSE_INT, METHOD_ATTRS);
        defineOwn("parseFloat", ContextRoot.PARSE_FLOAT, METHOD_ATTRS);
        defineOwn("prototype", JsNumberPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsNumber.getObject(context, args);
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
