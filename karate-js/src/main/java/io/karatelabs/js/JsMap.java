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

import java.util.LinkedHashMap;
import java.util.List;

/**
 * JavaScript Map instance: ordered key→value collection keyed by SameValueZero.
 * <p>
 * Storage is a {@link LinkedHashMap} keyed by the *original* JS key after a single
 * normalization step ({@code -0 → +0}, boxed primitives unwrapped). Lookup falls
 * back to a linear scan when the direct map miss could mask a SameValueZero hit
 * across Java numeric types ({@code Integer 1} vs {@code Double 1.0}, etc.) — JS
 * has one number type, but Java distinguishes them by {@code .equals} / hashCode.
 * <p>
 * Identity-keyed lookups (JsObject, JsArray) hit the fast path because those
 * classes override {@code equals} / {@code hashCode} to identity. The linear scan
 * is rare in practice (only on numeric-key collisions across Java types) and runs
 * over the small maps typical of LLM-written JS.
 */
class JsMap extends JsObject {

    final LinkedHashMap<Object, Object> entries = new LinkedHashMap<>();

    JsMap() {
        super(null, JsMapPrototype.INSTANCE);
    }

    /**
     * Spec: {@code Map.prototype.size} is a getter accessor. We don't model
     * accessor descriptors yet, so we surface {@code size} as an own intrinsic
     * returning the live entry count. All other names route through the
     * prototype chain in the usual way.
     */
    @Override
    protected Object resolveOwnIntrinsic(String name) {
        if ("size".equals(name)) {
            return entries.size();
        }
        return null;
    }

    private static final List<String> INTRINSIC_NAMES = List.of("size");

    @Override
    protected Iterable<String> ownIntrinsicNames() {
        return INTRINSIC_NAMES;
    }

    /**
     * Stage-1 normalization shared with {@link JsSet}. Unwraps boxed primitives and
     * collapses {@code -0} to {@code +0} per spec. Numeric type-collapse (e.g.
     * {@code 1 === 1.0}) is handled at lookup time by linear-scan fallback rather
     * than here, so original integer-typed values survive iteration.
     */
    static Object normalizeKey(Object key) {
        if (key instanceof JsPrimitive jp) {
            key = jp.getJavaValue();
        }
        if (key instanceof Number n) {
            double d = n.doubleValue();
            if (d == 0.0 && Double.doubleToRawLongBits(d) != 0L) {
                // -0 → +0 (preserve type as long as possible — Integer/Long stay primitive 0)
                if (key instanceof Double) return 0.0;
                if (key instanceof Float) return 0.0f;
            }
        }
        return key;
    }

    /** Sentinel returned by {@link #findStoredKey} when no matching key exists. */
    private static final Object NOT_FOUND = new Object();

    /**
     * Locates an entry whose key matches under SameValueZero. Returns
     * {@link #NOT_FOUND} (not {@code null} — that's a legitimate JS key) if none.
     * Walks linearly so that {@code Integer 1} matches {@code Double 1.0} per JS
     * single-number-type semantics — Java's {@code LinkedHashMap.containsKey} uses
     * {@code Number.equals}, which does not.
     */
    private Object findStoredKey(Object normalized) {
        if (entries.containsKey(normalized)) return normalized;
        if (!(normalized instanceof Number)) return NOT_FOUND;
        for (Object k : entries.keySet()) {
            if (Terms.eq(k, normalized, true)) return k;
        }
        return NOT_FOUND;
    }

    boolean hasKey(Object key) {
        return findStoredKey(normalizeKey(key)) != NOT_FOUND;
    }

    Object getValue(Object key) {
        Object stored = findStoredKey(normalizeKey(key));
        return stored == NOT_FOUND ? Terms.UNDEFINED : entries.get(stored);
    }

    void setValue(Object key, Object value) {
        Object normalized = normalizeKey(key);
        Object stored = findStoredKey(normalized);
        // Reuse the existing key reference so insertion order is preserved on update.
        entries.put(stored != NOT_FOUND ? stored : normalized, value);
    }

    boolean deleteKey(Object key) {
        Object stored = findStoredKey(normalizeKey(key));
        if (stored == NOT_FOUND) return false;
        entries.remove(stored);
        return true;
    }

    void clearAll() {
        entries.clear();
    }

}
