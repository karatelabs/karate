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
import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for JS prototype chains. Implements property lookup with
 * inheritance over a two-tier storage layout:
 * <ul>
 *   <li>{@link #builtins} — install-time map populated by the subclass
 *       constructor via {@link #install(String, int, JsCallable)} /
 *       {@link #install(String, Object)}. Shared JVM-wide and immutable
 *       post-construction; safe under concurrent engines.</li>
 *   <li>per-Engine user props ({@link #userProps()}) — user-added entries via
 *       {@code putMember}, stored on the current {@link Engine} so prototype
 *       mutations are invisible to (and indestructible by) other engines.
 *       Wins over {@link #builtins} per spec ({@code Array.prototype} methods
 *       are configurable + writable). Each entry is a {@link PropertySlot}
 *       ({@link DataSlot} for plain values, {@link AccessorSlot} for accessor
 *       descriptors); the slot's {@link PropertySlot#tombstoned} flag shadows
 *       a built-in deleted via {@code delete Foo.prototype.bar}.</li>
 * </ul>
 * <p>
 * Method identity ({@code Foo.prototype.bar === Foo.prototype.bar}) follows
 * naturally from the install map: each lookup returns the same wrapped
 * {@link JsBuiltinMethod} instance.
 */
abstract class Prototype implements ObjectLike {

    /**
     * JVM-wide monotonic fast-path flag: false until ANY engine installs a
     * user property on ANY built-in prototype. While false — the
     * overwhelmingly common case, since real scripts rarely polyfill
     * built-in prototypes — property lookups skip per-Engine overlay
     * resolution entirely, so the hot path pays no ThreadLocal read.
     * Never reset: once some engine polyfills, every engine pays the
     * (small) overlay-lookup cost for the rest of the JVM's life.
     */
    private static volatile boolean anyUserProps = false;

    /**
     * Engineless fallback for user-prop writes issued outside any JS
     * execution (no {@link Engine#current()}); hosts virtually never do
     * this. Shared JVM-wide like the pre-overlay storage was.
     */
    private Map<String, PropertySlot> orphanUserProps;

    /** Engineless counterpart of {@link Engine#numericPropPolluted}. */
    private static volatile boolean orphanNumericPropPolluted = false;

    /**
     * True iff user code installed a canonical numeric key on a built-in
     * prototype in the current Engine session. Lets {@code Array.prototype.*}
     * iteration helpers ({@code every} / {@code map} / {@code forEach} / …)
     * skip the full HasProperty proto-walk per element on the hot path: when
     * false (the common case — no user code did {@code Array.prototype[i] = …}
     * or {@code Object.prototype[i] = …}), the spec's HasProperty reduces to
     * an own-only check. Monotonic within an Engine.
     */
    static boolean isNumericPropPolluted() {
        if (orphanNumericPropPolluted) {
            return true;
        }
        Engine engine = Engine.current();
        return engine != null && engine.numericPropPolluted;
    }

    private static void markNumericPolluted() {
        Engine engine = Engine.current();
        if (engine != null) {
            engine.numericPropPolluted = true;
        } else {
            orphanNumericPropPolluted = true;
        }
    }

    /**
     * The current Engine's user-property overlay for this prototype — null
     * when none. Built-in prototypes are JVM-wide singletons, but user
     * mutations ({@code Array.prototype.foo = …}) are per-Engine state: they
     * live on the Engine (see {@link Engine#protoUserProps}) and are resolved
     * through the thread's current engine, so one engine's pollution is
     * invisible to every other engine and no cross-engine reset is needed.
     */
    private Map<String, PropertySlot> userProps() {
        if (!anyUserProps) {
            return null;
        }
        Engine engine = Engine.current();
        if (engine != null) {
            return engine.protoUserProps(this, false);
        }
        return orphanUserProps;
    }

    /** Same resolution as {@link #userProps()} but creates the overlay on demand. */
    private Map<String, PropertySlot> userPropsForWrite() {
        anyUserProps = true;
        Engine engine = Engine.current();
        if (engine != null) {
            return engine.protoUserProps(this, true);
        }
        if (orphanUserProps == null) {
            orphanUserProps = new LinkedHashMap<>();
        }
        return orphanUserProps;
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
    /** Install-time built-in members. Populated by subclass constructors via
     *  {@link #install} helpers. Immutable post-construction — user mutations
     *  land in the per-Engine overlay ({@link #userProps()}) and shadow
     *  these. Structural immutability is load-bearing: the map is read
     *  concurrently by every engine on every thread, with no per-Engine
     *  reset; {@link LazyRef} / {@link ConstructorRef} entries resolve
     *  without writing back. */
    private final Map<String, Object> builtins = new LinkedHashMap<>();

    Prototype(Prototype __proto__) {
        this.__proto__ = __proto__;
    }

    @Override
    public ObjectLike getPrototype() {
        return __proto__;
    }

    @Override
    public void putMember(String name, Object value) {
        Map<String, PropertySlot> userProps = userPropsForWrite();
        if (isCanonicalNumericKey(name)) markNumericPolluted();
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
        Map<String, PropertySlot> userProps = userProps();
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
                DataSlot ts = new DataSlot(name);
                ts.tombstoned = true;
                userPropsForWrite().put(name, ts);
            }
        } else {
            // No built-in to shadow; just drop the user slot entirely.
            userProps.remove(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, PropertySlot> userProps = userProps();
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
        Map<String, PropertySlot> userProps = userProps();
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            if (s.tombstoned) return walkProto(name);
            return s instanceof DataSlot ds ? ds.value : null;
        }
        Object builtin = resolveBuiltin(name, null);
        if (builtin != null) return builtin;
        return walkProto(name);
    }

    @Override
    public Object getMember(String name, Object receiver, CoreContext ctx) {
        Map<String, PropertySlot> userProps = userProps();
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            if (s.tombstoned) {
                return __proto__ == null ? null : __proto__.getMember(name, receiver, ctx);
            }
            return s.read(receiver, ctx);
        }
        Object builtin = resolveBuiltin(name, ctx);
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

    private Object resolveBuiltin(String name, CoreContext ctx) {
        Object result = builtins.get(name);
        if (result instanceof LazyRef lr) {
            // Lazily-allocated built-in (e.g. JsBuiltinMethod wrapper) —
            // cached inside the LazyRef itself, NOT written back into
            // builtins, so the shared map stays structurally immutable
            // under concurrent readers.
            return lr.resolve();
        }
        if (result instanceof ConstructorRef cr) {
            // `constructor` cross-reference — each Engine has its own
            // constructor instances, so resolve per access against the
            // reading engine and never cache.
            Engine engine = ctx != null ? ctx.getEngine() : Engine.current();
            return engine == null ? null : engine.builtinConstructor(cr.globalName);
        }
        return result;
    }

    /** Installs (or updates) a data descriptor at {@code name} in
     *  the per-Engine user props. Used by {@code Object.defineProperty} when the
     *  target is a prototype with a data descriptor (e.g.
     *  {@code Object.defineProperty(Function.prototype, "prop",
     *  {value: 1001, enumerable: false, configurable: true})} — the attrs
     *  byte must persist so {@link #getOwnAttrs} reports the spec values
     *  for descriptor reads and the for-in / {@code Object.keys} enumerable
     *  filter applies). Without this seam {@code applyDefine} would fall
     *  through to {@link #putMember} which carries no attrs and leaves the
     *  slot at {@link PropertySlot#ATTRS_DEFAULT} (W|E|C). */
    final void defineOwn(String name, Object value, byte attrs) {
        Map<String, PropertySlot> userProps = userPropsForWrite();
        if (isCanonicalNumericKey(name)) markNumericPolluted();
        PropertySlot existing = userProps.get(name);
        DataSlot s;
        if (existing instanceof DataSlot ds) {
            s = ds;
        } else {
            // Replace any prior accessor / tombstone slot with a fresh data slot.
            s = new DataSlot(name);
            userProps.put(name, s);
        }
        s.value = value;
        s.attrs = attrs;
        s.tombstoned = false;
    }

    /** Installs (or updates) an accessor descriptor at {@code name} in
     *  the per-Engine user props. Used by {@code Object.defineProperty} when the
     *  target is a prototype (e.g.
     *  {@code Object.defineProperty(String.prototype, "x", {get: …})}). */
    final void defineOwnAccessor(String name, JsCallable getter, JsCallable setter, byte attrs) {
        Map<String, PropertySlot> userProps = userPropsForWrite();
        if (isCanonicalNumericKey(name)) markNumericPolluted();
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
     *  when absent or tombstoned. User slots in the per-Engine user props shadow
     *  built-in slots in {@link #builtins} — same precedence as
     *  {@link #getMember}. Mirrors
     *  {@link JsObject#getOwnSlot(String)} / {@link JsArray#getOwnSlot(String)}
     *  so {@link PropertyAccess#findAccessorInChain} and descriptor-inspection
     *  paths can use a single signature across all three storage shapes. */
    final PropertySlot getOwnSlot(String name) {
        Map<String, PropertySlot> userProps = userProps();
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

    /** Install a built-in data slot (e.g. the {@code name} string on an
     *  error prototype). For {@code constructor} back-references use
     *  {@link #installConstructor} — constructor instances are per-Engine. */
    protected final void install(String name, Object value) {
        builtins.put(name, value);
    }

    /** Install a built-in accessor descriptor at {@code name}. Spec
     *  prototype getters (e.g. {@code RegExp.prototype.source},
     *  {@code Map.prototype.size}) live here; user-defined accessors via
     *  {@code Object.defineProperty} go through {@link #defineOwnAccessor}
     *  into the per-Engine user props instead. The
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

    /** Lazily-resolved built-in entry. Caches its own resolution (instead of
     *  writing back into {@link #builtins}) so the shared builtins map never
     *  structurally mutates after construction — it is read concurrently by
     *  every engine on every thread. The synchronized resolve keeps method
     *  identity ({@code Foo.prototype.bar === Foo.prototype.bar}) stable
     *  JVM-wide even when two engines race the first access. */
    private static final class LazyRef {
        private final Supplier<Object> supplier;
        private volatile Object resolved;

        LazyRef(Supplier<Object> supplier) {
            this.supplier = supplier;
        }

        Object resolve() {
            Object result = resolved;
            if (result == null) {
                synchronized (this) {
                    result = resolved;
                    if (result == null) {
                        result = supplier.get();
                        resolved = result;
                    }
                }
            }
            return result;
        }
    }

    /** Marker for the {@code constructor} cross-reference on a built-in
     *  prototype. Resolved per access against the reading Engine's
     *  constructor registry (see {@link Engine#builtinConstructor}) and
     *  never cached — constructor instances are per-Engine, so caching one
     *  engine's instance in the shared builtins map would leak it to all. */
    private record ConstructorRef(String globalName) {
    }

    /** Install the spec {@code constructor} back-reference, pointing at the
     *  built-in constructor registered under {@code globalName} ("Array",
     *  "TypeError", …) in each Engine. */
    protected final void installConstructor(String globalName) {
        builtins.put("constructor", new ConstructorRef(globalName));
    }

    /**
     * Spec attribute byte for an own property. Reads the per-property attrs
     * stored on the slot when one exists in the per-Engine user props; otherwise
     * falls back to the class default returned by {@link #defaultAttrs(String)}
     * (built-in methods carry {@code {writable: true, enumerable: false,
     * configurable: true}} on every standard prototype).
     */
    public final byte getOwnAttrs(String name) {
        Map<String, PropertySlot> userProps = userProps();
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
        Map<String, PropertySlot> userProps = userProps();
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            return !s.tombstoned;
        }
        return builtins.containsKey(name);
    }

}
