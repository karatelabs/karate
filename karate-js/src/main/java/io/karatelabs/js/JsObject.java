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

import java.util.*;

/**
 * JavaScript Object — own properties live in a single {@code props}
 * {@code Map<String, PropertySlot>} where each {@link Slot} carries value, attribute
 * byte (writable / enumerable / configurable), and tombstone flag.
 * <p>
 * Property lookup order:
 * <ol>
 *   <li>Own properties ({@code props}, skipping tombstoned slots)</li>
 *   <li>Subclass intrinsic resolution via {@link #resolveOwnIntrinsic(String)}
 *       — single source of truth for "names this subclass exposes as own
 *       without storing them in {@code props}". {@link #hasOwnIntrinsic} is
 *       a derived existence check.</li>
 *   <li>Prototype chain ({@code __proto__})</li>
 * </ol>
 */
class JsObject implements ObjectLike, Map<String, Object> {

    /** Re-exported from {@link Slot} for callers that historically referenced
     *  {@code JsObject.WRITABLE}. */
    static final byte WRITABLE = PropertySlot.WRITABLE;
    static final byte ENUMERABLE = PropertySlot.ENUMERABLE;
    static final byte CONFIGURABLE = PropertySlot.CONFIGURABLE;
    static final byte ATTRS_DEFAULT = PropertySlot.ATTRS_DEFAULT;

    /**
     * Own-property storage. One {@link Slot} per name carries the value plus
     * its attribute byte and tombstone flag. Lazily allocated — empty objects
     * pay no map overhead.
     */
    private Map<String, PropertySlot> props;
    /**
     * ES2022 private state ([[PrivateElements]]), deliberately out-of-band from
     * {@code props}: private names have no string identity, so nothing that walks
     * property names — {@code Object.keys}, {@code for-in}, spread,
     * {@code JSON.stringify}, {@code obj['#x']} — can reach or collide with them.
     * Keyed by {@link PrivateName} identity, so two classes that both spell
     * {@code #x} store into different entries. Lazily allocated.
     */
    private Map<PrivateName, Object> privates;
    /**
     * Symbol-keyed properties, by {@link JsSymbol} identity. Separate from
     * {@code props} for the same reason {@code privates} is: a minted symbol has
     * no string identity, so nothing that walks property names — {@code
     * Object.keys}, {@code for-in}, {@code getOwnPropertyNames}, spread,
     * {@code JSON.stringify} — can reach or collide with one. That is what makes
     * a customer payload key like {@code "@@type"} safe. Lazily allocated, so an
     * object with no symbol keys pays one null check.
     */
    private Map<JsSymbol, PropertySlot> symbols;
    private ObjectLike __proto__;
    /** Object-wide extensibility flags. Per-property attributes live on each
     *  Slot's {@code attrs} byte. The per-object flags double as fast-path
     *  early exits in {@link #putMember} / {@link #removeMember} so frozen
     *  objects don't have to consult per-Slot bits on every write. */
    private boolean nonExtensible;
    private boolean sealed;
    private boolean frozen;

    JsObject() {
        this.__proto__ = JsObjectPrototype.INSTANCE;
    }

    /** Construct from an initial {@code Map<String, Object>} of values — each
     *  entry is wrapped in a fresh {@link Slot} with default attributes. The
     *  source map is copied; subsequent mutations to it do NOT affect this
     *  object. */
    JsObject(Map<String, Object> seed) {
        this.__proto__ = JsObjectPrototype.INSTANCE;
        if (seed != null && !seed.isEmpty()) {
            this.props = new LinkedHashMap<>(seed.size());
            for (Map.Entry<String, Object> e : seed.entrySet()) {
                this.props.put(e.getKey(), new DataSlot(e.getKey(), e.getValue()));
            }
        }
    }

    /** Subclass constructor for a custom prototype. */
    protected JsObject(Map<String, Object> seed, ObjectLike proto) {
        this(seed);
        this.__proto__ = proto;
    }

    public ObjectLike getPrototype() {
        return __proto__;
    }

    public void setPrototype(ObjectLike proto) {
        this.__proto__ = proto;
    }

    /**
     * Whether this value should report {@code typeof === "function"} to JS.
     * Default {@code false}; overridden by {@link JsFunction} and by built-in
     * constructor singletons (e.g. the global {@code Boolean}, {@code RegExp},
     * {@code Error}) that are {@code JsObject} subclasses rather than
     * {@code JsFunction}. See {@link Terms#typeOf(Object)}.
     */
    boolean isJsFunction() {
        return false;
    }

