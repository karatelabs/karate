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
        // 1. Check named properties first (stored in namedProps)
        if (namedProps != null) {
            PropertySlot s = namedProps.get(name);
            if (s != null) return s.value;
        }
        // 2. Special case: length property
        if ("length".equals(name)) {
            return list.size();
        }
        // 3. Special case for __proto__ property access
        if ("__proto__".equals(name)) {
            return __proto__;
        }
        // 4. Numeric index access — supports prototype-chain reads where the JsArray
        // sits as `this` (e.g. f.[i] when `f.__proto__ === [1,2,3]`). Plain
        // `arr[i]` goes through PropertyAccess.getByIndex's fast path; this handles
        // the chained / generic getMember route.
        if (!name.isEmpty()) {
            char c0 = name.charAt(0);
            if (c0 >= '0' && c0 <= '9') {
                int i = parseIndex(name);
                if (i >= 0 && i < list.size()) {
                    return list.get(i);
                }
            }
        }
        // 5. @@iterator: stand-in for Symbol.iterator until real Symbol support lands.
        // Returns a callable that, given `this = array`, builds the spec-shaped iterator
        // object ({next() -> {value, done}}). Built-in fast path bypasses this seam;
        // only user code reading `arr[Symbol.iterator]()` lands here.
        if (IterUtils.SYMBOL_ITERATOR.equals(name)) {
            return (JsCallable) (ctx, args) -> {
                Object thisObj = ctx.getThisObject();
                JsIterator iter;
                if (thisObj instanceof JsArray arr) {
                    iter = IterUtils.getIterator(arr, ctx);
                } else {
                    iter = IterUtils.getIterator(JsArray.this, ctx);
                }
                return IterUtils.toIteratorObject(iter);
            };
        }
        // 6. Delegate to prototype chain
        if (__proto__ != null) {
            return __proto__.getMember(name);
        }
        return null;
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
        // Per-property writable=false: silently ignore (lenient mode — strict-mode
        // TypeError flip lives elsewhere). Mirrors JsObject.putMember's check.
        PropertySlot existing = namedProps == null ? null : namedProps.get(name);
        if (existing != null && !existing.isWritable()) {
            return;
        }
        // Numeric index write — write into the dense list (and pad with HOLE if
        // the index is past size, growing length per array-exotic semantics).
        // Two slow-path exits land in namedProps instead:
        //   - Descriptors: a JsAccessor value is a defineProperty install,
        //     which must shadow the dense slot (PropertyAccess reads consult
        //     namedProps first via getIndexedValue).
        //   - Existing namedProps entry at this index: keep the override
        //     active so the descriptor isn't silently lost.
        int i = parseIndex(name);
        if (i >= 0 && existing == null && !(value instanceof JsAccessor)) {
            while (list.size() < i) list.add(HOLE);
            if (i < list.size()) list.set(i, value);
            else list.add(value);
            return;
        }
        if (existing != null) {
            existing.value = value;
        } else {
            if (namedProps == null) {
                namedProps = new LinkedHashMap<>();
            }
            namedProps.put(name, new DataSlot(name, value));
        }
    }

    /**
     * Low-level descriptor write used by {@code Object.defineProperty}. Bypasses
     * the {@code [[Set]]} writable check (defineProperty is allowed to mutate
     * non-writable data props subject to its own configurable rules, which the
     * caller already validated). Stores the descriptor's writable / enumerable /
     * configurable bits on the {@link Slot}'s {@code attrs} byte so subsequent
     * reads via {@link #getOwnAttrs(String)} see them.
     * <p>
     * Data descriptors at a numeric index land in the dense {@link #list};
     * accessor descriptors and named (non-index) keys land in {@link #namedProps}
     * — {@link #getIndexedValue} consults namedProps first, so an accessor at
     * index 0 correctly shadows any dense value at the same slot.
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
             // handleLengthAssign with no context.
            handleLengthAssign(value, null);
            setAttrs("length", attrs);
            return;
        }
        int i = parseIndex(name);
        if (i >= 0 && !(value instanceof JsAccessor)) {
            // Data descriptor at an array index: write into the dense list and
            // clear any prior namedProps entry so the dense slot is the
            // authoritative source on read. Pad past-end with HOLE to grow length.
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
                PropertySlot s = new DataSlot(name, value);
                s.attrs = attrs;
                namedProps.put(name, s);
            }
        } else {
            if (namedProps == null) {
                namedProps = new LinkedHashMap<>();
            }
            PropertySlot s = namedProps.get(name);
            if (s == null) {
                s = new DataSlot(name, value);
                namedProps.put(name, s);
            } else {
                s.value = value;
            }
            s.attrs = attrs;
        }
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
     * and by {@code Object.defineProperty(arr, "length", ...)} (via {@link #defineOwn}).
     * <p>
     * Behavior:
     * <ul>
     *   <li>Coerces {@code value} to Uint32 (calls {@code valueOf}/{@code toString}
     *       via {@link Terms#toPrimitive} when {@code context} is non-null).
     *       Throws {@code RangeError} when the result is not a valid Uint32 —
     *       NaN, Infinity, negative, fractional, or {@code > 2^32-1} (spec
     *       step 5: "If newLen ≠ numberLen, throw a RangeError"). The
     *       RangeError is unconditional, not gated by strictness.</li>
     *   <li>Returns {@code false} when length's stored writable bit is false —
     *       caller decides whether to throw {@code TypeError} (spec [[Set]]
     *       with {@code Throw=true}, used by pop/shift/unshift/push and by
     *       strict-mode direct assignment) or silently ignore (lenient).</li>
     *   <li>When shrinking, partial-truncates above any non-configurable index
     *       in {@code [newLen, oldLen)} and returns {@code false} — same
     *       caller-throws rule.</li>
     *   <li>Returns {@code true} on full success.</li>
     * </ul>
     * <p>
     * Note: our backing store is bounded by {@link Integer#MAX_VALUE} so values
     * in {@code (Integer.MAX_VALUE, 2^32-1]} are rejected as RangeError today
     * (spec considers them valid). See TEST262.md "Deferred TODOs" for the
     * widening to a {@code long} length representation that would lift this
     * limit and cover {@code arr.length = 4294967295}.
     */
    boolean handleLengthAssign(Object value, CoreContext context) {
        long u32 = toValidUint32(value, context);
        return applySetLength((int) u32);
    }

    /**
     * Post-coercion entry for {@code Object.defineProperty(arr, "length", desc)} —
     * caller has already run {@link #toValidUint32} (so that spec-prescribed
     * double valueOf invocation is observable in test262 timing) and validated
     * the writable transition. Applies the length truncate/extend and stores
     * the new attribute byte; returns the same true/false partial-truncate
     * signal as {@link #handleLengthAssign} for the non-configurable case.
     */
    boolean defineLength(int newLen, byte attrs) {
        boolean ok = applySetLength(newLen);
        setAttrs("length", attrs);
        return ok;
    }

    /**
     * Public helper exposing spec ArraySetLength steps 3-5: ToUint32 + ToNumber
     * + RangeError on mismatch. {@code Object.defineProperty} runs this before
     * the descriptor validation block so RangeError fires before any TypeError
     * (per {@code define-own-prop-length-overflow-order.js}). Caller passes the
     * resulting Uint32 to {@link #defineLength}.
     */
    static long coerceToUint32(Object value, CoreContext context) {
        return toValidUint32(value, context);
    }

    private static long toValidUint32(Object value, CoreContext context) {
        // Spec: ToUint32 calls ToNumber once; ArraySetLength then calls ToNumber
        // again for the equality check (steps 3 and 4). Two valueOf invocations
        // are observable — see test262 define-own-prop-length-coercion-order.js
        // which asserts valueOfCalls === 2.
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
            // Bounded by our int-backed list; widening tracked in TEST262.md.
            throw JsErrorException.rangeError("Invalid array length");
        }
        return u32;
    }

    private boolean applySetLength(int newLen) {
        if ((getOwnAttrs("length") & JsObject.WRITABLE) == 0) {
            return false;
        }
        int oldLen = list.size();
        if (newLen >= oldLen) {
            while (list.size() < newLen) list.add(HOLE);
            return true;
        }
        // Walk the truncate range high-to-low looking for a non-configurable
        // index that blocks the rest of the truncation (spec partial-truncate).
        int blockingIndex = -1;
        for (int i = oldLen - 1; i >= newLen; i--) {
            if (!isIndexConfigurable(i)) {
                blockingIndex = i;
                break;
            }
        }
        if (blockingIndex >= 0) {
            int retainTo = blockingIndex + 1;
            if (retainTo < list.size()) {
                list.subList(retainTo, list.size()).clear();
                if (namedProps != null) {
                    for (int i = retainTo; i < oldLen; i++) namedProps.remove(Integer.toString(i));
                }
            }
            return false;
        }
        list.subList(newLen, oldLen).clear();
        if (namedProps != null) {
            for (int i = newLen; i < oldLen; i++) namedProps.remove(Integer.toString(i));
        }
        return true;
    }

    private boolean isIndexConfigurable(int i) {
        PropertySlot s = namedProps == null ? null : namedProps.get(Integer.toString(i));
        return s == null || s.isConfigurable();
    }

    @Override
    public void removeMember(String name) {
        if (namedProps != null) {
            namedProps.remove(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        // Arrays don't typically convert to maps; surface namedProps' values
        // (the value-side of each Slot, excluding any pure-attrs slots that
        // carry no value override).
        if (namedProps == null || namedProps.isEmpty()) return Collections.emptyMap();
        Map<String, Object> view = new LinkedHashMap<>(namedProps.size());
        for (PropertySlot s : namedProps.values()) {
            view.put(s.name, s.value);
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
     * {@link #namedProps} under the canonical string-form key (e.g. {@code "0"})
     * and take precedence over the dense {@link #list} backing store. Returns
     * the raw stored value — including a {@link JsAccessor} when one was
     * installed; the caller invokes the getter. Falls back to
     * {@link #getElement(int)} (which returns {@link Terms#UNDEFINED} for
     * out-of-bounds) when no descriptor is present.
     * <p>
     * Hot path: {@code namedProps == null} for plain arrays — single null
     * check, no allocation, then the existing fast path.
     */
    Object getIndexedValue(int index) {
        if (namedProps != null && index >= 0) {
            PropertySlot s = namedProps.get(Integer.toString(index));
            if (s != null) return s.value;
        }
        return getElement(index);
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
     * properties), and canonical numeric indices that are not holes
     * (i.e. {@code list.get(i) != HOLE}). Mirrors
     * {@link JsObject#isOwnProperty(String)}.
     */
    public boolean isOwnProperty(String name) {
        if ("length".equals(name)) return true;
        if (namedProps != null && namedProps.containsKey(name)) return true;
        int i = parseIndex(name);
        return i >= 0 && i < list.size() && list.get(i) != HOLE;
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
     * Other keys consult {@link #namedProps} via {@link #getAttrs} — slots
     * installed via {@code Object.defineProperty} (or via the indexed-descriptor
     * path on {@link #defineOwn}) carry the override; absent keys default to
     * all-true.
     */
    public byte getOwnAttrs(String name) {
        if ("length".equals(name)) {
            return lengthWritable ? PropertySlot.WRITABLE : 0;
        }
        return getAttrs(name);
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
        return () -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                while (index < list.size() && list.get(index) == HOLE) {
                    index++;
                }
                return index < list.size();
            }

            @Override
            public KeyValue next() {
                if (!hasNext()) throw new NoSuchElementException();
                int i = index++;
                return new KeyValue(JsArray.this, i, i + "", list.get(i));
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

}
