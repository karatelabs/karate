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
import java.util.function.Supplier;

/**
 * Base class for JS prototype chains. Implements property lookup with
 * inheritance over a two-tier storage layout:
 * <ul>
 *   <li>{@link #builtins} — install-time map populated by the subclass
 *       constructor via {@link #install(String, int, JsCallable)} /
 *       {@link #install(String, Object)}. Survives across Engine sessions
 *       (re-built only on first class-load); no per-Engine reset needed.</li>
 *   <li>{@link #userProps} — user-added entries via {@code putMember}; cleared
 *       per-Engine via {@link #clearAllUserProps}. Wins over {@link #builtins}
 *       per spec ({@code Array.prototype} methods are configurable + writable).</li>
 *   <li>{@link #tombstones} — names the user has deleted via
 *       {@code delete Foo.prototype.bar}; suppress {@link #builtins} lookup.</li>
 * </ul>
 * <p>
 * Method identity ({@code Foo.prototype.bar === Foo.prototype.bar}) follows
 * naturally from the install map: each lookup returns the same wrapped
 * {@link JsBuiltinMethod} instance.
 */
abstract class Prototype implements ObjectLike {

    /**
     * Built-in prototype singletons register themselves here at construction.
     * {@link Engine#Engine()} walks this list to clear each one's user-side
     * state ({@link #userProps} + {@link #tombstones}) so prototype mutations
     * inside one Engine instance don't bleed into the next. Required for
     * test262 isolation.
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
        }
    }

    private final Prototype __proto__;
    private Map<String, Object> userProps;
    private Set<String> tombstones;
    /** Install-time built-in members. Populated by subclass constructors via
     *  {@link #install} helpers. Treated as immutable post-construction —
     *  user mutations land in {@link #userProps} and shadow these. */
    private final Map<String, Object> builtins = new LinkedHashMap<>();

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
        if (tombstones != null) {
            tombstones.remove(name);
        }
    }

    @Override
    public void removeMember(String name) {
        if (tombstones != null && tombstones.contains(name)) {
            return;
        }
        boolean inUser = userProps != null && userProps.containsKey(name);
        boolean isBuiltin = !inUser && builtins.containsKey(name);
        if (!inUser && !isBuiltin) {
            return;
        }
        if (isBuiltin && (getOwnAttrs(name) & JsObject.CONFIGURABLE) == 0) {
            return;
        }
        if (inUser) {
            userProps.remove(name);
        }
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
        if (userProps != null && userProps.containsKey(name)) {
            return userProps.get(name);
        }
        if (tombstones == null || !tombstones.contains(name)) {
            Object result = builtins.get(name);
            if (result instanceof LazyRef lr) {
                // Cross-reference (e.g. `constructor` → JsXxxConstructor.INSTANCE)
                // — resolved on first access since both singletons must exist
                // by then. Cache the resolution to drop the indirection.
                result = lr.supplier.get();
                builtins.put(name, result);
            }
            if (result != null) {
                return result;
            }
        }
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    /** Install a built-in method. The {@link JsBuiltinMethod} wrapper is
     *  allocated lazily on first access (via {@link LazyRef}) — eager
     *  allocation here would deadlock the static-init cycle between
     *  {@link JsObjectPrototype} (which installs methods whose proto chain
     *  walks through {@link JsFunctionPrototype}) and
     *  {@link JsFunctionPrototype} (which extends Object.prototype). The
     *  lazy resolution caches the wrapped instance in {@link #builtins} on
     *  first read, so identity is preserved across subsequent reads. */
    protected final void install(String name, int length, JsCallable delegate) {
        builtins.put(name, new LazyRef(() -> new JsBuiltinMethod(name, length, delegate)));
    }

    /** Install a built-in data slot (e.g. {@code constructor} pointer). */
    protected final void install(String name, Object value) {
        builtins.put(name, value);
    }

    /** Install a built-in data slot whose value is resolved lazily on first
     *  access. Use for cross-references between prototypes and constructors
     *  (e.g. {@code Function.prototype.constructor} → {@code JsFunctionConstructor.INSTANCE})
     *  where the {@code INSTANCE} field is still being initialized at the
     *  moment of install but will be live by the time JS code reads it. */
    protected final void installLazy(String name, Supplier<Object> resolver) {
        builtins.put(name, new LazyRef(resolver));
    }

    private record LazyRef(Supplier<Object> supplier) {
    }

    /**
     * Spec attribute byte for an own property. Built-in methods on every
     * standard prototype carry {@code {writable: true, enumerable: false,
     * configurable: true}} — the default returned here. Subclasses with
     * intrinsic data slots requiring tighter attrs override.
     */
    public byte getOwnAttrs(String name) {
        return JsObject.WRITABLE | JsObject.CONFIGURABLE;
    }

    /**
     * True iff {@code name} is an "own" property on this prototype (user-added
     * or built-in). Used by {@code Object.prototype.hasOwnProperty} when the
     * receiver is the prototype object itself. Does NOT walk {@code __proto__}.
     */
    boolean hasOwnMember(String name) {
        if (tombstones != null && tombstones.contains(name)) {
            return false;
        }
        if (userProps != null && userProps.containsKey(name)) {
            return true;
        }
        return builtins.containsKey(name);
    }

}