    /**
     * True iff this object exposes {@code name} as an "own" intrinsic property
     * (e.g. {@code Date.prototype}, {@code Date.now}, {@code Date.UTC}).
     * Derived directly from {@link #resolveOwnIntrinsic(String)} — a non-null
     * resolution is, by definition, an own intrinsic; a null resolution means
     * the name is not on this object. Single source of truth eliminates the
     * old "subclass declares the name set twice" drift risk
     * (concretely: {@code JsFunction.constructor} once appeared in the
     * boolean override but not the value resolver, so
     * {@code f.hasOwnProperty('constructor')} reported true even though
     * {@code constructor} lives on {@code Function.prototype}, not on each
     * function instance).
     * <p>
     * Subclasses with non-resolveOwnIntrinsic-based storage
     * ({@link JsGlobalThis} bindings, {@link JsArray} numeric indices) override
     * {@link #isOwnProperty(String)} directly and bypass this hook.
     * <p>
     * Hot-path note: callers that only need an existence check pay the cost
     * of building the resolved value. Subclasses whose {@code resolveOwnIntrinsic}
     * allocates per call (built-in method lambdas, etc.) should install
     * those on the prototype instead — the prototype's {@code builtins} map
     * caches a single instance.
     */
    public boolean hasOwnIntrinsic(String name) {
        return resolveOwnIntrinsic(name) != null;
    }

    //==== ES2022 private state — see the `privates` field

    boolean hasPrivate(PrivateName pn) {
        return privates != null && privates.containsKey(pn);
    }

    Object getPrivate(PrivateName pn) {
        return privates == null ? null : privates.get(pn);
    }

    void putPrivate(PrivateName pn, Object value) {
        if (privates == null) {
            privates = new HashMap<>(4);
        }
        privates.put(pn, value);
    }

    //==== symbol-keyed state — see the `symbols` field

    boolean hasSymbol(JsSymbol sym) {
        return ownSymbolSlot(sym) != null;
    }

    /** Own slot for a symbol key, or null when absent. */
    PropertySlot ownSymbolSlot(JsSymbol sym) {
        PropertySlot s = symbols == null ? null : symbols.get(sym);
        return s == null || s.tombstoned ? null : s;
    }

    /** Own store only — the copy seams ({@code Object.assign}, spread) want
     *  exactly this. {@code ctx} must be the live context: the spec's Get runs
     *  an accessor's getter, and without one {@code AccessorSlot.read} yields
     *  undefined instead of invoking it. */
    Object getSymbol(JsSymbol sym, CoreContext ctx) {
        PropertySlot s = ownSymbolSlot(sym);
        return s == null ? Terms.UNDEFINED : s.read(this, ctx);
    }

    /** [[Get]] for a symbol key: own store, then up the prototype chain — a
     *  class method keyed by a symbol lives on the prototype, not the instance.
     *  Presence is the slot, never the value, so a stored {@code null} shadows
     *  an inherited entry instead of falling through to it. */
    Object getSymbolMember(JsSymbol sym, Object receiver, CoreContext ctx) {
        for (ObjectLike o = this; o != null; o = o.getPrototype()) {
            if (o instanceof JsObject jo) {
                PropertySlot s = jo.ownSymbolSlot(sym);
                if (s != null) {
                    return s.read(receiver, ctx);
                }
            }
        }
        return Terms.UNDEFINED;
    }

    /**
     * §10.1.9.2 OrdinarySetWithOwnDescriptor for a symbol key, with {@code this}
     * as the receiver. Walks the prototype chain: an inherited setter runs with
     * the receiver, an inherited non-writable data property rejects the write,
     * and anything else creates an own default-attribute slot. Mirrors what
     * {@code setByName} + {@code putMember} do for string keys.
     */
    void setSymbol(JsSymbol sym, Object value, CoreContext ctx, boolean strict) {
        for (ObjectLike o = this; o != null; o = o.getPrototype()) {
            if (!(o instanceof JsObject jo)) {
                continue;
            }
            PropertySlot s = jo.ownSymbolSlot(sym);
            if (s == null) {
                continue;
            }
            if (s instanceof AccessorSlot acc) {
                acc.write(this, value, ctx, strict);
                return;
            }
            if (!s.isWritable()) {
                if (strict) {
                    throw JsErrorException.typeError(
                            "Cannot assign to read only property '" + sym + "'");
                }
                return;
            }
            if (jo == this) {
                s.write(this, value, ctx, strict);
                return;
            }
            break; // inherited writable data property — shadow it with an own slot
        }
        if (frozen || sealed || nonExtensible) {
            if (strict) {
                throw JsErrorException.typeError(
                        "Cannot add property " + sym + ", object is not extensible");
            }
            return;
        }
        defineOwnSymbol(sym, value, PropertySlot.ATTRS_DEFAULT);
    }

    /** Low-level data-descriptor write for a symbol key — the
     *  {@code Object.defineProperty} seam. */
    void defineOwnSymbol(JsSymbol sym, Object value, byte attrs) {
        if (symbols == null) {
            symbols = new LinkedHashMap<>(4);
        }
        PropertySlot existing = symbols.get(sym);
        DataSlot s;
        if (existing instanceof DataSlot ds) {
            s = ds;
        } else {
            s = new DataSlot(sym.toString());
            symbols.put(sym, s);
        }
        s.value = value;
        s.attrs = attrs;
        s.tombstoned = false;
    }

