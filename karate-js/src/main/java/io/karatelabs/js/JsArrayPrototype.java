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

import java.util.*;

/**
 * Singleton prototype for Array instances. Contains instance methods like map,
 * filter, reduce, push, pop, etc. Inherits from JsObjectPrototype.
 * <p>
 * Built-in methods are wrapped via {@link Prototype#method(String, int, JsCallable)}
 * so each exposes spec {@code length}+{@code name} as own properties (read by
 * test262 via {@code Array.prototype.push.length === 1} etc.); the base
 * {@code Prototype} caches the wrapped instances per-Engine so identity holds
 * and tombstones from {@code delete Array.prototype.push} apply to a stable
 * instance.
 */
class JsArrayPrototype extends Prototype {

    static final JsArrayPrototype INSTANCE = new JsArrayPrototype();

    /**
     * The default {@code Array.prototype.toString} — equivalent to {@code this.join(",")}.
     * Exposed so pretty-printers (e.g. {@link JsConsole}) can detect a user-overridden
     * {@code toString} vs the default by reference identity. Stand-alone identity
     * (not wrapped in a JsBuiltinMethod) since identity-comparison is the contract.
     * <p>
     * Declared AFTER {@link #INSTANCE} because the lambda references {@code INSTANCE.join}
     * (forward-reference rules forbid the inverse). The constructor's
     * {@code install("toString", DEFAULT_TO_STRING)} therefore stores null, which the
     * post-construction fixup below replaces with the live lambda.
     */
    static final JsCallable DEFAULT_TO_STRING =
            (context, args) -> INSTANCE.join(context, new Object[0]);

    // Post-static-init fixup: the constructor ran with DEFAULT_TO_STRING still null
    // (declared after INSTANCE). Replace the null toString slot with the live lambda
    // now that all static fields are assigned.
    static {
        INSTANCE.install("toString", DEFAULT_TO_STRING);
    }

    private JsArrayPrototype() {
        super(JsObjectPrototype.INSTANCE);
        // toString uses DEFAULT_TO_STRING for identity-by-reference detection
        // (JsConsole compares against it); it stays unwrapped.
        install("toString", DEFAULT_TO_STRING);
        install("map", 1, this::map);
        install("filter", 1, this::filter);
        install("join", 1, this::join);
        install("find", 1, this::find);
        install("findIndex", 1, this::findIndex);
        install("push", 1, this::push);
        install("reverse", 0, this::reverse);
        install("includes", 1, this::includes);
        install("indexOf", 1, this::indexOf);
        install("slice", 2, this::slice);
        install("forEach", 1, this::forEach);
        install("concat", 1, this::concat);
        install("every", 1, this::every);
        install("some", 1, this::some);
        install("reduce", 1, this::reduce);
        install("reduceRight", 1, this::reduceRight);
        install("flat", 0, this::flat);
        install("flatMap", 1, this::flatMap);
        install("sort", 1, this::sort);
        install("fill", 1, this::fill);
        install("splice", 2, this::splice);
        install("shift", 0, this::shift);
        install("unshift", 1, this::unshift);
        install("lastIndexOf", 1, this::lastIndexOf);
        install("pop", 0, this::pop);
        install("at", 1, this::at);
        install("copyWithin", 2, this::copyWithin);
        install("keys", 0, this::keys);
        install("values", 0, this::values);
        install("entries", 0, this::entries);
        install("findLast", 1, this::findLast);
        install("findLastIndex", 1, this::findLastIndex);
        install("with", 2, this::withMethod);
        install("group", 1, this::group);
        install(IterUtils.SYMBOL_ITERATOR, 0, IterUtils.SYMBOL_ITERATOR_METHOD);
    }

    // Helper methods

