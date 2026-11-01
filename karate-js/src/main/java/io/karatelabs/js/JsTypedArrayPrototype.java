/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code %TypedArray%.prototype} ({@link #BASE}) and the per-kind prototypes
 * ({@code Uint8Array.prototype}, ...) that inherit from it. The element-wise
 * methods whose spec steps match Array's run Array.prototype's built-ins on the
 * typed receiver; {@code map}, {@code filter}, {@code slice} and {@code subarray}
 * answer the receiver's kind, and {@code sort} without a comparator is numeric.
 */
class JsTypedArrayPrototype extends Prototype {

    private static final byte ACCESSOR_ATTRS = (byte) (PropertySlot.CONFIGURABLE | PropertySlot.INTRINSIC);

    private static final List<String> ARRAY_METHODS = List.of("join", "indexOf", "lastIndexOf", "includes",
            "forEach", "every", "some", "find", "findIndex", "findLast", "findLastIndex", "reduce", "reduceRight",
            "at", "reverse", "keys", "values", "entries");

    /** The {@link #ARRAY_METHODS} from {@code reverse} on take no argument. */
    private static final int FIRST_NULLARY = ARRAY_METHODS.indexOf("reverse");

    static final JsTypedArrayPrototype BASE = new JsTypedArrayPrototype();

    private static final Map<JsTypedArray.Kind, Prototype> BY_KIND = new EnumMap<>(JsTypedArray.Kind.class);

    static {
        for (JsTypedArray.Kind kind : JsTypedArray.Kind.values()) {
            BY_KIND.put(kind, new KindPrototype(kind));
        }
    }

    static Prototype of(JsTypedArray.Kind kind) {
        return BY_KIND.get(kind);
    }

    private JsTypedArrayPrototype() {
        super(JsObjectPrototype.INSTANCE);
        accessor("buffer", JsTypedArray::buffer);
        accessor("byteLength", JsTypedArray::byteLength);
        accessor("byteOffset", t -> t.byteOffset);
        accessor("length", t -> t.length);
        for (int i = 0; i < ARRAY_METHODS.size(); i++) {
            String name = ARRAY_METHODS.get(i);
            install(name, i < FIRST_NULLARY ? 1 : 0, (context, args) -> {
                asTyped(context, name);
                return ((JsCallable) JsArrayPrototype.INSTANCE.builtin(name)).call(context, args);
            });
        }
        install("toString", JsArrayPrototype.DEFAULT_TO_STRING);
        install(IterUtils.SYMBOL_ITERATOR, 0, IterUtils.SYMBOL_ITERATOR_METHOD);
        install("set", 1, this::set);
        install("subarray", 2, this::subarray);
        install("slice", 2, this::slice);
        install("fill", 1, this::fill);
        install("map", 1, (context, args) -> sameKind(context, "map", args));
        install("filter", 1, (context, args) -> sameKind(context, "filter", args));
        install("sort", 1, this::sort);
    }

    private void accessor(String name, java.util.function.Function<JsTypedArray, Object> getter) {
        installAccessor(name, new JsBuiltinMethod("get " + name, 0,
                (ctx, args) -> getter.apply(asTyped(ctx, name))), null, ACCESSOR_ATTRS);
    }

    static JsTypedArray asTyped(Context context, String method) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsTypedArray t) {
            return t;
        }
        if (thisObj instanceof byte[] bytes) {
            return new JsUint8Array(bytes);
        }
        throw JsErrorException.typeError("this is not a typed array.");
    }

    private static CoreContext core(Context context) {
        return context instanceof CoreContext cc ? cc : null;
    }

    private static Object arg(Object[] args, int i) {
        return args.length > i ? args[i] : Terms.UNDEFINED;
    }

    /** The elements of {@code source} as numbers: a typed array's own, else an iterable's or an array-like's. */
    static List<Object> elementsOf(Object source, CoreContext cc) {
        if (source instanceof byte[] bytes) {
            source = new JsUint8Array(bytes);
        }
        if (source instanceof JsTypedArray t) {
            List<Object> values = new ArrayList<>(t.length);
            for (int i = 0; i < t.length; i++) {
                values.add(t.read(i));
            }
            return values;
        }
        if (source instanceof List<?> list) {
            return new ArrayList<>(JsArray.rawElements(list));
        }
        JsIterator iterator = source instanceof ObjectLike ? IterUtils.tryGetIterator(source, cc) : null;
        List<Object> values = new ArrayList<>();
        if (iterator != null) {
            while (iterator.hasNext()) {
                values.add(iterator.next());
            }
            return values;
        }
        ObjectLike arrayLike = Terms.toObjectLike(source);
        if (arrayLike == null) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Object lengthValue = arrayLike.getMember("length", arrayLike, cc);
        long length = JsArrayBuffer.toIndex(lengthValue == null ? Terms.UNDEFINED : lengthValue, cc,
                "Invalid typed array length: " + lengthValue);
        for (int i = 0; i < length; i++) {
            Object v = arrayLike.getMember(String.valueOf(i), arrayLike, cc);
            values.add(v == null ? Terms.UNDEFINED : v);
        }
        return values;
    }

    /** A new typed array of {@code kind} holding {@code values}, each converted per kind. */
    static Object fromValues(JsTypedArray.Kind kind, List<Object> values, CoreContext cc) {
        byte[] bytes = allocate(kind, values.size());
        for (int i = 0, at = 0; i < values.size(); i++, at += kind.size) {
            Object v = JsArray.unwrapHole(values.get(i));
            JsTypedArray.write(kind, bytes, at, Terms.toNumberCoerce(v, cc));
            if (cc != null && cc.isError()) {
                break;
            }
        }
        return JsTypedArray.fresh(kind, bytes);
    }

    static byte[] allocate(JsTypedArray.Kind kind, long length) {
        if (length * kind.size > Integer.MAX_VALUE - 8) {
            throw JsErrorException.rangeError("Array buffer allocation failed");
        }
        return new byte[(int) (length * kind.size)];
    }

    private Object set(Context context, Object[] args) {
        JsTypedArray target = asTyped(context, "set");
        CoreContext cc = core(context);
        Object offsetArg = arg(args, 1);
        double offset = offsetArg == Terms.UNDEFINED ? 0 : Terms.toNumberCoerce(offsetArg, cc).doubleValue();
        offset = Double.isNaN(offset) ? 0 : (offset < 0 ? Math.ceil(offset) : Math.floor(offset));
        if (offset < 0) {
            throw JsErrorException.rangeError("offset is out of bounds");
        }
        Object source = arg(args, 0);
        if (source == null || source == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        List<Object> values = Terms.isPrimitive(source) ? List.of() : elementsOf(source, cc);
        if (values.size() + offset > target.length) {
            throw JsErrorException.rangeError("offset is out of bounds");
        }
        int at = (int) offset;
        for (int i = 0; i < values.size(); i++) {
            target.write(at + i, Terms.toNumberCoerce(JsArray.unwrapHole(values.get(i)), cc));
        }
        return Terms.UNDEFINED;
    }

    private Object subarray(Context context, Object[] args) {
        JsTypedArray t = asTyped(context, "subarray");
        CoreContext cc = core(context);
        int begin = JsArrayBuffer.relativeIndex(arg(args, 0), t.length, 0, cc);
        int end = JsArrayBuffer.relativeIndex(arg(args, 1), t.length, t.length, cc);
        return JsTypedArray.view(t.kind, t.buffer(), t.byteOffset + begin * t.kind.size, Math.max(0, end - begin));
    }

    private Object slice(Context context, Object[] args) {
        JsTypedArray t = asTyped(context, "slice");
        CoreContext cc = core(context);
        int begin = JsArrayBuffer.relativeIndex(arg(args, 0), t.length, 0, cc);
        int end = JsArrayBuffer.relativeIndex(arg(args, 1), t.length, t.length, cc);
        int from = t.byteOffset + begin * t.kind.size;
        return JsTypedArray.fresh(t.kind, Arrays.copyOfRange(t.bytes, from, from + Math.max(0, end - begin) * t.kind.size));
    }

    private Object fill(Context context, Object[] args) {
        JsTypedArray t = asTyped(context, "fill");
        CoreContext cc = core(context);
        Number value = Terms.toNumberCoerce(arg(args, 0), cc);
        int begin = JsArrayBuffer.relativeIndex(arg(args, 1), t.length, 0, cc);
        int end = JsArrayBuffer.relativeIndex(arg(args, 2), t.length, t.length, cc);
        for (int i = begin; i < end; i++) {
            t.write(i, value);
        }
        return context.getThisObject();
    }

    private Object sameKind(Context context, String method, Object[] args) {
        JsTypedArray t = asTyped(context, method);
        Object result = ((JsCallable) JsArrayPrototype.INSTANCE.builtin(method)).call(context, args);
        CoreContext cc = core(context);
        if (cc != null && cc.isError() || !(result instanceof List<?> list)) {
            return result;
        }
        return fromValues(t.kind, JsArray.rawElements(list), cc);
    }

    private Object sort(Context context, Object[] args) {
        JsTypedArray t = asTyped(context, "sort");
        Object comparator = arg(args, 0);
        if (comparator != Terms.UNDEFINED && !(comparator instanceof JsCallable)) {
            throw JsErrorException.typeError("The comparison function must be either a function or undefined");
        }
        List<Object> values = elementsOf(t, core(context));
        if (comparator == Terms.UNDEFINED) {
            values.sort((a, b) -> Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()));
        } else {
            Object sorted = ((JsCallable) JsArrayPrototype.INSTANCE.builtin("toSorted"))
                    .call(context, new Object[]{comparator});
            if (!(sorted instanceof List<?> list)) {
                return context.getThisObject();
            }
            values = JsArray.rawElements(list);
        }
        for (int i = 0; i < values.size(); i++) {
            t.write(i, (Number) values.get(i));
        }
        return context.getThisObject();
    }

    /** {@code Int8Array.prototype}, {@code Uint8Array.prototype}, ...: the kind's constant, name and constructor. */
    private static final class KindPrototype extends Prototype {

        KindPrototype(JsTypedArray.Kind kind) {
            super(BASE);
            install("BYTES_PER_ELEMENT", kind.size);
            install("@@toStringTag", kind.jsName);
            installConstructor(kind.jsName);
        }

        @Override
        protected byte defaultAttrs(String name) {
            return "BYTES_PER_ELEMENT".equals(name) ? 0 : super.defaultAttrs(name);
        }

    }

}