    /** Low-level accessor-descriptor write for a symbol key — backs
     *  {@code get [sym]() {}} and {@code Object.defineProperty} with an accessor. */
    void defineOwnSymbolAccessor(JsSymbol sym, JsCallable getter, JsCallable setter, byte attrs) {
        if (symbols == null) {
            symbols = new LinkedHashMap<>(4);
        }
        PropertySlot existing = symbols.get(sym);
        AccessorSlot s;
        if (existing instanceof AccessorSlot as) {
            s = as;
        } else {
            s = new AccessorSlot(sym.toString());
            symbols.put(sym, s);
        }
        s.getter = getter;
        s.setter = setter;
        s.attrs = attrs;
        s.tombstoned = false;
    }

    boolean removeSymbol(JsSymbol sym, boolean strict) {
        PropertySlot s = ownSymbolSlot(sym);
        if (s == null) {
            return true;
        }
        if (!s.isConfigurable()) {
            if (strict) {
                throw JsErrorException.typeError("Cannot delete property '" + sym + "'");
            }
            return false;
        }
        symbols.remove(sym);
        return true;
    }

    /** Own enumerable symbol keys — the partition §7.3.26 CopyDataProperties
     *  and {@code Object.assign} copy. */
    List<JsSymbol> ownEnumerableSymbols() {
        if (symbols == null) {
            return List.of();
        }
        List<JsSymbol> out = new ArrayList<>(symbols.size());
        for (Map.Entry<JsSymbol, PropertySlot> e : symbols.entrySet()) {
            PropertySlot slot = e.getValue();
            if (!slot.tombstoned && slot.isEnumerable()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Attribute byte for an own symbol key, for descriptor reads. */
    byte ownSymbolAttrs(JsSymbol sym) {
        PropertySlot s = ownSymbolSlot(sym);
        return s == null ? 0 : s.attrs;
    }

    /** Own symbol keys in insertion order — the [[OwnPropertyKeys]] symbol
     *  partition behind {@code Object.getOwnPropertySymbols}. */
    List<JsSymbol> ownSymbols() {
        if (symbols == null) {
            return List.of();
        }
        List<JsSymbol> out = new ArrayList<>(symbols.size());
        for (Map.Entry<JsSymbol, PropertySlot> e : symbols.entrySet()) {
            if (!e.getValue().tombstoned) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    @Override
    public Object getMember(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) {
            // Tombstoned: a previously-existing intrinsic was deleted. Skip
            // both the slot's stale value and subclass intrinsic resolution
            // (which can't see this map); proceed directly to the prototype
            // chain so e.g. Math.abs.constructor still resolves after
            // `delete Math.abs.constructor`.
            if (s.tombstoned) {
                return __proto__ != null ? __proto__.getMember(name) : null;
            }
            // Raw-value semantic: AccessorSlot has no extractable value.
            // JS-semantic reads (PropertyAccess) use the receiver-aware
            // overload, which routes through slot.read.
            return s instanceof DataSlot ds ? ds.value : null;
        }
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        Object intrinsic = resolveOwnIntrinsic(name);
        if (intrinsic != null) return intrinsic;
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    @Override
    public Object getMember(String name, Object receiver, CoreContext ctx) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) {
            if (s.tombstoned) {
                return __proto__ != null ? __proto__.getMember(name, receiver, ctx) : null;
            }
            return s.read(receiver, ctx);
        }
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        Object intrinsic = resolveOwnIntrinsic(name);
        if (intrinsic != null) return intrinsic;
        if (__proto__ != null) {
            return __proto__.getMember(name, receiver, ctx);
        }
        return null;
    }

    /**
     * Subclass extension hook for "own" intrinsic members that are not stored
     * in {@code props} — e.g. {@code JsString.length}, {@code JsRegex.source},
     * {@code JsFunction.name} / {@code length} / {@code prototype}. Returns
     * the intrinsic value at this level only (no prototype walk); {@code null}
     * means "not an own intrinsic", letting the caller fall through to the
     * prototype chain.
     * <p>
     * Replaces the historical pattern where each subclass overrode the 1-arg
     * {@link #getMember(String)} and prefixed its body with
     * {@code Object own = super.getMember(name); if (own != null) return own;}.
     * Centralizing the intrinsic resolution here lets the 3-arg getMember
     * single-pass through (own slot → intrinsic hook → proto chain) and avoids
     * the double prototype walk that the 1-arg fallback caused for accessor
     * descriptors on the chain.
     */
    protected Object resolveOwnIntrinsic(String name) {
        return null;
    }

    /**
     * Names this subclass exposes as own intrinsics — the discovery seam used
     * by {@code Object.getOwnPropertyDescriptors} (and any future caller that
     * needs to enumerate intrinsics not materialized in {@link #toMap()}).
     * Default returns nothing; subclasses with a {@link #resolveOwnIntrinsic}
     * override return the closed name set they resolve.
     * <p>
     * Each subclass returns its own complete list (no chaining via
     * {@code super}) — matches the structure of {@link #resolveOwnIntrinsic},
     * which any subclass that resolves intrinsics already overrides
     * end-to-end. Built-in constructors install their methods as actual own
     * slots via {@code defineOwn} (so they surface through {@code toMap()}
     * directly) and inherit the {@link JsFunction} list for the
     * {@code prototype} / {@code name} / {@code length} surface.
     */
    protected Iterable<String> ownIntrinsicNames() {
        return Collections.emptyList();
    }

    /** Returns the own slot for {@code name}, or {@code null} when absent or
     *  tombstoned. Package-private — callers (defineProperty, the literal-
     *  accessor merge in {@link Interpreter}) need slot identity to inspect
     *  or mutate the descriptor in place. */
    PropertySlot getOwnSlot(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s == null || s.tombstoned) return null;
        return s;
    }

    /**
     * True iff {@code name} is an own property on this object — covers
     * non-tombstoned own slots and intrinsic properties declared by subclasses
     * (via {@code hasOwnIntrinsic}). Use this for
     * {@code Object.getOwnPropertyDescriptor} / {@code hasOwn} / {@code in}
     * semantics; raw {@code props.containsKey} would include tombstones.
     */
    public boolean isOwnProperty(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) {
            return !s.tombstoned;
        }
        return hasOwnIntrinsic(name);
    }

    boolean isTombstoned(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s != null && s.tombstoned;
    }

    /** Removes the tombstone for {@code name} if any. Subclasses use this when
     *  a write reanimates a previously-deleted entry. */
    void clearTombstone(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null && s.tombstoned) {
            props.remove(name);
        }
    }

