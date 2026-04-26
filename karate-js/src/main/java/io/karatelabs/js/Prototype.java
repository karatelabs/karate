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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            if (p.tombstones != null) {
                p.tombstones.clear();
            }
            if (p._methodCache != null) {
                p._methodCache.clear();
            }
        }
    }

    private final Prototype __proto__;
    private Map<String, Object> userProps;
    /**
     * Tombstones for built-in properties that the user has deleted via
     * {@code delete Foo.prototype.bar}. Mirror of {@link JsObject#_tombstones}:
     * the underlying built-in resolution lives in subclass switches and has
     * nothing for {@link #removeMember} to take out of {@link #userProps}, so we
     * record the deletion here and have {@link #getMember} skip the built-in
     * lookup when the name is tombstoned. A successful re-assignment via
     * {@link #putMember} clears the tombstone.
     */
    private Set<String> tombstones;
    /**
     * Per-Engine cache of {@link JsBuiltinMethod} instances returned by
     * {@link #getBuiltinProperty(String)}. Caches keep
     * {@code Foo.prototype.bar === Foo.prototype.bar} stable within a session
     * and let tombstones target the same instance reads return. Cleared per
     * Engine via {@link #clearAllUserProps()}; allocated on first method
     * resolution so prototypes that expose only data slots stay zero-cost.
     */
    private Map<String, JsBuiltinMethod> _methodCache;

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
        // A successful write clears any tombstone — the key now exists again,
        // mirroring JsObject.putMember.
        if (tombstones != null) {
            tombstones.remove(name);
        }
    }

    @Override
    public void removeMember(String name) {
        if (tombstones != null && tombstones.contains(name)) {
            return; // already gone
        }
        boolean inUser = userProps != null && userProps.containsKey(name);
        boolean isBuiltin = !inUser && getBuiltinProperty(name) != null;
        if (!inUser && !isBuiltin) {
            return;
        }
        // Spec built-in prototype methods are { configurable: true } — every
        // built-in resolved here is configurable unless the subclass tightens
        // it via getOwnAttrs. Honor that bit.
        if (isBuiltin && (getOwnAttrs(name) & JsObject.CONFIGURABLE) == 0) {
            return;
        }
        if (inUser) {
            userProps.remove(name);
        }
        // Tombstone if there's an underlying built-in to shadow; without this,
        // a subsequent `Array.prototype.push` lookup would re-resolve the
        // built-in and `hasOwnProperty` would incorrectly report it as own.
        if (isBuiltin) {
            if (tombstones == null) {
                tombstones = new HashSet<>();
            }
            tombstones.add(name);
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
        // 2. Built-in properties defined by this prototype — but skip the lookup
        // entirely when tombstoned (a `delete Foo.prototype.x` should not be
        // silently undone by re-resolving the built-in).
        if (tombstones == null || !tombstones.contains(name)) {
            Object result = lookupBuiltin(name);
            if (result != null) {
                return result;
            }
        }
        // 3. Delegate to __proto__ chain
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    /**
     * Cache-aware wrapper for {@link #getBuiltinProperty(String)}. Returns the
     * cached {@link JsBuiltinMethod} for {@code name} if present; otherwise calls
     * the subclass's {@code getBuiltinProperty} and caches the result if it's a
     * wrapped method. Non-method returns (data slots like {@code constructor}
     * pointers) bypass the cache and are re-resolved each call.
     */
    private Object lookupBuiltin(String name) {
        if (_methodCache != null) {
            JsBuiltinMethod cached = _methodCache.get(name);
            if (cached != null) return cached;
        }
        Object result = getBuiltinProperty(name);
        if (result instanceof JsBuiltinMethod jbm) {
            if (_methodCache == null) {
                _methodCache = new LinkedHashMap<>();
            }
            _methodCache.put(name, jbm);
        }
        return result;
    }

    /**
     * Sugar for the canonical {@code new JsBuiltinMethod(name, length, delegate)}
     * call used by {@link #getBuiltinProperty(String)} subclasses. Lets each
     * case in the dispatch switch read like
     * {@code case "push" -> method(name, 1, this::push)}.
     */
    protected JsBuiltinMethod method(String methodName, int length, JsCallable delegate) {
        return new JsBuiltinMethod(methodName, length, delegate);
    }

    /**
     * Spec attribute byte for an own property of this prototype. Built-in
     * methods on every standard prototype carry
     * {@code {writable: true, enumerable: false, configurable: true}}
     * unless otherwise noted; this default returns
     * {@code WRITABLE | CONFIGURABLE}. Subclasses with intrinsic data slots
     * (e.g. {@code Math.PI}-style constants on a future prototype) override.
     */
    public byte getOwnAttrs(String name) {
        return JsObject.WRITABLE | JsObject.CONFIGURABLE;
    }

    /**
     * Returns the value for a built-in property, or {@code null} to delegate to parent prototype.
     * Subclasses override this to provide their built-in methods and properties.
     *
     * @param name the property name
     * @return the property value, or {@code null} to continue lookup in __proto__
     */
    protected abstract Object getBuiltinProperty(String name);

    /**
     * True iff {@code name} is an "own" property on this prototype itself (user-added or
     * built-in). Used by {@code Object.prototype.hasOwnProperty} when the receiver is the
     * prototype object — spec-required for {@code Date.prototype.hasOwnProperty('toString')}
     * etc. to return true. Does NOT walk {@code __proto__}.
     */
    boolean hasOwnMember(String name) {
        if (tombstones != null && tombstones.contains(name)) {
            return false;
        }
        if (userProps != null && userProps.containsKey(name)) {
            return true;
        }
        return getBuiltinProperty(name) != null;
    }

}
