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
 * JavaScript Array implementation with unified prototype chain.
 * <p>
 * Implements List<Object> for Java interop with auto-unwrapping - Java code gets clean values.
 * JS internal code uses getElement/setElement for raw access (returns UNDEFINED for out of bounds).
 * <p>
 * Property lookup order:
 * <ol>
 *   <li>Own properties ({@code namedProps} map)</li>
 *   <li>Array-specific built-ins (length)</li>
 *   <li>Prototype chain ({@code __proto__})</li>
 * </ol>
 */
class JsArray implements ObjectLike, JsCallable, List<Object> {

    /**
     * Sparse-hole sentinel. Distinct from {@code null} (an explicit JS
     * {@code null} value) and {@link Terms#UNDEFINED} (an explicit
     * {@code undefined} value) — sparse array literals like {@code [0,,2]}
     * write this value at the hole positions, and {@code arr.length = 5} on
     * an empty array fills the new slots with it. The runtime translates
     * {@code HOLE} back to {@code undefined} at every read seam so user
     * code never observes the sentinel; {@link #isOwnProperty(String)} and
     * the spec-skipping iteration helpers ({@link #jsEntries()},
     * {@code Array.prototype.{forEach,map,filter,every,some,find,...}})
     * use it to distinguish holes from set-to-undefined slots — per spec,
     * {@code [,].hasOwnProperty(0) === false} but
     * {@code [undefined].hasOwnProperty(0) === true}.
     */
    static final Object HOLE = new Object() {
        @Override public String toString() { return "<<hole>>"; }
    };

    final List<Object> list;
    /**
     * Sparse string-keyed properties — descriptors installed via
     * {@code Object.defineProperty(arr, …)}, named (non-index) keys. Unified
     * value-plus-attribute storage: each {@link Slot} carries the value and
     * its attribute byte. Absent keys use {@link Slot#ATTRS_DEFAULT}.
     * <p>
     * Numeric indices that override the dense {@link #list} (accessors, or
     * data slots set via {@code defineProperty} with non-default attrs) are
     * stored here under the canonical string-form key ({@code "0"}, …);
     * {@link #getIndexedValue} consults this map first for indexed reads.
     */
    private Map<String, PropertySlot> namedProps;
    /**
     * Writable bit for the {@code "length"} property. Spec invariant: length is
     * always non-enumerable and non-configurable; only the writable bit can be
     * customized (cleared by {@code Object.defineProperty(arr, "length",
     * {writable: false})} or {@link #freeze()}). Stored separately because
     * length's value lives in {@link #list}{@code .size()} — putting it in
     * {@link #namedProps} as a Slot would either need an attrs-only marker or
     * shadow the dense length with the slot's null {@code value}.
     */
    private boolean lengthWritable = true;
    // Extensibility state — mirrors {@link JsObject}'s flags. Currently
    // state-only: {@link #isExtensible} / {@link #isSealed} / {@link #isFrozen}
    // report it but the indexed-write path doesn't yet enforce. Full
    // enforcement is the deferred Array slice TODO ({@code Object.freeze(arr)}
    // is a no-op for indexed access).
    private boolean nonExtensible;
    private boolean sealed;
    private boolean frozen;
    private ObjectLike __proto__ = JsArrayPrototype.INSTANCE;

    public JsArray(List<Object> list) {
        this.list = list;
    }

    public JsArray() {
        this(new ArrayList<>());
    }

    /**
     * Returns the prototype (__proto__) of this array.
     */
    public ObjectLike getPrototype() {
        return __proto__;
    }

    /**
     * Sets the prototype (__proto__) of this array.
     */
    public void setPrototype(ObjectLike proto) {
        this.__proto__ = proto;
    }

    // =================================================================================================
    // ObjectLike implementation
    // =================================================================================================

    @Override
    public Object getMember(String name) {
        // 1. Check named properties first (stored in namedProps).
        //    Accessor slots have no extractable raw value at this seam —
        //    surface as null. JS-semantic reads (PropertyAccess) use the
        //    receiver-aware overload, which routes through slot.read.
        if (namedProps != null) {
            PropertySlot s = namedProps.get(name);
            if (s != null) return s instanceof DataSlot ds ? ds.value : null;
        }
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        Object intrinsic = resolveOwnIntrinsic(name);
        if (intrinsic != null) return intrinsic;
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
    }

    @Override
    public Object getMember(String name, Object receiver, CoreContext ctx) {
        if (namedProps != null) {
            PropertySlot s = namedProps.get(name);
            if (s != null) return s.read(receiver, ctx);
        }
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        Object intrinsic = resolveOwnIntrinsic(name);
        if (intrinsic != null) return intrinsic;
        if (__proto__ != null) {
            return __proto__.getMember(name, receiver, ctx);
        }
        return null;
    }