    /** True iff {@code name} has a non-tombstoned own slot (excludes intrinsics). */
    boolean ownContainsKey(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s != null && !s.tombstoned;
    }

    /** Strict-mode [[Set]] rejection: assigning a non-writable / frozen prop. */
    static void failReadOnly(String name) {
        throw JsErrorException.typeError("Cannot assign to read only property '" + name + "'");
    }

    /** Strict-mode [[Set]] rejection: adding a key to a non-extensible object. */
    static void failNotExtensible(String name) {
        throw JsErrorException.typeError("Cannot add property " + name + ", object is not extensible");
    }

    /** Strict-mode [[Delete]] rejection: removing a non-configurable property. */
    static void failNotConfigurable(String name) {
        throw JsErrorException.typeError("Cannot delete property '" + name + "' of " + "[object Object]");
    }

    @Override
    public void putMember(String name, Object value) {
        putMember(name, value, null, false);
    }

    @Override
    public void putMember(String name, Object value, CoreContext ctx, boolean strict) {
        if ("__proto__".equals(name)) {
            if (value instanceof ObjectLike proto) {
                this.__proto__ = proto;
            } else if (value == null) {
                this.__proto__ = null;
            }
            return;
        }
        // Frozen: ignore all writes (sloppy); strict throws. Non-extensible:
        // ignore writes that would *create* a new own property; existing-key
        // updates are still allowed (sealed differs from frozen by allowing
        // them).
        if (frozen) {
            if (strict) failReadOnly(name);
            return;
        }
        PropertySlot s = props == null ? null : props.get(name);
        boolean keyExists = s != null && !s.tombstoned;
        // Intrinsic-backed properties (built-in length / name / Math.E …) need
        // to honor their spec attributes on [[Set]] too. Treat them as "exists"
        // for extensibility purposes (writes to them aren't creating a new key
        // from the perspective of nonExtensible) but consult getOwnAttrs for
        // the writable check. Tombstoned slots are treated as missing —
        // putting them back is allowed if the object is extensible.
        boolean intrinsic = !keyExists && hasOwnIntrinsic(name);
        if (nonExtensible && !keyExists && !intrinsic) {
            if (strict) failNotExtensible(name);
            return;
        }
        // Existing accessor descriptor: invoke the setter (get-only accessors
        // drop the write in sloppy mode, TypeError under strict — the slot's
        // own write() honors the flag). The ctx threads through for live
        // setters when the receiver-aware {@link PropertyAccess#setByName}
        // path supplies it; direct callers pass null.
        if (keyExists && s instanceof AccessorSlot acc) {
            acc.write(this, value, ctx, strict);
            return;
        }
        // Per-property writable=false: silently ignore the [[Set]] in sloppy
        // mode; TypeError under strict.
        if (keyExists && !s.isWritable()) {
            if (strict) failReadOnly(name);
            return;
        }
        if (intrinsic && (getOwnAttrs(name) & WRITABLE) == 0) {
            if (strict) failReadOnly(name);
            return;
        }
        if (s == null) {
            if (props == null) {
                props = new LinkedHashMap<>();
            }
            props.put(name, new DataSlot(name, value));
        } else {
            // Reuse the slot — clears any tombstone, preserves attrs.
            ((DataSlot) s).value = value;
            s.tombstoned = false;
        }
    }

