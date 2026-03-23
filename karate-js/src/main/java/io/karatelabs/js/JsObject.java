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

    private Map<String, Object> _map;
    private ObjectLike __proto__;

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
     * Sets the prototype (__proto__) of this object.
     */
    public void setPrototype(ObjectLike proto) {
        this.__proto__ = proto;
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
        if (_map == null) {
            _map = new LinkedHashMap<>();
        }
        _map.put(name, value);
    }

    @Override
    public void removeMember(String name) {
        if (_map != null) {
            _map.remove(name);
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
     * This is used internally by JS iteration constructs.
     */
    public Iterable<KeyValue> jsEntries() {
        return () -> new Iterator<>() {
            final Iterator<Map.Entry<String, Object>> entries = toMap().entrySet().iterator();
            int index = 0;

            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public KeyValue next() {
                Map.Entry<String, Object> entry = entries.next();
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
