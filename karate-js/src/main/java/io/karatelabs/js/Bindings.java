/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
 * Map for JS engine bindings with integrated let/const metadata.
 * <p>
 * Uses {@link BindValue} entries to store both values and binding type info.
 * Implements Map interface with auto-unwrapping for Java consumers.
 */
public class Bindings implements Map<String, Object> {

    private final Map<String, BindValue> map;

    /**
     * Creates empty Bindings.
     */
    public Bindings() {
        this.map = new HashMap<>();
    }

    /**
     * Creates Bindings by copying entries from the given map.
     */
    public Bindings(Map<String, Object> source) {
        this.map = new HashMap<>();
        if (source != null) {
            for (Entry<String, Object> e : source.entrySet()) {
                map.put(e.getKey(), new BindValue(e.getKey(), e.getValue()));
            }
        }
    }

    /**
     * Copy constructor - creates a deep copy of BindValues.
     * Used for loop iterations to capture closure state.
     */
    public Bindings(Bindings other) {
        this.map = new HashMap<>();
        for (Entry<String, BindValue> e : other.map.entrySet()) {
            map.put(e.getKey(), new BindValue(e.getValue()));
        }
    }

    //=== JS-native internal methods (no auto-unwrapping) ===

    /**
     * Raw get - returns JS value without unwrapping.
     */
    public Object getMember(String key) {
        BindValue bv = map.get(key);
        return bv != null ? bv.value : null;
    }

    /**
     * Raw existence check.
     */
    public boolean hasMember(String key) {
        return map.containsKey(key);
    }

    /**
     * Returns the raw (non-unwrapped) value for a key.
     */
    public Object getRaw(String key) {
        return getMember(key);
    }

    /**
     * Raw put with optional binding scope (for let/const declarations).
     */
    public void putMember(String key, Object value, BindScope scope, boolean initialized) {
        BindValue existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            if (scope != null) {
                existing.scope = scope;
                existing.initialized = initialized;
            }
        } else if (scope != null) {
            map.put(key, new BindValue(key, value, scope, initialized));
        } else {
            map.put(key, new BindValue(key, value));
        }
    }

    /**
     * Raw put without binding type (for var declarations).
     */
    public void putMember(String key, Object value) {
        putMember(key, value, null, true);
    }

    /**
     * Get BindValue for a key (for TDZ checks).
     */
    public BindValue getBindValue(String key) {
        return map.get(key);
    }

    /**
     * Clear binding scope for a key (for loop re-declaration).
     */
    public void clearBindingScope(String key) {
        BindValue bv = map.get(key);
        if (bv != null) {
            bv.scope = null;
            bv.initialized = true;
        }
    }

    //=== Level-aware operations for scope management ===

    /**
     * Push a binding at the specified level, linking to any existing binding as shadowed.
     */
    public void pushBinding(String key, Object value, BindScope scope, int level) {
        BindValue existing = map.get(key);
        BindValue newBv = new BindValue(key, value, scope, true, level, existing);
        map.put(key, newBv);
    }

    /**
     * Push a binding at the specified level with explicit initialized state.
     */
    public void pushBinding(String key, Object value, BindScope scope, int level, boolean initialized) {
        BindValue existing = map.get(key);
        BindValue newBv = new BindValue(key, value, scope, initialized, level, existing);
        map.put(key, newBv);
    }

    /**
     * Remove all bindings at the specified level, restoring previous (shadowed) bindings.
     */
    public void popLevel(int level) {
        Iterator<Entry<String, BindValue>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, BindValue> e = it.next();
            BindValue bv = e.getValue();
            if (bv.level == level) {
                if (bv.previous != null) {
                    e.setValue(bv.previous);
                } else {
                    it.remove();
                }
            }
        }
    }

    /**
     * Returns a raw Map view of the bindings without auto-unwrapping.
     */
    public Map<String, Object> getRawMap() {
        Map<String, Object> result = new HashMap<>(map.size());
        for (Entry<String, BindValue> e : map.entrySet()) {
            result.put(e.getKey(), e.getValue().value);
        }
        return result;
    }

    //=== Map interface (auto-unwrapping for Java consumers) ===

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        for (BindValue bv : map.values()) {
            if (Objects.equals(Engine.toJava(bv.value), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String s) {
            return Engine.toJava(getMember(s));
        }
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        Object previous = getMember(key);
        putMember(key, value);
        return Engine.toJava(previous);
    }

    @Override
    public Object remove(Object key) {
        if (key instanceof String s) {
            BindValue removed = map.remove(s);
            return removed != null ? Engine.toJava(removed.value) : null;
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            putMember(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<Object> values() {
        List<Object> list = new ArrayList<>(map.size());
        for (BindValue bv : map.values()) {
            list.add(Engine.toJava(bv.value));
        }
        return list;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Entry<String, BindValue> e : map.entrySet()) {
            result.put(e.getKey(), e.getValue().value);
        }
        return JsObject.getEntries(result);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (BindValue bv : map.values()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(bv.name).append("=").append(bv.value);
        }
        return sb.append("}").toString();
    }

}