    @SuppressWarnings("unchecked")
    private static List<Object> rawList(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsArray arr) {
            // Fast path: plain array with no descriptors installed via
            // Object.defineProperty. toList() is shared with JsUint8Array etc.
            // whose subclass overrides provide alternate storage.
            if (!arr.hasAnyDescriptor()) {
                return arr.toList();
            }
            // Slow path: snapshot via getIndexedValue so accessor descriptors
            // installed at any index dispatch through the getter (matching the
            // ObjectLike branch below for non-array array-likes).
            CoreContext cc = context instanceof CoreContext cx ? cx : null;
            int len = arr.size();
            List<Object> snapshot = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                Object v = arr.getIndexedValue(i, arr, cc);
                snapshot.add(v == null ? Terms.UNDEFINED : v);
            }
            return snapshot;
        }
        if (thisObj instanceof List<?> list) {
            return (List<Object>) list;
        }
        // Handle raw Java arrays (String[], int[], Object[], etc.) and byte[] via toObjectLike
        // Note: toJsArray excludes byte[], but toObjectLike handles it via JsUint8Array
        ObjectLike ol = Terms.toObjectLike(thisObj);
        if (ol instanceof JsArray arr) {
            return arr.toList();
        }
        // Spec: Array.prototype.* are intentionally generic — they treat `this` as an
        // array-like object with a `.length` and indexed properties. Snapshot 0..len-1
        // for read-only operations. Mutating methods (push/pop/sort/...) on non-arrays
        // mutate this snapshot, which doesn't propagate back to the source — non-spec,
        // but better than crashing. Real spec ToObject + property writes would need
        // descriptor infrastructure we don't yet have.
        if (ol != null) {
            Object lenObj = ol.getMember("length");
            if (lenObj instanceof Number n) {
                int len = Math.max(n.intValue(), 0);
                List<Object> snapshot = new ArrayList<>(len);
                CoreContext cc = context instanceof CoreContext cx ? cx : null;
                for (int i = 0; i < len; i++) {
                    // Accessor descriptors at any index resolve via the
                    // receiver-aware getMember. Setter-only / no-ctx
                    // accessors yield undefined.
                    Object v = ol.getMember(String.valueOf(i), ol, cc);
                    snapshot.add(v == null ? Terms.UNDEFINED : v);
                }
                return snapshot;
            }
        }
        return new ArrayList<>();
    }

    private static Iterable<KeyValue> jsEntries(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsArray arr && !arr.hasAnyDescriptor()) {
            return arr.jsEntries();
        }
        // Handle raw Java arrays (String[], int[], Object[], etc.) and byte[] via toObjectLike
        // Note: toJsArray excludes byte[], but toObjectLike handles it via JsUint8Array
        ObjectLike ol = Terms.toObjectLike(thisObj);
        if (ol instanceof JsArray arr && !arr.hasAnyDescriptor()) {
            return arr.jsEntries();
        }
        // Falls through to the array-like snapshot in rawList for ObjectLike with .length —
        // including JsArrays whose indices carry accessor descriptors, so the snapshot
        // resolves the getters with the right `this`.
        List<Object> list = rawList(context);
        return () -> new Iterator<>() {
            int index = 0;
            @Override
            public boolean hasNext() {
                return index < list.size();
            }
            @Override
            public KeyValue next() {
                int i = index++;
                return new KeyValue(thisObj, i, i + "", list.get(i));
            }
        };
    }

    private static final String LENGTH_NON_WRITABLE =
            "Cannot assign to read only property 'length' of object '[object Array]'";

    /**
     * Spec-shape {@code Set(O, "length", newLen, true)} for the four mutating
     * methods (pop/shift/push/unshift). Routes through
     * {@link JsArray#handleLengthAssign} so {@link JsArray.ArrayLength#applySet}
     * applies the truncate / extend (and HOLE-pad as needed); throws TypeError
     * when length is non-writable per the spec's {@code Throw=true} contract.
     * <p>
     * Critically called <em>after</em> the spec's Get / Delete / Set
     * per-element steps so prototype getter/setter side-effects observable
     * via call-count assertions ({@code set-length-array-length-is-non-writable.js}
     * cluster) match — a getter that flips length to non-writable still has
     * fired exactly once before the throw.
     */
    private static void setLengthOrThrow(JsArray arr, int newLen, CoreContext ctx) {
        if (!arr.handleLengthAssign(newLen, ctx)) {
            throw JsErrorException.typeError(LENGTH_NON_WRITABLE);
        }
    }

    /**
     * Spec-shape {@code HasProperty(O, name)} — own ({@link ObjectLike#isOwnProperty})
     * plus a walk of the {@code __proto__} chain. Used by shift/unshift's
     * move loop to distinguish "Set inherited value at the moved-to slot"
     * (fromPresent true; spec step Set(O, toKey, Get(O, fromKey), true))
     * from "delete the moved-to slot" (fromPresent false; spec step
     * DeletePropertyOrThrow(O, toKey)). Walks via {@link ObjectLike#getPrototype}
     * so {@code Array.prototype[i] = …} or accessor descriptors installed
     * higher up the chain are observed. Cycle-safe in practice (constructor
     * graphs have no proto cycles); a defensive depth-cap could be added if
     * a hostile bridge ever introduces one.
     */
    private static boolean hasPropertyChain(ObjectLike obj, String name) {
        for (ObjectLike o = obj; o != null; o = o.getPrototype()) {
            if (o.isOwnProperty(name)) return true;
        }
        return false;
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    private static JsCallable toCallable(Object[] args) {
        if (args.length > 0 && args[0] instanceof JsCallable callable) {
            return callable;
        }
        return (c, a) -> Terms.UNDEFINED;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(List<Object> source, List<Object> result, int depth) {
        for (Object item : source) {
            if (depth > 0 && item instanceof List<?> list) {
                flatten((List<Object>) list, result, depth - 1);
            } else {
                result.add(item);
            }
        }
    }

    // Instance methods

    private Object map(Context context, Object[] args) {
        List<Object> results = new ArrayList<>();
        JsCallable callable = toCallable(args);
        for (KeyValue kv : Terms.toIterable(context.getThisObject())) {
            Object result = callable.call(context, new Object[]{kv.value(), kv.index()});
            results.add(result);
        }
        return results;
    }

    private Object filter(Context context, Object[] args) {
        List<Object> results = new ArrayList<>();
        JsCallable callable = toCallable(args);
        for (KeyValue kv : jsEntries(context)) {
            Object result = callable.call(context, new Object[]{kv.value(), kv.index()});
            if (Terms.isTruthy(result)) {
                results.add(kv.value());
            }
        }
        return results;
    }

    private Object join(Context context, Object[] args) {
        StringBuilder sb = new StringBuilder();
        CoreContext cc = context instanceof CoreContext core ? core : null;
        String delimiter = ",";
        if (args.length > 0 && args[0] != null && args[0] != Terms.UNDEFINED) {
            delimiter = Terms.toStringCoerce(args[0], cc);
        }
        // Spec §23.1.3.18: walks 0..length-1 reading each via [[Get]]; holes
        // contribute the empty string (per spec they read as undefined and
        // undefined → ""). The hole-skipping {@link #jsEntries} would
        // produce {@code [0,,,3].join() === "0,3"} which is wrong —
        // {@code "0,,,3"} is the spec answer. Use the dense {@code rawList}
        // snapshot (which already translates HOLE → UNDEFINED at the seam)
        // so the index walk respects length without the hole-skip filter.
        List<Object> snapshot = rawList(context);
        for (int i = 0, n = snapshot.size(); i < n; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            Object v = snapshot.get(i);
            // Spec: null / undefined / hole elements contribute the empty
            // string. JsArray.unwrapHole already mapped HOLE → UNDEFINED
            // before snapshot; the rawList fast-path on a plain JsArray
            // returns toList() directly, which still carries HOLE — so
            // check for both.
            if (v != null && v != Terms.UNDEFINED && v != JsArray.HOLE) {
                sb.append(Terms.toStringCoerce(v, cc));
            }
        }
        return sb.toString();
    }

    private Object find(Context context, Object[] args) {
        JsCallable callable = toCallable(args);
        for (KeyValue kv : jsEntries(context)) {
            Object result = callable.call(context, new Object[]{kv.value(), kv.index()});
            if (Terms.isTruthy(result)) {
                return kv.value();
            }
        }
        return Terms.UNDEFINED;
    }

    private Object findIndex(Context context, Object[] args) {
        JsCallable callable = toCallable(args);
        for (KeyValue kv : jsEntries(context)) {
            Object result = callable.call(context, new Object[]{kv.value(), kv.index()});
            if (Terms.isTruthy(result)) {
                return kv.index();
            }
        }
        return -1;
    }

    private Object push(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        if (thisObj instanceof JsArray arr) {
            // Spec §23.1.3.21 Array.prototype.push:
            //   5. For each item E: Set(O, ToString(len), E, true); len += 1.
            //   6. Set(O, "length", len, true).
            // Per-item Set walks the proto chain so a setter installed on
            // Array.prototype["0"] fires (test262 set-length-array-length-
            // is-non-writable.js push variant). The proto setter accepting
            // the value means no own property is created — matches the
            // assertion !arr.hasOwnProperty(0).
            int len = arr.size();
            for (int i = 0; i < args.length; i++) {
                PropertyAccess.setByName(arr, String.valueOf(len + i), args[i], cc, null);
            }
            int newLen = len + args.length;
            setLengthOrThrow(arr, newLen, cc);
            return arr.size();
        }
        // Generic array-like fallback.
        List<Object> thisArray = rawList(context);
        thisArray.addAll(Arrays.asList(args));
        return thisArray.size();
    }

    private Object reverse(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        List<Object> result = new ArrayList<>();
        for (int i = size; i > 0; i--) {
            result.add(thisArray.get(i - 1));
        }
        return result;
    }

    private Object includes(Context context, Object[] args) {
        for (KeyValue kv : jsEntries(context)) {
            if (Terms.eq(kv.value(), args[0], false)) {
                return true;
            }
        }
        return false;
    }

    private Object indexOf(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length == 0) {
            return -1;
        }
        Object searchElement = args[0];
        int fromIndex = 0;
        if (args.length > 1 && args[1] != null) {
            fromIndex = Terms.objectToNumber(args[1]).intValue();
            if (fromIndex < 0) {
                fromIndex = Math.max(size + fromIndex, 0);
            }
        }
        if (fromIndex >= size) {
            return -1;
        }
        for (int i = fromIndex; i < size; i++) {
            if (Terms.eq(thisArray.get(i), searchElement, false)) {
                return i;
            }
        }
        return -1;
    }

    private Object slice(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        int start = 0;
        int end = size;
        if (args.length > 0 && args[0] != null) {
            start = Terms.objectToNumber(args[0]).intValue();
            if (start < 0) {
                start = Math.max(size + start, 0);
            }
        }
        if (args.length > 1 && args[1] != null) {
            end = Terms.objectToNumber(args[1]).intValue();
            if (end < 0) {
                end = Math.max(size + end, 0);
            }
        }
        start = Math.min(start, size);
        end = Math.min(end, size);
        List<Object> result = new ArrayList<>();
        for (int i = start; i < end; i++) {
            result.add(thisArray.get(i));
        }
        return result;
    }

    private Object forEach(Context context, Object[] args) {
        JsCallable callable = toCallable(args);
        for (KeyValue kv : jsEntries(context)) {
            if (context instanceof CoreContext cc) {
                cc.iteration = kv.index();
            }
            callable.call(context, new Object[]{kv.value(), kv.index(), context.getThisObject()});
        }
        return Terms.UNDEFINED;
    }

    @SuppressWarnings("unchecked")
    private Object concat(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        List<Object> result = new ArrayList<>(thisArray);
        for (Object arg : args) {
            if (arg instanceof List<?> list) {
                result.addAll((List<Object>) list);
            } else {
                result.add(arg);
            }
        }
        return result;
    }

    private Object every(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        if (thisArray.isEmpty()) {
            return true;
        }
        JsCallable callable = toCallable(args);
        for (KeyValue kv : jsEntries(context)) {
            Object result = callable.call(context, new Object[]{kv.value(), kv.index(), thisArray});
            if (!Terms.isTruthy(result)) {
                return false;
            }
        }
        return true;
    }

    private Object some(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        if (thisArray.isEmpty()) {
            return false;
        }
        JsCallable callable = toCallable(args);
        for (KeyValue kv : jsEntries(context)) {
            Object result = callable.call(context, new Object[]{kv.value(), kv.index(), thisArray});
            if (Terms.isTruthy(result)) {
                return true;
            }
        }
        return false;
    }

    private Object reduce(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        JsCallable callable = toCallable(args);
        if (thisArray.isEmpty() && args.length < 2) {
            throw JsErrorException.typeError("Reduce of empty array with no initial value");
        }
        int startIndex = 0;
        Object accumulator;
        if (args.length >= 2) {
            accumulator = args[1];
        } else {
            accumulator = thisArray.getFirst();
            startIndex = 1;
        }
        for (int i = startIndex; i < thisArray.size(); i++) {
            Object currentValue = thisArray.get(i);
            accumulator = callable.call(context, new Object[]{accumulator, currentValue, i, thisArray});
        }
        return accumulator;
    }

    private Object reduceRight(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        JsCallable callable = toCallable(args);
        if (thisArray.isEmpty() && args.length < 2) {
            throw JsErrorException.typeError("Reduce of empty array with no initial value");
        }
        int startIndex = thisArray.size() - 1;
        Object accumulator;
        if (args.length >= 2) {
            accumulator = args[1];
        } else {
            accumulator = thisArray.get(startIndex);
            startIndex--;
        }
        for (int i = startIndex; i >= 0; i--) {
            Object currentValue = thisArray.get(i);
            accumulator = callable.call(context, new Object[]{accumulator, currentValue, i, thisArray});
        }
        return accumulator;
    }

    private Object flat(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int depth = 1;
        if (args.length > 0 && args[0] != null) {
            Number depthNum = Terms.objectToNumber(args[0]);
            if (!Double.isNaN(depthNum.doubleValue()) && !Double.isInfinite(depthNum.doubleValue())) {
                depth = depthNum.intValue();
            }
        }
        List<Object> result = new ArrayList<>();
        flatten(thisArray, result, depth);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object flatMap(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        JsCallable callable = toCallable(args);
        List<Object> mappedResult = new ArrayList<>();
        int index = 0;
        for (Object item : thisArray) {
            Object mapped = callable.call(context, new Object[]{item, index, thisArray});
            if (mapped instanceof List<?> list) {
                mappedResult.addAll((List<Object>) list);
            } else {
                mappedResult.add(mapped);
            }
            index++;
        }
        return mappedResult;
    }

    private Object sort(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        List<Object> list = new ArrayList<>(thisArray);
        if (list.isEmpty()) {
            return list;
        }
        if (args.length > 0 && args[0] instanceof JsCallable) {
            JsCallable callable = toCallable(args);
            list.sort((a, b) -> {
                Object result = callable.call(context, new Object[]{a, b});
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
                return 0;
            });
        } else {
            list.sort((a, b) -> {
                String strA = a != null ? a.toString() : "";
                String strB = b != null ? b.toString() : "";
                return strA.compareTo(strB);
            });
        }
        // in js, sort modifies the original array and returns it
        for (int i = 0; i < list.size(); i++) {
            if (i < thisArray.size()) {
                thisArray.set(i, list.get(i));
            } else {
                thisArray.add(list.get(i));
            }
        }
        return thisArray;
    }

    private Object fill(Context context, Object[] args) {
        if (args.length == 0) {
            return context.getThisObject();
        }
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0) {
            return thisArray;
        }
        Object value = args[0];
        int start = 0;
        int end = size;
        if (args.length > 1 && args[1] != null) {
            start = Terms.objectToNumber(args[1]).intValue();
            if (start < 0) {
                start = Math.max(size + start, 0);
            }
        }
        if (args.length > 2 && args[2] != null) {
            end = Terms.objectToNumber(args[2]).intValue();
            if (end < 0) {
                end = Math.max(size + end, 0);
            }
        }
        start = Math.min(start, size);
        end = Math.min(end, size);
        for (int i = start; i < end; i++) {
            thisArray.set(i, value);
        }
        return thisArray;
    }

    private Object splice(Context context, Object[] args) {
        if (args.length == 0) {
            return new ArrayList<>();
        }
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0) {
            return new ArrayList<>();
        }
        int start = 0;
        if (args[0] != null) {
            start = Terms.objectToNumber(args[0]).intValue();
            if (start < 0) {
                start = Math.max(size + start, 0);
            }
        }
        start = Math.min(start, size);
        int deleteCount = size - start;
        if (args.length > 1 && args[1] != null) {
            deleteCount = Terms.objectToNumber(args[1]).intValue();
            deleteCount = Math.min(Math.max(deleteCount, 0), size - start);
        }
        List<Object> elementsToAdd = new ArrayList<>();
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                elementsToAdd.add(args[i]);
            }
        }
        List<Object> removedElements = new ArrayList<>();
        for (int i = start; i < start + deleteCount; i++) {
            removedElements.add(thisArray.get(i));
        }
        int newSize = size - deleteCount + elementsToAdd.size();
        List<Object> newList = new ArrayList<>(newSize);
        for (int i = 0; i < start; i++) {
            newList.add(thisArray.get(i));
        }
        newList.addAll(elementsToAdd);
        for (int i = start + deleteCount; i < size; i++) {
            newList.add(thisArray.get(i));
        }
        // update original array
        thisArray.clear();
        thisArray.addAll(newList);
        return removedElements;
    }

    private Object shift(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        if (thisObj instanceof JsArray arr) {
            // Spec §23.1.3.27 Array.prototype.shift:
            //   3. If len = 0: Set(O, "length", 0, true); return undefined.
            //   4. first = Get(O, "0").
            //   5-7. Repeat for k = 1 to len-1:
            //          fromKey = ToString(k); toKey = ToString(k-1)
            //          if HasProperty(O, fromKey): Set(O, toKey, Get(O, fromKey), true)
            //          else:                       DeletePropertyOrThrow(O, toKey)
            //   8. DeletePropertyOrThrow(O, ToString(len-1)).
            //   9. Set(O, "length", len-1, true).
            // Get/Set/Delete walk the proto chain so accessors / inherited
            // indices (Array.prototype[i] = …) fire at the spec-correct steps —
            // covers test262 S15.4.4.9_A4_T1 / A4_T2 (inherited proto value
            // surfaces at toKey when fromKey is a hole). The final delete
            // (step 8) is implicit in {@link JsArray.ArrayLength#applySet}'s
            // truncate when length is writable; same pragmatic simplification
            // as pop. Strict-mode flip on Set/Delete failure → deferred TODO.
            int len = arr.size();
            if (len == 0) {
                setLengthOrThrow(arr, 0, cc);
                return Terms.UNDEFINED;
            }
            Object first = arr.getMember("0", arr, cc);
            if (first == null) first = Terms.UNDEFINED;
            for (int k = 1; k < len; k++) {
                String fromKey = String.valueOf(k);
                String toKey = String.valueOf(k - 1);
                if (hasPropertyChain(arr, fromKey)) {
                    Object fromVal = arr.getMember(fromKey, arr, cc);
                    if (fromVal == null) fromVal = Terms.UNDEFINED;
                    PropertyAccess.setByName(arr, toKey, fromVal, cc, null);
                } else {
                    arr.removeMember(toKey);
                }
            }
            setLengthOrThrow(arr, len - 1, cc);
            return first;
        }
        // Generic array-like fallback.
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0) {
            return Terms.UNDEFINED;
        }
        Object firstElement = thisArray.getFirst();
        List<Object> newList = new ArrayList<>(size - 1);
        for (int i = 1; i < size; i++) {
            newList.add(thisArray.get(i));
        }
        thisArray.clear();
        thisArray.addAll(newList);
        return firstElement;
    }

    private Object unshift(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        if (thisObj instanceof JsArray arr) {
            // Spec §23.1.3.32 Array.prototype.unshift:
            //   4. If argCount > 0:
            //      Repeat for k = len down to 1:
            //        from = ToString(k-1); to = ToString(k+argCount-1)
            //        if HasProperty(O, from): Set(O, to, Get(O, from), true)
            //        else:                    DeletePropertyOrThrow(O, to)
            //      Repeat for j = 0 to argCount-1: Set(O, ToString(j), items[j], true)
            //   5. Set(O, "length", len + argCount, true).
            // Move loop dispatches through Get/Set/Delete so a proto-installed
            // accessor at an intermediate index fires at the spec-correct
            // step (test262 S15.4.4.13_A4_T2: inherited index surfaces at
            // the moved-to slot when the source slot is a hole). Per-arg Set
            // walks proto setters so a setter installed on Array.prototype["0"]
            // fires (set-length-array-length-is-non-writable.js unshift
            // variant). Strict-mode flip on Set/Delete failure → deferred TODO.
            int len = arr.size();
            int argCount = args.length;
            if (argCount > 0) {
                for (int k = len - 1; k >= 0; k--) {
                    String fromKey = String.valueOf(k);
                    String toKey = String.valueOf(k + argCount);
                    if (hasPropertyChain(arr, fromKey)) {
                        Object fromVal = arr.getMember(fromKey, arr, cc);
                        if (fromVal == null) fromVal = Terms.UNDEFINED;
                        PropertyAccess.setByName(arr, toKey, fromVal, cc, null);
                    } else {
                        arr.removeMember(toKey);
                    }
                }
                for (int j = 0; j < argCount; j++) {
                    PropertyAccess.setByName(arr, String.valueOf(j), args[j], cc, null);
                }
            }
            int newLen = len + argCount;
            setLengthOrThrow(arr, newLen, cc);
            return arr.size();
        }
        // Generic array-like fallback.
        List<Object> thisArray = rawList(context);
        if (args.length == 0) {
            return thisArray.size();
        }
        List<Object> newList = new ArrayList<>(thisArray.size() + args.length);
        newList.addAll(Arrays.asList(args));
        newList.addAll(thisArray);
        thisArray.clear();
        thisArray.addAll(newList);
        return thisArray.size();
    }

    private Object lastIndexOf(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length == 0) {
            return -1;
        }
        Object searchElement = args[0];
        int fromIndex = size - 1;
        if (args.length > 1 && args[1] != null) {
            Number n = Terms.objectToNumber(args[1]);
            if (Double.isNaN(n.doubleValue())) {
                fromIndex = size - 1;
            } else {
                fromIndex = n.intValue();
                if (fromIndex < 0) {
                    fromIndex = size + fromIndex;
                } else if (fromIndex >= size) {
                    fromIndex = size - 1;
                }
            }
        }
        if (fromIndex < 0) {
            return -1;
        }
        for (int i = fromIndex; i >= 0; i--) {
            if (Terms.eq(thisArray.get(i), searchElement, false)) {
                return i;
            }
        }
        return -1;
    }

    private Object pop(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        if (thisObj instanceof JsArray arr) {
            // Spec §23.1.3.20 Array.prototype.pop:
            //   3. If len = 0: Set(O, "length", +0, true); return undefined.
            //   4. Else: element = Get(O, ToString(len-1)); DeletePropertyOrThrow(...);
            //      Set(O, "length", len-1, true); return element.
            // The Get walks the proto chain when the index is a hole — proto-
            // installed accessors (Array.prototype["last"] = {get:…}) fire
            // during pop, observable via call-count assertions in
            // set-length-array-length-is-non-writable.js. The length-set
            // happens AFTER the Get / Delete, so a getter side-effect that
            // makes length non-writable still throws at the spec-correct step.
            int len = arr.size();
            if (len == 0) {
                setLengthOrThrow(arr, 0, cc);
                return Terms.UNDEFINED;
            }
            int newLen = len - 1;
            String index = String.valueOf(newLen);
            Object element = arr.getMember(index, arr, cc);
            if (element == null) element = Terms.UNDEFINED;
            // DeletePropertyOrThrow(O, index) — the per-element delete is
            // implicit in {@link JsArray.ArrayLength#applySet}'s truncate
            // when length is writable, so omitted here. (When length is
            // non-writable, the spec leaves the array in an inconsistent
            // intermediate state — the test cluster doesn't observe it.)
            setLengthOrThrow(arr, newLen, cc);
            return element;
        }
        // Generic array-like fallback (Array.prototype.pop.call(obj))
        // — best-effort snapshot manipulation; doesn't write back to source.
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0) {
            return Terms.UNDEFINED;
        }
        Object lastElement = thisArray.get(size - 1);
        List<Object> newList = new ArrayList<>(size - 1);
        for (int i = 0; i < size - 1; i++) {
            newList.add(thisArray.get(i));
        }
        thisArray.clear();
        thisArray.addAll(newList);
        return lastElement;
    }

    private Object at(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length == 0 || args[0] == null) {
            return Terms.UNDEFINED;
        }
        int index = Terms.objectToNumber(args[0]).intValue();
        if (index < 0) {
            index = size + index;
        }
        if (index < 0 || index >= size) {
            return Terms.UNDEFINED;
        }
        return thisArray.get(index);
    }

    private Object copyWithin(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length == 0) {
            return thisArray;
        }
        int target = Terms.objectToNumber(args[0]).intValue();
        if (target < 0) {
            target = Math.max(size + target, 0);
        }
        int start = 0;
        if (args.length > 1 && args[1] != null) {
            start = Terms.objectToNumber(args[1]).intValue();
            if (start < 0) {
                start = Math.max(size + start, 0);
            }
        }
        int end = size;
        if (args.length > 2 && args[2] != null) {
            end = Terms.objectToNumber(args[2]).intValue();
            if (end < 0) {
                end = Math.max(size + end, 0);
            }
        }
        start = Math.min(start, size);
        end = Math.min(end, size);
        target = Math.min(target, size);
        List<Object> toCopy = new ArrayList<>();
        for (int i = start; i < end; i++) {
            toCopy.add(thisArray.get(i));
        }
        if (toCopy.isEmpty()) {
            return thisArray;
        }
        // avoid concurrent modification issues
        List<Object> list = new ArrayList<>(thisArray);
        // copy elements over
        int copyCount = 0;
        for (int i = target; i < size && copyCount < toCopy.size(); i++) {
            list.set(i, toCopy.get(copyCount++));
        }
        // update original array
        thisArray.clear();
        thisArray.addAll(list);
        return thisArray;
    }

    private Object keys(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        List<Object> result = new ArrayList<>();
        int size = thisArray.size();
        for (int i = 0; i < size; i++) {
            result.add(i);
        }
        return result;
    }

    private Object values(Context context, Object[] args) {
        return rawList(context);
    }

    private Object entries(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        List<Object> result = new ArrayList<>();
        int size = thisArray.size();
        for (int i = 0; i < size; i++) {
            List<Object> entry = new ArrayList<>();
            entry.add(i);
            entry.add(thisArray.get(i));
            result.add(entry);
        }
        return result;
    }

    private Object findLast(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length == 0) {
            return Terms.UNDEFINED;
        }
        JsCallable callable = toCallable(args);
        for (int i = size - 1; i >= 0; i--) {
            Object value = thisArray.get(i);
            Object result = callable.call(context, new Object[]{value, i, thisArray});
            if (Terms.isTruthy(result)) {
                return value;
            }
        }
        return Terms.UNDEFINED;
    }

    private Object findLastIndex(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length == 0) {
            return -1;
        }
        JsCallable callable = toCallable(args);
        for (int i = size - 1; i >= 0; i--) {
            Object value = thisArray.get(i);
            Object result = callable.call(context, new Object[]{value, i, thisArray});
            if (Terms.isTruthy(result)) {
                return i;
            }
        }
        return -1;
    }

    private Object withMethod(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        int size = thisArray.size();
        if (size == 0 || args.length < 2) {
            return thisArray;
        }
        int index = Terms.objectToNumber(args[0]).intValue();
        if (index < 0) {
            index = size + index;
        }
        if (index < 0 || index >= size) {
            return thisArray; // If index is out of bounds, return a copy of the array
        }
        Object value = args[1];
        // Create a copy of the original array
        List<Object> result = new ArrayList<>(thisArray);
        // Replace the value at the specified index
        result.set(index, value);
        return result;
    }

    private Object group(Context context, Object[] args) {
        List<Object> thisArray = rawList(context);
        if (args.length == 0) {
            return new JsObject();
        }
        JsCallable callable = toCallable(args);
        Map<String, List<Object>> groups = new HashMap<>();
        for (KeyValue kv : jsEntries(context)) {
            Object key = callable.call(context, new Object[]{kv.value(), kv.index(), thisArray});
            String keyStr = key == null ? "null" : key.toString();
            if (!groups.containsKey(keyStr)) {
                groups.put(keyStr, new ArrayList<>());
            }
            groups.get(keyStr).add(kv.value());
        }
        JsObject result = new JsObject();
        for (Map.Entry<String, List<Object>> entry : groups.entrySet()) {
            result.putMember(entry.getKey(), entry.getValue());
        }
        return result;
    }

}
