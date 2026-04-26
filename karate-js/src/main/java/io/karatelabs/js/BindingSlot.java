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
 * One entry in a {@link BindingsStore} — a variable binding cell. Maps to
 * an ES 8.1 EnvironmentRecord entry. Holds the binding {@link #value} cell
 * plus lexical-scope metadata: declaration kind ({@link #scope}), TDZ
 * ({@link #initialized}), scope-level chain ({@link #level} +
 * {@link #previous} for shadowing), eval-id (REPL re-declaration), and
 * the {@link #hidden} flag (filtered from {@code Engine.getBindings()}).
 *
 * <p>Distinct family from {@link PropertySlot} — bindings and property
 * descriptors share no concrete behavior, so they live as independent
 * sealed/final roots rather than under a common parent.
 */
final class BindingSlot {

    final String name;
    Object value;

    /** null = var (or implicit), LET / CONST otherwise. */
    BindScope scope;
    /** TDZ — false until first assignment for let / const. */
    boolean initialized = true;
    /** Scope level where this binding was created. */
    int level;
    /** Shadowed binding restored by {@link BindingsStore#popLevel}. */
    BindingSlot previous;
    /** Cross-eval REPL re-declaration tracking. */
    short evalId;
    /** Filtered from {@link Engine#getBindings()} but visible to the engine's
     *  lookup chain (used by lazy built-in cache and
     *  {@link Engine#putRootBinding}). */
    boolean hidden;

    BindingSlot(String name) {
        this.name = name;
    }

    BindingSlot(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    BindingSlot(String name, Object value, BindScope scope, boolean initialized) {
        this.name = name;
        this.value = value;
        this.scope = scope;
        this.initialized = initialized;
    }

    BindingSlot(String name, Object value, BindScope scope, boolean initialized, int level, BindingSlot previous) {
        this.name = name;
        this.value = value;
        this.scope = scope;
        this.initialized = initialized;
        this.level = level;
        this.previous = previous;
    }

    /** Copy constructor for loop-iteration snapshots. */
    BindingSlot(BindingSlot other) {
        this.name = other.name;
        this.value = other.value;
        this.scope = other.scope;
        this.initialized = other.initialized;
        this.level = other.level;
        this.previous = other.previous;
        this.evalId = other.evalId;
        this.hidden = other.hidden;
    }

    @Override
    public String toString() {
        return name + "=" + value + (scope != null ? " (" + scope + ")" : "") + "@L" + level;
    }

}
