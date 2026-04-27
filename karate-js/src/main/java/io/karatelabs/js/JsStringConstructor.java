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
 * JavaScript String constructor function.
 * <p>
 * Static methods are wrapped in {@link JsBuiltinMethod} (per the JsMath /
 * JsNumberConstructor template) so they expose spec {@code length} and
 * {@code name}; method instances are cached per-Engine in {@code methodCache}.
 * {@link #hasOwnIntrinsic} / {@link #getOwnAttrs} declare each method plus
 * the {@code prototype} slot per spec.
 */
class JsStringConstructor extends JsFunction {

    static final JsStringConstructor INSTANCE = new JsStringConstructor();

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    private JsStringConstructor() {
        this.name = "String";
        this.length = 1;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("fromCharCode", new JsBuiltinMethod("fromCharCode", 1, (JsInvokable) this::fromCharCode), METHOD_ATTRS);
        defineOwn("fromCodePoint", new JsBuiltinMethod("fromCodePoint", 1, (JsInvokable) this::fromCodePoint), METHOD_ATTRS);
        defineOwn("raw", new JsBuiltinMethod("raw", 1, this::raw), METHOD_ATTRS);
        defineOwn("prototype", JsStringPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsString.getObject(context, args);
    }

    // Static methods

    private Object fromCharCode(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof Number num) {
                sb.append((char) num.intValue());
            }
        }
        return sb.toString();
    }

    private Object fromCodePoint(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof Number num) {
                int n = num.intValue();
                if (n < 0 || n > 0x10FFFF) {
                    throw JsErrorException.rangeError("Invalid code point " + num);
                }
                sb.appendCodePoint(n);
            }
        }
        return sb.toString();
    }

    // Spec §22.1.2.4 — String.raw(template, ...substitutions). Walks
    // template.raw[k] for k in [0, length), interleaving substitutions[k] from
    // the second arg onward. Coercion goes through the spec ToString helper so
    // host objects with a JS toString return user-visible strings, and
    // non-array-like raw values (length=NaN/0) fall through to the empty
    // string per §22.1.2.4 step 6 / 8.
    private Object raw(Context context, Object[] args) {
        Object template = args.length > 0 ? args[0] : Terms.UNDEFINED;
        Terms.requireObjectCoercible(template, "String.raw");
        if (!(template instanceof ObjectLike templateObj)) {
            throw JsErrorException.typeError("String.raw template must be an object");
        }
        Object rawObj = templateObj.getMember("raw");
        Terms.requireObjectCoercible(rawObj, "String.raw .raw");
        if (!(rawObj instanceof ObjectLike raw)) {
            throw JsErrorException.typeError("String.raw .raw must be an object");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        // Spec ToLength(raw.length): NaN / negative / undefined → 0.
        double rawLen = Terms.objectToNumber(raw.getMember("length")).doubleValue();
        if (Double.isNaN(rawLen) || rawLen <= 0) return "";
        long length = rawLen >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (long) rawLen;
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < length; i++) {
            sb.append(Terms.toStringCoerce(raw.getMember(Long.toString(i)), cc));
            if (i + 1 == length) break;
            // substitutions are positional starting at args[1].
            int subIdx = (int) (i + 1);
            if (subIdx < args.length) {
                sb.append(Terms.toStringCoerce(args[subIdx], cc));
            }
        }
        return sb.toString();
    }

}
