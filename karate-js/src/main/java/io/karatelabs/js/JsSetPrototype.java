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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Singleton prototype for {@link JsSet} instances. Inherits from {@link JsObjectPrototype}.
 */
class JsSetPrototype extends Prototype {

    static final JsSetPrototype INSTANCE = new JsSetPrototype();

    private JsSetPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("add", 1, this::add);
        install("has", 1, this::has);
        install("delete", 1, this::delete);
        install("clear", 0, this::clear);
        install("forEach", 1, this::forEach);
        // Set.prototype.keys / values / @@iterator all share the same impl;
        // each must report its own name (see name.js tests).
        install("keys", 0, this::values);
        JsBuiltinMethod values = new JsBuiltinMethod("values", 0, this::values);
        install("values", values);
        install("entries", 0, this::entriesMethod);
        install(IterUtils.SYMBOL_ITERATOR, values);
        // ES2025 set-methods — spec §24.2.4. All seven take a single set-like
        // arg and route through GetSetRecord (read size/has/keys, coerce size
        // to integer, validate callability). Result-bearing methods build a
        // fresh JsSet by populating elements directly (spec bypasses
        // Set.prototype.add — verified by add-not-called.js test262).
        install("union", 1, this::union);
        install("intersection", 1, this::intersection);
        install("difference", 1, this::difference);
        install("symmetricDifference", 1, this::symmetricDifference);
        install("isSubsetOf", 1, this::isSubsetOf);
        install("isSupersetOf", 1, this::isSupersetOf);
        install("isDisjointFrom", 1, this::isDisjointFrom);
    }

    private static JsSet asSet(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsSet s) {
            return s;
        }
        throw JsErrorException.typeError("Method Set.prototype called on incompatible receiver");
    }

    private Object add(Context context, Object[] args) {
        JsSet s = asSet(context);
        s.addValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
        return s;
    }

    private Object has(Context context, Object[] args) {
        return asSet(context).has(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object delete(Context context, Object[] args) {
        return asSet(context).deleteValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object clear(Context context, Object[] args) {
        asSet(context).clearAll();
        return Terms.UNDEFINED;
    }

    private Object forEach(Context context, Object[] args) {
        JsSet s = asSet(context);
        if (args.length == 0 || !(args[0] instanceof JsCallable cb)) {
            throw JsErrorException.typeError("Set.prototype.forEach: callback is not a function");
        }
        Object thisArg = args.length > 1 ? args[1] : Terms.UNDEFINED;
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object savedThis = cc != null ? cc.thisObject : null;
        try {
            // Spec: forEach walks the live entry list. Entries added after the cursor
            // (at the end) are visited; entries deleted before the cursor reaches them
            // are skipped. Re-fetch the keyset on each step so additions show up.
            int cursor = 0;
            while (cursor < s.elements.size()) {
                Iterator<Object> it = s.elements.keySet().iterator();
                for (int i = 0; i < cursor && it.hasNext(); i++) it.next();
                if (!it.hasNext()) break;
                Object v = it.next();
                if (cc != null) cc.thisObject = thisArg;
                // Per spec: callback receives (value, value, set) — both first args identical
                // (sets have no separate key vs value).
                cb.call(context, new Object[]{v, v, s});
                cursor++;
            }
        } finally {
            if (cc != null) cc.thisObject = savedThis;
        }
        return Terms.UNDEFINED;
    }

    private Object values(Context context, Object[] args) {
        JsSet s = asSet(context);
        return IterUtils.toIteratorObject(setIterator(s, false));
    }

    private Object entriesMethod(Context context, Object[] args) {
        JsSet s = asSet(context);
        return IterUtils.toIteratorObject(setIterator(s, true));
    }

    // -------------------------------------------------------------------------
    // ES2025 set-methods (§24.2.4)
    // -------------------------------------------------------------------------

    /**
     * Spec §24.2.1.2 GetSetRecord(obj). Reads {@code size}, coerces to integer
     * (throwing on NaN), then reads {@code has}/{@code keys} and validates
     * callability. The result is the input to all seven set-methods.
     */
    private static final class SetRecord {
        final ObjectLike setObj;
        final long intSize;
        final JsCallable has;
        final JsCallable keys;

        SetRecord(ObjectLike setObj, long intSize, JsCallable has, JsCallable keys) {
            this.setObj = setObj;
            this.intSize = intSize;
            this.has = has;
            this.keys = keys;
        }
    }

    private static SetRecord getSetRecord(Object other, Context context) {
        if (!(other instanceof ObjectLike obj)) {
            throw JsErrorException.typeError("Set.prototype method called with non-object");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object rawSize = obj.getMember("size", obj, cc);
        Number numSize = Terms.toNumberCoerce(rawSize, cc);
        double d = numSize.doubleValue();
        if (Double.isNaN(d)) {
            throw JsErrorException.typeError("Set-like object's size is not a number");
        }
        // ToIntegerOrInfinity, then RangeError on negative — spec step 7.
        long intSize;
        if (Double.isInfinite(d)) {
            intSize = d > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        } else {
            intSize = (long) d;
        }
        if (intSize < 0) {
            throw JsErrorException.rangeError("Set-like object's size is negative");
        }
        Object hasFn = obj.getMember("has", obj, cc);
        if (!(hasFn instanceof JsCallable has)) {
            throw JsErrorException.typeError("Set-like object's 'has' is not callable");
        }
        Object keysFn = obj.getMember("keys", obj, cc);
        if (!(keysFn instanceof JsCallable keys)) {
            throw JsErrorException.typeError("Set-like object's 'keys' is not callable");
        }
        return new SetRecord(obj, intSize, has, keys);
    }

    private static JsIterator keysIter(SetRecord rec, Context ctx) {
        return IterUtils.iteratorFromCallable(rec.keys, rec.setObj, ctx);
    }

    /** Spec normalize: -0 → +0 on values pulled from a foreign keys() iteration. */
    private static Object normalize(Object v) {
        return JsMap.normalizeKey(v);
    }

    /** Direct-populate a JsSet, bypassing Set.prototype.add per spec. */
    private static void rawAdd(JsSet s, Object value) {
        Object normalized = normalize(value);
        // Linear-scan match for cross-Java-numeric-type SameValueZero, mirroring
        // JsSet.has's findStoredValue logic.
        for (Object existing : s.elements.keySet()) {
            if (Terms.eq(existing, normalized, true)) return;
        }
        s.elements.put(normalized, Boolean.TRUE);
    }

    /** Invoke {@code rec.has(value)} with {@code rec.setObj} as this. */
    private static boolean otherHas(SetRecord rec, Object value, Context ctx) {
        CoreContext cc = ctx instanceof CoreContext c ? c : null;
        Object savedThis = cc != null ? cc.thisObject : null;
        try {
            if (cc != null) cc.thisObject = rec.setObj;
            Object r = rec.has.call(ctx, new Object[]{value});
            return Terms.isTruthy(r);
        } finally {
            if (cc != null) cc.thisObject = savedThis;
        }
    }

    private Object union(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        JsSet result = new JsSet();
        // Spec step 5: copy this's elements into result, preserving order.
        for (Object v : thisSet.elements.keySet()) {
            result.elements.put(v, Boolean.TRUE);
        }
        JsIterator it = keysIter(rec, ctx);
        while (it.hasNext()) {
            Object next = normalize(it.next());
            rawAdd(result, next);
        }
        return result;
    }

    private Object intersection(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        JsSet result = new JsSet();
        if (thisSet.elements.size() <= rec.intSize) {
            // Spec step 5: walk this; keep elements present in other (via other.has).
            // Snapshot to avoid concurrent modification when has() mutates this.
            List<Object> snapshot = new ArrayList<>(thisSet.elements.keySet());
            for (Object e : snapshot) {
                if (!thisSet.elements.containsKey(e)) continue; // deleted by has()
                if (otherHas(rec, e, ctx)) {
                    result.elements.put(e, Boolean.TRUE);
                }
            }
        } else {
            // Spec step 6: walk other.keys(); keep elements present in this.
            // Order is `other`'s iteration order (per result-order.js).
            JsIterator it = keysIter(rec, ctx);
            LinkedHashSet<Object> seen = new LinkedHashSet<>();
            while (it.hasNext()) {
                Object next = normalize(it.next());
                if (seen.contains(next)) continue;
                seen.add(next);
                if (thisSet.has(next)) {
                    rawAdd(result, next);
                }
            }
        }
        return result;
    }

    private Object difference(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        JsSet result = new JsSet();
        // Step 4: copy this into result.
        for (Object v : thisSet.elements.keySet()) {
            result.elements.put(v, Boolean.TRUE);
        }
        if (thisSet.elements.size() <= rec.intSize) {
            // Step 5: iterate this; remove entries that other.has() reports.
            // Snapshot — has() may mutate this concurrently (set-like-class-mutation.js).
            List<Object> snapshot = new ArrayList<>(thisSet.elements.keySet());
            for (Object e : snapshot) {
                if (!result.elements.containsKey(e)) continue;
                if (otherHas(rec, e, ctx)) {
                    result.elements.remove(e);
                }
            }
        } else {
            // Step 6: iterate other.keys(); remove matches from result.
            JsIterator it = keysIter(rec, ctx);
            while (it.hasNext()) {
                Object next = normalize(it.next());
                // Find via SameValueZero (cross-Java-numeric-type).
                Object stored = null;
                if (result.elements.containsKey(next)) {
                    stored = next;
                } else if (next instanceof Number) {
                    for (Object k : result.elements.keySet()) {
                        if (Terms.eq(k, next, true)) {
                            stored = k;
                            break;
                        }
                    }
                }
                if (stored != null) result.elements.remove(stored);
            }
        }
        return result;
    }

    private Object symmetricDifference(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        JsSet result = new JsSet();
        // Step 5: copy this into result.
        for (Object v : thisSet.elements.keySet()) {
            result.elements.put(v, Boolean.TRUE);
        }
        // Step 7: for each value from other.keys(), remove from result if present
        // (toggle-out), else append (toggle-in). Cross-Java-numeric-type lookup.
        JsIterator it = keysIter(rec, ctx);
        while (it.hasNext()) {
            Object next = normalize(it.next());
            Object stored = null;
            if (result.elements.containsKey(next)) {
                stored = next;
            } else if (next instanceof Number) {
                for (Object k : result.elements.keySet()) {
                    if (Terms.eq(k, next, true)) {
                        stored = k;
                        break;
                    }
                }
            }
            if (stored != null) {
                result.elements.remove(stored);
            } else {
                result.elements.put(next, Boolean.TRUE);
            }
        }
        return result;
    }

    private Object isSubsetOf(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        // Quick exit per spec: if this has more elements, can't be subset.
        if (thisSet.elements.size() > rec.intSize) return false;
        // Walk this; every elt must be in other.
        List<Object> snapshot = new ArrayList<>(thisSet.elements.keySet());
        for (Object e : snapshot) {
            if (!otherHas(rec, e, ctx)) return false;
        }
        return true;
    }

    private Object isSupersetOf(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        // Quick exit: if other has more elements, can't be superset.
        if (thisSet.elements.size() < rec.intSize) return false;
        // Walk other.keys(); every elt must be in this.
        JsIterator it = keysIter(rec, ctx);
        while (it.hasNext()) {
            Object next = normalize(it.next());
            if (!thisSet.has(next)) return false;
        }
        return true;
    }

    private Object isDisjointFrom(Context ctx, Object[] args) {
        JsSet thisSet = asSet(ctx);
        Object other = args.length > 0 ? args[0] : Terms.UNDEFINED;
        SetRecord rec = getSetRecord(other, ctx);
        if (thisSet.elements.size() <= rec.intSize) {
            // Walk this; if any elt is in other, not disjoint.
            List<Object> snapshot = new ArrayList<>(thisSet.elements.keySet());
            for (Object e : snapshot) {
                if (otherHas(rec, e, ctx)) return false;
            }
        } else {
            JsIterator it = keysIter(rec, ctx);
            while (it.hasNext()) {
                Object next = normalize(it.next());
                if (thisSet.has(next)) return false;
            }
        }
        return true;
    }

    /**
     * @param entries when {@code true}, yields {@code [v, v]} pairs (matching Map.entries
     *                shape); when {@code false}, yields {@code v} alone.
     */
    private static JsIterator setIterator(JsSet s, boolean entries) {
        return new JsIterator() {
            int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < s.elements.size();
            }

            @Override
            public Object next() {
                if (cursor >= s.elements.size()) {
                    throw new NoSuchElementException();
                }
                Iterator<Object> it = s.elements.keySet().iterator();
                for (int i = 0; i < cursor; i++) it.next();
                Object v = it.next();
                cursor++;
                if (entries) {
                    List<Object> pair = new ArrayList<>(2);
                    pair.add(v);
                    pair.add(v);
                    return pair;
                }
                return v;
            }
        };
    }

}
