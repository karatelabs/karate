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

/**
 * JavaScript Set instance: ordered unique-element collection keyed by SameValueZero.
 * <p>
 * Storage is a {@link LinkedHashMap} (used as an ordered set; values are
 * placeholders) so the same numeric cross-type linear-scan fallback as {@link JsMap}
 * applies — see {@link JsMap} javadoc for why.
 */
class JsSet extends JsObject {

    private static final Object PRESENT = new Object();

    final LinkedHashMap<Object, Object> elements = new LinkedHashMap<>();

    JsSet() {
        super(null, JsSetPrototype.INSTANCE);
    }

    /** See {@link JsMap#getMember(String)} — same accessor-getter workaround for {@code size}. */
    @Override
    public Object getMember(String name) {
        if ("size".equals(name)) {
            return elements.size();
        }
        return super.getMember(name);
    }

    private static final Object NOT_FOUND = new Object();

    private Object findStoredValue(Object normalized) {
        if (elements.containsKey(normalized)) return normalized;
        if (!(normalized instanceof Number)) return NOT_FOUND;
        for (Object v : elements.keySet()) {
            if (Terms.eq(v, normalized, true)) return v;
        }
        return NOT_FOUND;
    }

    boolean has(Object value) {
        return findStoredValue(JsMap.normalizeKey(value)) != NOT_FOUND;
    }

    boolean addValue(Object value) {
        Object normalized = JsMap.normalizeKey(value);
        if (findStoredValue(normalized) != NOT_FOUND) return false;
        elements.put(normalized, PRESENT);
        return true;
    }

    boolean deleteValue(Object value) {
        Object stored = findStoredValue(JsMap.normalizeKey(value));
        if (stored == NOT_FOUND) return false;
        elements.remove(stored);
        return true;
    }

    void clearAll() {
        elements.clear();
    }

}
