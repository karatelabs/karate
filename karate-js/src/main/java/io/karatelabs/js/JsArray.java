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
    private Map<String, Object> namedProps;
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
        if (namedProps != null && namedProps.containsKey(name)) {
            return namedProps.get(name);
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
        // arr.length = N — truncate (N < size) or extend with HOLE (N > size)
        // per §10.4.2.4 ArraySetLength. Numeric coercion via Terms.objectToNumber
        // matches what `arr.length = "5"` and `arr.length = 5.0` do in JS.
        if ("length".equals(name)) {
            Number n = Terms.objectToNumber(value);
            if (n != null) {
                setLength(n.intValue());
            }
            return;
        }
        // Numeric index write — write into the dense list (and pad with HOLE if
        // the index is past size, growing length per array-exotic semantics).
        // Two slow-path exits land in namedProps instead:
        //   - Descriptors: a JsAccessor value is a defineProperty install,
        //     which must shadow the dense slot (PropertyAccess reads consult
        //     namedProps first via getIndexedSlot).
        //   - Existing namedProps entry at this index: keep the override
        //     active so the descriptor isn't silently lost.
        int i = parseIndex(name);
        boolean hasNamedAtIndex = namedProps != null && namedProps.containsKey(name);
        if (i >= 0 && !hasNamedAtIndex && !(value instanceof JsAccessor)) {
            while (list.size() < i) list.add(HOLE);
            if (i < list.size()) list.set(i, value);
            else list.add(value);
            return;
        }
        if (namedProps == null) {
            namedProps = new LinkedHashMap<>();
        }
        namedProps.put(name, value);
    }

    /**
     * Sets {@code length} per §10.4.2.4 ArraySetLength: shrinking truncates
     * the dense list (loses values past the new length); growing pads with
     * {@link #HOLE} so the new slots are sparse, not set-to-undefined.
     * Negative or NaN coercions are ignored (caller validates per spec).
     */
    void setLength(int newLength) {
        if (newLength < 0) return;
        int cur = list.size();
        if (newLength < cur) {
            // sublist().clear() removes the tail in one shot
            list.subList(newLength, cur).clear();
        } else {
            while (list.size() < newLength) list.add(HOLE);
        }
    }

    @Override
    public void removeMember(String name) {
        if (namedProps != null) {
            namedProps.remove(name);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        // Arrays don't typically convert to maps, return named props
        return namedProps == null ? Collections.emptyMap() : namedProps;
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
     * JS internal access - returns raw value, UNDEFINED for out of bounds
     * or for sparse holes (so callers never observe {@link #HOLE}).
     */
    public Object getElement(int index) {
        if (index < 0 || index >= list.size()) {
            return Terms.UNDEFINED;  // JS semantics
        }
        Object v = list.get(index);
        return v == HOLE ? Terms.UNDEFINED : v;
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
    Object getIndexedSlot(int index) {
        if (namedProps != null && index >= 0) {
            Object slot = namedProps.get(Integer.toString(index));
            if (slot != null) return slot;
        }
        return getElement(index);
    }

    /** True iff a descriptor is installed at index {@code index} via {@link #namedProps}. */
    boolean hasIndexedDescriptor(int index) {
        return namedProps != null && index >= 0
                && namedProps.containsKey(Integer.toString(index));
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
     * {@code length} is {@code {writable: true, enumerable: false,
     * configurable: false}} per §10.4.2 ArrayCreate. Other keys default to
     * all-true (per-key attribute tracking matching {@link JsObject#_attrs}
     * is a follow-up).
     */
    public byte getOwnAttrs(String name) {
        if ("length".equals(name)) {
            // writable only — not enumerable, not configurable.
            return JsObject.WRITABLE;
        }
        return JsObject.ATTRS_DEFAULT;
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
     * Array(n) creates array with n empty slots, Array(a,b,c) creates [a,b,c].
     */
    static JsArray create(Object[] args) {
        if (args.length == 1 && args[0] instanceof Number n) {
            int count = n.intValue();
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(null);
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
