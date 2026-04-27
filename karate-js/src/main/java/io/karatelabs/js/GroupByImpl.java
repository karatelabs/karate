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
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Shared implementation for the ES2024 {@code Object.groupBy} / {@code Map.groupBy}
 * static methods (spec abstract operation {@code GroupBy}). The two callers
 * differ only in (a) how the callback's return value is coerced into a key
 * — {@code property} (ToPropertyKey → String) vs {@code zero} (-0 → +0
 * normalize) — and (b) the result container shape (null-prototype object vs
 * {@code Map}). Iteration / arity / RangeError / IfAbruptCloseIterator are
 * identical between them.
 */
final class GroupByImpl {

    private GroupByImpl() {
    }

    /** A single group: key plus the list of values whose callback returned that key. */
    static final class Group {
        final Object key;
        final List<Object> values = new ArrayList<>();

        Group(Object key) {
            this.key = key;
        }
    }

    /**
     * Walks {@code items} via the spec iterator protocol, invokes
     * {@code callback(value, k)} for each element, coerces the return value
     * into a key per {@code propertyMode} (true → ToPropertyKey, false →
     * -0 → +0 normalize), and accumulates values into groups in
     * first-seen-key order. Group key equality uses {@link Terms#sameValue}
     * (matches spec for the Map.groupBy case; for property-mode the keys are
     * always strings and SameValue collapses to equals).
     */
    static List<Group> run(Object items, Object callback, boolean propertyMode, Context context) {
        if (items == null || items == Terms.UNDEFINED) {
            throw JsErrorException.typeError("groupBy called with null or undefined items");
        }
        if (!(callback instanceof JsCallable cb)) {
            throw JsErrorException.typeError("groupBy callback is not a function");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        JsIterator iter = IterUtils.getIterator(items, context);
        List<Group> groups = new ArrayList<>();
        long k = 0;
        while (iter.hasNext()) {
            Object value = iter.next();
            Object rawKey = cb.call(context, new Object[]{value, (double) k});
            // IfAbruptCloseIterator: a callback that threw via context.error
            // ends iteration without re-grouping. Surface the same error to
            // our caller (already on cc.error).
            if (cc != null && cc.isError()) {
                return groups;
            }
            Object key;
            if (propertyMode) {
                // Spec ToPropertyKey — dispatches JS toString for ObjectLike values.
                key = Terms.toPropertyKey(rawKey, cc);
                if (cc != null && cc.isError()) {
                    return groups;
                }
            } else {
                // Spec: if key is -0𝔽, set key to +0𝔽. JsMap.normalizeKey
                // handles boxed-primitive unwrap + -0/+0 collapse uniformly.
                key = JsMap.normalizeKey(rawKey);
            }
            Group existing = null;
            for (Group g : groups) {
                if (Terms.sameValue(g.key, key)) {
                    existing = g;
                    break;
                }
            }
            if (existing == null) {
                existing = new Group(key);
                groups.add(existing);
            }
            existing.values.add(value);
            k++;
        }
        return groups;
    }

    /** Build a null-prototype {@link JsObject} from {@link #run} groups —
     *  the result shape Object.groupBy returns. */
    static JsObject toNullProtoObject(List<Group> groups) {
        JsObject result = new JsObject();
        result.setPrototype(null);
        for (Group g : groups) {
            // Property-mode keys are always strings (post ToPropertyKey).
            String name = (String) g.key;
            JsArray arr = new JsArray(new ArrayList<>(g.values));
            result.putMember(name, arr);
        }
        return result;
    }

    /** Build a fresh {@link JsMap} from {@link #run} groups — the result
     *  shape Map.groupBy returns. Insertion order matches first-seen-key
     *  order so {@code Array.from(map.keys())} surfaces them in spec order. */
    static JsMap toMap(List<Group> groups) {
        JsMap map = new JsMap();
        for (Group g : groups) {
            JsArray arr = new JsArray(new ArrayList<>(g.values));
            // Direct put (not setValue) — the keys are already normalized.
            map.entries.put(g.key, arr);
        }
        return map;
    }

    /** Convenience accumulator over a {@link LinkedHashMap} — kept for any
     *  future caller that wants a string-keyed grouping result without the
     *  intermediate {@link Group} list. Not used by the current callers. */
    @SuppressWarnings("unused")
    static LinkedHashMap<String, List<Object>> toStringKeyedMap(List<Group> groups) {
        LinkedHashMap<String, List<Object>> result = new LinkedHashMap<>();
        for (Group g : groups) {
            result.put((String) g.key, g.values);
        }
        return result;
    }
}
