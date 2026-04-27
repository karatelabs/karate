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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Top-level {@code this} — the karate-js stand-in for spec {@code globalThis}.
 * <p>
 * Pure façade over the engine's single {@link BindingsStore}. Every observable
 * piece of state — values, attribute bytes, tombstones — lives on the
 * {@link BindingSlot} for that name; {@link JsObject#props} is unused (the
 * inherited {@code JsObject} machinery is only reached for {@code __proto__}
 * and the {@link Map} interface defaults that we override below).
 * <p>
 * The {@code hidden} flag on individual entries preserves the contract that
 * {@link Engine#putRootBinding} injections and lazy-cached built-ins are
 * invisible to {@link Engine#getBindings()}, but fully visible here (so
 * {@code Object.getOwnPropertyDescriptor(this, "Math")} etc. work).
 * <p>
 * Tombstones (on the {@link BindingSlot}) make {@code delete this.Math}
 * stick — without one, the next read would re-trigger {@code initGlobal} via
 * {@code root.get(name)} and resurrect the global.
 */
final class JsGlobalThis extends JsObject {

    private final ContextRoot root;

    JsGlobalThis(ContextRoot root) {
        this.root = root;
    }

    private BindingsStore bindings() {
        return root.getEngine().bindings;
    }

    @Override
    public Object getMember(String name) {
        if ("__proto__".equals(name)) return getPrototype();
        BindingsStore b = bindings();
        if (b.isTombstoned(name)) {
            ObjectLike proto = getPrototype();
            return proto != null ? proto.getMember(name) : null;
        }
        if (b.hasMember(name)) return b.getMember(name);
        // Triggers lazy initGlobal for built-ins and caches them as hidden.
        if (root.hasKey(name)) return root.get(name);
        // Accessor descriptors installed via Object.defineProperty(this, …)
        // land on {@link JsObject#props} (BindingSlot is data-only) — consult
        // super before walking the prototype chain so they're reachable.
        Object own = super.getMember(name);
        if (own != null) return own;
        ObjectLike proto = getPrototype();
        return proto != null ? proto.getMember(name) : null;
    }

    @Override
    public Object getMember(String name, Object receiver, CoreContext ctx) {
        if ("__proto__".equals(name)) return getPrototype();
        BindingsStore b = bindings();
        if (b.isTombstoned(name)) {
            ObjectLike proto = getPrototype();
            return proto != null ? proto.getMember(name, receiver, ctx) : null;
        }
        if (b.hasMember(name)) return b.getMember(name);
        if (root.hasKey(name)) return root.get(name);
        Object own = super.getMember(name, receiver, ctx);
        if (own != null) return own;
        ObjectLike proto = getPrototype();
        return proto != null ? proto.getMember(name, receiver, ctx) : null;
    }

    @Override
    public boolean isOwnProperty(String name) {
        BindingsStore b = bindings();
        if (b.isTombstoned(name)) return false;
        if (b.hasMember(name) || root.hasKey(name)) return true;
        // Accessor descriptors installed on {@link JsObject#props}.
        return super.isOwnProperty(name);
    }

    @Override
    public void putMember(String name, Object value) {
        if ("__proto__".equals(name)) {
            super.putMember(name, value);
            return;
        }
        // Honor writable=false from a previous defineProperty call (lenient
        // mode silently drops the write; strict-mode TypeError flip is a
        // separate concern).
        BindingSlot s = bindings().getSlot(name);
        if (s != null && s.attrsExplicit && (s.attrs & WRITABLE) == 0) {
            return;
        }
        bindings().clearTombstone(name);
        bindings().putMember(name, value);
    }

    @Override
    void defineOwn(String name, Object value, byte attrs) {
        // Object.defineProperty(globalThis, name, ...) routes here. Value +
        // attrs both land on the BindingSlot — no separate state to keep in
        // sync. attrsExplicit drives getOwnAttrs to honor the byte verbatim
        // even when it equals ATTRS_DEFAULT (which is observably distinct
        // from the global default of W|C).
        BindingsStore b = bindings();
        b.clearTombstone(name);
        b.putMember(name, value);
        BindingSlot s = b.getSlot(name);
        s.attrs = attrs;
        s.attrsExplicit = true;
    }

    @Override
    public void removeMember(String name) {
        BindingsStore b = bindings();
        BindingSlot s = b.getSlot(name);
        if (s == null) {
            // Possibly a built-in not yet realized — tombstone in case the
            // next read would lazy-resurrect via initGlobal.
            if (root.hasKey(name)) {
                b.tombstone(name);
            }
            return;
        }
        // Configurability check: defineProperty-style descriptors block delete
        // when configurable=false; default attrs (W|C) allow it.
        byte attrs = s.attrsExplicit ? s.attrs : (byte) (WRITABLE | CONFIGURABLE);
        if ((attrs & CONFIGURABLE) == 0) {
            return;
        }
        if (root.hasKey(name)) {
            // Built-in shadow: tombstone in place so the next read doesn't
            // re-resurrect via initGlobal.
            b.tombstone(name);
        } else {
            b.remove(name);
        }
    }

    // hasOwnIntrinsic is unreachable on globalThis: putMember / removeMember /
    // isOwnProperty are all overridden to consult bindings directly, and the
    // INTRINSIC_PROBE_NAMES discovery probe in JsObjectConstructor.getOwn-
    // PropertyDescriptors checks names ("length"/"name"/"prototype"/"constructor")
    // that are never bindings on globalThis. Inherits the JsObject default
    // (resolveOwnIntrinsic-derived) which returns false here — same answer.

    @Override
    public byte getOwnAttrs(String name) {
        BindingSlot s = bindings().getSlot(name);
        if (s != null && !s.tombstoned && s.attrsExplicit) {
            return s.attrs;
        }
        // Per ES spec §10.2 / §19.1: every non-essential global is
        // { writable: true, enumerable: false, configurable: true }.
        // (Essentials like NaN / Infinity / undefined are non-writable but
        // not distinguished here — fix when test262 surfaces a fail.)
        if ((s != null && !s.tombstoned) || root.hasKey(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    public Iterable<KeyValue> jsEntries(CoreContext ctx) {
        // BindingSlot is data-only — no accessor descriptors on globalThis
        // (yet). Same iteration regardless of ctx.
        return jsEntries();
    }

    /**
     * For-in / Object.keys / etc. iterate the unified bindings store, not
     * {@link JsObject#props} (which is unused for globalThis). Filters via
     * {@link #isEnumerable} so non-explicit globals (default {@code W | C})
     * are skipped.
     */
    @Override
    public Iterable<KeyValue> jsEntries() {
        return () -> new Iterator<>() {
            final Iterator<Map.Entry<String, Object>> source =
                    bindings().getRawMap().entrySet().iterator();
            int index = 0;
            Map.Entry<String, Object> peeked = null;

            private boolean advance() {
                while (source.hasNext()) {
                    Map.Entry<String, Object> e = source.next();
                    if (bindings().isTombstoned(e.getKey())) continue;
                    if (isEnumerable(e.getKey())) {
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
                return new KeyValue(JsGlobalThis.this, index++, entry.getKey(), entry.getValue());
            }
        };
    }

    @Override
    public Map<String, Object> toMap() {
        // Raw view of the unified store — both visible and hidden entries,
        // identity preserved (no JsFunction → JsFunctionWrapper). Built-ins
        // not yet lazy-cached aren't here; Object.keys(globalThis) only sees
        // realized entries (and gets filtered down further by enumerability
        // via getOwnAttrs above). Merge in any JsObject.props entries — those
        // hold accessor descriptors installed via Object.defineProperty(this, …)
        // (BindingSlot is data-only).
        Map<String, Object> raw = bindings().getRawMap();
        Map<String, Object> propsMap = super.toMap();
        if (propsMap.isEmpty()) {
            return raw.isEmpty() ? Collections.emptyMap() : raw;
        }
        Map<String, Object> merged = new LinkedHashMap<>(raw.size() + propsMap.size());
        merged.putAll(raw);
        merged.putAll(propsMap);
        return merged;
    }

    // Map<String, Object> overrides — JsObject's defaults check `props` (always
    // empty for globalThis). Forward to the unified store so Java consumers
    // (e.g. iteration over globalThis) see the real state.

    @Override
    public int size() {
        return toMap().size();
    }

    @Override
    public boolean isEmpty() {
        return toMap().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String s)) return false;
        BindingsStore b = bindings();
        if (b.isTombstoned(s)) return false;
        return b.hasMember(s) || root.hasKey(s);
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String s)) return null;
        BindingsStore b = bindings();
        if (b.isTombstoned(s)) return null;
        if (b.hasMember(s)) return b.getMember(s);
        return root.hasKey(s) ? root.get(s) : null;
    }

}
