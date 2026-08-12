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
 * JavaScript WeakSet instance: object-only membership with no size, no
 * iteration and no clear. See {@link JsWeakMap} for why storage is strong and
 * identity-keyed.
 */
class JsWeakSet extends JsObject {

    private static final Object PRESENT = new Object();

    private final IdentityHashMap<Object, Object> elements = new IdentityHashMap<>();

    JsWeakSet() {
        super(null, JsWeakSetPrototype.INSTANCE);
    }

    boolean has(Object value) {
        return JsWeakMap.canBeHeldWeakly(value) && elements.containsKey(value);
    }

    void addValue(Object value) {
        if (!JsWeakMap.canBeHeldWeakly(value)) {
            throw JsErrorException.typeError("Invalid value used in weak set");
        }
        elements.put(value, PRESENT);
    }

    boolean deleteValue(Object value) {
        if (!has(value)) {
            return false;
        }
        elements.remove(value);
        return true;
    }

}
