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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JavaScript Object implementation with unified prototype chain.
 * <p>
 * Property lookup order:
 * <ol>
 *   <li>Own properties ({@code _map})</li>
 *   <li>Prototype chain ({@code __proto__})</li>
 * </ol>
 */
class JsObject implements ObjectLike, JsCallable, Map<String, Object> {

    // Per-property attribute bits stored in a single byte. Sparse: a key is only
    // present in {@code _attrs} if its triplet *deviates* from the all-true default,
    // so plain `obj.foo = 1` writes never allocate the map. Bit layout matches
    // the order of the spec's three boolean attributes.
    static final byte WRITABLE = 0b001;
    static final byte ENUMERABLE = 0b010;
    static final byte CONFIGURABLE = 0b100;
    static final byte ATTRS_DEFAULT = WRITABLE | ENUMERABLE | CONFIGURABLE;

    /**
     * JVM-wide singletons that must reset their per-Engine mutable state when a
     * fresh {@link Engine} is constructed. Built-in constructor instances
     * ({@code JsNumberConstructor.INSTANCE}, {@code JsObjectConstructor.INSTANCE},
     * etc.) call {@link #registerForEngineReset()} to enroll themselves; the
     * {@link Engine} constructor walks this list and invokes
     * {@link #clearEngineState()} on each entry. Mirrors {@code Prototype.ALL} but
     * for the JsObject hierarchy, where state is {@code _map} / {@code _attrs} /
     * {@code _tombstones} (plus subclass caches).
     * <p>
     * Per-Engine instances ({@code JsMath}, allocated fresh in
     * {@code ContextRoot.initGlobal}) are GC'd with their owning Engine and must
     * NOT register here.
     */
    private static final List<JsObject> ENGINE_RESET_LIST = new CopyOnWriteArrayList<>();

    static void clearAllEngineState() {
        for (JsObject o : ENGINE_RESET_LIST) {
            o.clearEngineState();
        }
    }

    private Map<String, Object> _map;
    private ObjectLike __proto__;
    // Object-wide extensibility flags. Per-property attributes live in {@code _attrs}.
    // The per-object flags double as fast-path early exits in putMember / removeMember
    // so frozen objects don't have to consult _attrs on every write.
    private boolean nonExtensible;
    private boolean sealed;
    private boolean frozen;
    private Map<String, Byte> _attrs;
    // Tombstones for intrinsic-backed own properties (length / name on built-in
    // functions, etc.) that the user has deleted via `delete obj.foo` or
    // `Object.defineProperty(obj, 'foo', {configurable: ?})`. The intrinsic
    // resolution lives in subclass {@code getMember} switches, not in {@code _map},
    // so deletion has nothing to remove from the map — we mark the name as
    // "removed" here, and {@link #getMember} / {@link #isOwnProperty} skip the
    // intrinsic fallback when the name is tombstoned. Lazily allocated.
    private java.util.Set<String> _tombstones;

    JsObject(Map<String, Object> map) {
        this._map = map;
        this.__proto__ = JsObjectPrototype.INSTANCE;
    }

    JsObject() {
        this(null);
    }

    /**
     * Protected constructor for subclasses that need a different prototype.
     */
    protected JsObject(Map<String, Object> map, ObjectLike proto) {
        this._map = map;
        this.__proto__ = proto;
    }

