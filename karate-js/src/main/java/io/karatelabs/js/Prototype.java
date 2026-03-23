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

import java.util.Collections;
import java.util.Map;

/**
 * Base class for JS prototype chains. Implements property lookup with inheritance.
 * <p>
 * Property resolution order in {@link #getMember(String)}:
 * <ol>
 *   <li>Check instance properties ({@code props} map)</li>
 *   <li>Call {@link #getBuiltinProperty(String)} - subclass-defined built-in properties</li>
 *   <li>If {@code getBuiltinProperty} returns {@code null}, delegate to {@code __proto__}</li>
 * </ol>
 * <p>
 * Subclasses override {@link #getBuiltinProperty(String)} and return:
 * <ul>
 *   <li>A value - to handle the property at this level</li>
 *   <li>{@code null} - to delegate lookup to the parent prototype (__proto__)</li>
 * </ul>
 * <p>
 * Built-in prototypes (Array.prototype, String.prototype, etc.) are immutable singletons.
 * Attempting to modify them via putMember/removeMember will throw a TypeError.
 */
abstract class Prototype implements ObjectLike {

    private final Prototype __proto__;

    Prototype(Prototype __proto__) {
        this.__proto__ = __proto__;
    }

    @Override
    public ObjectLike getPrototype() {
        return __proto__;
    }

    @Override
    public void putMember(String name, Object value) {
        throw new RuntimeException("TypeError: Cannot add property '" + name + "' to immutable built-in prototype");
    }

    @Override
    public void removeMember(String name) {
        throw new RuntimeException("TypeError: Cannot delete property '" + name + "' from immutable built-in prototype");
    }

    @Override
    public Map<String, Object> toMap() {
        return Collections.emptyMap();
    }

    @Override
    public final Object getMember(String name) {
        // 1. Check built-in properties defined by this prototype
        Object result = getBuiltinProperty(name);
        if (result != null) {
            return result;
        }
        // 2. Delegate to __proto__ chain
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    /**
     * Returns the value for a built-in property, or {@code null} to delegate to parent prototype.
     * Subclasses override this to provide their built-in methods and properties.
     *
     * @param name the property name
     * @return the property value, or {@code null} to continue lookup in __proto__
     */
    protected abstract Object getBuiltinProperty(String name);

}
