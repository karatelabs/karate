/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
 * Unified entry primitive — the value of one name-keyed slot in either a
 * {@link BindingsStore} (variable binding) or a {@link JsObject} (own
 * property). Spec mapping: PropertyDescriptor (ES 6.2.5) on the property
 * side; EnvironmentRecord entries (ES 8.1) on the binding side.
 * <p>
 * Fields irrelevant to one side default to harmless values for the other:
 * properties leave {@link #scope} null, {@link #level} 0, {@link #previous}
 * null; bindings leave {@link #tombstoned} false. {@link #attrs} starts at
 * {@link #ATTRS_DEFAULT} (W|E|C all-true) — correct for both sides until
 * defineProperty / freeze / seal / let / const tightens it.
 */
final class Slot {

    /** Bit 0: writable (data descriptors only — accessors ignore). */
    static final byte WRITABLE = 0b001;
    /** Bit 1: enumerable. */
    static final byte ENUMERABLE = 0b010;
    /** Bit 2: configurable. */
    static final byte CONFIGURABLE = 0b100;
    /** Default for newly-created own properties / bindings: all three on. */
    static final byte ATTRS_DEFAULT = WRITABLE | ENUMERABLE | CONFIGURABLE;

    final String name;
    Object value;
    byte attrs = ATTRS_DEFAULT;

    /** Property side: shadows an intrinsic / proto entry on delete. The slot
     *  stays in the owning map so {@code getMember} skips the intrinsic
     *  fallback and falls through to the prototype chain. Unused on bindings. */
    boolean tombstoned;

    /** Binding side: null = var (or property); LET / CONST otherwise. */
    BindScope scope;
    /** Binding side: TDZ — false until first assignment for let / const. */
    boolean initialized = true;
    /** Binding side: scope level where this binding was created. */
    int level;
    /** Binding side: shadowed binding restored by {@link BindingsStore#popLevel}. */
    Slot previous;
    /** Binding side: cross-eval REPL re-declaration tracking. */
    short evalId;
    /** Binding side: filtered from {@link Engine#getBindings()} but visible
     *  to the engine's lookup chain (used by lazy built-in cache and
     *  {@link Engine#putRootBinding}). On the property side this field is
     *  unused — non-enumerable is expressed via the ENUMERABLE attr bit. */
    boolean hidden;

    Slot(String name) {
        this.name = name;
    }

    Slot(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    Slot(String name, Object value, BindScope scope, boolean initialized) {
        this.name = name;
        this.value = value;
        this.scope = scope;
        this.initialized = initialized;
    }

    Slot(String name, Object value, BindScope scope, boolean initialized, int level, Slot previous) {
        this.name = name;
        this.value = value;
        this.scope = scope;
        this.initialized = initialized;
        this.level = level;
        this.previous = previous;
    }

    /** Copy constructor for loop-iteration snapshots. */
    Slot(Slot other) {
        this.name = other.name;
        this.value = other.value;
        this.attrs = other.attrs;
        this.tombstoned = other.tombstoned;
        this.scope = other.scope;
        this.initialized = other.initialized;
        this.level = other.level;
        this.previous = other.previous;
        this.evalId = other.evalId;
        this.hidden = other.hidden;
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

    @Override
    public String toString() {
        return name + "=" + value + (scope != null ? " (" + scope + ")" : "") + "@L" + level;
    }

}