    /**
     * Returns the prototype (__proto__) of this object.
     */
    public ObjectLike getPrototype() {
        return __proto__;
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
     * Sets the prototype (__proto__) of this object.
     */
    public void setPrototype(ObjectLike proto) {
        this.__proto__ = proto;
    }

    /**
     * True iff this object exposes {@code name} as an "own" intrinsic property
     * (e.g. {@code Date.prototype}, {@code Date.now}, {@code Date.UTC}). Default:
     * false — only user-added entries in {@link #_map} count as own.
     * <p>
     * Subclasses (especially built-in constructors) override to declare which
     * names their {@link #getMember} resolves directly without delegating to
     * {@code __proto__}. Used by {@code Object.prototype.hasOwnProperty} so that
     * {@code Date.hasOwnProperty('UTC') === true} per spec.
     */
    public boolean hasOwnIntrinsic(String name) {
        return false;
    }

    @Override
    public Object getMember(String name) {
        // 0. Tombstoned: a previously-existing intrinsic was deleted. Skip
        // both _map and subclass intrinsic resolution (which can't see this
        // map); proceed directly to the prototype chain so e.g.
        // Math.abs.constructor still resolves after `delete Math.abs.constructor`.
        if (_tombstones != null && _tombstones.contains(name)) {
            return __proto__ != null ? __proto__.getMember(name) : null;
        }
        // 1. Check own properties
        if (_map != null && _map.containsKey(name)) {
            return _map.get(name);
        }
        // 2. Special case for __proto__ property access
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        // 3. Delegate to prototype chain
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    /**
     * True iff {@code name} is an own property on this object — covers
     * {@code _map} entries, intrinsic properties declared by subclasses (via
     * {@link #hasOwnIntrinsic}), and excludes tombstoned (deleted) intrinsics.
     * Use this for {@code Object.getOwnPropertyDescriptor} / {@code hasOwn} /
     * {@code in} semantics; raw {@code _map.containsKey} misses intrinsic
     * length / name / prototype on built-in functions.
     */
    public boolean isOwnProperty(String name) {
        if (_tombstones != null && _tombstones.contains(name)) return false;
        if (_map != null && _map.containsKey(name)) return true;
        return hasOwnIntrinsic(name);
    }

    boolean isTombstoned(String name) {
        return _tombstones != null && _tombstones.contains(name);
    }

    /** True iff {@code name} is in the own-property map (excluding intrinsics and tombstones). */
    boolean ownContainsKey(String name) {
        return _map != null && _map.containsKey(name);
    }

    @Override
    public void putMember(String name, Object value) {
        if ("__proto__".equals(name)) {
            if (value instanceof ObjectLike proto) {
                this.__proto__ = proto;
            } else if (value == null) {
                this.__proto__ = null;
            }
            return;
        }
        // Frozen: silently ignore all writes (lenient mode — strict-mode
        // TypeError flip lives elsewhere). Non-extensible: ignore writes
        // that would *create* a new own property; existing-key updates are
        // still allowed (sealed differs from frozen by allowing them).
        if (frozen) {
            return;
        }
        boolean keyExists = _map != null && _map.containsKey(name);
        boolean tombstoned = _tombstones != null && _tombstones.contains(name);
        // Intrinsic-backed properties (built-in length / name / Math.E …) need
        // to honor their spec attributes on [[Set]] too. Treat them as "exists"
        // for extensibility purposes (writes to them aren't creating a new key
        // from the perspective of nonExtensible) but consult getOwnAttrs for
        // the writable check. Tombstoned entries are treated as missing —
        // putting them back is allowed if the object is extensible.
        boolean intrinsic = !keyExists && !tombstoned && hasOwnIntrinsic(name);
        if (nonExtensible && !keyExists && !intrinsic) {
            return;
        }
        // Per-property writable=false: silently ignore the [[Set]]. Spec says
        // throw under strict; we're non-strict by default.
        if (keyExists && _attrs != null) {
            Byte b = _attrs.get(name);
            if (b != null && (b & WRITABLE) == 0) {
                return;
            }
        }
        if (intrinsic && (getOwnAttrs(name) & WRITABLE) == 0) {
            return;
        }
        if (_map == null) {
            _map = new LinkedHashMap<>();
        }
        _map.put(name, value);
        // Successful write clears any tombstone — the key now exists again.
        if (tombstoned) {
            _tombstones.remove(name);
        }
    }

    /**
     * Returns the attribute byte for {@code name}: bit-OR of {@link #WRITABLE},
     * {@link #ENUMERABLE}, {@link #CONFIGURABLE}. Defaults to all-true when the
     * key has never been touched by {@code defineProperty} / {@code seal} /
     * {@code freeze}.
     */
    byte getAttrs(String name) {
        if (_attrs == null) return ATTRS_DEFAULT;
        Byte b = _attrs.get(name);
        return b == null ? ATTRS_DEFAULT : b;
    }

    /**
     * Spec-correct attribute byte for an intrinsic own property. Default reads
     * from {@link #_attrs} via {@link #getAttrs(String)} — same as user-defined
     * keys. Subclasses (especially built-in constructors / prototypes / the
     * {@link JsFunction} hierarchy) override to return tighter attributes for
     * intrinsic members declared via {@link #hasOwnIntrinsic(String)} —
     * e.g. built-in method properties default to
     * {@code {writable: true, enumerable: false, configurable: true}}; built-in
     * constants default to all-false.
     * <p>
     * The owner of this method is also responsible for declaring the same key
     * via {@code hasOwnIntrinsic} — otherwise {@code getOwnPropertyDescriptor}
     * won't reach this lookup at all.
     */
    public byte getOwnAttrs(String name) {
        return getAttrs(name);
    }

    /** Stores the attribute byte for {@code name}; absence means all-true. */
    void setAttrs(String name, byte attrs) {
        if (attrs == ATTRS_DEFAULT) {
            if (_attrs != null) {
                _attrs.remove(name);
            }
            return;
        }
        if (_attrs == null) {
            _attrs = new LinkedHashMap<>();
        }
        _attrs.put(name, attrs);
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
     * Low-level descriptor write used by {@code Object.defineProperty}. Bypasses
     * the {@code [[Set]]} writable check (defineProperty is allowed to mutate
     * non-writable data props subject to its own configurable rules, which the
     * caller already validated). Caller is responsible for extensibility +
     * configurability checks.
     */
    void defineOwn(String name, Object value, byte attrs) {
        if (_map == null) {
            _map = new LinkedHashMap<>();
        }
        _map.put(name, value);
        setAttrs(name, attrs);
    }

    boolean isExtensible() {
        return !nonExtensible;
    }

    boolean isSealed() {
        return sealed || frozen;
    }

    boolean isFrozen() {
        return frozen;
    }

    void preventExtensions() {
        this.nonExtensible = true;
    }

    void seal() {
        this.nonExtensible = true;
        this.sealed = true;
        // Mark every existing key as non-configurable so that
        // getOwnPropertyDescriptor reports configurable=false. Per-object flag
        // is the fast-path early exit on writes/removes; this map is for the
        // attribute readers.
        if (_map != null) {
            for (String key : _map.keySet()) {
                byte cur = getAttrs(key);
                setAttrs(key, (byte) (cur & ~CONFIGURABLE));
            }
        }
    }

    void freeze() {
        this.nonExtensible = true;
        this.sealed = true;
        this.frozen = true;
        if (_map != null) {
            for (Map.Entry<String, Object> e : _map.entrySet()) {
                byte cur = getAttrs(e.getKey());
                cur &= ~CONFIGURABLE;
                // writable is N/A for accessor properties — only set on data slots.
                if (!(e.getValue() instanceof JsAccessor)) {
                    cur &= ~WRITABLE;
                }
                setAttrs(e.getKey(), cur);
            }
        }
    }

    /**
     * Enroll this JsObject in {@link #ENGINE_RESET_LIST} — call from a singleton
     * constructor whose lifetime exceeds a single {@link Engine} session (typically
     * {@code <Constructor>.INSTANCE} fields referenced from
     * {@code ContextRoot.initGlobal}). See {@link #ENGINE_RESET_LIST} for the
     * criteria; per-Engine instances must not register.
     */
    protected final void registerForEngineReset() {
        ENGINE_RESET_LIST.add(this);
    }

    /**
     * Reset per-Engine mutable state on this singleton so user-set properties /
     * tombstones / extensibility flips from one test don't bleed into the next.
     * Default clears {@code _map}, {@code _attrs}, {@code _tombstones}, and the
     * three extensibility flags. Subclasses with additional caches (e.g. a
     * {@code _methodCache} of wrapped {@link JsBuiltinMethod} instances) should
     * override and call {@code super.clearEngineState()} first.
     */
    protected void clearEngineState() {
        if (_map != null) _map.clear();
        if (_attrs != null) _attrs.clear();
        if (_tombstones != null) _tombstones.clear();
        nonExtensible = false;
        sealed = false;
        frozen = false;
    }

    /**
     * Sugar for the canonical {@code new JsBuiltinMethod(name, length, delegate)}
     * call used by built-in constructor singletons in their {@code resolveMember}
     * switches. Same shape as {@link Prototype#method(String, int, JsCallable)}
     * — written so that each switch case reads
     * {@code case "isFinite" -> method(name, 1, (JsInvokable) this::isFinite)}
     * with the case label value flowing into the wrap as a single source of truth.
     */
    protected static JsBuiltinMethod method(String methodName, int length, JsCallable delegate) {
        return new JsBuiltinMethod(methodName, length, delegate);
    }

    @Override
    public void removeMember(String name) {
        // Already tombstoned — nothing to do.
        if (_tombstones != null && _tombstones.contains(name)) {
            return;
        }
        boolean inMap = _map != null && _map.containsKey(name);
        boolean intrinsic = hasOwnIntrinsic(name);
        if (!inMap && !intrinsic) {
            return;
        }
        // Configurability check. Per-property attributes in _attrs win when
        // present; otherwise fall back to the intrinsic's getOwnAttrs default.
        // Sealed/frozen flags also imply non-configurable (they populate _attrs
        // on existing keys), but checking the bit directly is cheaper than
        // walking the flag fast-path twice.
        boolean configurable;
        if (inMap && _attrs != null && _attrs.get(name) != null) {
            configurable = (_attrs.get(name) & CONFIGURABLE) != 0;
        } else if (intrinsic) {
            configurable = (getOwnAttrs(name) & CONFIGURABLE) != 0;
        } else {
            configurable = true; // user-set property with no _attrs entry
        }
        if (!configurable) {
            return;
        }
        // Remove the user-set entry (if any).
        if (inMap) {
            _map.remove(name);
            if (_attrs != null) {
                _attrs.remove(name);
            }
        }
        // Tombstone if there is an underlying intrinsic. Without this, after
        // `Math.abs = X; delete Math.abs;` the intrinsic Math.abs would
        // "shine through" and `hasOwnProperty` would incorrectly report it as
        // own. The tombstone shadows the intrinsic; a later assignment
        // (`Math.abs = Y`) clears the tombstone in putMember.
        if (intrinsic) {
            if (_tombstones == null) {
                _tombstones = new java.util.HashSet<>();
            }
            _tombstones.add(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        return _map == null ? Collections.emptyMap() : _map;
    }

    // =================================================================================================
    // Map<String, Object> interface - auto-unwraps values for Java consumers
    // =================================================================================================

    @Override
    public int size() {
        return _map == null ? 0 : _map.size();
    }

    @Override
    public boolean isEmpty() {
        return _map == null || _map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return _map != null && _map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        if (_map == null) return false;
        // Check for unwrapped equivalence
        for (Object v : _map.values()) {
            Object unwrapped = Engine.toJava(v);
            if (Objects.equals(unwrapped, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        // Map.get() - auto-unwrap, own properties only (no prototype chain)
        Object raw = _map != null ? _map.get(key) : null;
        return Engine.toJava(raw);
    }

    @Override
    public Object put(String key, Object value) {
        if (_map == null) {
            _map = new LinkedHashMap<>();
        }
        Object previous = _map.put(key, value);
        return Engine.toJava(previous);
    }

    @Override
    public Object remove(Object key) {
        if (_map == null) return null;
        Object previous = _map.remove(key);
        return Engine.toJava(previous);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (_map == null) {
            _map = new LinkedHashMap<>();
        }
        _map.putAll(m);
    }

    @Override
    public void clear() {
        if (_map != null) {
            _map.clear();
        }
    }

    @Override
    public Set<String> keySet() {
        return _map == null ? Collections.emptySet() : _map.keySet();
    }

    @Override
    public Collection<Object> values() {
        if (_map == null) return Collections.emptyList();
        // Return unwrapped values
        List<Object> unwrapped = new ArrayList<>(_map.size());
        for (Object v : _map.values()) {
            unwrapped.add(Engine.toJava(v));
        }
        return unwrapped;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        if (_map == null) return Collections.emptySet();
        // Return entries with unwrapped values
        return getEntries(_map);
    }

    static Set<Entry<String, Object>> getEntries(Map<String, Object> map) {
        Set<Entry<String, Object>> unwrapped = new LinkedHashSet<>();
        for (Entry<String, Object> entry : map.entrySet()) {
            unwrapped.add(new AbstractMap.SimpleEntry<>(entry.getKey(), Engine.toJava(entry.getValue())));
        }
        return unwrapped;
    }

    // =================================================================================================

    /**
     * Returns an iterable for JS for-in/for-of iteration with KeyValue pairs.
     * This is used internally by JS iteration constructs and by {@code Terms.toIterable},
     * which is the back-end for {@code Object.keys / values / entries / assign} and
     * {@code for...in}. All of those filter by enumerable per spec — only
     * {@code Object.getOwnPropertyNames} / {@code hasOwn} need every own key, and
     * those go through {@link #toMap()} directly.
     */
    public Iterable<KeyValue> jsEntries() {
        return () -> new Iterator<>() {
            final Iterator<Map.Entry<String, Object>> entries = toMap().entrySet().iterator();
            int index = 0;
            Map.Entry<String, Object> peeked = null;

            private boolean advance() {
                while (entries.hasNext()) {
                    Map.Entry<String, Object> e = entries.next();
                    // Fast path: no _attrs map means every key is enumerable.
                    if (_attrs == null || isEnumerable(e.getKey())) {
                        peeked = e;
                        return true;
                    }
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
                Map.Entry<String, Object> entry = peeked;
                peeked = null;
                return new KeyValue(JsObject.this, index++, entry.getKey(), entry.getValue());
            }
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return new JsObject();
    }

    // Use identity-based hashCode/equals to avoid infinite recursion
    // when objects have circular references (e.g., constructor ↔ prototype)
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

}
