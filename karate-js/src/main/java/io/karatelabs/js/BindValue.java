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
 * Represents a binding entry with its value and optional let/const metadata.
 * <p>
 * Supports level-keyed scoping where each binding knows its scope level and
 * can link to a previous (shadowed) binding with the same name.
 */
class BindValue {

    final String name;
    final int level;            // scope level where binding was created
    final BindValue previous;   // shadowed binding (forms stack per variable)
    Object value;
    BindScope scope;  // null for var, LET/CONST for let/const
    boolean initialized;
    short evalId;               // tracks which eval() declared this binding

    BindValue(String name, Object value) {
        this.name = name;
        this.level = 0;
        this.previous = null;
        this.value = value;
        this.initialized = true;
    }

    BindValue(String name, Object value, BindScope scope, boolean initialized) {
        this.name = name;
        this.level = 0;
        this.previous = null;
        this.value = value;
        this.scope = scope;
        this.initialized = initialized;
    }

    BindValue(String name, Object value, BindScope scope, boolean initialized, int level, BindValue previous) {
        this.name = name;
        this.level = level;
        this.previous = previous;
        this.value = value;
        this.scope = scope;
        this.initialized = initialized;
    }

    /**
     * Copy constructor for loop iteration snapshots.
     */
    BindValue(BindValue other) {
        this.name = other.name;
        this.level = other.level;
        this.previous = other.previous;
        this.value = other.value;
        this.scope = other.scope;
        this.initialized = other.initialized;
    }

    @Override
    public String toString() {
        return name + "=" + value + (scope != null ? " (" + scope + ")" : "") + "@L" + level;
    }

}
