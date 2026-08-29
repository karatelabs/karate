/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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

import io.karatelabs.common.StringUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code JSON} global — allocated per-Engine via
 * {@code ContextRoot.initGlobal}. {@code stringify} and {@code parse} are
 * installed at construction time as own properties with the standard
 * built-in attributes ({@code WRITABLE | CONFIGURABLE}, non-enumerable),
 * so user code can read / overwrite them and {@code defineProperties}
 * walks them via the spec [[OwnPropertyKeys]] surface (slice JSON keys
 * via {@code Object.defineProperties(target, JSON)} where the test setup
 * stores arbitrary user keys on the JSON global).
 */
public class JsJson extends JsObject {

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    public JsJson() {
        defineOwn("stringify", new JsBuiltinMethod("stringify", 3, stringify()), METHOD_ATTRS);
        defineOwn("parse", new JsBuiltinMethod("parse", 2, parse()), METHOD_ATTRS);
    }

    private static JsCallable stringify() {
        return (context, args) -> {
            Object value = args[0];
            Object replacer = args.length > 1 ? args[1] : null;
            Object space = args.length > 2 ? args[2] : null;

            // the array form (spec PropertyList) has to be tested BEFORE the
            // function form because JsArray implements both List and JsCallable
            List<String> propertyList = replacer instanceof List<?> l ? replacerKeys(l) : null;
            JsCallable replacerFn = propertyList == null && replacer instanceof JsCallable fn ? fn : null;

            // spec §25.5.2 step 12: the root is serialized as the "" property of a
            // synthetic wrapper — the holder a replacer function sees as its `this`.
            Object holder = replacerFn == null ? null : rootHolder(value);
            value = serializeProperty(context, holder, "", value, replacerFn, propertyList,
                    Collections.newSetFromMap(new IdentityHashMap<>()));

            // Handle space parameter for pretty printing
            boolean pretty = false;
            String indentStr = "  ";

            if (space != null) {
                if (space instanceof Number) {
                    int indent = Math.min(((Number) space).intValue(), 10);
                    if (indent > 0) {
                        pretty = true;
                        indentStr = " ".repeat(indent);
                    }
                } else if (space instanceof String spaceStr) {
                    if (!spaceStr.isEmpty()) {
                        pretty = true;
                        indentStr = spaceStr.substring(0, Math.min(spaceStr.length(), 10));
                    }
                }
            }

            // Use centralized StringUtils.formatJson for both compact and pretty output
            // This ensures proper handling of JS types (undefined, JsValue wrappers)
            // lenient=false for strict JSON (double quotes), sort=false to preserve order.
            // A String value is the exception: formatJson passes a bare String through as
            // already-formatted text, but here it is a JSON string and has to be quoted —
            // the shape a root-level toJSON (Date) or replacer return lands in.
            // §25.5.2: an unrepresentable root (undefined, a function) is JS
            // undefined, not the text "undefined" the generic formatter emits
            if (value instanceof JsUndefined || value instanceof JsFunction) {
                return Terms.UNDEFINED;
            }
            return value instanceof String s
                    ? StringUtils.formatJsonString(s)
                    : StringUtils.formatJson(value, pretty, false, false, indentStr);
        };
    }

    /** Spec §25.5.2: only String / Number entries (and their wrappers) name a
     *  key; anything else is ignored, and duplicates collapse. */
    private static List<String> replacerKeys(List<?> replacer) {
        List<String> keys = new ArrayList<>(replacer.size());
        for (Object entry : replacer) {
            String k = replacerKey(entry);
            if (k != null && !keys.contains(k)) {
                keys.add(k);
            }
        }
        return keys;
    }

    private static String replacerKey(Object entry) {
        if (entry instanceof String s) return s;
        if (entry instanceof Number n) return Terms.numberToString(n);
        if (entry instanceof JsString || entry instanceof JsNumber) {
            return replacerKey(((JsValue) entry).getJavaValue());
        }
        return null;
    }

    private static JsObject rootHolder(Object value) {
        JsObject holder = new JsObject(new LinkedHashMap<>(1));
        holder.put("", value);
        return holder;
    }

