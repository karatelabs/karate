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
    /** When true, structural mutation (push/pop/putNew/remove) is a no-op.
     *  Used by closure-captured stores: the function-creation snapshot freezes
     *  the set of names; cell value updates still flow through (mutation of
     *  {@code BindingSlot.value} is not gated). See
     *  {@link #captured(java.util.Map)}. */
    private final boolean immutable;
    /** Head of the intrusive per-level slot chain, indexed by scope level.
     *  {@link #pushBinding} links each level>0 slot here so {@link #popLevel}
     *  walks only that level's slots — the whole-map iteration it replaces
     *  was a top CPU + allocation line on loop-heavy profiles. Lazily
     *  created; level 0 (function/global lifetime) is never popped and never
     *  chained. */
    private BindingSlot[] levelHeads;

    BindingsStore() {
        this.map = new HashMap<>();
        this.immutable = false;
    }

    /** Copy entries from a host-supplied Map (evalWith local vars). */
    BindingsStore(Map<String, Object> source) {
        this.map = new HashMap<>();
        this.immutable = false;
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
        this.immutable = false;
        for (Map.Entry<String, BindingSlot> e : other.map.entrySet()) {
            BindingSlot copy = new BindingSlot(e.getValue());
            map.put(e.getKey(), copy);
            // Rebuild the level chain for the copied top slot. Shadowed
            // `previous` slots are shared by reference with the source store
            // and must NOT be re-linked here — their nextInLevel belongs to
            // the source's chains.
            linkLevel(copy, copy.level);
        }
    }

    private BindingsStore(Map<String, BindingSlot> map, boolean immutable) {
        this.map = map;
        this.immutable = immutable;
    }

    /**
     * Closure-capture snapshot: a {@link BindingsStore} that wraps a
     * pre-built name → {@link BindingSlot} map and refuses structural
     * mutation. Cell-value updates via existing-key {@code putMember}
     * still propagate to the original Slots — sibling closures sharing
     * the snapshot see writes (the spec-correct closure semantic).
     */
    static BindingsStore captured(Map<String, BindingSlot> snapshot) {
        return new BindingsStore(snapshot, true);
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
     * (and scope, if provided), or inserts a fresh Slot.
     *
     * <p>On an immutable (closure-captured) store: existing-key value updates
     * still propagate (sibling closures must observe writes); structural
     * adds are silently ignored. */
    void putMember(String key, Object value, BindScope scope, boolean initialized) {
        BindingSlot existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            if (scope != null) {
                existing.scope = scope;
                existing.initialized = initialized;
            }
        } else if (immutable) {
            return;
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
        if (immutable) return;
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
        if (immutable) return;
        map.remove(key);
    }

    /** Tombstone a binding so {@link JsGlobalThis} reads skip it (preventing
     *  lazy re-resurrection of a built-in via {@code initGlobal} after
     *  {@code delete globalThis.X}). Creates an empty slot if absent so the
     *  tombstone exists even when the underlying built-in was never realized. */
    void tombstone(String key) {
        if (immutable) return;
        BindingSlot s = map.get(key);
        if (s == null) {
            s = new BindingSlot(key);
            map.put(key, s);
        }
        s.value = null;
        s.tombstoned = true;
    }

    /** True iff {@code key} is present and tombstoned. */
    boolean isTombstoned(String key) {
        BindingSlot s = map.get(key);
        return s != null && s.tombstoned;
    }

    /** Clear any tombstone on {@code key}. No-op if absent or not tombstoned. */
    void clearTombstone(String key) {
        BindingSlot s = map.get(key);
        if (s != null && s.tombstoned) {
            s.tombstoned = false;
        }
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
     * shadowed previous-of-this-name. No-op on immutable stores. */
    void pushBinding(String key, Object value, BindScope scope, int level) {
        if (immutable) return;
        BindingSlot existing = map.get(key);
        BindingSlot newSlot = new BindingSlot(key, value, scope, true, level, existing);
        map.put(key, newSlot);
        linkLevel(newSlot, level);
    }

    /** Push a binding with explicit initialized state (TDZ-aware paths). */
    void pushBinding(String key, Object value, BindScope scope, int level, boolean initialized) {
        if (immutable) return;
        BindingSlot existing = map.get(key);
        BindingSlot newSlot = new BindingSlot(key, value, scope, initialized, level, existing);
        map.put(key, newSlot);
        linkLevel(newSlot, level);
    }

    /** Link a slot into its level's chain. Level 0 is function/global
     *  lifetime — never popped, never chained. */
    private void linkLevel(BindingSlot s, int level) {
        if (level <= 0) {
            return;
        }
        if (levelHeads == null) {
            levelHeads = new BindingSlot[Math.max(8, level + 1)];
        } else if (level >= levelHeads.length) {
            levelHeads = java.util.Arrays.copyOf(levelHeads, Math.max(levelHeads.length * 2, level + 1));
        }
        s.nextInLevel = levelHeads[level];
        levelHeads[level] = s;
    }

    /** Drop every binding at {@code level}, restoring shadowed predecessors.
     *
     * <p>Walks only the level's intrusive chain — allocation-free and
     * O(bindings at this level), replacing the historical whole-map
     * iteration. Semantics are preserved exactly, including the map-walk's
     * one-visit-per-name behavior: a restored shadow whose own level equals
     * the popped level (same-level re-push, the loop-iteration
     * re-declaration path in {@code CoreContext.declare}) is deferred to the
     * next pop of that level via {@link BindingSlot#deferredPop}, which is
     * when the old code would have seen it as the map's entry. */
    void popLevel(int level) {
        if (immutable || levelHeads == null || level >= levelHeads.length) {
            return;
        }
        BindingSlot s = levelHeads[level];
        levelHeads[level] = null;
        while (s != null) {
            BindingSlot next = s.nextInLevel;
            s.nextInLevel = null;
            if (s.deferredPop) {
                s.deferredPop = false;
                s.nextInLevel = levelHeads[level];
                levelHeads[level] = s;
            } else if (map.get(s.name) == s) {
                if (s.previous != null) {
                    map.put(s.name, s.previous);
                    if (s.previous.level == level) {
                        s.previous.deferredPop = true;
                    }
                } else {
                    map.remove(s.name);
                }
            }
            // else: stale — removed or replaced since push; nothing to do
            s = next;
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
        levelHeads = null;
    }

}
