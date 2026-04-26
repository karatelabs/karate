/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Raw bindings store — internal storage layer for the JS engine. Holds
 * {@link Slot} entries keyed by name and exposes only raw, identity-
 * preserving operations: no auto-unwrapping of {@link JsValue} / {@link JsFunction},
 * no Map-interface concessions for host iteration. Engine internals (CoreContext,
 * ContextRoot, JsGlobalThis, Interpreter, JsFunctionNode) read and write here.
 * <p>
 * The host-facing view is {@link Bindings}, a Map wrapper that delegates to a
 * store and applies {@link Engine#toJava} on read. Splitting the two surfaces
 * means iterating an internal store cannot accidentally wrap {@code JsFunction}
 * → {@code JsFunctionWrapper} and lose identity for downstream descriptor checks.
 */
class BindingsStore {

    private final Map<String, BindingSlot> map;

    BindingsStore() {
        this.map = new HashMap<>();
    }

    /** Copy entries from a host-supplied Map (evalWith local vars). */
    BindingsStore(Map<String, Object> source) {
        this.map = new HashMap<>();
        if (source != null) {
            for (Map.Entry<String, Object> e : source.entrySet()) {
                map.put(e.getKey(), new BindingSlot(e.getKey(), e.getValue()));
            }
        }
    }

    /**
     * Deep copy — used for loop-iteration snapshots where each iteration
     * captures its own copy of let/const bindings for closure semantics.
     */
    BindingsStore(BindingsStore other) {
        this.map = new HashMap<>();
        for (Map.Entry<String, BindingSlot> e : other.map.entrySet()) {
            map.put(e.getKey(), new BindingSlot(e.getValue()));
        }
    }

    //=== raw read =====================================================================================================

    Object getMember(String key) {
        BindingSlot s = map.get(key);
        return s != null ? s.value : null;
    }

    boolean hasMember(String key) {
        return map.containsKey(key);
    }

    /** Slot accessor for TDZ checks, scope inspection, evalId stamping, etc. */
    BindingSlot getSlot(String key) {
        return map.get(key);
    }

    boolean isHidden(String key) {
        BindingSlot s = map.get(key);
        return s != null && s.hidden;
    }

    /**
     * Snapshot of all entries (visible + hidden) without unwrapping. Used by
     * {@link JsGlobalThis#toMap} where the entire global state needs to be
     * iterated identity-preservingly (subsequent enumerability filtering happens
     * via {@code getOwnAttrs}).
     */
    Map<String, Object> getRawMap() {
        Map<String, Object> result = new HashMap<>(map.size());
        for (Map.Entry<String, BindingSlot> e : map.entrySet()) {
            result.put(e.getKey(), e.getValue().value);
        }
        return result;
    }

    /**
     * Snapshot filtered by the {@code hidden} flag.
     *
     * @param hidden if true, only hidden entries ({@link Engine#getRootBindings});
     *               if false, only visible entries ({@link Engine#getRawBindings}).
     */
    Map<String, Object> getRawMap(boolean hidden) {
        Map<String, Object> result = new HashMap<>(map.size());
        for (Map.Entry<String, BindingSlot> e : map.entrySet()) {
            if (e.getValue().hidden == hidden) {
                result.put(e.getKey(), e.getValue().value);
            }
        }
        return result;
    }

    //=== raw write ====================================================================================================

    /** var-style write — no scope metadata. */
    void putMember(String key, Object value) {
        putMember(key, value, null, true);
    }

    /** Write with optional let/const scope. Updates an existing entry's value
     * (and scope, if provided), or inserts a fresh Slot. */
    void putMember(String key, Object value, BindScope scope, boolean initialized) {
        BindingSlot existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            if (scope != null) {
                existing.scope = scope;
                existing.initialized = initialized;
            }
        } else if (scope != null) {
            map.put(key, new BindingSlot(key, value, scope, initialized));
        } else {
            map.put(key, new BindingSlot(key, value));
        }
    }

    /**
     * Hidden entry — visible to the engine's lookup chain but filtered out
     * of {@link Engine#getBindings()}. Used by {@link Engine#putRootBinding}
     * and by {@link ContextRoot}'s lazy built-in cache.
     */
    void putHidden(String key, Object value) {
        BindingSlot existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            existing.hidden = true;
        } else {
            BindingSlot s = new BindingSlot(key, value);
            s.hidden = true;
            map.put(key, s);
        }
    }

    void remove(String key) {
        map.remove(key);
    }

    /** Reset binding metadata so a let/const re-declaration in the same loop
     * iteration re-initializes cleanly. */
    void clearBindingScope(String key) {
        BindingSlot s = map.get(key);
        if (s != null) {
            s.scope = null;
            s.initialized = true;
        }
    }

    //=== level-aware =================================================================================================

    /** Push a binding at {@code level}, linking any existing binding as the
     * shadowed previous-of-this-name. */
    void pushBinding(String key, Object value, BindScope scope, int level) {
        BindingSlot existing = map.get(key);
        BindingSlot newSlot = new BindingSlot(key, value, scope, true, level, existing);
        map.put(key, newSlot);
    }

    /** Push a binding with explicit initialized state (TDZ-aware paths). */
    void pushBinding(String key, Object value, BindScope scope, int level, boolean initialized) {
        BindingSlot existing = map.get(key);
        BindingSlot newSlot = new BindingSlot(key, value, scope, initialized, level, existing);
        map.put(key, newSlot);
    }

    /** Drop every binding at {@code level}, restoring shadowed predecessors. */
    void popLevel(int level) {
        Iterator<Map.Entry<String, BindingSlot>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BindingSlot> e = it.next();
            BindingSlot s = e.getValue();
            if (s.level == level) {
                if (s.previous != null) {
                    e.setValue(s.previous);
                } else {
                    it.remove();
                }
            }
        }
    }

    //=== bulk =========================================================================================================

    boolean isEmpty() {
        return map.isEmpty();
    }

    /** Iteration over keys (visible + hidden). Used by JsFunctionNode to
     * snapshot let/const bindings at function-creation time. */
    Iterable<String> keys() {
        return map.keySet();
    }

    void clear() {
        map.clear();
    }

}
