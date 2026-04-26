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
 * Static methods are wrapped in {@link JsBuiltinMethod} (per the JsMath /
 * JsNumberConstructor template) so they expose spec {@code length} and
 * {@code name}; method instances are cached per-Engine in {@code methodCache}.
 * {@link #hasOwnIntrinsic} / {@link #getOwnAttrs} declare each method plus
 * the {@code prototype} slot per spec.
 */
class JsArrayConstructor extends JsFunction {

    static final JsArrayConstructor INSTANCE = new JsArrayConstructor();

    private java.util.Map<String, JsBuiltinMethod> methodCache;

    private JsArrayConstructor() {
        this.name = "Array";
        this.length = 1;
        registerForEngineReset();
    }

    @Override
    public Object getMember(String name) {
        if (isTombstoned(name) || ownContainsKey(name)) {
            return super.getMember(name);
        }
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
            case "from" -> method(name, 1, this::from);
            case "isArray" -> method(name, 1, (JsInvokable) this::isArray);
            case "of" -> method(name, 0, (JsInvokable) this::of);
            case "prototype" -> JsArrayPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        return isArrayMethod(name) || super.hasOwnIntrinsic(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        if (isArrayMethod(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        if ("prototype".equals(name)) {
            return 0;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        if (methodCache != null) methodCache.clear();
    }

    private static boolean isArrayMethod(String n) {
        return switch (n) {
            case "from", "isArray", "of" -> true;
            default -> false;
        };
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
            return results;
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
            return results;
        }
        throw JsErrorException.typeError(source + " is not iterable");
    }

    private Object isArray(Object[] args) {
        return args.length > 0 && args[0] instanceof List;
    }

    private Object of(Object[] args) {
        return Arrays.asList(args);
    }

}