    /**
     * Array intrinsics: {@code length} (live from dense list) and numeric-index
     * reads when the array sits in a prototype chain (so a child
     * {@code __proto__ === [1,2,3]} resolves {@code child[0]} via getMember
     * here — direct {@code arr[i]} skips this and hits
     * {@link PropertyAccess#getByIndex}'s fast path). Subclasses
     * ({@link JsUint8Array}) override and call {@code super.resolveOwnIntrinsic}
     * to inherit these. The {@link IterUtils#SYMBOL_ITERATOR @@iterator}
     * stand-in is installed on {@link JsArrayPrototype} per spec, not here.
     * <p>
     * {@link #HOLE} positions return {@code null} (not the sentinel) so that
     * the caller falls through to the prototype chain — per spec, a hole is
     * an absent own property and {@code [[Get]]} walks the proto. The
     * spec-shape {@code Array.prototype.{pop, shift}} rely on this so the
     * {@code Get(O, idx)} step invokes a prototype getter installed via
     * {@code Object.defineProperty(Array.prototype, idx, {get: …})} —
     * asserted by the test262 {@code set-length-array-length-is-non-writable.js}
     * cluster's getter call-count assertions.
     */
    protected Object resolveOwnIntrinsic(String name) {
        if ("length".equals(name)) {
            return list.size();
        }
        if (!name.isEmpty()) {
            char c0 = name.charAt(0);
            if (c0 >= '0' && c0 <= '9') {
                int i = parseIndex(name);
                // {@link #hasOwnIndexedSlot} / {@link #getElement} are
                // virtual so buffer-backed subclasses ({@link JsUint8Array})
                // surface their own indexed values here too.
                if (hasOwnIndexedSlot(i)) {
                    return getElement(i);
                }
            }
        }
        return null;
    }

    /** Returns the own slot for {@code name} in {@link #namedProps}, or
     *  {@code null} if absent. Package-private — used by descriptor
     *  inspection paths that need slot identity. */
    PropertySlot getOwnSlot(String name) {
        return namedProps == null ? null : namedProps.get(name);
    }

