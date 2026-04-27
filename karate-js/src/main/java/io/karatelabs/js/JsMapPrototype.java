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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Singleton prototype for {@link JsMap} instances. Inherits from {@link JsObjectPrototype}.
 */
class JsMapPrototype extends Prototype {

    static final JsMapPrototype INSTANCE = new JsMapPrototype();

    private JsMapPrototype() {
        super(JsObjectPrototype.INSTANCE);
        // Note: `size` is an accessor on the spec prototype, not a method. JsMap.getMember
        // intercepts `m.size` directly (returning the live count) so we don't expose it
        // here as a JsCallable — that would shape-mismatch real spec consumers.
        install("get", 1, this::get);
        install("set", 2, this::set);
        install("has", 1, this::has);
        install("delete", 1, this::delete);
        install("clear", 0, this::clear);
        install("forEach", 1, this::forEach);
        install("keys", 0, this::keys);
        install("values", 0, this::values);
        JsBuiltinMethod entries = new JsBuiltinMethod("entries", 0, this::entriesMethod);
        install("entries", entries);
        // Spec @@iterator on Map.prototype === Map.prototype.entries — same
        // wrapped instance keeps identity.
        install(IterUtils.SYMBOL_ITERATOR, entries);
        // ES2025 upsert proposal — Map.prototype.{getOrInsert, getOrInsertComputed}.
        install("getOrInsert", 2, this::getOrInsert);
        install("getOrInsertComputed", 2, this::getOrInsertComputed);
    }

    private static JsMap asMap(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsMap m) {
            return m;
        }
        throw JsErrorException.typeError("Method Map.prototype called on incompatible receiver");
    }

    private Object get(Context context, Object[] args) {
        return asMap(context).getValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object set(Context context, Object[] args) {
        JsMap m = asMap(context);
        Object key = args.length > 0 ? args[0] : Terms.UNDEFINED;
        Object value = args.length > 1 ? args[1] : Terms.UNDEFINED;
        m.setValue(key, value);
        return m;
    }

    private Object has(Context context, Object[] args) {
        return asMap(context).hasKey(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object delete(Context context, Object[] args) {
        return asMap(context).deleteKey(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object clear(Context context, Object[] args) {
        asMap(context).clearAll();
        return Terms.UNDEFINED;
    }

    private Object forEach(Context context, Object[] args) {
        JsMap m = asMap(context);
        if (args.length == 0 || !(args[0] instanceof JsCallable cb)) {
            throw JsErrorException.typeError("Map.prototype.forEach: callback is not a function");
        }
        // Spec: forEach walks the live entry list. Entries appended during iteration
        // are visited; deletions ahead of the cursor are skipped naturally because the
        // cursor advances past them.
        int cursor = 0;
        while (cursor < m.entries.size()) {
            Iterator<Map.Entry<Object, Object>> it = m.entries.entrySet().iterator();
            for (int i = 0; i < cursor && it.hasNext(); i++) it.next();
            if (!it.hasNext()) break;
            Map.Entry<Object, Object> e = it.next();
            cb.call(context, new Object[]{e.getValue(), e.getKey(), m});
            cursor++;
        }
        return Terms.UNDEFINED;
    }

    private Object keys(Context context, Object[] args) {
        JsMap m = asMap(context);
        return IterUtils.toIteratorObject(mapIterator(m, MapIteratorKind.KEY));
    }

    private Object values(Context context, Object[] args) {
        JsMap m = asMap(context);
        return IterUtils.toIteratorObject(mapIterator(m, MapIteratorKind.VALUE));
    }

    private Object entriesMethod(Context context, Object[] args) {
        JsMap m = asMap(context);
        return IterUtils.toIteratorObject(mapIterator(m, MapIteratorKind.KEY_VALUE));
    }

    /**
     * Spec ES2025 upsert §24.1.3.x:
     * {@code Map.prototype.getOrInsert(key, value)} — returns the existing
     * value if the key is already present (under SameValueZero with -0
     * normalization), otherwise stores {@code value} and returns it.
     */
    private Object getOrInsert(Context context, Object[] args) {
        JsMap m = asMap(context);
        Object key = args.length > 0 ? args[0] : Terms.UNDEFINED;
        Object value = args.length > 1 ? args[1] : Terms.UNDEFINED;
        if (m.hasKey(key)) {
            return m.getValue(key);
        }
        m.setValue(key, value);
        return value;
    }

    /**
     * Spec ES2025 upsert: {@code getOrInsertComputed(key, callbackfn)}. If the
     * key is present, returns the existing value WITHOUT invoking the callback
     * (does-not-evaluate-callbackfn-if-key-present.js). Otherwise calls
     * {@code callbackfn(canonicalKey)} (canonical-key-passed-to-callback.js)
     * and stores the result. Re-checks key presence after the callback returns
     * so a callback that mutates the map can't leave a stale insert
     * (overwrites-mutation-from-callbackfn.js).
     */
    private Object getOrInsertComputed(Context context, Object[] args) {
        JsMap m = asMap(context);
        Object key = args.length > 0 ? args[0] : Terms.UNDEFINED;
        Object cb = args.length > 1 ? args[1] : Terms.UNDEFINED;
        if (!(cb instanceof JsCallable callable)) {
            throw JsErrorException.typeError("Map.prototype.getOrInsertComputed: callback is not a function");
        }
        if (m.hasKey(key)) {
            return m.getValue(key);
        }
        // Canonical key: spec normalizes -0 to +0 before invoking callback.
        Object canonicalKey = JsMap.normalizeKey(key);
        Object value = callable.call(context, new Object[]{canonicalKey});
        // Spec: a callback that threw stops the operation. The engine signals
        // throws via {@code cc.error}; bail without inserting so post-throw
        // {@code map.has(key) === false} (check-state-after-callback-fn-throws.js).
        CoreContext cc = context instanceof CoreContext c ? c : null;
        if (cc != null && cc.isError()) {
            return Terms.UNDEFINED;
        }
        // Java-null callback returns surface to JS as undefined. Re-checking
        // map state after the callback returned: per spec, OVERWRITE any
        // entry the callback inserted at the same key
        // (overwrites-mutation-from-callbackfn.js).
        if (value == null) value = Terms.UNDEFINED;
        m.setValue(canonicalKey, value);
        return value;
    }

    private enum MapIteratorKind { KEY, VALUE, KEY_VALUE }

    /**
     * Iterator that walks the map's entries in insertion order. Per spec, mutations
     * during iteration are observed (entries added after the cursor are visited;
     * deleted entries are skipped). Implemented by snapshotting the keys lazily at
     * each {@code next()} via a fresh entry iterator.
     */
    private static JsIterator mapIterator(JsMap m, MapIteratorKind kind) {
        return new JsIterator() {
            int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < m.entries.size();
            }

            @Override
            public Object next() {
                if (cursor >= m.entries.size()) {
                    throw new NoSuchElementException();
                }
                // LinkedHashMap preserves insertion order; advance cursor in step with the
                // sequential view. This is O(n) per step in the worst case but correct under
                // mid-iteration mutation; the test262 suite exercises tiny maps.
                Iterator<Map.Entry<Object, Object>> it = m.entries.entrySet().iterator();
                for (int i = 0; i < cursor; i++) it.next();
                Map.Entry<Object, Object> e = it.next();
                cursor++;
                return switch (kind) {
                    case KEY -> e.getKey();
                    case VALUE -> e.getValue();
                    case KEY_VALUE -> {
                        List<Object> pair = new ArrayList<>(2);
                        pair.add(e.getKey());
                        pair.add(e.getValue());
                        yield pair;
                    }
                };
            }
        };
    }

}