    /**
     * Returns the attribute byte for {@code name}: bit-OR of {@link #WRITABLE},
     * {@link #ENUMERABLE}, {@link #CONFIGURABLE}. Defaults to all-true when the
     * key has never been touched by {@code defineProperty} / {@code seal} /
     * {@code freeze}.
     */
    byte getAttrs(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s == null ? ATTRS_DEFAULT : s.attrs;
    }

    /**
     * Spec-correct attribute byte for an intrinsic own property. Default reads
     * the slot's attrs via {@code getAttrs(String)}. Subclasses (especially
     * built-in constructors / prototypes / the {@link JsFunction} hierarchy)
     * override to return tighter attributes for intrinsic members declared via
     * {@link #hasOwnIntrinsic(String)} — e.g. built-in method properties default
     * to {@code {writable: true, enumerable: false, configurable: true}}; built-in
     * constants default to all-false.
     * <p>
     * The owner of this method is also responsible for declaring the same key
     * via {@code hasOwnIntrinsic} — otherwise {@code getOwnPropertyDescriptor}
     * won't reach this lookup at all.
     */
    public byte getOwnAttrs(String name) {
        return getAttrs(name);
    }

    /** True iff explicit per-property attrs deviating from the all-true default
     *  have been recorded for {@code name} (i.e. {@code defineProperty} /
     *  {@code seal} / {@code freeze} touched it). Subclasses use this to decide
     *  whether to honor the stored attrs vs. apply a class-default. */
    boolean hasExplicitAttrs(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s != null && s.attrs != ATTRS_DEFAULT;
    }

