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
 * One own property on a {@link JsObject} — ES 6.2.5 PropertyDescriptor. Two
 * concrete shapes:
 * <ul>
 *   <li>{@link DataSlot}: [[Value]], [[Writable]], [[Enumerable]],
 *       [[Configurable]].</li>
 *   <li>{@link AccessorSlot}: [[Get]], [[Set]], [[Enumerable]],
 *       [[Configurable]].</li>
 * </ul>
 * <p>
 * The {@link #attrs} byte encodes W|E|C plus an extra {@link #INTRINSIC}
 * bit used by per-Engine reset to distinguish install-time intrinsics
 * (preserve across engine reuse) from user-set entries (clear on reset).
 * The W bit is meaningless for accessors and not consulted by
 * {@link AccessorSlot}; the spec requires its omission from descriptor
 * output, which {@link JsObjectConstructor#buildDescriptor} handles by
 * branching on the slot family.
 */
sealed abstract class PropertySlot permits DataSlot, AccessorSlot {

    /** Bit 0: writable (data descriptors only — accessors ignore). */
    static final byte WRITABLE = 0b0001;
    /** Bit 1: enumerable. */
    static final byte ENUMERABLE = 0b0010;
    /** Bit 2: configurable. */
    static final byte CONFIGURABLE = 0b0100;
    /** Bit 3: install-time intrinsic — survives {@code clearEngineState}. */
    static final byte INTRINSIC = 0b1000;
    /** Default for newly-created own properties: W|E|C all-true, not intrinsic. */
    static final byte ATTRS_DEFAULT = WRITABLE | ENUMERABLE | CONFIGURABLE;

    final String name;
    byte attrs = ATTRS_DEFAULT;

    /** Shadows an intrinsic / proto entry on delete. The slot stays in the
     *  owning map so {@code getMember} skips the intrinsic fallback and
     *  falls through to the prototype chain. */
    boolean tombstoned;

    PropertySlot(String name) {
        this.name = name;
    }

    boolean isWritable() {
        return (attrs & WRITABLE) != 0;
    }

    boolean isEnumerable() {
        return (attrs & ENUMERABLE) != 0;
    }

    boolean isConfigurable() {
        return (attrs & CONFIGURABLE) != 0;
    }

    boolean isIntrinsic() {
        return (attrs & INTRINSIC) != 0;
    }

    /** True for {@link AccessorSlot}; false for {@link DataSlot}. */
    abstract boolean isAccessor();

    /** Read the property's effective value — for {@link DataSlot} the
     *  stored value; for {@link AccessorSlot} the result of invoking the
     *  getter (or {@code undefined} if get-only is missing). */
    abstract Object read(Object receiver, CoreContext ctx);

    /** Write the property — for {@link DataSlot} stores the value if
     *  writable; for {@link AccessorSlot} invokes the setter. Lenient mode
     *  silently ignores; strict mode throws TypeError. */
    abstract void write(Object receiver, Object newValue, CoreContext ctx, boolean strict);

}
