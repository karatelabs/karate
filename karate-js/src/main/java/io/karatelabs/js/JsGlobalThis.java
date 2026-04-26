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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level {@code this} — the karate-js stand-in for spec {@code globalThis}.
 * <p>
 * Pure façade: holds no own data. The two existing global stores stay
 * separate (so {@link Engine#putRootBinding} can keep its hidden-from-host
 * contract), but JsGlobalThis presents them as one global object:
 * <ul>
 *   <li>{@link Engine#bindings} — user-visible store. Reads observed first;
 *       writes always land here. Same instance as the script-level
 *       {@code _bindings}, so {@code this.foo = 1; foo} and
 *       {@code foo = 1; this.foo} go through one store.</li>
 *   <li>{@link ContextRoot#_bindings} — hidden store. Lazy-cached built-ins
 *       ({@code initGlobal}) and {@link Engine#putRootBinding} land here.
 *       Reads observed only after a miss in Engine.bindings; writes never
 *       touch it (would defeat the hidden-from-host contract).</li>
 * </ul>
 * Tombstones (inherited from {@link JsObject}) make {@code delete this.Math}
 * stick — without one, the next read would re-trigger {@code initGlobal} and
 * resurrect the global.
 */
final class JsGlobalThis extends JsObject {

    private final ContextRoot root;

    /**
     * Names whose attrs were explicitly written by {@code defineProperty} (or
     * accessed-once internal calls). The inherited {@code _attrs} map drops
     * entries whose value equals {@link JsObject#ATTRS_DEFAULT} (W|E|C) — for
     * a normal JsObject that's the right call (default == fresh assignment),
     * but for globalThis the global default ({@code W | C}) differs from the
     * data-property default, so we need a separate signal to know whether to
     * apply the global-default attrs or the stored attrs.
     */
    private java.util.Set<String> _explicit;

    JsGlobalThis(ContextRoot root) {
        this.root = root;
    }

    /** User-visible bindings (Engine.bindings); script-level _bindings. */
    private Bindings userBindings() {
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
        Bindings user = userBindings();
        if (user.hasMember(name)) return user.getMember(name);
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
                && (_explicit != null && _explicit.contains(name))) {
            return;
        }
        // Writing reanimates a tombstone.
        clearTombstone(name);
        // Always write to the user-visible store, never the root's hidden one
        // — `this.foo = 1` is observable; it has to land where `foo` (free
        // variable) and `Engine.getBindings().get("foo")` will see it.
        userBindings().putMember(name, value);
    }

    @Override
    void defineOwn(String name, Object value, byte attrs) {
        // Object.defineProperty(globalThis, name, ...) routes here. Value
        // goes to userBindings (so plain `name` lookup sees it); attrs go
        // to the inherited _attrs map AND _explicit so getOwnAttrs honors
        // the explicit attrs even when they happen to equal ATTRS_DEFAULT
        // (which setAttrs would otherwise prune as redundant).
        clearTombstone(name);
        userBindings().putMember(name, value);
        setAttrs(name, attrs);
        if (_explicit == null) _explicit = new java.util.HashSet<>();
        _explicit.add(name);
    }

    @Override
    public void removeMember(String name) {
        Bindings user = userBindings();
        if (user.hasMember(name)) user.remove(name);
        if (root._bindings != null) root._bindings.remove(name);
        if (_explicit != null) _explicit.remove(name);
        // super applies the configurability check + tombstones the slot so
        // the next read doesn't re-resurrect a built-in via initGlobal.
        super.removeMember(name);
    }

    @Override
    public boolean isOwnProperty(String name) {
        if (isTombstoned(name)) return false;
        return userBindings().hasMember(name) || root.hasKey(name);
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        if (isTombstoned(name)) return false;
        return userBindings().hasMember(name) || root.hasKey(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        // defineProperty on globalThis is the only path that should bypass
        // the global-default attrs. _explicit tracks those explicitly even
        // when their stored byte equals ATTRS_DEFAULT (in which case _attrs
        // wouldn't carry them).
        if (_explicit != null && _explicit.contains(name)) {
            return super.getOwnAttrs(name);
        }
        // Per ES spec §10.2 / §19.1: every non-essential global is
        // { writable: true, enumerable: false, configurable: true }.
        // (Essentials like NaN / Infinity / undefined are non-writable but
        // not distinguished here — fix when test262 surfaces a fail.)
        if (userBindings().hasMember(name) || root.hasKey(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    public Map<String, Object> toMap() {
        Bindings user = userBindings();
        if ((root._bindings == null || root._bindings.isEmpty()) && user.isEmpty()) {
            return Collections.emptyMap();
        }
        // Merged view of raw values: user bindings win on key collision (same
        // priority as getMember). Use getRawMap (NOT entrySet) — entrySet
        // auto-unwraps via Engine.toJava, which wraps JsFunction in
        // JsFunctionWrapper and breaks identity for descriptor lookups.
        // Built-ins not yet lazy-cached aren't here; Object.keys(globalThis)
        // only sees realized entries.
        Map<String, Object> merged = new LinkedHashMap<>();
        if (root._bindings != null) merged.putAll(root._bindings.getRawMap());
        merged.putAll(user.getRawMap());
        return merged;
    }

    // Map<String, Object> overrides — JsObject's defaults check _map (always
    // empty here). Forward to the unified view so Java consumers
    // (e.g. iteration over globalThis) see the real state.

    @Override
    public int size() {
        return toMap().size();
    }

    @Override
    public boolean isEmpty() {
        return userBindings().isEmpty() && (root._bindings == null || root._bindings.isEmpty());
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String s && !isTombstoned(s)
                && (userBindings().hasMember(s) || root.hasKey(s));
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String s) || isTombstoned(s)) return null;
        Bindings user = userBindings();
        if (user.hasMember(s)) return user.getMember(s);
        return root.hasKey(s) ? root.get(s) : null;
    }

}