    /** Stores the attribute byte for {@code name} on its slot. Creates a
     *  data slot if absent (rare — defineProperty path uses
     *  {@link #defineOwn} / {@link #defineOwnAccessor} which set both value
     *  and attrs). */
    void setAttrs(String name, byte attrs) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s == null) {
            if (attrs == ATTRS_DEFAULT) return;
            if (props == null) {
                props = new LinkedHashMap<>();
            }
            s = new DataSlot(name);
            props.put(name, s);
        }
        s.attrs = attrs;
    }

    boolean isWritable(String name) {
        return (getAttrs(name) & WRITABLE) != 0;
    }

    /**
     * Spec-correct enumerability check. Routes through {@link #getOwnAttrs}
     * so subclass overrides (e.g. JsMath returning {@code WRITABLE |
     * CONFIGURABLE} — no enumerable bit — for its built-in methods) apply.
     */
    boolean isEnumerable(String name) {
        return (getOwnAttrs(name) & ENUMERABLE) != 0;
    }

    boolean isConfigurable(String name) {
        return (getAttrs(name) & CONFIGURABLE) != 0;
    }

    /**
     * Low-level data-descriptor write used by {@code Object.defineProperty}.
     * Bypasses the {@code [[Set]]} writable check (defineProperty is allowed
     * to mutate non-writable data props subject to its own configurable
     * rules, which the caller already validated). Caller is responsible for
     * extensibility + configurability checks.
     * <p>
     * Replaces any prior accessor slot at this name with a fresh
     * {@link DataSlot} — switching descriptor shape is the caller's
     * responsibility (configurability is enforced upstream).
     */
    void defineOwn(String name, Object value, byte attrs) {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        PropertySlot existing = props.get(name);
        DataSlot s;
        if (existing instanceof DataSlot ds) {
            s = ds;
        } else {
            s = new DataSlot(name);
            props.put(name, s);
        }
        s.value = value;
        s.attrs = attrs;
        s.tombstoned = false;
    }

    /**
     * Low-level accessor-descriptor write. Installs (or updates) an
     * {@link AccessorSlot} at {@code name}. Replaces any prior data slot —
     * configurability is enforced upstream by
     * {@code Object.defineProperty}.
     */
    void defineOwnAccessor(String name, JsCallable getter, JsCallable setter, byte attrs) {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        PropertySlot existing = props.get(name);
        AccessorSlot s;
        if (existing instanceof AccessorSlot as) {
            s = as;
        } else {
            s = new AccessorSlot(name);
            props.put(name, s);
        }
        s.getter = getter;
        s.setter = setter;
        s.attrs = attrs;
        s.tombstoned = false;
    }

    @Override
    public boolean isExtensible() {
        return !nonExtensible;
    }

    @Override
    public boolean isSealed() {
        return sealed || frozen;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void setExtensible(boolean extensible) {
        // Monotonic — only the false direction does anything.
        if (extensible) return;
        this.nonExtensible = true;
    }

    @Override
    public void setSealed(boolean sealed) {
        if (!sealed) return;
        this.nonExtensible = true;
        this.sealed = true;
        // Mark every existing key as non-configurable so that
        // getOwnPropertyDescriptor reports configurable=false. Per-object flag
        // is the fast-path early exit on writes/removes; clearing the bit on
        // each slot is for the attribute readers.
        if (props != null) {
            for (PropertySlot s : props.values()) {
                if (!s.tombstoned) {
                    s.attrs &= ~CONFIGURABLE;
                }
            }
        }
        if (symbols != null) {
            for (PropertySlot s : symbols.values()) {
                if (!s.tombstoned) {
                    s.attrs &= ~CONFIGURABLE;
                }
            }
        }
    }

    @Override
    public void setFrozen(boolean frozen) {
        if (!frozen) return;
        this.nonExtensible = true;
        this.sealed = true;
        this.frozen = true;
        if (props != null) {
            for (PropertySlot s : props.values()) {
                if (s.tombstoned) continue;
                s.attrs &= ~CONFIGURABLE;
                // writable is N/A for accessor properties — only clear on data slots.
                if (s instanceof DataSlot) {
                    s.attrs &= ~WRITABLE;
                }
            }
        }
        if (symbols != null) {
            for (PropertySlot s : symbols.values()) {
                if (s.tombstoned) continue;
                s.attrs &= ~CONFIGURABLE;
                if (s instanceof DataSlot) {
                    s.attrs &= ~WRITABLE;
                }
            }
        }
    }

    /**
     * Sugar for the canonical {@code new JsBuiltinMethod(name, length, delegate)}
     * call used by built-in constructor singletons in their {@code resolveMember}
     * switches. Same shape as {@code Prototype.install(String, int, JsCallable)}
     * — written so that each switch case reads
     * {@code case "isFinite" -> method(name, 1, (JsInvokable) this::isFinite)}
     * with the case label value flowing into the wrap as a single source of truth.
     */
    protected static JsBuiltinMethod method(String methodName, int length, JsCallable delegate) {
        return new JsBuiltinMethod(methodName, length, delegate);
    }

    @Override
    public void removeMember(String name) {
        removeMember(name, null, false);
    }

    @Override
    public void removeMember(String name, CoreContext ctx, boolean strict) {
        PropertySlot s = props == null ? null : props.get(name);
        // Already tombstoned — nothing to do.
        if (s != null && s.tombstoned) {
            return;
        }
        boolean inMap = s != null;
        boolean intrinsic = hasOwnIntrinsic(name);
        if (!inMap && !intrinsic) {
            return;
        }
        // Configurability check. The slot's attrs byte wins when present;
        // otherwise fall back to the intrinsic's getOwnAttrs default. Sealed/
        // frozen flags imply non-configurable (they cleared the bit on every
        // slot when applied), so checking the slot's attrs alone is enough.
        // Sloppy `delete` of a non-configurable prop returns false silently;
        // strict mode throws TypeError.
        byte attrs = inMap ? s.attrs : getOwnAttrs(name);
        if ((attrs & CONFIGURABLE) == 0) {
            if (strict) failNotConfigurable(name);
            return;
        }
        if (intrinsic) {
            // Tombstone if there is an underlying intrinsic. Without this, after
            // `Math.abs = X; delete Math.abs;` the intrinsic Math.abs would
            // "shine through" and `hasOwnProperty` would incorrectly report it
            // as own. The tombstone shadows the intrinsic; a later assignment
            // (`Math.abs = Y`) clears the tombstone in putMember.
            DataSlot ds;
            if (s instanceof DataSlot existing) {
                ds = existing;
            } else {
                if (props == null) {
                    props = new LinkedHashMap<>();
                }
                ds = new DataSlot(name);
                props.put(name, ds);
            }
            ds.value = null;
            ds.attrs = ATTRS_DEFAULT;
            ds.tombstoned = true;
        } else {
            props.remove(name);
        }
    }

    /**
     * Java-interop snapshot of own data. Accessor descriptors surface as
     * {@code null} entries — this is the no-side-effects boundary where the
     * absence of a {@link CoreContext} would force getters to silently fail
     * anyway. {@link Map#get}, {@link #values()}, {@link #containsValue},
     * {@link #entrySet} and the no-arg {@link #jsEntries()} all follow the
     * same rule for the same reason.
     * <p>
     * Spec-correct iteration that <em>must</em> invoke accessor getters
     * ({@code Object.keys / values / entries / assign}) goes through
     * {@link #jsEntries(CoreContext)} — Refactor E carved that out as the
     * single ctx-aware seam. If you find yourself reaching for {@code toMap}
     * from JS-semantic code, you probably want {@code jsEntries(ctx)} instead.
     */
    @Override
    public Map<String, Object> toMap() {
        if (props == null || props.isEmpty()) return Collections.emptyMap();
        Map<String, Object> view = new LinkedHashMap<>(props.size());
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) {
                view.put(s.name, s instanceof DataSlot ds ? ds.value : null);
            }
        }
        return view;
    }

    // =================================================================================================
    // Map<String, Object> interface - auto-unwraps values for Java consumers
    // =================================================================================================

    @Override
    public int size() {
        if (props == null) return 0;
        int n = 0;
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) n++;
        }
        return n;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String name)) return false;
        return ownContainsKey(name);
    }

    @Override
    public boolean containsValue(Object value) {
        if (props == null) return false;
        for (PropertySlot s : props.values()) {
            if (s.tombstoned) continue;
            // Accessor slots have no raw value at the Java-interop boundary
            // — they're skipped (matches null-ish containsValue semantics).
            if (!(s instanceof DataSlot ds)) continue;
            Object unwrapped = Engine.toJava(ds.value);
            if (Objects.equals(unwrapped, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        // Map.get() — auto-unwrap, own properties only (no prototype chain).
        // Accessor slots have no raw value; surface as null.
        PropertySlot s = props == null || !(key instanceof String name) ? null : props.get(name);
        if (s == null || s.tombstoned) return null;
        return s instanceof DataSlot ds ? Engine.toJava(ds.value) : null;
    }

    @Override
    public Object put(String key, Object value) {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        PropertySlot s = props.get(key);
        Object previous = null;
        if (s instanceof DataSlot ds && !ds.tombstoned) {
            previous = ds.value;
        }
        if (s instanceof DataSlot ds) {
            ds.value = value;
            ds.tombstoned = false;
        } else {
            // Replace any prior accessor slot (or absent slot) with a fresh
            // data slot. Configurability checks belong to defineProperty;
            // this path is the Map-interface back door, used by user-side
            // Java code and by the literal-object init path.
            props.put(key, new DataSlot(key, value));
        }
        return Engine.toJava(previous);
    }

    @Override
    public Object remove(Object key) {
        if (props == null || !(key instanceof String name)) return null;
        PropertySlot s = props.remove(name);
        if (s == null || s.tombstoned) return null;
        return s instanceof DataSlot ds ? Engine.toJava(ds.value) : null;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        for (Map.Entry<? extends String, ?> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        if (props != null) {
            props.clear();
        }
    }

    @Override
    public Set<String> keySet() {
        if (props == null || props.isEmpty()) return Collections.emptySet();
        Set<String> keys = new LinkedHashSet<>(props.size());
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) keys.add(s.name);
        }
        return keys;
    }

    @Override
    public Collection<Object> values() {
        if (props == null || props.isEmpty()) return Collections.emptyList();
        List<Object> unwrapped = new ArrayList<>(props.size());
        for (PropertySlot s : props.values()) {
            if (s.tombstoned) continue;
            unwrapped.add(s instanceof DataSlot ds ? Engine.toJava(ds.value) : null);
        }
        return unwrapped;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        if (props == null || props.isEmpty()) return Collections.emptySet();
        Set<Entry<String, Object>> out = new LinkedHashSet<>(props.size());
        for (PropertySlot s : props.values()) {
            if (s.tombstoned) continue;
            Object v = s instanceof DataSlot ds ? Engine.toJava(ds.value) : null;
            out.add(new AbstractMap.SimpleEntry<>(s.name, v));
        }
        return out;
    }

    static Set<Entry<String, Object>> getEntries(Map<String, Object> map) {
        // Back the entry set with a LinkedHashMap rather than a LinkedHashSet:
        // LinkedHashSet.add hashes each Entry (key^value), and value.hashCode() recurses
        // forever when a user variable holds a self-referential Map / List.
        // Input keys are already unique (they come from a Map.entrySet), so de-dup-by-hash
        // is wasted work even in the common case.
        Map<String, Object> unwrapped = new LinkedHashMap<>();
        for (Entry<String, Object> entry : map.entrySet()) {
            unwrapped.put(entry.getKey(), Engine.toJava(entry.getValue()));
        }
        return unwrapped.entrySet();
    }

    // =================================================================================================

    /**
     * Returns an iterable for JS for-in/for-of iteration with KeyValue pairs.
     * Used internally by JS iteration constructs and by {@link Terms#toIterable},
     * which is the back-end for {@code Object.keys / values / entries / assign}
     * and {@code for...in}. All of those filter by enumerable per spec.
     * <p>
     * Iterates {@code props} directly so each yielded value reads the slot's
     * current value at next() time — callback-driven mutations during iteration
     * are visible (test262 {@code Array.prototype.map}'s "callback mutates
     * earlier index, later index sees update" semantics rely on this).
     * Subclasses with alternate storage ({@link JsGlobalThis}) override.
     * Routes through {@code isEnumerable} so subclass {@code getOwnAttrs}
     * overrides (e.g. JsMath returning {@code WRITABLE | CONFIGURABLE} — no
     * enumerable bit — for its built-in methods) win.
     * <p>
     * The no-arg form is the Java-interop seam — accessor properties have no
     * extractable raw value here and surface as {@code null}. The
     * {@link #jsEntries(CoreContext)} overload is the spec-correct iteration
     * for {@code Object.keys/values/entries/assign} (invokes accessor getters
     * via {@link PropertySlot#read}).
     */
    public Iterable<KeyValue> jsEntries() {
        return jsEntries(null);
    }

    /**
     * Spec §9.1.11.1 OrdinaryOwnPropertyKeys ordering applied to an
     * insertion-order key set: integer-index keys (per
     * {@link JsArray#parseIndex}) move to the front in ascending numeric
     * order, then remaining string keys keep their insertion order. Returns
     * the input unchanged when no integer-index keys are present (the common
     * case for plain object literals with named properties only).
     * <p>
     * Used at every spec seam that reports own keys for an ordinary object —
     * {@link #jsEntries(CoreContext)} (drives {@code Object.keys / values /
     * entries / assign}) and {@code JsObjectConstructor.ownKeys} (drives
     * {@code Object.getOwnPropertyNames / getOwnPropertyDescriptors /
     * defineProperties}). {@link JsArray} has its own integer-first iteration
     * via the dense list; this helper is for plain {@code JsObject} /
     * {@link ObjectLike} backed by an insertion-ordered prop map.
     */
    public static Set<String> orderedOwnKeys(Set<String> insertionOrder) {
        if (insertionOrder == null || insertionOrder.size() < 2) return insertionOrder;
        TreeMap<Integer, String> intKeys = null;
        List<String> stringKeys = null;
        for (String k : insertionOrder) {
            int idx = JsArray.parseIndex(k);
            if (idx >= 0) {
                if (intKeys == null) intKeys = new TreeMap<>();
                intKeys.put(idx, k);
            } else {
                if (stringKeys == null) stringKeys = new ArrayList<>(insertionOrder.size());
                stringKeys.add(k);
            }
        }
        if (intKeys == null) return insertionOrder;
        LinkedHashSet<String> ordered = new LinkedHashSet<>(insertionOrder.size());
        ordered.addAll(intKeys.values());
        if (stringKeys != null) ordered.addAll(stringKeys);
        return ordered;
    }

    /**
     * JS-semantic iteration variant — accessor descriptors invoke their
     * getters with {@code ctx} when {@code ctx != null}; otherwise behaves as
     * the no-arg {@code jsEntries()} (raw values, accessors → null). This is
     * the back-end called from {@code Object.keys / values / entries / assign}
     * via {@link Terms#toIterable(Object, CoreContext)} so accessor
     * descriptors observe their spec invocation.
     * <p>
     * Yields entries in §9.1.11.1 OrdinaryOwnPropertyKeys order — integer
     * indices ascending, then string keys insertion-order. Slots are
     * partitioned in a single pre-pass; values are still re-read at yield
     * time so callback-mutated values propagate.
     */
    public Iterable<KeyValue> jsEntries(CoreContext ctx) {
        return () -> new Iterator<>() {
            final Iterator<PropertySlot> source = orderedSlotsForIteration().iterator();
            int index = 0;
            PropertySlot peeked = null;

            private boolean advance() {
                while (source.hasNext()) {
                    PropertySlot s = source.next();
                    // Re-check at yield time so callbacks that flip
                    // enumerable / tombstone a not-yet-yielded slot mid-
                    // iteration are observed (test262
                    // {entries,values}/getter-making-future-key-nonenumerable).
                    if (s.tombstoned) continue;
                    if (!isEnumerable(s.name)) continue;
                    peeked = s;
                    return true;
                }
                peeked = null;
                return false;
            }

            @Override
            public boolean hasNext() {
                return peeked != null || advance();
            }

            @Override
            public KeyValue next() {
                if (peeked == null && !advance()) {
                    throw new NoSuchElementException();
                }
                PropertySlot s = peeked;
                peeked = null;
                // Read at yield time so callback-driven mutations before
                // the next next() call propagate. With ctx, accessor slots
                // resolve via getter invocation; without ctx, they surface
                // as null (Java-interop semantic).
                Object v = ctx != null
                        ? s.read(JsObject.this, ctx)
                        : s instanceof DataSlot ds ? ds.value : null;
                return new KeyValue(JsObject.this, index++, s.name, v);
            }
        };
    }

    /** Non-tombstoned slots in §9.1.11.1 ordering — integer-index slots
     *  ascending, then named slots in insertion order. Backs
     *  {@link #jsEntries(CoreContext)}; the enumerable filter is applied at
     *  yield time, not here, so a callback that mid-iteration flips a future
     *  slot to non-enumerable is observed. */
    private List<PropertySlot> orderedSlotsForIteration() {
        if (props == null || props.isEmpty()) return Collections.emptyList();
        TreeMap<Integer, PropertySlot> intSlots = null;
        List<PropertySlot> stringSlots = null;
        for (PropertySlot s : props.values()) {
            if (s.tombstoned) continue;
            int idx = JsArray.parseIndex(s.name);
            if (idx >= 0) {
                if (intSlots == null) intSlots = new TreeMap<>();
                intSlots.put(idx, s);
            } else {
                if (stringSlots == null) stringSlots = new ArrayList<>();
                stringSlots.add(s);
            }
        }
        if (intSlots == null) {
            return stringSlots != null ? stringSlots : Collections.emptyList();
        }
        List<PropertySlot> out = new ArrayList<>(
                intSlots.size() + (stringSlots == null ? 0 : stringSlots.size()));
        out.addAll(intSlots.values());
        if (stringSlots != null) out.addAll(stringSlots);
        return out;
    }

    // Identity-based hashCode/equals to avoid infinite recursion on circular
    // references (e.g. constructor ↔ prototype).
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

}