    /**
     * Spec §25.5.2 SerializeJSONProperty, as a single walk over the value tree:
     * {@code toJSON} then the replacer function, wrapper unwrap, the BigInt and
     * cycle TypeErrors, then the container recursion. The emitted text is still
     * {@link StringUtils#formatJson} — this pass only resolves what the spec says
     * the *value* is, and returns the input object unchanged when nothing along a
     * branch transformed it, so the no-replacer / no-{@code toJSON} case hands
     * the formatter exactly the tree it was handed before.
     * <p>
     * {@code seen} tracks the current path only (add / recurse / remove), so a
     * cycle is a TypeError here instead of unbounded recursion, while legal
     * diamond sharing (same object under two keys) stays serializable.
     */
    private static Object serializeProperty(Context context, Object holder, String key, Object value,
                                            JsCallable replacerFn, List<String> propertyList, Set<Object> seen) {
        if (value instanceof ObjectLike ol
                && ol.getMember("toJSON", value, cc(context)) instanceof JsCallable toJson) {
            value = callWithThis(context, toJson, value, new Object[]{key});
        }
        if (replacerFn != null) {
            // A JS throw from user code propagates as-is (spec: SerializeJSONProperty
            // forwards abrupt completions) — the live context keeps the thrown value's
            // JS identity instead of the host-invocation EngineException wrap.
            value = callWithThis(context, replacerFn, holder, new Object[]{key, value});
        }
        if (value instanceof JsFunction) {
            return value; // JSON has no functions; the formatter drops / nulls them
        }
        // §25.5.2: a Symbol value is unrepresentable — omitted as a property,
        // null as an array element. Ahead of the JsValue unwrap because a
        // JsSymbol is a JsObject and would otherwise recurse into "{}".
        if (value instanceof JsSymbol) {
            return Terms.UNDEFINED;
        }
        if (value instanceof JsValue jv && !(value instanceof JsUndefined)) {
            value = jv.getJavaValue(); // Number / String / Boolean / Date wrappers
        }
        if (value instanceof BigInteger || value instanceof JsBigInt) {
            throw JsErrorException.typeError("Do not know how to serialize a BigInt");
        }
        // §25.5.2.2 SerializeJSONNumber: a finite Number is its ToString, a
        // non-finite one is the literal null — JSON has no NaN / Infinity.
        // Only here: Terms.numberToString stays the ToString seam String(NaN) shares.
        if (value instanceof Number n && !Double.isFinite(n.doubleValue())) {
            return null;
        }
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
            return value;
        }
        if (!seen.add(value)) {
            throw JsErrorException.typeError("Converting circular structure to JSON");
        }
        try {
            return value instanceof List<?> list
                    ? serializeArray(context, list, replacerFn, propertyList, seen)
                    : serializeObject(context, value, replacerFn, propertyList, seen);
        } finally {
            seen.remove(value);
        }
    }

    private static Object serializeObject(Context context, Object value, JsCallable replacerFn,
                                          List<String> propertyList, Set<Object> seen) {
        Map<Object, Object> result;
        // a PropertyList always reshapes the object — it selects and reorders keys
        boolean changed = propertyList != null;
        if (value instanceof JsObject jo) {
            // §25.5.2 SerializeJSONProperty does Get(holder, key): accessor
            // getters run, and a PropertyList key resolves through the
            // prototype chain — the raw toMap view has neither
            CoreContext ctx = cc(context);
            if (propertyList != null) {
                result = new LinkedHashMap<>(propertyList.size());
                for (String key : propertyList) {
                    if (!Terms.in(key, value)) {
                        continue; // absent everywhere on the chain — skipped, not null
                    }
                    Object child = jo.getMember(key, value, ctx);
                    result.put(key, serializeProperty(context, value, key, child, replacerFn, propertyList, seen));
                }
            } else {
                result = new LinkedHashMap<>();
                for (KeyValue kv : jo.jsEntries(ctx)) {
                    result.put(kv.key(), serializeProperty(context, value, kv.key(), kv.value(),
                            replacerFn, propertyList, seen));
                }
            }
            // always the materialized map: the formatter reads raw toMap storage,
            // which diverges from the Get view whenever accessors are present
            return result;
        }
        // plain Java Map (host interop) or non-JsObject ObjectLike — raw entries,
        // so UNDEFINED survives as a distinct value rather than collapsing to
        // null via Java unwrapping
        Map<?, ?> src = value instanceof ObjectLike ol ? ol.toMap() : (Map<?, ?>) value;
        result = new LinkedHashMap<>(src.size());
        if (propertyList != null) {
            for (String key : propertyList) {
                if (src.containsKey(key)) {
                    result.put(key, serializeProperty(context, value, key, src.get(key), replacerFn, propertyList, seen));
                }
            }
        } else {
            for (Map.Entry<?, ?> entry : src.entrySet()) {
                Object child = entry.getValue();
                Object serialized = serializeProperty(context, value, String.valueOf(entry.getKey()), child,
                        replacerFn, propertyList, seen);
                changed |= serialized != child;
                result.put(entry.getKey(), serialized);
            }
        }
        return changed ? result : value;
    }

    private static Object serializeArray(Context context, List<?> list, JsCallable replacerFn,
                                         List<String> propertyList, Set<Object> seen) {
        JsArray array = list instanceof JsArray a ? a : null; // raw element reads, no Java unwrap
        int size = list.size();
        List<Object> result = new ArrayList<>(size);
        boolean changed = false;
        for (int i = 0; i < size; i++) {
            Object raw = array == null ? list.get(i) : array.list.get(i);
            // a dense element is its own value; a hole reads through Get so an
            // own accessor or inherited indexed getter fires (§25.5.2) — absent
            // everywhere it stays undefined and serializes as null
            Object child = array != null && raw == JsArray.HOLE
                    ? array.getMember(String.valueOf(i), array, cc(context))
                    : JsArray.unwrapHole(raw);
            Object serialized = serializeProperty(context, list, String.valueOf(i), child,
                    replacerFn, propertyList, seen);
            changed |= serialized != raw;
            result.add(serialized);
        }
        return changed ? result : list;
    }

    private static JsCallable parse() {
        return (context, args) -> {
            Object result = JsonParser.parse((String) args[0]);
            if (args.length > 1 && args[1] instanceof JsCallable reviver) {
                // spec §25.5.1 step 7: the parsed root is revived as the "" property
                // of a synthetic wrapper — the holder the reviver sees as its `this`
                return internalize(context, rootHolder(result), "", result, reviver);
            }
            return result;
        };
    }

    /**
     * Spec §25.5.1 InternalizeJSONProperty. Bottom-up: every child is revived —
     * and dropped when the reviver returns undefined — before the reviver runs
     * for the holder's own key. {@code JsonParser} hands back mutable
     * {@code LinkedHashMap} / {@code ArrayList} nodes, so the walk edits the tree
     * in place the way the spec edits the holder.
     */
    @SuppressWarnings("unchecked")
    private static Object internalize(Context context, Object holder, String key, Object value, JsCallable reviver) {
        if (value instanceof List<?>) {
            List<Object> list = (List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                // a deleted element leaves a hole, which reads back as undefined
                list.set(i, internalize(context, list, String.valueOf(i), list.get(i), reviver));
            }
        } else if (value instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (String k : new ArrayList<>(map.keySet())) {
                Object revived = internalize(context, map, k, map.get(k), reviver);
                if (revived == Terms.UNDEFINED) {
                    map.remove(k);
                } else {
                    map.put(k, revived);
                }
            }
        }
        return callWithThis(context, reviver, holder, new Object[]{key, value});
    }

    private static CoreContext cc(Context context) {
        return context instanceof CoreContext core ? core : null;
    }

    private static Object callWithThis(Context context, JsCallable fn, Object thisObject, Object[] args) {
        CoreContext core = cc(context);
        if (core == null) {
            return fn.call(context, args);
        }
        Object saved = core.thisObject;
        core.thisObject = thisObject;
        try {
            return fn.call(core, args);
        } finally {
            core.thisObject = saved;
        }
    }

}
