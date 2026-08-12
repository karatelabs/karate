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

import java.util.IdentityHashMap;

/**
 * JavaScript WeakMap instance: object-keyed key→value collection with no size,
 * no iteration and no clear.
 * <p>
 * Entries are held strongly — this engine runs short-lived scripts, so nothing
 * observable depends on GC-weakness. The API surface and error behavior are
 * what must match the spec.
 * <p>
 * Storage is an {@link IdentityHashMap}: WeakMap keys are compared by object
 * identity, and identity is NOT what {@code equals} means for every object this
 * engine can surface as a JS value (host-supplied {@code java.util.Map} /
 * {@code List} values compare by content).
 */
class JsWeakMap extends JsObject {

    private final IdentityHashMap<Object, Object> entries = new IdentityHashMap<>();

    JsWeakMap() {
        super(null, JsWeakMapPrototype.INSTANCE);
    }

    /** Spec CanBeHeldWeakly: objects only. Real WeakMaps also accept
     *  non-registered symbols, but this engine has no symbol primitive. */
    static boolean canBeHeldWeakly(Object value) {
        return !Terms.isPrimitive(value);
    }

    boolean hasKey(Object key) {
        return canBeHeldWeakly(key) && entries.containsKey(key);
    }

    Object getValue(Object key) {
        // containsKey, not get() == null — a stored JS null is a Java null value
        // and must not read back as undefined.
        return hasKey(key) ? entries.get(key) : Terms.UNDEFINED;
    }

    void setValue(Object key, Object value) {
        if (!canBeHeldWeakly(key)) {
            throw JsErrorException.typeError("Invalid value used as weak map key");
        }
        entries.put(key, value);
    }

    boolean deleteKey(Object key) {
        if (!hasKey(key)) {
            return false;
        }
        entries.remove(key);
        return true;
    }

}
