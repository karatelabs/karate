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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for JS prototype chains. Implements property lookup with inheritance.
 * <p>
 * Property resolution order in {@link #getMember(String)}:
 * <ol>
 *   <li>User-added properties ({@code userProps} map) — wins over built-ins per spec
 *       (Array.prototype methods are configurable: true, writable: true, so
 *       {@code Array.prototype.map = customFn} legitimately overrides)</li>
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
 * Built-in prototypes are singletons but not frozen — user code may extend them
 * ({@code Array.prototype.myMethod = ...}) and the override will be visible to
 * all instances via prototype lookup. {@code removeMember} only removes from
 * {@code userProps}; built-in methods cannot be deleted.
 */
abstract class Prototype implements ObjectLike {

    /**
     * Built-in prototype singletons register themselves here at construction time.
     * {@link Engine#Engine()} walks this list and clears each one's {@code userProps}
     * so that prototype mutations (e.g. {@code Map.prototype.set = function() { throw ... }})
     * inside one Engine instance don't bleed into the next. Required for test262 isolation —
     * tests routinely overwrite built-in methods to probe construction internals.
     */
    private static final List<Prototype> ALL = new CopyOnWriteArrayList<>();

    static void clearAllUserProps() {
        for (Prototype p : ALL) {
            if (p.userProps != null) {
                p.userProps.clear();
            }
        }
    }

    private final Prototype __proto__;
    private Map<String, Object> userProps;

    Prototype(Prototype __proto__) {
        this.__proto__ = __proto__;
        ALL.add(this);
    }

    @Override
    public ObjectLike getPrototype() {
        return __proto__;
    }

    @Override
    public void putMember(String name, Object value) {
        if (userProps == null) {
            userProps = new LinkedHashMap<>();
        }
        userProps.put(name, value);
    }

    @Override
    public void removeMember(String name) {
        // Only user-added properties are removable; built-in methods stay.
        if (userProps != null) {
            userProps.remove(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        return userProps == null ? Collections.emptyMap() : userProps;
    }

    @Override
    public final Object getMember(String name) {
        // 1. User-added properties win over built-ins (configurable: true per spec)
        if (userProps != null && userProps.containsKey(name)) {
            return userProps.get(name);
        }
        // 2. Built-in properties defined by this prototype
        Object result = getBuiltinProperty(name);
        if (result != null) {
            return result;
        }
        // 3. Delegate to __proto__ chain
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
