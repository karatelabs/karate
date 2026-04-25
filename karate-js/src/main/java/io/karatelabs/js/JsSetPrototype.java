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
import java.util.NoSuchElementException;

/**
 * Singleton prototype for {@link JsSet} instances. Inherits from {@link JsObjectPrototype}.
 */
class JsSetPrototype extends Prototype {

    static final JsSetPrototype INSTANCE = new JsSetPrototype();

    private JsSetPrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "add" -> (JsCallable) this::add;
            case "has" -> (JsCallable) this::has;
            case "delete" -> (JsCallable) this::delete;
            case "clear" -> (JsCallable) this::clear;
            case "forEach" -> (JsCallable) this::forEach;
            case "keys", "values" -> (JsCallable) this::values;
            case "entries" -> (JsCallable) this::entriesMethod;
            default -> {
                if (IterUtils.SYMBOL_ITERATOR.equals(name)) {
                    yield (JsCallable) this::values;
                }
                yield null;
            }
        };
    }

    private static JsSet asSet(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsSet s) {
            return s;
        }
        throw JsErrorException.typeError("Method Set.prototype called on incompatible receiver");
    }

    private Object add(Context context, Object[] args) {
        JsSet s = asSet(context);
        s.addValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
        return s;
    }

    private Object has(Context context, Object[] args) {
        return asSet(context).has(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object delete(Context context, Object[] args) {
        return asSet(context).deleteValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object clear(Context context, Object[] args) {
        asSet(context).clearAll();
        return Terms.UNDEFINED;
    }

    private Object forEach(Context context, Object[] args) {
        JsSet s = asSet(context);
        if (args.length == 0 || !(args[0] instanceof JsCallable cb)) {
            throw JsErrorException.typeError("Set.prototype.forEach: callback is not a function");
        }
        Object thisArg = args.length > 1 ? args[1] : Terms.UNDEFINED;
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object savedThis = cc != null ? cc.thisObject : null;
        try {
            // Spec: forEach walks the live entry list. Entries added after the cursor
            // (at the end) are visited; entries deleted before the cursor reaches them
            // are skipped. Re-fetch the keyset on each step so additions show up.
            int cursor = 0;
            while (cursor < s.elements.size()) {
                Iterator<Object> it = s.elements.keySet().iterator();
                for (int i = 0; i < cursor && it.hasNext(); i++) it.next();
                if (!it.hasNext()) break;
                Object v = it.next();
                if (cc != null) cc.thisObject = thisArg;
                // Per spec: callback receives (value, value, set) — both first args identical
                // (sets have no separate key vs value).
                cb.call(context, new Object[]{v, v, s});
                cursor++;
            }
        } finally {
            if (cc != null) cc.thisObject = savedThis;
        }
        return Terms.UNDEFINED;
    }

    private Object values(Context context, Object[] args) {
        JsSet s = asSet(context);
        return IterUtils.toIteratorObject(setIterator(s, false));
    }

    private Object entriesMethod(Context context, Object[] args) {
        JsSet s = asSet(context);
        return IterUtils.toIteratorObject(setIterator(s, true));
    }

    /**
     * @param entries when {@code true}, yields {@code [v, v]} pairs (matching Map.entries
     *                shape); when {@code false}, yields {@code v} alone.
     */
    private static JsIterator setIterator(JsSet s, boolean entries) {
        return new JsIterator() {
            int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < s.elements.size();
            }

            @Override
            public Object next() {
                if (cursor >= s.elements.size()) {
                    throw new NoSuchElementException();
                }
                Iterator<Object> it = s.elements.keySet().iterator();
                for (int i = 0; i < cursor; i++) it.next();
                Object v = it.next();
                cursor++;
                if (entries) {
                    List<Object> pair = new ArrayList<>(2);
                    pair.add(v);
                    pair.add(v);
                    return pair;
                }
                return v;
            }
        };
    }

}
