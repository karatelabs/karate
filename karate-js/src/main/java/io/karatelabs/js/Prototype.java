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
 *       per spec ({@code Array.prototype} methods are configurable + writable).
 *       Each entry is a {@link PropertySlot} ({@link DataSlot} for plain values,
 *       {@link AccessorSlot} for accessor descriptors); the slot's
 *       {@link PropertySlot#tombstoned} flag shadows a built-in deleted via
 *       {@code delete Foo.prototype.bar}.</li>
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
     * state ({@link #userProps}) so prototype mutations inside one Engine
     * instance don't bleed into the next. Required for test262 isolation.
     */
    private static final List<Prototype> ALL = new CopyOnWriteArrayList<>();

    /**
     * Per-Engine flag: set when a canonical numeric property is installed on
     * any prototype's userProps via {@link #putMember} or
     * {@link #defineOwnAccessor}. Lets {@code Array.prototype.*} iteration
     * helpers ({@code every} / {@code map} / {@code forEach} / …) skip the
     * full HasProperty proto-walk per element on the hot path: when this is
     * false (the common case — no user code did
     * {@code Array.prototype[i] = …} or {@code Object.prototype[i] = …}),
     * the spec's HasProperty reduces to an own-only check.
     * <p>
     * Monotonic within an Engine (we don't track when it stops being polluted
     * — once true, fast path is bypassed for the remainder of the session).
     * Reset to false in {@link #clearAllUserProps} so the next Engine starts
     * clean — required for test262 isolation.
     */
    private static volatile boolean numericPropPolluted = false;

    /** True iff any user code installed a canonical numeric key on a prototype
     *  in this Engine session. See {@link #numericPropPolluted}. */
    static boolean isNumericPropPolluted() {
        return numericPropPolluted;
    }

    static void clearAllUserProps() {
        for (Prototype p : ALL) {
            if (p.userProps != null) {
                p.userProps.clear();
            }
        }
        numericPropPolluted = false;
    }

    /** Canonical-integer key check for the {@link #numericPropPolluted}
     *  trigger. Mirrors {@code JsArray.parseIndex}'s strictness: rejects
     *  {@code "01"}, {@code "+1"}, {@code "-1"}, {@code "1.0"}; accepts
     *  {@code "0"} through 10-digit integers. */
    private static boolean isCanonicalNumericKey(String name) {
        int n = name.length();
        if (n == 0 || n > 10) return false;
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return n == 1 || name.charAt(0) != '0';
    }

    private final Prototype __proto__;
    private Map<String, PropertySlot> userProps;
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
        if (isCanonicalNumericKey(name)) numericPropPolluted = true;
        PropertySlot existing = userProps.get(name);
        if (existing instanceof DataSlot ds) {
            ds.value = value;
            ds.tombstoned = false;
        } else {
            // Replace any prior accessor / tombstone slot with a fresh data slot.
            userProps.put(name, new DataSlot(name, value));
        }
    }

    @Override
    public void removeMember(String name) {
        PropertySlot existing = userProps == null ? null : userProps.get(name);
        if (existing != null && existing.tombstoned) {
            return;
        }
        boolean inUser = existing != null;
        // shadowsBuiltin is independent of inUser: even when a user slot
        // exists, the builtin underneath survives in {@link #builtins} and
        // would re-emerge through {@link #getMember} / {@link #isOwnProperty}
        // unless we tombstone the user slot rather than just dropping it.
        // Pre-fix bug: write-then-delete (e.g. {@code Number.prototype.toString
        // = 'foo'; delete Number.prototype.toString}) silently restored the
        // built-in and broke ~155 test262 prop-desc tests through
        // propertyHelper.isConfigurable's delete + !hasOwn check.
        boolean shadowsBuiltin = builtins.containsKey(name);
        if (!inUser && !shadowsBuiltin) {
            return;
        }
        if (shadowsBuiltin && (getOwnAttrs(name) & JsObject.CONFIGURABLE) == 0) {
            return;
        }
        if (shadowsBuiltin) {
            // Tombstone needed so the builtin doesn't re-emerge. Reuse an
            // existing DataSlot; otherwise (no user slot, or user slot is an
            // accessor) install a fresh tombstone DataSlot that replaces it.
            if (existing instanceof DataSlot ds) {
                ds.value = null;
                ds.tombstoned = true;
            } else {
                if (userProps == null) {
                    userProps = new LinkedHashMap<>();
                }
                DataSlot ts = new DataSlot(name);
                ts.tombstoned = true;
                userProps.put(name, ts);
            }
        } else {
            // No built-in to shadow; just drop the user slot entirely.
            userProps.remove(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        if (userProps == null || userProps.isEmpty()) return Collections.emptyMap();
        Map<String, Object> view = new LinkedHashMap<>(userProps.size());
        for (PropertySlot s : userProps.values()) {
            if (s.tombstoned) continue;
            view.put(s.name, s instanceof DataSlot ds ? ds.value : null);
        }
        return view;
    }

    @Override
    public final Object getMember(String name) {
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            if (s.tombstoned) return walkProto(name);
            return s instanceof DataSlot ds ? ds.value : null;
        }
        Object builtin = resolveBuiltin(name);
        if (builtin != null) return builtin;
        return walkProto(name);
    }

    @Override
    public Object getMember(String name, Object receiver, CoreContext ctx) {
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            if (s.tombstoned) {
                return __proto__ == null ? null : __proto__.getMember(name, receiver, ctx);
            }
            return s.read(receiver, ctx);
        }
        Object builtin = resolveBuiltin(name);
        // Built-in accessor slot (installed via {@link #installAccessor}) —
        // dispatch through {@link AccessorSlot#read} so the getter sees the
        // user receiver and the prototype-self sentinel branch fires for
        // {@code RegExp.prototype.source} / {@code .flags} / etc.
        if (builtin instanceof AccessorSlot acc) return acc.read(receiver, ctx);
        if (builtin != null) return builtin;
        return __proto__ == null ? null : __proto__.getMember(name, receiver, ctx);
    }

    private Object walkProto(String name) {
        return __proto__ == null ? null : __proto__.getMember(name);
    }

    private Object resolveBuiltin(String name) {
        Object result = builtins.get(name);
        if (result instanceof LazyRef lr) {
            // Cross-reference (e.g. `constructor` → JsXxxConstructor.INSTANCE)
            // — resolved on first access since both singletons must exist
            // by then. Cache the resolution to drop the indirection.
            result = lr.supplier.get();
            builtins.put(name, result);
        }
        return result;
    }

    /** Installs (or updates) an accessor descriptor at {@code name} in
     *  {@link #userProps}. Used by {@code Object.defineProperty} when the
     *  target is a prototype (e.g.
     *  {@code Object.defineProperty(String.prototype, "x", {get: …})}). */
    final void defineOwnAccessor(String name, JsCallable getter, JsCallable setter, byte attrs) {
        if (userProps == null) {
            userProps = new LinkedHashMap<>();
        }
        if (isCanonicalNumericKey(name)) numericPropPolluted = true;
        PropertySlot existing = userProps.get(name);
        AccessorSlot s;
        if (existing instanceof AccessorSlot as) {
            s = as;
        } else {
            s = new AccessorSlot(name);
            userProps.put(name, s);
        }
        s.getter = getter;
        s.setter = setter;
        s.attrs = attrs;
        s.tombstoned = false;
    }

    /** Returns the own slot at {@code name} (data or accessor) when one is
     *  installed via {@link #defineOwnAccessor} / {@link #putMember} or
     *  via the install-time {@link #installAccessor}, or {@code null}
     *  when absent or tombstoned. User slots in {@link #userProps} shadow
     *  built-in slots in {@link #builtins} — same precedence as
     *  {@link #getMember}. Mirrors
     *  {@link JsObject#getOwnSlot(String)} / {@link JsArray#getOwnSlot(String)}
     *  so {@link PropertyAccess#findAccessorInChain} and descriptor-inspection
     *  paths can use a single signature across all three storage shapes. */
    final PropertySlot getOwnSlot(String name) {
        if (userProps != null) {
            PropertySlot s = userProps.get(name);
            if (s != null) return s.tombstoned ? null : s;
        }
        Object builtin = builtins.get(name);
        return builtin instanceof AccessorSlot acc ? acc : null;
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

    /** Install a built-in accessor descriptor at {@code name}. Spec
     *  prototype getters (e.g. {@code RegExp.prototype.source},
     *  {@code Map.prototype.size}) live here; user-defined accessors via
     *  {@code Object.defineProperty} go through {@link #defineOwnAccessor}
     *  into {@link #userProps} instead. {@link #builtins} survives
     *  per-Engine reset, which is required so the spec accessors don't
     *  vanish between {@code new Engine()} cycles. The
     *  {@link AccessorSlot} is materialized eagerly because lambdas / the
     *  spec sentinel closures don't need the static-init deferral that
     *  {@link #install(String, int, JsCallable)}'s LazyRef pattern protects
     *  against. */
    protected final void installAccessor(String name, JsCallable getter, JsCallable setter, byte attrs) {
        AccessorSlot s = new AccessorSlot(name);
        s.getter = getter;
        s.setter = setter;
        s.attrs = attrs;
        builtins.put(name, s);
    }

    private record LazyRef(Supplier<Object> supplier) {
    }

    /**
     * Spec attribute byte for an own property. Reads the per-property attrs
     * stored on the slot when one exists in {@link #userProps}; otherwise
     * falls back to the class default returned by {@link #defaultAttrs(String)}
     * (built-in methods carry {@code {writable: true, enumerable: false,
     * configurable: true}} on every standard prototype).
     */
    public final byte getOwnAttrs(String name) {
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null && !s.tombstoned) {
            return s.attrs;
        }
        // Built-in accessor descriptors carry their own attrs (typically
        // {@code C | INTRINSIC}). Plain built-in data members fall back to
        // {@link #defaultAttrs}.
        Object builtin = builtins.get(name);
        if (builtin instanceof AccessorSlot acc) return acc.attrs;
        return defaultAttrs(name);
    }

    /** Class-default attribute byte for built-in members (those installed via
     *  {@link #install}). Default {@code W | C} matches the spec for built-in
     *  methods on every standard prototype; subclasses with intrinsic data
     *  slots requiring tighter attrs override. */
    protected byte defaultAttrs(String name) {
        return JsObject.WRITABLE | JsObject.CONFIGURABLE;
    }

    /**
     * True iff {@code name} is an "own" property on this prototype (user-added
     * or built-in). Used by {@code Object.prototype.hasOwnProperty} when the
     * receiver is the prototype object itself. Does NOT walk {@code __proto__}.
     */
    @Override
    public boolean isOwnProperty(String name) {
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            return !s.tombstoned;
        }
        return builtins.containsKey(name);
    }

}
