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

/**
 * Top-level {@code this} binding — the closest karate-js gets to spec
 * {@code globalThis}. A view onto the {@link ContextRoot} that exposes the
 * built-in globals ({@code Math}, {@code Object}, {@code Array}, …) as own
 * properties so spec idioms like {@code Object.getOwnPropertyDescriptor(this,
 * "Math")} and {@code Object.prototype.hasOwnProperty.call(this, "Math")}
 * return the descriptor / {@code true} that test262's {@code propertyHelper.js}
 * expects.
 * <p>
 * Reads route through {@link ContextRoot#get(String)}, so a global is the same
 * instance whether the user reaches it via {@code Math} or {@code this.Math}.
 * Writes via {@code this.x = ...} land in this object's own map (they do
 * <em>not</em> back-propagate into root bindings); identity therefore diverges
 * for those writes — fix when a real test exercises it.
 */
final class JsGlobalThis extends JsObject {

    private final ContextRoot root;

    JsGlobalThis(ContextRoot root) {
        this.root = root;
    }

    @Override
    public Object getMember(String name) {
        // Tombstones / user-set entries / __proto__ go through the JsObject path first
        // so `delete this.Math` / `this.foo = 1` keep working.
        if (isTombstoned(name) || ownContainsKey(name) || "__proto__".equals(name)) {
            return super.getMember(name);
        }
        // Resolve globals via the root (which caches in _bindings after first lookup,
        // so identity is preserved across `Math` and `this.Math` reads).
        if (root.hasKey(name)) {
            return root.get(name);
        }
        return super.getMember(name);
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        // Anything ContextRoot will hand back as a global counts as an own
        // property of globalThis for hasOwnProperty / getOwnPropertyDescriptor.
        // ContextRoot.hasKey covers both already-cached values in _bindings and
        // the hardcoded built-in name list.
        return root.hasKey(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        // Per ES spec §10.2 / §19.1: every non-essential global is
        // { writable: true, enumerable: false, configurable: true }.
        // (Essentials like NaN/Infinity/undefined are non-writable but not
        // distinguished here — fix when test262 surfaces a fail.)
        if (!ownContainsKey(name) && hasOwnIntrinsic(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        return super.getOwnAttrs(name);
    }

}
