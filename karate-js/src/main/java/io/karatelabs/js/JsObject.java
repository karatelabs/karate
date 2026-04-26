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
 * JavaScript Object — own properties live in a single {@code props}
 * {@code Map<String, PropertySlot>} where each {@link Slot} carries value, attribute
 * byte (writable / enumerable / configurable), and tombstone flag.
 * <p>
 * Property lookup order:
 * <ol>
 *   <li>Own properties ({@code props}, skipping tombstoned slots)</li>
 *   <li>Subclass intrinsic resolution ({@link #hasOwnIntrinsic} / virtual
 *       {@link #getMember} switches)</li>
 *   <li>Prototype chain ({@code __proto__})</li>
 * </ol>
 */
class JsObject implements ObjectLike, Map<String, Object> {

    /** Re-exported from {@link Slot} for callers that historically referenced
     *  {@code JsObject.WRITABLE}. */
    static final byte WRITABLE = PropertySlot.WRITABLE;
    static final byte ENUMERABLE = PropertySlot.ENUMERABLE;
    static final byte CONFIGURABLE = PropertySlot.CONFIGURABLE;
    static final byte ATTRS_DEFAULT = PropertySlot.ATTRS_DEFAULT;

    /**
     * JVM-wide singletons that must reset their per-Engine mutable state when a
     * fresh {@link Engine} is constructed. Built-in constructor instances
     * ({@code JsNumberConstructor.INSTANCE}, {@code JsObjectConstructor.INSTANCE},
     * etc.) call {@link #registerForEngineReset()} to enroll themselves; the
     * {@link Engine} constructor walks this list and invokes
     * {@link #clearEngineState()} on each entry.
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

    /**
     * Own-property storage. One {@link Slot} per name carries the value plus
     * its attribute byte and tombstone flag. Lazily allocated — empty objects
     * pay no map overhead.
     */
    private Map<String, PropertySlot> props;
    private ObjectLike __proto__;
    /** Object-wide extensibility flags. Per-property attributes live on each
     *  Slot's {@code attrs} byte. The per-object flags double as fast-path
     *  early exits in {@link #putMember} / {@link #removeMember} so frozen
     *  objects don't have to consult per-Slot bits on every write. */
    private boolean nonExtensible;
    private boolean sealed;
    private boolean frozen;

    JsObject() {
        this.__proto__ = JsObjectPrototype.INSTANCE;
    }

    /** Construct from an initial {@code Map<String, Object>} of values — each
     *  entry is wrapped in a fresh {@link Slot} with default attributes. The
     *  source map is copied; subsequent mutations to it do NOT affect this
     *  object. */
    JsObject(Map<String, Object> seed) {
        this.__proto__ = JsObjectPrototype.INSTANCE;
        if (seed != null && !seed.isEmpty()) {
            this.props = new LinkedHashMap<>(seed.size());
            for (Map.Entry<String, Object> e : seed.entrySet()) {
                this.props.put(e.getKey(), new DataSlot(e.getKey(), e.getValue()));
            }
        }
    }

    /** Subclass constructor for a custom prototype. */
    protected JsObject(Map<String, Object> seed, ObjectLike proto) {
        this(seed);
        this.__proto__ = proto;
    }

    public ObjectLike getPrototype() {
        return __proto__;
    }

    public void setPrototype(ObjectLike proto) {
        this.__proto__ = proto;
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
     * True iff this object exposes {@code name} as an "own" intrinsic property
     * (e.g. {@code Date.prototype}, {@code Date.now}, {@code Date.UTC}). Default:
     * false — only user-added entries in {@link #props} count as own.
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
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) {
            // Tombstoned: a previously-existing intrinsic was deleted. Skip
            // both the slot's stale value and subclass intrinsic resolution
            // (which can't see this map); proceed directly to the prototype
            // chain so e.g. Math.abs.constructor still resolves after
            // `delete Math.abs.constructor`.
            if (s.tombstoned) {
                return __proto__ != null ? __proto__.getMember(name) : null;
            }
            return s.value;
        }
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    /**
     * True iff {@code name} is an own property on this object — covers
     * non-tombstoned own slots and intrinsic properties declared by subclasses
     * (via {@link #hasOwnIntrinsic}). Use this for
     * {@code Object.getOwnPropertyDescriptor} / {@code hasOwn} / {@code in}
     * semantics; raw {@code props.containsKey} would include tombstones.
     */
    public boolean isOwnProperty(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) {
            return !s.tombstoned;
        }
        return hasOwnIntrinsic(name);
    }

    boolean isTombstoned(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s != null && s.tombstoned;
    }

    /** Removes the tombstone for {@code name} if any. Subclasses use this when
     *  a write reanimates a previously-deleted entry. */
    void clearTombstone(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null && s.tombstoned) {
            props.remove(name);
        }
    }

    /** True iff {@code name} has a non-tombstoned own slot (excludes intrinsics). */
    boolean ownContainsKey(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s != null && !s.tombstoned;
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
        PropertySlot s = props == null ? null : props.get(name);
        boolean keyExists = s != null && !s.tombstoned;
        // Intrinsic-backed properties (built-in length / name / Math.E …) need
        // to honor their spec attributes on [[Set]] too. Treat them as "exists"
        // for extensibility purposes (writes to them aren't creating a new key
        // from the perspective of nonExtensible) but consult getOwnAttrs for
        // the writable check. Tombstoned slots are treated as missing —
        // putting them back is allowed if the object is extensible.
        boolean intrinsic = !keyExists && hasOwnIntrinsic(name);
        if (nonExtensible && !keyExists && !intrinsic) {
            return;
        }
        // Per-property writable=false: silently ignore the [[Set]]. Spec says
        // throw under strict; we're non-strict by default.
        if (keyExists && !s.isWritable()) {
            return;
        }
        if (intrinsic && (getOwnAttrs(name) & WRITABLE) == 0) {
            return;
        }
        if (s == null) {
            if (props == null) {
                props = new LinkedHashMap<>();
            }
            props.put(name, new DataSlot(name, value));
        } else {
            // Reuse the slot — clears any tombstone, preserves attrs.
            s.value = value;
            s.tombstoned = false;
        }
    }

    /**
     * Returns the attribute byte for {@code name}: bit-OR of {@link #WRITABLE},
     * {@link #ENUMERABLE}, {@link #CONFIGURABLE}. Defaults to all-true when the
     * key has never been touched by {@code defineProperty} / {@code seal} /
     * {@code freeze}.
     */
    byte getAttrs(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s == null ? ATTRS_DEFAULT : s.attrs;
    }

    /**
     * Spec-correct attribute byte for an intrinsic own property. Default reads
     * the slot's attrs via {@link #getAttrs(String)}. Subclasses (especially
     * built-in constructors / prototypes / the {@link JsFunction} hierarchy)
     * override to return tighter attributes for intrinsic members declared via
     * {@link #hasOwnIntrinsic(String)} — e.g. built-in method properties default
     * to {@code {writable: true, enumerable: false, configurable: true}}; built-in
     * constants default to all-false.
     * <p>
     * The owner of this method is also responsible for declaring the same key
     * via {@code hasOwnIntrinsic} — otherwise {@code getOwnPropertyDescriptor}
     * won't reach this lookup at all.
     */
    public byte getOwnAttrs(String name) {
        return getAttrs(name);
    }

    /** True iff explicit per-property attrs deviating from the all-true default
     *  have been recorded for {@code name} (i.e. {@code defineProperty} /
     *  {@code seal} / {@code freeze} touched it). Subclasses use this to decide
     *  whether to honor the stored attrs vs. apply a class-default. */
    boolean hasExplicitAttrs(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        return s != null && s.attrs != ATTRS_DEFAULT;
    }

    /** Stores the attribute byte for {@code name} on its slot. Creates a slot
     *  if absent (rare — defineProperty path uses {@link #defineOwn} which sets
     *  both value and attrs). */
    void setAttrs(String name, byte attrs) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s == null) {
            if (attrs == ATTRS_DEFAULT) return;
            if (props == null) {
                props = new LinkedHashMap<>();
            }
            s = new DataSlot(name);
            props.put(name, s);
        }
        s.attrs = attrs;
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
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        PropertySlot s = props.get(name);
        if (s == null) {
            s = new DataSlot(name);
            props.put(name, s);
        }
        s.value = value;
        s.attrs = attrs;
        s.tombstoned = false;
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
        // is the fast-path early exit on writes/removes; clearing the bit on
        // each slot is for the attribute readers.
        if (props != null) {
            for (PropertySlot s : props.values()) {
                if (!s.tombstoned) {
                    s.attrs &= ~CONFIGURABLE;
                }
            }
        }
    }

    void freeze() {
        this.nonExtensible = true;
        this.sealed = true;
        this.frozen = true;
        if (props != null) {
            for (PropertySlot s : props.values()) {
                if (s.tombstoned) continue;
                s.attrs &= ~CONFIGURABLE;
                // writable is N/A for accessor properties — only clear on data slots.
                if (!(s.value instanceof JsAccessor)) {
                    s.attrs &= ~WRITABLE;
                }
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
     * Default clears {@code props} and the three extensibility flags. Subclasses
     * with additional caches (e.g. a {@code methodCache} of wrapped
     * {@link JsBuiltinMethod} instances) should override and call
     * {@code super.clearEngineState()} first.
     */
    protected void clearEngineState() {
        if (props != null) props.clear();
        nonExtensible = false;
        sealed = false;
        frozen = false;
        // Subclasses with eager-installed intrinsics override and call
        // {@code super.clearEngineState()} first, then re-run their
        // {@code installIntrinsics()} routine — the install code is the
        // single source of truth for what gets restored. The {@link
        // PropertySlot#INTRINSIC} bit stays informational (strict-mode
        // checks, introspection) rather than reset-controlling.
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
        PropertySlot s = props == null ? null : props.get(name);
        // Already tombstoned — nothing to do.
        if (s != null && s.tombstoned) {
            return;
        }
        boolean inMap = s != null;
        boolean intrinsic = hasOwnIntrinsic(name);
        if (!inMap && !intrinsic) {
            return;
        }
        // Configurability check. The slot's attrs byte wins when present;
        // otherwise fall back to the intrinsic's getOwnAttrs default. Sealed/
        // frozen flags imply non-configurable (they cleared the bit on every
        // slot when applied), so checking the slot's attrs alone is enough.
        byte attrs = inMap ? s.attrs : getOwnAttrs(name);
        if ((attrs & CONFIGURABLE) == 0) {
            return;
        }
        if (intrinsic) {
            // Tombstone if there is an underlying intrinsic. Without this, after
            // `Math.abs = X; delete Math.abs;` the intrinsic Math.abs would
            // "shine through" and `hasOwnProperty` would incorrectly report it
            // as own. The tombstone shadows the intrinsic; a later assignment
            // (`Math.abs = Y`) clears the tombstone in putMember.
            if (s == null) {
                if (props == null) {
                    props = new LinkedHashMap<>();
                }
                s = new DataSlot(name);
                props.put(name, s);
            }
            s.value = null;
            s.attrs = ATTRS_DEFAULT;
            s.tombstoned = true;
        } else {
            props.remove(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        if (props == null || props.isEmpty()) return Collections.emptyMap();
        // Surface non-tombstoned slots as a name → value map. Built fresh —
        // callers iterating heavily should cache the result. (jsEntries reads
        // props directly to avoid the rebuild.)
        Map<String, Object> view = new LinkedHashMap<>(props.size());
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) {
                view.put(s.name, s.value);
            }
        }
        return view;
    }

    // =================================================================================================
    // Map<String, Object> interface - auto-unwraps values for Java consumers
    // =================================================================================================

    @Override
    public int size() {
        if (props == null) return 0;
        int n = 0;
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) n++;
        }
        return n;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String name)) return false;
        return ownContainsKey(name);
    }

    @Override
    public boolean containsValue(Object value) {
        if (props == null) return false;
        for (PropertySlot s : props.values()) {
            if (s.tombstoned) continue;
            Object unwrapped = Engine.toJava(s.value);
            if (Objects.equals(unwrapped, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        // Map.get() — auto-unwrap, own properties only (no prototype chain).
        PropertySlot s = props == null || !(key instanceof String name) ? null : props.get(name);
        if (s == null || s.tombstoned) return null;
        return Engine.toJava(s.value);
    }

    @Override
    public Object put(String key, Object value) {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        PropertySlot s = props.get(key);
        Object previous = null;
        if (s == null) {
            props.put(key, new DataSlot(key, value));
        } else {
            previous = s.tombstoned ? null : s.value;
            s.value = value;
            s.tombstoned = false;
        }
        return Engine.toJava(previous);
    }

    @Override
    public Object remove(Object key) {
        if (props == null || !(key instanceof String name)) return null;
        PropertySlot s = props.remove(name);
        if (s == null || s.tombstoned) return null;
        return Engine.toJava(s.value);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        for (Map.Entry<? extends String, ?> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        if (props != null) {
            props.clear();
        }
    }

    @Override
    public Set<String> keySet() {
        if (props == null || props.isEmpty()) return Collections.emptySet();
        Set<String> keys = new LinkedHashSet<>(props.size());
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) keys.add(s.name);
        }
        return keys;
    }

    @Override
    public Collection<Object> values() {
        if (props == null || props.isEmpty()) return Collections.emptyList();
        List<Object> unwrapped = new ArrayList<>(props.size());
        for (PropertySlot s : props.values()) {
            if (!s.tombstoned) unwrapped.add(Engine.toJava(s.value));
        }
        return unwrapped;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        if (props == null || props.isEmpty()) return Collections.emptySet();
        Set<Entry<String, Object>> out = new LinkedHashSet<>(props.size());
        for (PropertySlot s : props.values()) {
            if (s.tombstoned) continue;
            out.add(new AbstractMap.SimpleEntry<>(s.name, Engine.toJava(s.value)));
        }
        return out;
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
     * Used internally by JS iteration constructs and by {@link Terms#toIterable},
     * which is the back-end for {@code Object.keys / values / entries / assign}
     * and {@code for...in}. All of those filter by enumerable per spec.
     * <p>
     * Iterates {@link #props} directly so each yielded value reads the slot's
     * current value at next() time — callback-driven mutations during iteration
     * are visible (test262 {@code Array.prototype.map}'s "callback mutates
     * earlier index, later index sees update" semantics rely on this).
     * Subclasses with alternate storage ({@link JsGlobalThis}) override.
     * Routes through {@link #isEnumerable} so subclass {@code getOwnAttrs}
     * overrides (e.g. JsMath returning {@code WRITABLE | CONFIGURABLE} — no
     * enumerable bit — for its built-in methods) win.
     */
    public Iterable<KeyValue> jsEntries() {
        return () -> new Iterator<>() {
            final Iterator<PropertySlot> source = props == null
                    ? Collections.<PropertySlot>emptyIterator()
                    : props.values().iterator();
            int index = 0;
            PropertySlot peeked = null;

            private boolean advance() {
                while (source.hasNext()) {
                    PropertySlot s = source.next();
                    if (s.tombstoned) continue;
                    if (isEnumerable(s.name)) {
                        peeked = s;
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
                PropertySlot s = peeked;
                peeked = null;
                // Read s.value at yield time so callback-driven mutations
                // before the next next() call propagate.
                return new KeyValue(JsObject.this, index++, s.name, s.value);
            }
        };
    }

    // Identity-based hashCode/equals to avoid infinite recursion on circular
    // references (e.g. constructor ↔ prototype).
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

}
