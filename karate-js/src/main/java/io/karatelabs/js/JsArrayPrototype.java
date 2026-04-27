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
        // for the read-only / new-array-returning methods (slice / concat / flat /
        // join / at / keys / values / entries / withMethod / group). Mutating
        // methods (push / pop / shift / unshift / sort / splice / reverse / fill /
        // copyWithin) bypass this and dispatch per-index through specGet / specSet /
        // specDelete on the receiver so writes propagate back to a non-array
        // ObjectLike `this`.
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
     * Spec-shape {@code Set(O, "length", newLen, true)} for every mutating
     * method (pop/shift/push/unshift/sort/splice/reverse/fill/copyWithin).
     * On a {@link JsArray} routes through {@link JsArray#handleLengthAssign}
     * so {@link JsArray.ArrayLength#applySet} applies the truncate / extend
     * (and HOLE-pad as needed); throws TypeError when length is non-writable
     * per the spec's {@code Throw=true} contract. On a generic {@code ObjectLike}
     * receiver (the {@code obj.shift = Array.prototype.shift; obj.shift()}
     * pattern) writes via {@link PropertyAccess#setByName} so a setter
     * installed at {@code length} on the proto chain fires.
     * <p>
     * Critically called <em>after</em> the spec's Get / Delete / Set
     * per-element steps so prototype getter/setter side-effects observable
     * via call-count assertions ({@code set-length-array-length-is-non-writable.js}
     * cluster) match — a getter that flips length to non-writable still has
     * fired exactly once before the throw.
     */
    private static void setLength(ObjectLike target, int newLen, CoreContext ctx) {
        if (target instanceof JsArray arr) {
            if (!arr.handleLengthAssign(newLen, ctx)) {
                throw JsErrorException.typeError(LENGTH_NON_WRITABLE);
            }
            return;
        }
        PropertyAccess.setByName(target, "length", newLen, ctx, null);
    }

    /**
     * Spec-shape {@code Get(O, name)} — proto-walking via the receiver-aware
     * {@link ObjectLike#getMember(String, Object, CoreContext)} so accessor
     * descriptors installed anywhere in the chain dispatch with the right
     * {@code this}. Returns {@link Terms#UNDEFINED} when the chain bottoms
     * out so callers don't have to null-coalesce.
     */
    private static Object specGet(ObjectLike target, String name, CoreContext ctx) {
        Object v = target.getMember(name, target, ctx);
        return v == null ? Terms.UNDEFINED : v;
    }

    /**
     * Spec-shape {@code Set(O, name, value, true)} — routes through
     * {@link PropertyAccess#setByName} so accessor-setters installed on the
     * proto chain fire (test262 {@code set-length-array-length-is-non-writable.js}
     * cluster) and JsArray length writes route through
     * {@link JsArray#handleLengthAssign}.
     */
    private static void specSet(ObjectLike target, String name, Object value, CoreContext ctx) {
        PropertyAccess.setByName(target, name, value, ctx, null);
    }

    /**
     * Spec-shape {@code DeletePropertyOrThrow(O, name)} — JsArray tombstones
     * the dense slot with {@link JsArray#HOLE} (so {@code hasOwnProperty(idx)}
     * reads false after); generic ObjectLike removes the entry from
     * {@code props} via {@link ObjectLike#removeMember}. Lenient on the spec's
     * configurable check; strict-mode TypeError flip tracked under the
     * JsArray.removeMember TODO.
     */
    private static void specDelete(ObjectLike target, String name) {
        target.removeMember(name);
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

    /** Visitor for {@link #specIterate}; return false to short-circuit. */
    @FunctionalInterface
    private interface IndexVisitor {
        boolean visit(int index, Object value);
    }

    /**
     * Spec-correct length-bounded iteration for the
     * {@code Array.prototype.{every, some, forEach, map, filter, reduce,
     * reduceRight, find, findIndex, findLast, findLastIndex, includes,
     * indexOf, lastIndexOf, flat, flatMap}} family. Walks 0..len-1 (or
     * in reverse), invoking {@code visitor} for each index — with
     * {@code skipAbsent} true (every / some / forEach / map / filter /
     * reduce / indexOf / etc.) the visitor is skipped when
     * {@code HasProperty(O, ToString(k))} is false; with {@code skipAbsent}
     * false (find / findIndex / findLast / includes) every index is
     * visited and a hole reads as {@code undefined} via {@code Get}'s
     * proto walk.
     * <p>
     * Hot path: when {@code thisObj} is a plain {@link JsArray} with no
     * descriptors, its prototype is the standard {@code JsArrayPrototype}
     * singleton, and no canonical-numeric key was ever installed on a
     * prototype's userProps in this Engine
     * ({@link Prototype#isNumericPropPolluted} returns false), HasProperty
     * reduces to "own non-HOLE" — the loop reads dense {@code list} entries
     * directly, no per-element {@code String.valueOf} or chain walk. Slow
     * path (proto pollution, custom proto, descriptors, generic ObjectLike
     * receiver) walks {@link #hasPropertyChain} and {@link ObjectLike#getMember}
     * per index.
     */
    private static void specIterate(Context context, boolean ascending, boolean skipAbsent,
                                    IndexVisitor visitor) {
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = thisObj instanceof ObjectLike o ? o : Terms.toObjectLike(thisObj);
        if (target == null) return;
        int len = lengthOf(target, cc);
        // Fast path: plain JsArray (exact class — buffer-backed
        // {@link JsUint8Array} routes through the slow path so its
        // {@link JsArray#hasOwnIndexedSlot} override fires), no descriptors,
        // standard {@code Array.prototype}, no proto pollution. Hot loop
        // reads dense {@code list} entries directly — the spec HasProperty
        // reduces to an in-bounds non-HOLE check, no per-element
        // {@code String.valueOf} or chain walk.
        boolean clean = target.getClass() == JsArray.class
                && !((JsArray) target).hasAnyDescriptor()
                && ((JsArray) target).getPrototype() == JsArrayPrototype.INSTANCE
                && !Prototype.isNumericPropPolluted();
        if (clean) {
            JsArray arr = (JsArray) target;
            List<Object> list = arr.list;
            // {@code len} is captured at start (spec semantics); the underlying
            // list may shrink ({@code arr.length = N} in callback) or grow
            // ({@code arr.push(…)}) mid-iteration. Re-check {@code list.size()}
            // per step so OOR positions are treated as absent (HasProperty
            // false), matching the slow path.
            if (ascending) {
                for (int k = 0; k < len; k++) {
                    Object v = k < list.size() ? list.get(k) : JsArray.HOLE;
                    if (v == JsArray.HOLE) {
                        if (skipAbsent) continue;
                        v = Terms.UNDEFINED;
                    }
                    if (!visitor.visit(k, v)) return;
                }
            } else {
                for (int k = len - 1; k >= 0; k--) {
                    Object v = k < list.size() ? list.get(k) : JsArray.HOLE;
                    if (v == JsArray.HOLE) {
                        if (skipAbsent) continue;
                        v = Terms.UNDEFINED;
                    }
                    if (!visitor.visit(k, v)) return;
                }
            }
            return;
        }
        if (ascending) {
            for (int k = 0; k < len; k++) {
                if (!visitOneSlow(target, k, skipAbsent, cc, visitor)) return;
            }
        } else {
            for (int k = len - 1; k >= 0; k--) {
                if (!visitOneSlow(target, k, skipAbsent, cc, visitor)) return;
            }
        }
    }

    /** Slow-path single-index step for {@link #specIterate}. Returns false
     *  to short-circuit (visitor said stop), true to continue (including
     *  the skipped-absent case). Spec semantics: a present index reads via
     *  proto-walking {@link ObjectLike#getMember}; an absent index either
     *  skips or visits with undefined (per {@code skipAbsent}). */
    private static boolean visitOneSlow(ObjectLike target, int k, boolean skipAbsent,
                                        CoreContext cc, IndexVisitor visitor) {
        String key = String.valueOf(k);
        boolean present = hasPropertyChain(target, key);
        if (!present) {
            if (skipAbsent) return true;
            return visitor.visit(k, Terms.UNDEFINED);
        }
        Object v = target.getMember(key, target, cc);
        if (v == null) v = Terms.UNDEFINED;
        return visitor.visit(k, v);
    }

    /** Resolve the spec {@code ToLength(? Get(O, "length"))} of an array-like
     *  receiver. {@link JsArray} uses the dense {@code list.size()} (already
     *  a clamped int). Generic ObjectLike receivers read {@code .length} via
     *  {@code getMember}, then route through {@link Terms#toNumberCoerce} so
     *  a {@code length: {valueOf(){return N}}} object resolves N via the
     *  spec-prescribed valueOf invocation (test262 {@code S15.4.4.{9,13}_A2_T5}
     *  cluster); valueOf abrupt-completion propagates via {@code ctx.isError()}
     *  — caller checks and bails. NaN / -Infinity / negative / undefined map
     *  to 0 per ToLength's clamp; result is further clamped to
     *  {@code [0, Integer.MAX_VALUE]}. The full Uint53 spec range needs a
     *  long-typed length field — deferred. */
    private static int lengthOf(ObjectLike target, CoreContext ctx) {
        if (target instanceof JsArray arr) return arr.size();
        Object lenObj = target.getMember("length", target, ctx);
        if (lenObj == null || lenObj == Terms.UNDEFINED) return 0;
        Number n = lenObj instanceof ObjectLike
                ? Terms.toNumberCoerce(lenObj, ctx)
                : Terms.objectToNumber(lenObj);
        if (ctx != null && ctx.isError()) return 0;
        double d = n == null ? Double.NaN : n.doubleValue();
        if (Double.isNaN(d) || d <= 0) return 0;
        if (d >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) d;
    }

    /** Spec-shape {@code O = ? ToObject(this value)}. Returns the receiver
     *  unchanged when it's already an {@link ObjectLike}; wraps raw Java
     *  Lists / arrays via {@link Terms#toObjectLike} (so a Java
     *  {@link java.util.ArrayList} surfaces as a {@link JsArray} sharing the
     *  underlying list — mutations propagate). Returns {@code null} for
     *  uncoercible values (raw {@code null} / {@code undefined}); spec wants
     *  TypeError, current callers bail with a no-op pending the strict-mode
     *  plumbing TODO. */
    private static ObjectLike toReceiver(Object thisObj) {
        return thisObj instanceof ObjectLike o ? o : Terms.toObjectLike(thisObj);
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
            // Spec FlattenIntoArray skips holes (HasProperty-skipping); the
            // raw List walk would otherwise add HOLE sentinels to the result
            // (visible to user code as the marker object).
            if (item == JsArray.HOLE) continue;
            if (depth > 0 && item instanceof List<?> list) {
                flatten((List<Object>) list, result, depth - 1);
            } else {
                result.add(item);
            }
        }
    }

    // Instance methods

    private Object map(Context context, Object[] args) {
        // Spec §23.1.3.21: result is a fresh Array of length=len. For each
        // k where HasProperty(O, k): result[k] = callback(Get(O, k), k, O).
        // Indices where HasProperty is false stay HOLE in the result so
        // {@code result.length} matches source length and sparse positions
        // round-trip (test262 `15.4.4.19-8-6.js` expects this).
        // Returns a plain {@code ArrayList} so {@link JsArray#get(int)}'s
        // toJava unwrap doesn't strip {@code Terms.UNDEFINED} / {@link JsDate}
        // wrappers (Java-interop callers in {@code JsJavaInteropTest} expect
        // raw values via {@code list.get(i)}); JS-side reads still go through
        // {@link Terms#toObjectLike} which wraps as {@link JsArray} for
        // chained {@code .filter} / {@code .length} access.
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = thisObj instanceof ObjectLike o ? o : Terms.toObjectLike(thisObj);
        int len = target == null ? 0 : lengthOf(target, cc);
        List<Object> results = new ArrayList<>(len);
        for (int i = 0; i < len; i++) results.add(JsArray.HOLE);
        JsCallable callable = toCallable(args);
        specIterate(context, true, true, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            results.set(k, r);
            return true;
        });
        return results;
    }

    private Object filter(Context context, Object[] args) {
        List<Object> results = new ArrayList<>();
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        specIterate(context, true, true, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (Terms.isTruthy(r)) results.add(v);
            return true;
        });
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
        // Spec §23.1.3.9: visits every index 0..len-1 (NO HasProperty filter)
        // — holes read as undefined via Get's proto walk. {@code skipAbsent=false}.
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        Object[] result = {Terms.UNDEFINED};
        specIterate(context, true, false, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (Terms.isTruthy(r)) {
                result[0] = v;
                return false;
            }
            return true;
        });
        return result[0];
    }

    private Object findIndex(Context context, Object[] args) {
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        int[] result = {-1};
        specIterate(context, true, false, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (Terms.isTruthy(r)) {
                result[0] = k;
                return false;
            }
            return true;
        });
        return result[0];
    }

    private Object push(Context context, Object[] args) {
        // Spec §23.1.3.21 Array.prototype.push:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   5. For each E: Set(O, ToString(len), E, true); len += 1.
        //   6. Set(O, "length", len, true).
        // Per-item Set walks the proto chain so a setter installed on
        // Array.prototype["0"] fires (test262
        // set-length-array-length-is-non-writable.js push variant). On a
        // generic ObjectLike receiver (obj.push = Array.prototype.push) the
        // same Set / length-Set steps write back to obj — the snapshot path
        // we used to take here was non-spec.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return 0;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        for (int i = 0; i < args.length; i++) {
            specSet(target, String.valueOf(len + i), args[i], cc);
        }
        int newLen = len + args.length;
        setLength(target, newLen, cc);
        return newLen;
    }

    private Object reverse(Context context, Object[] args) {
        // Spec §23.1.3.26 Array.prototype.reverse:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   3. middle = floor(len / 2).
        //   4. For lower in [0, middle): upper = len - lower - 1
        //      (lowerHas, lowerVal) = HasProperty(O, lowerKey) ? (T, Get) : (F, _)
        //      (upperHas, upperVal) = HasProperty(O, upperKey) ? (T, Get) : (F, _)
        //      If both: Set lowerKey = upperVal; Set upperKey = lowerVal
        //      Else if upperHas only: Set lowerKey = upperVal; Delete upperKey
        //      Else if lowerHas only: Delete lowerKey; Set upperKey = lowerVal
        //      Else: do nothing
        //   5. Return O.
        // Per-pair HasProperty/Get/Set/Delete dispatches through the proto
        // chain — required for test262 S15.4.4.8_A2_T1 cluster (generic
        // ObjectLike with sparse holes; reverse must mutate `this` and
        // return it, deleting moved-from-hole positions on the destination
        // side). The previous implementation returned a fresh list and
        // didn't mutate the receiver — non-spec for both array and
        // ObjectLike receivers.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return Terms.UNDEFINED;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        int middle = len >>> 1;
        for (int lower = 0; lower < middle; lower++) {
            int upper = len - lower - 1;
            String lowerKey = String.valueOf(lower);
            String upperKey = String.valueOf(upper);
            boolean lowerHas = hasPropertyChain(target, lowerKey);
            boolean upperHas = hasPropertyChain(target, upperKey);
            Object lowerVal = lowerHas ? specGet(target, lowerKey, cc) : null;
            Object upperVal = upperHas ? specGet(target, upperKey, cc) : null;
            if (lowerHas && upperHas) {
                specSet(target, lowerKey, upperVal, cc);
                specSet(target, upperKey, lowerVal, cc);
            } else if (upperHas) {
                specSet(target, lowerKey, upperVal, cc);
                specDelete(target, upperKey);
            } else if (lowerHas) {
                specDelete(target, lowerKey);
                specSet(target, upperKey, lowerVal, cc);
            }
        }
        return target;
    }

    private Object includes(Context context, Object[] args) {
        // Spec §23.1.3.13: visits every index 0..len-1 (NO HasProperty filter)
        // — `[1,2,,4].includes(undefined) === true` because the hole reads as
        // undefined via Get's proto walk. SameValueZero comparison.
        Object searchElement = args.length > 0 ? args[0] : Terms.UNDEFINED;
        boolean[] found = {false};
        specIterate(context, true, false, (k, v) -> {
            if (Terms.eq(v, searchElement, false)) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    private Object indexOf(Context context, Object[] args) {
        // Spec §23.1.3.16: HasProperty-skipping (holes contribute nothing —
        // never compared, never returned). The sparse-skip is observable:
        // `[,1].indexOf(undefined) === -1`, but
        // `[undefined,1].indexOf(undefined) === 0`.
        if (args.length == 0) return -1;
        Object searchElement = args[0];
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = thisObj instanceof ObjectLike o ? o : Terms.toObjectLike(thisObj);
        int len = target == null ? 0 : lengthOf(target, cc);
        if (len == 0) return -1;
        int fromIndex = 0;
        if (args.length > 1 && args[1] != null) {
            fromIndex = Terms.objectToNumber(args[1]).intValue();
            if (fromIndex < 0) fromIndex = Math.max(len + fromIndex, 0);
        }
        if (fromIndex >= len) return -1;
        int[] result = {-1};
        int from = fromIndex;
        specIterate(context, true, true, (k, v) -> {
            if (k < from) return true;
            if (Terms.eq(v, searchElement, false)) {
                result[0] = k;
                return false;
            }
            return true;
        });
        return result[0];
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
        Object thisObj = context.getThisObject();
        specIterate(context, true, true, (k, v) -> {
            if (context instanceof CoreContext cc) {
                cc.iteration = k;
            }
            callable.call(context, new Object[]{v, k, thisObj});
            return true;
        });
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
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        boolean[] result = {true};
        specIterate(context, true, true, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (!Terms.isTruthy(r)) {
                result[0] = false;
                return false;
            }
            return true;
        });
        return result[0];
    }

    private Object some(Context context, Object[] args) {
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        boolean[] result = {false};
        specIterate(context, true, true, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (Terms.isTruthy(r)) {
                result[0] = true;
                return false;
            }
            return true;
        });
        return result[0];
    }

    private Object reduce(Context context, Object[] args) {
        // Spec §23.1.3.24: HasProperty-skipping. Initial accumulator is
        // args[1] when present; otherwise the first present value (and that
        // index is then skipped on the iteration). Empty + no initial throws.
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        Object[] acc = new Object[1];
        boolean[] hasAcc = {args.length >= 2};
        if (hasAcc[0]) acc[0] = args[1];
        specIterate(context, true, true, (k, v) -> {
            if (!hasAcc[0]) {
                acc[0] = v;
                hasAcc[0] = true;
                return true;
            }
            acc[0] = callable.call(context, new Object[]{acc[0], v, k, thisObj});
            return true;
        });
        if (!hasAcc[0]) {
            throw JsErrorException.typeError("Reduce of empty array with no initial value");
        }
        return acc[0];
    }

    private Object reduceRight(Context context, Object[] args) {
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        Object[] acc = new Object[1];
        boolean[] hasAcc = {args.length >= 2};
        if (hasAcc[0]) acc[0] = args[1];
        specIterate(context, false, true, (k, v) -> {
            if (!hasAcc[0]) {
                acc[0] = v;
                hasAcc[0] = true;
                return true;
            }
            acc[0] = callable.call(context, new Object[]{acc[0], v, k, thisObj});
            return true;
        });
        if (!hasAcc[0]) {
            throw JsErrorException.typeError("Reduce of empty array with no initial value");
        }
        return acc[0];
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
        // Spec §23.1.3.11: HasProperty-skipping at the source level; mapper
        // result is flattened by 1 (so a returned array is spliced in,
        // anything else appended as-is). Returned holes inside the mapped
        // arrays are skipped (handled by {@link #flatten}'s HOLE check).
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        List<Object> mappedResult = new ArrayList<>();
        specIterate(context, true, true, (k, v) -> {
            Object mapped = callable.call(context, new Object[]{v, k, thisObj});
            if (mapped instanceof List<?> list) {
                for (Object item : list) {
                    if (item == JsArray.HOLE) continue;
                    mappedResult.add(item);
                }
            } else {
                mappedResult.add(mapped);
            }
            return true;
        });
        return mappedResult;
    }

    private Object sort(Context context, Object[] args) {
        // Spec §23.1.3.30 Array.prototype.sort:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   Gather present items: items = [Get(O, k) for k in [0,len) if
        //     HasProperty(O, k)]. holeCount = len - items.length.
        //   Sort items via SortCompare (comparator if provided; else default
        //     ToString-based comparison; undefined entries always sort last).
        //   Write back: Set(O, k, items[k]) for k in [0, items.length);
        //     DeletePropertyOrThrow(O, k) for k in [items.length, len).
        //   Return O.
        // Default comparator uses ToString order (test262 S15.4.4.11_A3_T1
        // expects `[1, true, "a"].sort()` → ["1","a",true] by string order).
        // Generic ObjectLike receivers (test262 S15.4.4.11_A3_* cluster:
        // alphabetR.sort = Array.prototype.sort) must mutate `this` directly
        // and return it — the previous snapshot-and-replace path didn't.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return Terms.UNDEFINED;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        List<Object> items = new ArrayList<>(len);
        for (int k = 0; k < len; k++) {
            String key = String.valueOf(k);
            if (hasPropertyChain(target, key)) {
                items.add(specGet(target, key, cc));
            }
        }
        JsCallable comparator = (args.length > 0 && args[0] instanceof JsCallable jc) ? jc : null;
        items.sort((a, b) -> {
            // Spec SortCompare: undefined sorts last. Both undefined → 0.
            boolean aUndef = a == null || a == Terms.UNDEFINED;
            boolean bUndef = b == null || b == Terms.UNDEFINED;
            if (aUndef && bUndef) return 0;
            if (aUndef) return 1;
            if (bUndef) return -1;
            if (comparator != null) {
                Object r = comparator.call(context, new Object[]{a, b});
                if (r instanceof Number n) {
                    double d = n.doubleValue();
                    if (Double.isNaN(d)) return 0;
                    return d < 0 ? -1 : (d > 0 ? 1 : 0);
                }
                return 0;
            }
            String sa = Terms.toStringCoerce(a, cc);
            String sb = Terms.toStringCoerce(b, cc);
            return sa.compareTo(sb);
        });
        for (int k = 0; k < items.size(); k++) {
            specSet(target, String.valueOf(k), items.get(k), cc);
        }
        for (int k = items.size(); k < len; k++) {
            specDelete(target, String.valueOf(k));
        }
        return target;
    }

    private Object fill(Context context, Object[] args) {
        // Spec §23.1.3.7 Array.prototype.fill:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   3-9. actualStart, actualEnd via relative-index clamp.
        //   10. For k in [actualStart, actualEnd): Set(O, ToString(k), value, true).
        //   11. Return O.
        // Per-index Set means a generic ObjectLike receiver
        // (obj.fill = Array.prototype.fill; obj.length = N) is filled in
        // place — the previous snapshot-and-set path didn't write back.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return Terms.UNDEFINED;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        Object value = args.length > 0 ? args[0] : Terms.UNDEFINED;
        int start = 0;
        int end = len;
        if (args.length > 1 && args[1] != null) {
            int rel = Terms.objectToNumber(args[1]).intValue();
            start = rel < 0 ? Math.max(len + rel, 0) : Math.min(rel, len);
        }
        if (args.length > 2 && args[2] != null) {
            int rel = Terms.objectToNumber(args[2]).intValue();
            end = rel < 0 ? Math.max(len + rel, 0) : Math.min(rel, len);
        }
        for (int k = start; k < end; k++) {
            specSet(target, String.valueOf(k), value, cc);
        }
        return target;
    }

    private Object splice(Context context, Object[] args) {
        // Spec §23.1.3.29 Array.prototype.splice:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   3-7. actualStart, actualDeleteCount, itemCount = ...
        //   9-10. Build result A: for k in [0, actualDeleteCount):
        //         from = ToString(actualStart + k); if HasProperty(O, from):
        //         CreateDataPropertyOrThrow(A, ToString(k), Get(O, from)).
        //   11-15. Move tail to make room (or close gap) via per-index
        //          HasProperty / Get / Set / Delete in the spec-mandated
        //          direction (high→low for itemCount>actualDeleteCount,
        //          low→high for itemCount<actualDeleteCount) so overlap is
        //          handled correctly.
        //   16-17. Write items at actualStart; Set(O, "length", len -
        //          actualDeleteCount + itemCount).
        // Generic ObjectLike receivers (test262 S15.4.4.12_A2_* cluster:
        // obj.splice = Array.prototype.splice) need this per-index dispatch
        // — the previous snapshot-and-replace path didn't write back.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return new ArrayList<>();
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        int actualStart;
        if (args.length == 0 || args[0] == null) {
            actualStart = 0;
        } else {
            int relativeStart = Terms.objectToNumber(args[0]).intValue();
            actualStart = relativeStart < 0
                    ? Math.max(len + relativeStart, 0)
                    : Math.min(relativeStart, len);
        }
        int actualDeleteCount;
        int itemCount;
        if (args.length == 0) {
            actualDeleteCount = 0;
            itemCount = 0;
        } else if (args.length == 1) {
            actualDeleteCount = len - actualStart;
            itemCount = 0;
        } else {
            int dc = args[1] == null ? 0 : Terms.objectToNumber(args[1]).intValue();
            actualDeleteCount = Math.min(Math.max(dc, 0), len - actualStart);
            itemCount = Math.max(args.length - 2, 0);
        }
        List<Object> removed = new ArrayList<>(actualDeleteCount);
        for (int k = 0; k < actualDeleteCount; k++) {
            String from = String.valueOf(actualStart + k);
            removed.add(hasPropertyChain(target, from)
                    ? specGet(target, from, cc) : Terms.UNDEFINED);
        }
        // Move tail to close (low→high) or open (high→low) the gap.
        if (itemCount < actualDeleteCount) {
            for (int k = actualStart; k < len - actualDeleteCount; k++) {
                String from = String.valueOf(k + actualDeleteCount);
                String to = String.valueOf(k + itemCount);
                if (hasPropertyChain(target, from)) {
                    specSet(target, to, specGet(target, from, cc), cc);
                } else {
                    specDelete(target, to);
                }
            }
            for (int k = len; k > len - actualDeleteCount + itemCount; k--) {
                specDelete(target, String.valueOf(k - 1));
            }
        } else if (itemCount > actualDeleteCount) {
            for (int k = len - actualDeleteCount; k > actualStart; k--) {
                String from = String.valueOf(k + actualDeleteCount - 1);
                String to = String.valueOf(k + itemCount - 1);
                if (hasPropertyChain(target, from)) {
                    specSet(target, to, specGet(target, from, cc), cc);
                } else {
                    specDelete(target, to);
                }
            }
        }
        for (int k = 0; k < itemCount; k++) {
            specSet(target, String.valueOf(actualStart + k), args[k + 2], cc);
        }
        setLength(target, len - actualDeleteCount + itemCount, cc);
        return removed;
    }

    private Object shift(Context context, Object[] args) {
        // Spec §23.1.3.27 Array.prototype.shift:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   3. If len = 0: Set(O, "length", 0, true); return undefined.
        //   4. first = Get(O, "0").
        //   5-7. Repeat for k = 1 to len-1:
        //          fromKey = ToString(k); toKey = ToString(k-1)
        //          if HasProperty(O, fromKey): Set(O, toKey, Get(O, fromKey), true)
        //          else:                       DeletePropertyOrThrow(O, toKey)
        //   8. DeletePropertyOrThrow(O, ToString(len-1)).
        //   9. Set(O, "length", len-1, true).
        // Get/Set/Delete walk the proto chain so accessors / inherited indices
        // (Array.prototype[i] = …) fire at the spec-correct steps — covers
        // test262 S15.4.4.9_A4_T1 / A4_T2 (inherited proto value surfaces at
        // toKey when fromKey is a hole). On a generic ObjectLike receiver
        // (test262 S15.4.4.9_A2_* cluster: obj.shift = Array.prototype.shift)
        // the same per-index Set / Delete writes back to the receiver — the
        // snapshot path we used to take here was non-spec.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return Terms.UNDEFINED;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        if (len == 0) {
            setLength(target, 0, cc);
            return Terms.UNDEFINED;
        }
        Object first = specGet(target, "0", cc);
        for (int k = 1; k < len; k++) {
            String fromKey = String.valueOf(k);
            String toKey = String.valueOf(k - 1);
            if (hasPropertyChain(target, fromKey)) {
                specSet(target, toKey, specGet(target, fromKey, cc), cc);
            } else {
                specDelete(target, toKey);
            }
        }
        specDelete(target, String.valueOf(len - 1));
        setLength(target, len - 1, cc);
        return first;
    }

    private Object unshift(Context context, Object[] args) {
        // Spec §23.1.3.32 Array.prototype.unshift:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   4. If argCount > 0:
        //      Repeat for k = len down to 1:
        //        from = ToString(k-1); to = ToString(k+argCount-1)
        //        if HasProperty(O, from): Set(O, to, Get(O, from), true)
        //        else:                    DeletePropertyOrThrow(O, to)
        //      Repeat for j = 0 to argCount-1: Set(O, ToString(j), items[j], true)
        //   5. Set(O, "length", len + argCount, true).
        // Move loop dispatches through Get/Set/Delete so a proto-installed
        // accessor at an intermediate index fires at the spec-correct step
        // (test262 S15.4.4.13_A4_T2: inherited index surfaces at the moved-to
        // slot when the source slot is a hole). Per-arg Set walks proto
        // setters so a setter installed on Array.prototype["0"] fires
        // (set-length-array-length-is-non-writable.js unshift variant). On a
        // generic ObjectLike receiver (test262 S15.4.4.13_A2_* cluster: obj.
        // unshift = Array.prototype.unshift) the same Set / Delete writes
        // back to the receiver.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return 0;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        int argCount = args.length;
        if (argCount > 0) {
            for (int k = len - 1; k >= 0; k--) {
                String fromKey = String.valueOf(k);
                String toKey = String.valueOf(k + argCount);
                if (hasPropertyChain(target, fromKey)) {
                    specSet(target, toKey, specGet(target, fromKey, cc), cc);
                } else {
                    specDelete(target, toKey);
                }
            }
            for (int j = 0; j < argCount; j++) {
                specSet(target, String.valueOf(j), args[j], cc);
            }
        }
        int newLen = len + argCount;
        setLength(target, newLen, cc);
        return newLen;
    }

    private Object lastIndexOf(Context context, Object[] args) {
        // Spec §23.1.3.18: HasProperty-skipping reverse search.
        if (args.length == 0) return -1;
        Object searchElement = args[0];
        Object thisObj = context.getThisObject();
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = thisObj instanceof ObjectLike o ? o : Terms.toObjectLike(thisObj);
        int len = target == null ? 0 : lengthOf(target, cc);
        if (len == 0) return -1;
        int fromIndex = len - 1;
        if (args.length > 1 && args[1] != null) {
            Number n = Terms.objectToNumber(args[1]);
            if (!Double.isNaN(n.doubleValue())) {
                fromIndex = n.intValue();
                if (fromIndex < 0) fromIndex = len + fromIndex;
                else if (fromIndex >= len) fromIndex = len - 1;
            }
        }
        if (fromIndex < 0) return -1;
        int[] result = {-1};
        int from = fromIndex;
        specIterate(context, false, true, (k, v) -> {
            if (k > from) return true;
            if (Terms.eq(v, searchElement, false)) {
                result[0] = k;
                return false;
            }
            return true;
        });
        return result[0];
    }

    private Object pop(Context context, Object[] args) {
        // Spec §23.1.3.20 Array.prototype.pop:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   3. If len = 0: Set(O, "length", +0, true); return undefined.
        //   4. Else: element = Get(O, ToString(len-1));
        //            DeletePropertyOrThrow(O, ToString(len-1));
        //            Set(O, "length", len-1, true); return element.
        // The Get walks the proto chain when the index is a hole — proto-
        // installed accessors (Array.prototype["last"] = {get:…}) fire
        // during pop, observable via call-count assertions in
        // set-length-array-length-is-non-writable.js. The length-set happens
        // AFTER the Get / Delete, so a getter side-effect that makes length
        // non-writable still throws at the spec-correct step. On JsArray the
        // explicit Delete is redundant with {@link JsArray.ArrayLength#applySet}'s
        // truncate (which removes trailing entries), but kept for spec-shape
        // uniformity — a no-op when truncate succeeds.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike target = toReceiver(context.getThisObject());
        if (target == null) return Terms.UNDEFINED;
        int len = lengthOf(target, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        if (len == 0) {
            setLength(target, 0, cc);
            return Terms.UNDEFINED;
        }
        int newLen = len - 1;
        String index = String.valueOf(newLen);
        Object element = specGet(target, index, cc);
        specDelete(target, index);
        setLength(target, newLen, cc);
        return element;
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
        // Spec §23.1.3.4 Array.prototype.copyWithin:
        //   1. O = ToObject(this).  2. len = ToLength(Get(O, "length")).
        //   3-10. to, from, finalEnd, count via relative-index clamps.
        //   11. direction = (from < to && to < from + count) ? -1 : 1
        //       (high→low when source range overlaps destination start).
        //   12. While count > 0: HasProperty(O, fromKey) ? Set(O, toKey, Get)
        //       : DeletePropertyOrThrow(O, toKey). Step from/to by direction.
        //   13. Return O.
        // Per-index dispatch threads accessor descriptors and supports
        // generic ObjectLike receivers — the previous snapshot-and-replace
        // path didn't write back to a non-array `this`.
        CoreContext cc = context instanceof CoreContext cx ? cx : null;
        ObjectLike receiver = toReceiver(context.getThisObject());
        if (receiver == null) return Terms.UNDEFINED;
        int len = lengthOf(receiver, cc);
        if (cc != null && cc.isError()) return Terms.UNDEFINED;
        int to = 0;
        if (args.length > 0 && args[0] != null) {
            int rel = Terms.objectToNumber(args[0]).intValue();
            to = rel < 0 ? Math.max(len + rel, 0) : Math.min(rel, len);
        }
        int from = 0;
        if (args.length > 1 && args[1] != null) {
            int rel = Terms.objectToNumber(args[1]).intValue();
            from = rel < 0 ? Math.max(len + rel, 0) : Math.min(rel, len);
        }
        int finalEnd = len;
        if (args.length > 2 && args[2] != null) {
            int rel = Terms.objectToNumber(args[2]).intValue();
            finalEnd = rel < 0 ? Math.max(len + rel, 0) : Math.min(rel, len);
        }
        int count = Math.min(finalEnd - from, len - to);
        int direction;
        if (from < to && to < from + count) {
            direction = -1;
            from += count - 1;
            to += count - 1;
        } else {
            direction = 1;
        }
        while (count > 0) {
            String fromKey = String.valueOf(from);
            String toKey = String.valueOf(to);
            if (hasPropertyChain(receiver, fromKey)) {
                specSet(receiver, toKey, specGet(receiver, fromKey, cc), cc);
            } else {
                specDelete(receiver, toKey);
            }
            from += direction;
            to += direction;
            count--;
        }
        return receiver;
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
        // Spec §23.1.3.11: visits every index len-1..0 (NO HasProperty filter).
        if (args.length == 0) return Terms.UNDEFINED;
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        Object[] result = {Terms.UNDEFINED};
        specIterate(context, false, false, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (Terms.isTruthy(r)) {
                result[0] = v;
                return false;
            }
            return true;
        });
        return result[0];
    }

    private Object findLastIndex(Context context, Object[] args) {
        if (args.length == 0) return -1;
        JsCallable callable = toCallable(args);
        Object thisObj = context.getThisObject();
        int[] result = {-1};
        specIterate(context, false, false, (k, v) -> {
            Object r = callable.call(context, new Object[]{v, k, thisObj});
            if (Terms.isTruthy(r)) {
                result[0] = k;
                return false;
            }
            return true;
        });
        return result[0];
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