    private static int parseIndex(String s) {
        // Strict canonical-integer parse: rejects "01", "+1", "-1", "1.0".
        // Spec: an array index is a String whose value is a CanonicalNumericIndexString
        // less than 2^32 - 1. We don't model the upper bound (in-range check by list.size).
        int n = s.length();
        if (n == 0) return -1;
        if (n > 10) return -1; // any 11+ digit index overflows int and isn't a usable element index
        int v = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
        }
        // Reject leading-zero forms like "01", but allow "0".
        if (n > 1 && s.charAt(0) == '0') return -1;
        return v;
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
        // TypeError flip lives elsewhere). Mirrors JsObject.putMember's
        // frozen early exit. Non-extensible / sealed are checked downstream
        // per-write so existing-index updates still flow.
        if (frozen) {
            return;
        }
        // arr.length = N — primitives only on this path. Object values with
        // valueOf/toString must route through {@link #handleLengthAssign} from
        // a context-bearing call site (PropertyAccess.setByName special-cases).
        // Throws RangeError on invalid Uint32 per §10.4.2.4 step 5; silently
        // ignores when length is non-writable (lenient mode — strict-mode flip
        // happens at the [[Set]] caller).
        if ("length".equals(name)) {
            handleLengthAssign(value, null);
            return;
        }
        // Existing accessor: invoke the setter (lenient — get-only accessors
        // silently drop the write). No ctx on this entry point — reach the
        // ctx-aware path via PropertyAccess.setByName for live setters.
        PropertySlot existing = namedProps == null ? null : namedProps.get(name);
        if (existing instanceof AccessorSlot acc) {
            acc.write(this, value, null, false);
            return;
        }
        // Per-property writable=false: silently ignore (lenient mode — strict-mode
        // TypeError flip lives elsewhere). Mirrors JsObject.putMember's check.
        if (existing != null && !existing.isWritable()) {
            return;
        }
        // Numeric index write — fast path into the dense list (and pad with
        // HOLE if past size, growing length per array-exotic semantics). Slow
        // path (existing namedProps entry at this index) keeps the override
        // active so a non-default attrs byte isn't silently lost. Sentinel
        // HOLE positions count as "key absent" — overwriting one with a value
        // creates a new own property, so non-extensible blocks them too.
        int i = parseIndex(name);
        if (i >= 0 && existing == null) {
            boolean indexExists = i < list.size() && list.get(i) != HOLE;
            if (nonExtensible && !indexExists) {
                return;
            }
            while (list.size() < i) list.add(HOLE);
            if (i < list.size()) list.set(i, value);
            else list.add(value);
            return;
        }
        // Named-prop path: non-extensible blocks creation of new keys; existing
        // (non-tombstoned) slots are still writable.
        if (existing == null && nonExtensible) {
            return;
        }
        if (existing instanceof DataSlot ds) {
            ds.value = value;
        } else {
            if (namedProps == null) {
                namedProps = new LinkedHashMap<>();
            }
            namedProps.put(name, new DataSlot(name, value));
        }
    }

    /**
     * Low-level data-descriptor write used by {@code Object.defineProperty}.
     * Bypasses the {@code [[Set]]} writable check (defineProperty is
     * allowed to mutate non-writable data props subject to its own
     * configurable rules, which the caller already validated). Stores the
     * descriptor's writable / enumerable / configurable bits on the
     * {@link PropertySlot}'s {@code attrs} byte so subsequent reads via
     * {@link #getOwnAttrs(String)} see them.
     * <p>
     * Data descriptors at a numeric index land in the dense {@link #list}
     * (and reflect their attrs into {@link #namedProps} only when non-default).
     * Named (non-index) keys live in {@link #namedProps}.
     * <p>
     * The {@code length} key routes through ArraySetLength: validates the value
     * as Uint32 (RangeError on invalid), truncates/extends accordingly, then
     * stores the writable bit. Spec invariants (length is non-enum, non-config)
     * are enforced upstream in {@link JsObjectConstructor#defineProperty}.
     */
    void defineOwn(String name, Object value, byte attrs) {
        if ("length".equals(name)) {
            // The caller (Object.defineProperty) is expected to route length
            // through {@link #defineLength} after running ToUint32 — but
            // tolerate direct callers (e.g. the literal-object init path on
            // arrays, which is currently unused) by going through
            // ArrayLength.handleAssign with no context.
            ArrayLength.handleAssign(this, value, null);
            setAttrs("length", attrs);
            return;
        }
        int i = parseIndex(name);
        if (i >= 0) {
            // Data descriptor at an array index: write into the dense list
            // and clear any prior namedProps entry so the dense slot is the
            // authoritative source on read. Pad past-end with HOLE to grow
            // length.
            if (namedProps != null) {
                namedProps.remove(name);
            }
            while (list.size() < i) list.add(HOLE);
            if (i < list.size()) list.set(i, value);
            else list.add(value);
            // Attrs only stick when non-default — otherwise the absence of a
            // namedProps entry leaves the index at ATTRS_DEFAULT.
            if (attrs != PropertySlot.ATTRS_DEFAULT) {
                if (namedProps == null) {
                    namedProps = new LinkedHashMap<>();
                }
                DataSlot s = new DataSlot(name, value);
                s.attrs = attrs;
                namedProps.put(name, s);
            }
        } else {
            if (namedProps == null) {
                namedProps = new LinkedHashMap<>();
            }
            PropertySlot existing = namedProps.get(name);
            DataSlot s;
            if (existing instanceof DataSlot ds) {
                s = ds;
            } else {
                s = new DataSlot(name);
                namedProps.put(name, s);
            }
            s.value = value;
            s.attrs = attrs;
        }
    }

    /**
     * Low-level accessor-descriptor write. Installs (or updates) an
     * {@link AccessorSlot} in {@link #namedProps} — accessor slots always
     * shadow the dense list, so the canonical-string-form key works for
     * both index and named keys.
     */
    void defineOwnAccessor(String name, JsCallable getter, JsCallable setter, byte attrs) {
        // Spec §10.4.2.1 ArrayDefineOwnProperty: defining a property at an
        // index >= length extends length to {@code idx + 1}. The data-slot
        // path in {@link #defineOwn} already does this via its HOLE-pad loop;
        // mirror it for accessor descriptors so {@code Array.prototype.*}
        // iteration helpers see the extended length (test262
        // {@code lastIndexOf/15.4.4.15-8-a-14.js}: an accessor at index 20
        // must extend a length-3 array to length 21 so the iterator visits
        // index 20 first and the getter's side-effect — deleting
        // {@code Array.prototype[1]} — runs before the index-1 read).
        int i = parseIndex(name);
        if (i >= 0) {
            while (list.size() <= i) list.add(HOLE);
        }
        if (namedProps == null) {
            namedProps = new LinkedHashMap<>();
        }
        PropertySlot existing = namedProps.get(name);
        AccessorSlot s;
        if (existing instanceof AccessorSlot as) {
            s = as;
        } else {
            s = new AccessorSlot(name);
            namedProps.put(name, s);
        }
        s.getter = getter;
        s.setter = setter;
        s.attrs = attrs;
    }

    /**
     * Returns the attribute byte for {@code name}: bit-OR of {@link Slot#WRITABLE},
     * {@link Slot#ENUMERABLE}, {@link Slot#CONFIGURABLE}. Defaults to all-true
     * when the key has never been touched by {@code defineProperty} /
     * {@code seal} / {@code freeze}. Mirrors {@link JsObject#getAttrs(String)}.
     */
    byte getAttrs(String name) {
        PropertySlot s = namedProps == null ? null : namedProps.get(name);
        return s == null ? PropertySlot.ATTRS_DEFAULT : s.attrs;
    }

    /** Stores the attribute byte for {@code name} on its slot. The
     *  {@code "length"} key special-cases to the {@link #lengthWritable} field
     *  since length's value lives in the dense list, not in a namedProps slot.
     *  Other keys without an existing slot or accessor: storing non-default
     *  attrs is a no-op (nothing to attach attrs to without a value override). */
    void setAttrs(String name, byte attrs) {
        if ("length".equals(name)) {
            lengthWritable = (attrs & PropertySlot.WRITABLE) != 0;
            return;
        }
        PropertySlot s = namedProps == null ? null : namedProps.get(name);
        if (s != null) {
            s.attrs = attrs;
        }
    }

    /**
     * Spec §10.4.2.4 ArraySetLength entry point — used by {@code arr.length = X}
     * (via {@link #putMember(String, Object)} from {@link PropertyAccess#setByName})
     * and by {@code Object.defineProperty(arr, "length", ...)} (via
     * {@link #defineOwn}). See {@link ArrayLength#handleAssign} for the full
     * coerce-then-apply contract; this is the JsArray-instance entry.
     */
    boolean handleLengthAssign(Object value, CoreContext context) {
        return ArrayLength.handleAssign(this, value, context);
    }

    /**
     * Post-coercion entry for {@code Object.defineProperty(arr, "length", desc)} —
     * caller has already run {@link #coerceToUint32} (so that spec-prescribed
     * double valueOf invocation is observable in test262 timing) and validated
     * the writable transition. Applies the length truncate/extend and stores
     * the new attribute byte; returns the same true/false partial-truncate
     * signal as {@link #handleLengthAssign} for the non-configurable case.
     */
    boolean defineLength(int newLen, byte attrs) {
        return ArrayLength.defineProperty(this, newLen, attrs);
    }

    /**
     * Public helper exposing spec ArraySetLength steps 3-5: ToUint32 + ToNumber
     * + RangeError on mismatch. {@code Object.defineProperty} runs this before
     * the descriptor validation block so RangeError fires before any TypeError
     * (per {@code define-own-prop-length-overflow-order.js}). Caller passes the
     * resulting Uint32 to {@link #defineLength}.
     */
    static long coerceToUint32(Object value, CoreContext context) {
        return ArrayLength.coerceToUint32(value, context);
    }

    @Override
    public void removeMember(String name) {
        if (namedProps != null) {
            namedProps.remove(name);
        }
        // Numeric index: tombstone the dense slot with HOLE so the own
        // property is absent ({@link #isOwnProperty} / hasOwnProperty report
        // false; {@link #resolveOwnIntrinsic} returns null and the read walks
        // the proto chain). Without this, {@code delete arr[i]} silently
        // preserves the value and {@link JsArrayPrototype#shift} /
        // {@link JsArrayPrototype#unshift}'s move-loop "DeletePropertyOrThrow"
        // branch is a no-op on dense indices. List length is left unchanged
        // (delete doesn't shrink the array — the spec uses [[Delete]] +
        // separate length-set). Configurable / sealed / frozen enforcement
        // is a separate deferred TODO; this lenient path matches today's
        // namedProps behavior.
        int i = parseIndex(name);
        if (i >= 0 && i < list.size()) {
            list.set(i, HOLE);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        // Arrays don't typically convert to maps; surface namedProps' values
        // (the value-side of each Slot, excluding any pure-attrs slots that
        // carry no value override). Accessor slots have no extractable raw
        // value at this Java-interop boundary; surface as null.
        if (namedProps == null || namedProps.isEmpty()) return Collections.emptyMap();
        Map<String, Object> view = new LinkedHashMap<>(namedProps.size());
        for (PropertySlot s : namedProps.values()) {
            view.put(s.name, s instanceof DataSlot ds ? ds.value : null);
        }
        return view;
    }

    // =================================================================================================
    // List<Object> implementation - returns raw values (no auto-unwrap)
    // =================================================================================================

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return list.contains(o);
    }

    @Override
    public Iterator<Object> iterator() {
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean add(Object o) {
        return list.add(o);
    }

    @Override
    public boolean remove(Object o) {
        return list.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return list.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Object> c) {
        return list.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends Object> c) {
        return list.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return list.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return list.retainAll(c);
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public Object get(int index) {
        // List.get() auto-unwraps for Java consumers (converts undefined to null,
        // unwraps JsValue types). Also translates HOLE→null so sparse slots
        // surface as Java null (the closest representation of JS undefined for
        // a List consumer; matches what Engine.toJava does for Terms.UNDEFINED).
        Object v = list.get(index);
        if (v == HOLE) return null;
        return Engine.toJava(v);
    }

    @Override
    public Object set(int index, Object element) {
        return list.set(index, element);
    }

    @Override
    public void add(int index, Object element) {
        list.add(index, element);
    }

    @Override
    public Object remove(int index) {
        return list.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<Object> listIterator() {
        return list.listIterator();
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
        return list.listIterator(index);
    }

    @Override
    public List<Object> subList(int fromIndex, int toIndex) {
        return new JsArray(new ArrayList<>(list.subList(fromIndex, toIndex)));
    }

    // =================================================================================================
    // JS internal access - returns raw values
    // =================================================================================================

    /**
     * Translate the {@link #HOLE} sentinel to {@link Terms#UNDEFINED} so
     * external callers never observe the internal marker. Single canonical
     * unwrap site reused by {@link #getElement}, {@link IterUtils}, and
     * {@link PropertyAccess} indexed reads.
     */
    static Object unwrapHole(Object v) {
        return v == HOLE ? Terms.UNDEFINED : v;
    }

    /**
     * JS internal access - returns raw value, UNDEFINED for out of bounds
     * or for sparse holes (so callers never observe {@link #HOLE}).
     */
    public Object getElement(int index) {
        if (index < 0 || index >= list.size()) {
            return Terms.UNDEFINED;  // JS semantics
        }
        return unwrapHole(list.get(index));
    }

    /**
     * Indexed read that honors descriptors installed via
     * {@code Object.defineProperty(arr, i, {...})}. Such descriptors land in
     * {@link #namedProps} under the canonical string-form key (e.g.
     * {@code "0"}) and take precedence over the dense {@link #list} backing
     * store. Resolves accessor descriptors via {@link PropertySlot#read}
     * with {@code receiver} bound as {@code this}.
     * <p>
     * When the index is out-of-bounds or lands on a {@link #HOLE} (own
     * property absent), walks the {@code __proto__} chain so
     * {@code Array.prototype[i] = …} or an accessor installed on a
     * prototype surfaces through {@code arr[i]} — matching the behavior of
     * {@link #getMember(String, Object, CoreContext)} (test262
     * S15.4.4.9_A4_T1 / S15.4.4.13_A4_T2 read inherited indices after a
     * mutating call). Returns {@link Terms#UNDEFINED} when the chain has no
     * matching entry.
     * <p>
     * Hot path: {@code namedProps == null} and the index is in-bounds with
     * a non-HOLE value — single null check, two range checks, dense read.
     */
    Object getIndexedValue(int index, Object receiver, CoreContext ctx) {
        if (namedProps != null && index >= 0) {
            PropertySlot s = namedProps.get(Integer.toString(index));
            if (s != null) return s.read(receiver, ctx);
        }
        if (hasOwnIndexedSlot(index)) {
            return getElement(index);
        }
        if (__proto__ != null && index >= 0) {
            Object inherited = __proto__.getMember(Integer.toString(index), receiver, ctx);
            if (inherited != null) return inherited;
        }
        return Terms.UNDEFINED;
    }

    /**
     * True iff a descriptor is installed at index {@code index} — either an
     * accessor / overridden value or a non-default attribute byte. Both live
     * in {@link #namedProps} now (one Slot per overridden index). Routes the
     * indexed-write fast path through {@code setByName} so writable=false is
     * honored.
     */
    boolean hasIndexedDescriptor(int index) {
        if (index < 0 || namedProps == null) return false;
        return namedProps.containsKey(Integer.toString(index));
    }

    /**
     * True iff any descriptor (numeric or otherwise) was installed via
     * {@code Object.defineProperty} or a named-prop write. Lets bulk
     * iterators (e.g. {@code Array.prototype.*}) skip the per-index slow
     * path on plain arrays while still routing through accessor dispatch
     * on the rare arrays that need it.
     */
    boolean hasAnyDescriptor() {
        return namedProps != null && !namedProps.isEmpty();
    }

    /**
     * True iff {@code name} is an own property on this array — covers
     * {@code length} (always own), any user-set entry in {@link #namedProps}
     * (descriptors installed via {@code Object.defineProperty}, named
     * properties), and canonical numeric indices via
     * {@link #hasOwnIndexedSlot}. Mirrors {@link JsObject#isOwnProperty(String)}.
     */
    public boolean isOwnProperty(String name) {
        if ("length".equals(name)) return true;
        if (namedProps != null && namedProps.containsKey(name)) return true;
        return hasOwnIndexedSlot(parseIndex(name));
    }

    /**
     * True iff {@code index} resolves to an own data slot at the dense
     * storage layer (post-{@link #namedProps}). Plain {@link JsArray}:
     * in-bounds and non-{@link #HOLE}. Buffer-backed subclasses
     * ({@link JsUint8Array}) override for {@code buffer.length} bounds —
     * every in-buffer index is present (no hole concept). Used by
     * {@link #isOwnProperty} and {@link #getIndexedValue} so spec
     * HasProperty + Get work uniformly across storage shapes.
     */
    boolean hasOwnIndexedSlot(int index) {
        return index >= 0 && index < list.size() && list.get(index) != HOLE;
    }

    /**
     * Spec-correct attribute byte for an own property of this array.
     * {@code length} starts out {@code {writable: true, enumerable: false,
     * configurable: false}} per §10.4.2 ArrayCreate; the writable bit may
     * later be cleared by {@code Object.defineProperty(arr, "length",
     * {writable: false})} or {@code Object.freeze(arr)} — that override is
     * stored on the {@code "length"} Slot in {@link #namedProps} and consulted
     * here. Length is always non-enumerable and non-configurable; the stored
     * byte's other bits are ignored by this getter.
     * <p>
     * Dense-list indices (no namedProps override) report all-true by default;
     * a sealed array clears their {@code configurable} bit and a frozen array
     * also clears {@code writable}. Mirrors what {@link #seal} / {@link #freeze}
     * already do for {@link #namedProps} slots — without this derived view,
     * {@code Object.getOwnPropertyDescriptor(frozenArr, 0)} would still
     * report all-true on the dense slots and {@code defineProperty}'s
     * configurable check wouldn't fire.
     * <p>
     * Other named keys consult {@link #namedProps} via {@link #getAttrs} —
     * slots installed via {@code Object.defineProperty} (or via the indexed-
     * descriptor path on {@link #defineOwn}) carry the override; absent keys
     * default to all-true.
     */
    public byte getOwnAttrs(String name) {
        if ("length".equals(name)) {
            return lengthWritable ? PropertySlot.WRITABLE : 0;
        }
        // Dense-list indices without a namedProps override: derive the attribute
        // byte from the array's integrity-level flags so descriptors / defineProperty
        // see frozen/sealed correctly.
        if (namedProps == null || !namedProps.containsKey(name)) {
            int i = parseIndex(name);
            if (i >= 0 && i < list.size() && list.get(i) != HOLE) {
                byte attrs = PropertySlot.ATTRS_DEFAULT;
                if (frozen) attrs &= ~(PropertySlot.WRITABLE | PropertySlot.CONFIGURABLE);
                else if (sealed) attrs &= ~PropertySlot.CONFIGURABLE;
                return attrs;
            }
        }
        return getAttrs(name);
    }

    /** Spec-correct enumerability check. Mirrors {@link JsObject#isEnumerable}
     *  so {@code Object.prototype.propertyIsEnumerable} can dispatch on
     *  arrays without falling through to a "true regardless" default. */
    boolean isEnumerable(String name) {
        return (getOwnAttrs(name) & PropertySlot.ENUMERABLE) != 0;
    }

    public void setElement(int index, Object value) {
        list.set(index, value);
    }

    public void addElement(Object value) {
        list.add(value);
    }

    public void removeElement(int index) {
        list.remove(index);
    }

    /**
     * Returns the raw internal list (for JS internal use).
     */
    public List<Object> toList() {
        return list;
    }

    // =================================================================================================
    // JS iteration support
    // =================================================================================================

    /**
     * Returns an iterable for JS for-in/for-of iteration with KeyValue pairs.
     * Skips sparse holes per spec: {@code Array.prototype.{forEach,map,filter,
     * every,some,find,findIndex,reduce,reduceRight}} and {@code for...in} all
     * skip indices whose own property is absent. ({@code for...of} reads
     * each index via {@code Get(O, k)} which returns {@code undefined} for
     * holes — different code path; this iterator is for the hole-skipping
     * built-ins.)
     */
    public Iterable<KeyValue> jsEntries() {
        return jsEntries(null);
    }

    /**
     * JS-semantic iteration variant — when {@code ctx != null}, accessor
     * descriptors at numeric indices (installed via
     * {@code Object.defineProperty(arr, i, {get: …})}) invoke their getter
     * via {@link PropertySlot#read}; otherwise behaves as
     * {@link #jsEntries()}. Hot path: {@code namedProps == null} for plain
     * arrays — single null check, then the existing dense-list iteration.
     * <p>
     * Skips non-enumerable indices: a descriptor installed via
     * {@code Object.defineProperty(arr, i, {enumerable: false, …})} stores
     * its attrs in {@link #namedProps}; consult that on each step so for-in /
     * {@code Object.keys} match {@link JsObject#jsEntries(CoreContext)}.
     */
    public Iterable<KeyValue> jsEntries(CoreContext ctx) {
        return () -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                while (index < list.size()) {
                    Object v = list.get(index);
                    if (v == HOLE) {
                        index++;
                        continue;
                    }
                    if (namedProps != null) {
                        PropertySlot s = namedProps.get(Integer.toString(index));
                        if (s != null && (s.attrs & PropertySlot.ENUMERABLE) == 0) {
                            index++;
                            continue;
                        }
                    }
                    return true;
                }
                return false;
            }

            @Override
            public KeyValue next() {
                if (!hasNext()) throw new NoSuchElementException();
                int i = index++;
                Object v;
                if (ctx != null && namedProps != null) {
                    PropertySlot s = namedProps.get(Integer.toString(i));
                    v = s != null ? s.read(JsArray.this, ctx) : list.get(i);
                } else {
                    v = list.get(i);
                }
                return new KeyValue(JsArray.this, i, i + "", v);
            }
        };
    }

    // =================================================================================================
    // Helper methods
    // =================================================================================================

    static JsArray toArray(Map<String, Object> map) {
        List<Object> list = new ArrayList<>();
        if (map.containsKey("length")) {
            Object length = map.get("length");
            if (length instanceof Number) {
                int size = ((Number) length).intValue();
                for (int i = 0; i < size; i++) {
                    list.add(Terms.UNDEFINED);
                }
            }
        }
        Set<Integer> indexes = new HashSet<>();
        for (String key : map.keySet()) {
            try {
                int index = Integer.parseInt(key);
                indexes.add(index);
            } catch (Exception e) {
                // ignore
            }
        }
        for (int index : indexes) {
            list.add(index, map.get(index + ""));
        }
        return new JsArray(list);
    }

    @Override
    public Object call(Context context, Object[] args) {
        return create(args);
    }

    /**
     * ES6: Both Array() and new Array() return an Array object.
     * Array(n) creates a sparse array of length n (HOLE-filled, per spec —
     * {@code new Array(3).hasOwnProperty(0) === false}); Array(a,b,c) creates
     * [a,b,c]. Throws RangeError when the single-numeric form's argument is
     * not a valid Uint32 — covers {@code new Array(-1)} /
     * {@code new Array(4294967296)} / {@code new Array(1.5)}.
     */
    static JsArray create(Object[] args) {
        if (args.length == 1 && args[0] instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 0
                    || d > 4294967295.0 || d != Math.floor(d)) {
                throw JsErrorException.rangeError("Invalid array length");
            }
            if (d > Integer.MAX_VALUE) {
                throw JsErrorException.rangeError("Invalid array length");
            }
            int count = (int) d;
            List<Object> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(HOLE);
            }
            return new JsArray(list);
        }
        return new JsArray(new ArrayList<>(Arrays.asList(args)));
    }

    // Use identity-based hashCode/equals to avoid infinite recursion
    // when arrays contain objects with circular references
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * Spec §10.4.2.4 ArraySetLength + the supporting coercion / partial-truncate
     * machinery, co-located so the six entry points on {@link JsArray}
     * ({@code defineOwn(name="length")}, {@link #setAttrs(String, byte)},
     * {@link #handleLengthAssign}, {@link #defineLength},
     * {@link #coerceToUint32}, {@link #putMember(String, Object)}'s length
     * branch) share one implementation site.
     * <p>
     * Pure code-locality refactor — no behavior change. The deferred
     * spec-precise pop/shift interleaving fix
     * ({@code set-length-array-length-is-non-writable.js} cluster) lives here
     * when it lands, since the partial-truncate walk in {@link #applySet} is
     * where the get/delete-before-throw timing has to be modeled.
     */
    static final class ArrayLength {

        private ArrayLength() {
        }

        /**
         * Spec ArraySetLength entry — coerces {@code value} to Uint32, then
         * applies. Throws {@link JsErrorException} (RangeError) for invalid
         * Uint32; returns {@code false} on length-non-writable / partial-
         * truncate (caller decides whether to throw TypeError); {@code true}
         * on full success.
         */
        static boolean handleAssign(JsArray arr, Object value, CoreContext context) {
            long u32 = coerceToUint32(value, context);
            return applySet(arr, (int) u32);
        }

        /**
         * Post-coercion entry — caller already ran {@link #coerceToUint32}
         * (used by {@code Object.defineProperty(arr, "length", desc)} so the
         * spec-prescribed double {@code valueOf} fires before descriptor
         * validation). Applies the length and stores the writable bit.
         */
        static boolean defineProperty(JsArray arr, int newLen, byte attrs) {
            boolean ok = applySet(arr, newLen);
            arr.setAttrs("length", attrs);
            return ok;
        }

        /**
         * Spec ArraySetLength steps 3-5: ToUint32 + ToNumber + RangeError on
         * mismatch. Two {@code valueOf} invocations are observable per spec
         * — {@code define-own-prop-length-coercion-order.js} asserts
         * {@code valueOfCalls === 2}. The current backing store is bounded
         * by {@link Integer#MAX_VALUE}; values in
         * {@code (Integer.MAX_VALUE, 2^32-1]} surface as RangeError today
         * (spec considers them valid — widening tracked in TEST262.md).
         */
        static long coerceToUint32(Object value, CoreContext context) {
            Object first = value instanceof ObjectLike && context != null
                    ? Terms.toPrimitive(value, "number", context) : value;
            Number n1 = Terms.objectToNumber(first);
            double d1 = n1 == null ? Double.NaN : n1.doubleValue();
            long u32;
            if (Double.isNaN(d1) || Double.isInfinite(d1)) {
                u32 = 0;
            } else {
                double truncated = d1 < 0 ? -Math.floor(-d1) : Math.floor(d1);
                long modded = (long) (truncated - Math.floor(truncated / 4294967296.0) * 4294967296.0);
                if (modded < 0) modded += 4294967296L;
                u32 = modded;
            }
            Object second = value instanceof ObjectLike && context != null
                    ? Terms.toPrimitive(value, "number", context) : value;
            Number n2 = Terms.objectToNumber(second);
            double d2 = n2 == null ? Double.NaN : n2.doubleValue();
            // NaN != anything (including itself) — naturally caught by the inequality.
            if (d2 != (double) u32) {
                throw JsErrorException.rangeError("Invalid array length");
            }
            if (u32 > Integer.MAX_VALUE) {
                throw JsErrorException.rangeError("Invalid array length");
            }
            return u32;
        }

        /**
         * Apply a coerced new length: HOLE-pad to extend, walk the truncate
         * range high-to-low looking for a non-configurable index that blocks
         * the rest (partial-truncate), drop matching {@code namedProps}
         * entries. Returns {@code false} for length-non-writable, length-
         * extension on a non-extensible array, or partial-truncate; caller
         * decides TypeError vs lenient.
         */
        static boolean applySet(JsArray arr, int newLen) {
            if ((arr.getOwnAttrs("length") & JsObject.WRITABLE) == 0) {
                return false;
            }
            int oldLen = arr.list.size();
            if (newLen >= oldLen) {
                // Extending the array adds new HOLE-filled own indices —
                // non-extensible blocks creation of any new own property.
                if (newLen > oldLen && arr.nonExtensible) {
                    return false;
                }
                while (arr.list.size() < newLen) arr.list.add(HOLE);
                return true;
            }
            int blockingIndex = -1;
            for (int i = oldLen - 1; i >= newLen; i--) {
                if (!isIndexConfigurable(arr, i)) {
                    blockingIndex = i;
                    break;
                }
            }
            if (blockingIndex >= 0) {
                int retainTo = blockingIndex + 1;
                if (retainTo < arr.list.size()) {
                    arr.list.subList(retainTo, arr.list.size()).clear();
                    if (arr.namedProps != null) {
                        for (int i = retainTo; i < oldLen; i++) arr.namedProps.remove(Integer.toString(i));
                    }
                }
                return false;
            }
            arr.list.subList(newLen, oldLen).clear();
            if (arr.namedProps != null) {
                for (int i = newLen; i < oldLen; i++) arr.namedProps.remove(Integer.toString(i));
            }
            return true;
        }

        private static boolean isIndexConfigurable(JsArray arr, int i) {
            PropertySlot s = arr.namedProps == null ? null : arr.namedProps.get(Integer.toString(i));
            return s == null || s.isConfigurable();
        }

    }

    // -------------------------------------------------------------------------
    // Extensibility state. Mirrors {@link JsObject}'s API so
    // {@code Object.freeze} / {@code seal} / {@code preventExtensions} +
    // their predicates dispatch through the unified {@link ObjectLike} hook
    // and don't need a per-type {@code instanceof} fork.
    // -------------------------------------------------------------------------

    @Override
    public boolean isExtensible() {
        return !nonExtensible;
    }

    @Override
    public boolean isSealed() {
        return sealed || frozen;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void setExtensible(boolean extensible) {
        if (extensible) return;
        this.nonExtensible = true;
    }

    @Override
    public void setSealed(boolean sealed) {
        if (!sealed) return;
        this.nonExtensible = true;
        this.sealed = true;
        if (namedProps != null) {
            for (PropertySlot s : namedProps.values()) {
                if (!s.tombstoned) {
                    s.attrs &= ~PropertySlot.CONFIGURABLE;
                }
            }
        }
    }

    @Override
    public void setFrozen(boolean frozen) {
        if (!frozen) return;
        this.nonExtensible = true;
        this.sealed = true;
        this.frozen = true;
        this.lengthWritable = false;
        if (namedProps != null) {
            for (PropertySlot s : namedProps.values()) {
                if (s.tombstoned) continue;
                s.attrs &= ~PropertySlot.CONFIGURABLE;
                if (s instanceof DataSlot) {
                    s.attrs &= ~PropertySlot.WRITABLE;
                }
            }
        }
    }

}
