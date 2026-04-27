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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JavaScript Array constructor function.
 * <p>
 * Singleton; static methods + the {@code prototype} slot are eagerly
 * installed at construction time as own properties with spec attrs:
 * methods are {@code W | C} (non-enumerable), {@code prototype} is
 * all-false. {@link #clearEngineState} re-runs the install on per-Engine
 * reset so user mutations from a prior session don't leak.
 */
class JsArrayConstructor extends JsFunction {

    static final JsArrayConstructor INSTANCE = new JsArrayConstructor();

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    private JsArrayConstructor() {
        this.name = "Array";
        this.length = 1;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("from", new JsBuiltinMethod("from", 1, this::from), METHOD_ATTRS);
        defineOwn("isArray", new JsBuiltinMethod("isArray", 1, (JsInvokable) this::isArray), METHOD_ATTRS);
        defineOwn("of", new JsBuiltinMethod("of", 0, (JsInvokable) this::of), METHOD_ATTRS);
        defineOwn("prototype", JsArrayPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsArray.create(args);
    }

    // Static methods

    @SuppressWarnings("unchecked")
    private Object from(Context context, Object[] args) {
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Array.from requires an iterable or array-like object, not " + (args.length == 0 ? "undefined" : args[0]));
        }
        Object source = args[0];
        JsCallable mapFn = (args.length > 1 && args[1] instanceof JsCallable) ? (JsCallable) args[1] : null;
        List<Object> results = new ArrayList<>();
        // Iterable path: anything with @@iterator (built-in or user). Per spec, this
        // takes priority over the array-like length-walk fallback.
        JsIterator iter = IterUtils.tryGetIterator(source, context);
        if (iter != null) {
            int index = 0;
            while (iter.hasNext()) {
                Object v = iter.next();
                Object mapped = mapFn == null ? v : mapFn.call(context, new Object[]{v, index});
                results.add(mapped);
                index++;
            }
            // Spec §23.1.2.1 returns an Array exotic — wrap so
            // `Array.from(...).constructor === Array` and
            // `Array.from(...) instanceof Array` hold.
            return new JsArray(results);
        }
        // Array-like fallback: map-of-string-keys with `length` (e.g. {0: 'a', 1: 'b', length: 2}).
        if (source instanceof Map) {
            JsArray array = JsArray.toArray((Map<String, Object>) source);
            int index = 0;
            for (Object v : array.list) {
                Object mapped = mapFn == null ? v : mapFn.call(context, new Object[]{v, index});
                results.add(mapped);
                index++;
            }
            return new JsArray(results);
        }
        throw JsErrorException.typeError(source + " is not iterable");
    }

    private Object isArray(Object[] args) {
        return args.length > 0 && args[0] instanceof List;
    }

    private Object of(Object[] args) {
        // Spec §23.1.2.3 — return an Array. Wrap in JsArray (mutable copy)
        // so callers can {@code push}/{@code pop} and so
        // {@code Array.of(...).constructor === Array} holds; the bare
        // {@code Arrays.asList} would be a fixed-size Java List.
        List<Object> list = new ArrayList<>(args.length);
        for (Object arg : args) list.add(arg);
        return new JsArray(list);
    }

}
