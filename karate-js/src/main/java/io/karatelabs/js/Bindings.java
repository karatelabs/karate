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
 * Host-facing {@link Map} view over an internal {@link BindingsStore}.
 * <p>
 * Auto-unwraps values on read (JS types → Java types via {@link Engine#toJava})
 * and filters out {@code hidden} entries — preserves the contract that
 * {@link Engine#putRootBinding}-injected resources and lazy-cached built-ins
 * stay invisible to host inspection.
 * <p>
 * Engine internals don't go through this class; they read and write the
 * {@link BindingsStore} directly to keep value identity intact.
 */
public class Bindings implements Map<String, Object> {

    private final BindingsStore store;

    Bindings(BindingsStore store) {
        this.store = store;
    }

    BindingsStore getStore() {
        return store;
    }

    @Override
    public int size() {
        return store.getRawMap(false).size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String s)) return false;
        return store.hasMember(s) && !store.isHidden(s);
    }

    @Override
    public boolean containsValue(Object value) {
        for (Map.Entry<String, Object> e : store.getRawMap(false).entrySet()) {
            if (Objects.equals(Engine.toJava(e.getValue()), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String s)) return null;
        if (!store.hasMember(s) || store.isHidden(s)) return null;
        return Engine.toJava(store.getMember(s));
    }

    @Override
    public Object put(String key, Object value) {
        Object previous = store.hasMember(key) && !store.isHidden(key)
                ? Engine.toJava(store.getMember(key))
                : null;
        store.putMember(key, value);
        return previous;
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String s)) return null;
        if (!store.hasMember(s) || store.isHidden(s)) return null;
        Object previous = Engine.toJava(store.getMember(s));
        store.remove(s);
        return previous;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Map.Entry<? extends String, ?> e : m.entrySet()) {
            store.putMember(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public Set<String> keySet() {
        return store.getRawMap(false).keySet();
    }

    @Override
    public Collection<Object> values() {
        Map<String, Object> raw = store.getRawMap(false);
        List<Object> list = new ArrayList<>(raw.size());
        for (Object v : raw.values()) {
            list.add(Engine.toJava(v));
        }
        return list;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        // JsObject.getEntries auto-unwraps each value via Engine.toJava, so
        // entrySet iteration matches the spot-lookup contract on get().
        return JsObject.getEntries(store.getRawMap(false));
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
        return store.getRawMap(false).toString();
    }

}
