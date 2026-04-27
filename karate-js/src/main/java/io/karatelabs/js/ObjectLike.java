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

import java.util.Map;

public interface ObjectLike {

    /**
     * Raw-value accessor — returns the stored data value, or {@code null}
     * for accessor descriptors (which have no extractable raw value).
     * Internal map / Java-interop callers (toMap, Map.get, jsEntries) and
     * subclass {@code super.getMember(name)} chains use this.
     * <p>
     * For JS-semantic reads that must invoke an accessor's getter, use
     * {@link #getMember(String, Object, CoreContext)} instead.
     */
    Object getMember(String name);

    /**
     * JS-semantic resolved read. For data descriptors returns the value;
     * for accessor descriptors invokes the getter with {@code receiver}
     * bound as {@code this}. {@code receiver} is the object the property
     * is being read on (may differ from {@code this} when walking a
     * prototype chain). {@code ctx} threads through to the getter call.
     * Default delegates to {@link #getMember(String)} — implementations
     * with accessor storage ({@link JsObject}, {@link JsArray}) override.
     */
    default Object getMember(String name, Object receiver, CoreContext ctx) {
        return getMember(name);
    }

    void putMember(String name, Object value);

    void removeMember(String name);

    Map<String, Object> toMap();

    /**
     * Returns the prototype (__proto__) for prototype chain walking.
     * Default returns null (no prototype chain).
     */
    default ObjectLike getPrototype() {
        return null;
    }

    /**
     * True iff {@code name} is an own property — not inherited, not absent.
     * Default reads {@link #toMap()} which works for any host ObjectLike;
     * {@link JsObject} / {@link JsArray} / {@link Prototype} override with
     * tighter implementations that distinguish tombstones from absent
     * keys and intrinsic-installed entries.
     */
    default boolean isOwnProperty(String name) {
        return toMap().containsKey(name);
    }

    // -------------------------------------------------------------------------
    // Extensibility API — Object.{isExtensible, isSealed, isFrozen,
    // preventExtensions, seal, freeze} dispatch through these is/setX pairs.
    // Defaults treat an ObjectLike that doesn't model state (plain Map host
    // bridges, etc.) as perpetually extensible: {@code isExtensible == true},
    // {@code isSealed == false}, {@code isFrozen == false}, mutators are
    // no-ops. {@link JsObject} and {@link JsArray} carry the three-bit state
    // and override.
    // <p>
    // Per spec, integrity levels are monotonic — once non-extensible, sealed,
    // or frozen, you can't reverse it. The setters honor the direction that
    // makes the object more constrained ({@code setExtensible(false)},
    // {@code setSealed(true)}, {@code setFrozen(true)}); the other direction
    // is a silent no-op (lenient mode — strict-mode TypeError flip lives
    // elsewhere if it ever lands).
    // -------------------------------------------------------------------------

    default boolean isExtensible() {
        return true;
    }

    default boolean isSealed() {
        return false;
    }

    default boolean isFrozen() {
        return false;
    }

    default void setExtensible(boolean extensible) {
    }

    default void setSealed(boolean sealed) {
    }

    default void setFrozen(boolean frozen) {
    }

}
