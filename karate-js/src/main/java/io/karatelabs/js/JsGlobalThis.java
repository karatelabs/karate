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
import java.util.Map;

/**
 * Top-level {@code this} — the karate-js stand-in for spec {@code globalThis}.
 * <p>
 * Pure façade over the engine's single {@link Bindings} store. Reads / writes
 * go through that one store; the {@code hidden} flag on individual entries
 * preserves the contract that {@link Engine#putRootBinding} injections and
 * lazy-cached built-ins are invisible to {@link Engine#getBindings()}, but
 * fully visible here (so {@code Object.getOwnPropertyDescriptor(this, "Math")}
 * etc. work).
 * <p>
 * Tombstones (inherited from {@link JsObject}) make {@code delete this.Math}
 * stick — without one, the next read would re-trigger {@code initGlobal} via
 * {@code root.get(name)} and resurrect the global.
 */
final class JsGlobalThis extends JsObject {

    private final ContextRoot root;

    /**
     * Names whose attrs were explicitly written by {@code defineProperty} (or
     * accessed-once internal calls). The inherited {@code _attrs} map prunes
     * entries equal to {@link JsObject#ATTRS_DEFAULT} (W|E|C) — fine for a
     * normal JsObject (default == fresh assignment), but globalThis's default
     * ({@code W | C}, no E) differs, so we need a separate signal to know
     * whether to apply the global default or honor the stored attrs.
     */
    private java.util.Set<String> _explicit;

    JsGlobalThis(ContextRoot root) {
        this.root = root;
    }

    private Bindings bindings() {
        return ((Engine) root.getEngine()).bindings;
    }

    @Override
    public Object getMember(String name) {
        if (isTombstoned(name)) {
            // Skip own resolution but still walk the prototype chain — matches
            // JsObject.getMember's tombstone branch.
            ObjectLike proto = getPrototype();
            return proto != null ? proto.getMember(name) : null;
        }
        if ("__proto__".equals(name)) return getPrototype();
        Bindings b = bindings();
        if (b.hasMember(name)) return b.getMember(name);
        // Triggers lazy initGlobal for built-ins and caches them as hidden.
        if (root.hasKey(name)) return root.get(name);
        // Walk the prototype chain — JsObject defaults to JsObjectPrototype.INSTANCE,
        // which is where `propertyIsEnumerable`, `hasOwnProperty`, `toString` etc. live.
        ObjectLike proto = getPrototype();
        return proto != null ? proto.getMember(name) : null;
    }

    @Override
    public void putMember(String name, Object value) {
        if ("__proto__".equals(name)) {
            super.putMember(name, value);
            return;
        }
        // Honor writable=false from a previous defineProperty call (lenient
        // mode silently drops the write; strict-mode TypeError flip is a
        // separate concern). Without this, propertyHelper's isWritable probe
        // reports writable=true on a defined-as-non-writable global.
        if ((getOwnAttrs(name) & WRITABLE) == 0
                && bindings().hasMember(name)) {
            return;
        }
        // Writing reanimates a tombstone.
        clearTombstone(name);
        // `this.foo = 1` is observable; it has to land where `foo` (free
        // variable) and `Engine.getBindings().get("foo")` will see it. The
        // unified store handles that — we just write a non-hidden entry.
        bindings().putMember(name, value);
    }

    @Override
    void defineOwn(String name, Object value, byte attrs) {
        // Object.defineProperty(globalThis, name, ...) routes here. Value goes
        // to the unified bindings (so plain `name` lookup sees it); attrs go
        // to the inherited _attrs map AND _explicit so getOwnAttrs honors the
        // explicit attrs even when they happen to equal ATTRS_DEFAULT
        // (which setAttrs would otherwise prune as redundant).
        clearTombstone(name);
        bindings().putMember(name, value);
        setAttrs(name, attrs);
        if (_explicit == null) _explicit = new java.util.HashSet<>();
        _explicit.add(name);
    }

    @Override
    public void removeMember(String name) {
        bindings().remove(name);
        if (_explicit != null) _explicit.remove(name);
        // super applies the configurability check + tombstones the slot so
        // the next read doesn't re-resurrect a built-in via initGlobal.
        super.removeMember(name);
    }

    @Override
    public boolean isOwnProperty(String name) {
        if (isTombstoned(name)) return false;
        return bindings().hasMember(name) || root.hasKey(name);
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        if (isTombstoned(name)) return false;
        return bindings().hasMember(name) || root.hasKey(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        // defineProperty on globalThis is the only path that should bypass
        // the global-default attrs. _explicit tracks those explicitly even
        // when their stored byte equals ATTRS_DEFAULT (in which case _attrs
        // wouldn't carry them, since setAttrs prunes the default).
        if (_explicit != null && _explicit.contains(name)) {
            return super.getOwnAttrs(name);
        }
        // Per ES spec §10.2 / §19.1: every non-essential global is
        // { writable: true, enumerable: false, configurable: true }.
        // (Essentials like NaN / Infinity / undefined are non-writable but
        // not distinguished here — fix when test262 surfaces a fail.)
        if (bindings().hasMember(name) || root.hasKey(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    public Map<String, Object> toMap() {
        // Raw view of the unified store — both visible and hidden entries.
        // Built-ins not yet lazy-cached aren't here; Object.keys(globalThis)
        // only sees realized entries (and gets filtered down further by
        // enumerability via getOwnAttrs above).
        Map<String, Object> raw = bindings().getRawMap();
        return raw.isEmpty() ? Collections.emptyMap() : raw;
    }

    // Map<String, Object> overrides — JsObject's defaults check _map (always
    // empty here). Forward to the unified store so Java consumers
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
        return key instanceof String s && !isTombstoned(s)
                && (bindings().hasMember(s) || root.hasKey(s));
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String s) || isTombstoned(s)) return null;
        Bindings b = bindings();
        if (b.hasMember(s)) return b.getMember(s);
        return root.hasKey(s) ? root.get(s) : null;
    }

}
