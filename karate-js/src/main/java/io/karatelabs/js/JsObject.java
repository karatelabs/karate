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

    private Map<String, Object> _map;
    private ObjectLike __proto__;
    // Object-wide extensibility flags. Per-property attributes live in {@code _attrs}.
    // The per-object flags double as fast-path early exits in putMember / removeMember
    // so frozen objects don't have to consult _attrs on every write.
    private boolean nonExtensible;
    private boolean sealed;
    private boolean frozen;
    private Map<String, Byte> _attrs;

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
        if (nonExtensible && !keyExists) {
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
        if (_map == null) {
            _map = new LinkedHashMap<>();
        }
        _map.put(name, value);
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

    boolean isEnumerable(String name) {
        return (getAttrs(name) & ENUMERABLE) != 0;
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

    @Override
    public void removeMember(String name) {
        if (_map == null || !_map.containsKey(name)) {
            return;
        }
        // Per-property configurable=false: silently fail. Spec says throw under
        // strict; we're non-strict by default. Sealed/frozen flags imply this
        // (they set configurable=false on every key) but checking the bit
        // directly is cheaper than walking _attrs after a flag fast-path miss.
        if (_attrs != null) {
            Byte b = _attrs.get(name);
            if (b != null && (b & CONFIGURABLE) == 0) {
                return;
            }
        }
        _map.remove(name);
        if (_attrs != null) {
            _attrs.remove(name);
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
