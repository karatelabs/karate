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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code structuredClone} global — HTML StructuredSerialize /
 * StructuredDeserialize, scoped to the value shapes real-world JS passes
 * through it: primitives, plain objects, arrays, {@code Date}, {@code Map},
 * {@code Set}, {@code RegExp}, {@code Error} and boxed primitives. Objects are
 * memoized by identity, so a cyclic graph clones to an equally cyclic one
 * rather than being rejected.
 * <p>
 * Functions and symbols raise a {@code DataCloneError}. Anything else the
 * engine cannot deep-copy — host Java values reaching JS through the bridge —
 * is passed through by reference rather than rejected; the alternative would
 * make {@code structuredClone} unusable on any object graph that touches Java.
 */
final class JsStructuredClone {

    private static final byte DATA_ATTRS = JsObject.WRITABLE | JsObject.CONFIGURABLE;

    private JsStructuredClone() {
    }

    static Object call(Context context, Object[] args) {
        if (args.length == 0) {
            throw JsErrorException.typeError("structuredClone requires at least 1 argument");
        }
        // args[1] is the options bag carrying `transfer` — nothing in this
        // engine is transferable, so it is accepted and ignored
        return clone(args[0], new IdentityHashMap<>(), context instanceof CoreContext cc ? cc : null);
    }

    private static Object clone(Object value, IdentityHashMap<Object, Object> memo, CoreContext ctx) {
        String type = Terms.typeOf(value);
        if ("function".equals(type) || "symbol".equals(type)) {
            throw dataCloneError(type);
        }
        Object seen = memo.get(value);
        if (seen != null) {
            return seen;
        }
        if (value instanceof JsPrimitive jp) {
            return remember(memo, value, boxed(jp));
        }
        if (value instanceof JsDate d) {
            return remember(memo, value, new JsDate(d.getTimeValue()));
        }
        if (value instanceof JsRegex r) {
            return remember(memo, value, new JsRegex(r.pattern, r.flags));
        }
        if (value instanceof JsArray a) {
            JsArray copy = new JsArray(new ArrayList<>(a.list.size()));
            memo.put(value, copy);
            for (Object e : a.list) {
                copy.list.add(clone(e, memo, ctx));
            }
            return copy;
        }
        if (value instanceof JsMap m) {
            JsMap copy = new JsMap();
            memo.put(value, copy);
            for (Map.Entry<Object, Object> e : m.entries.entrySet()) {
                copy.setValue(clone(e.getKey(), memo, ctx), clone(e.getValue(), memo, ctx));
            }
            copyOwn(m, copy, memo, ctx);
            return copy;
        }
        if (value instanceof JsSet s) {
            JsSet copy = new JsSet();
            memo.put(value, copy);
            for (Object e : s.elements.keySet()) {
                copy.addValue(clone(e, memo, ctx));
            }
            copyOwn(s, copy, memo, ctx);
            return copy;
        }
        if (value instanceof JsError e) {
            // the prototype carries `name` and `constructor`, so reusing it is
            // what keeps `instanceof TypeError` true on the clone
            JsError copy = e.getPrototype() instanceof JsErrorPrototype p
                    ? new JsError(p) : new JsError(JsErrorPrototype.ERROR);
            memo.put(value, copy);
            // `message` is non-enumerable, so it never shows up in copyOwn
            if (e.isOwnProperty("message")) {
                copy.defineOwn("message", e.getMember("message"), DATA_ATTRS);
            }
            copyOwn(e, copy, memo, ctx);
            return copy;
        }
        if (value instanceof JsObject o) {
            JsObject copy = new JsObject();
            memo.put(value, copy);
            copyOwn(o, copy, memo, ctx);
            return copy;
        }
        if (value instanceof Map<?, ?> m) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            memo.put(value, copy);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(e.getKey(), clone(e.getValue(), memo, ctx));
            }
            return copy;
        }
        if (value instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l.size());
            memo.put(value, copy);
            for (Object e : l) {
                copy.add(clone(e, memo, ctx));
            }
            return copy;
        }
        return value;
    }

    private static Object remember(IdentityHashMap<Object, Object> memo, Object source, Object copy) {
        memo.put(source, copy);
        return copy;
    }

    private static void copyOwn(JsObject source, ObjectLike target,
                                IdentityHashMap<Object, Object> memo, CoreContext ctx) {
        for (KeyValue kv : source.jsEntries(ctx)) {
            target.putMember(kv.key(), clone(kv.value(), memo, ctx));
        }
    }

    private static Object boxed(JsPrimitive jp) {
        Object v = jp.getJavaValue();
        if (v instanceof String s) return new JsString(s);
        if (v instanceof Boolean b) return new JsBoolean(b);
        if (v instanceof BigInteger bi) return new JsBigInt(bi);
        if (v instanceof Number n) return new JsNumber(n);
        return jp;
    }

    /**
     * There is no {@code DOMException} in this engine, so the browser's
     * {@code DataCloneError} surfaces as a plain {@code Error} carrying that
     * name — enough for the {@code e.name === 'DataCloneError'} check real
     * code writes.
     */
    private static JsErrorException dataCloneError(String type) {
        JsError e = new JsError(JsErrorPrototype.ERROR);
        e.defineOwn("message", "DataCloneError: a " + type + " could not be cloned", DATA_ATTRS);
        e.defineOwn("name", "DataCloneError", DATA_ATTRS);
        return new JsErrorException(e);
    }

}
