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
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Singleton prototype for {@link JsUrlSearchParams}.
 */
class JsUrlSearchParamsPrototype extends Prototype {

    static final JsUrlSearchParamsPrototype INSTANCE = new JsUrlSearchParamsPrototype();

    private JsUrlSearchParamsPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("append", 2, (ctx, args) -> {
            JsUrlSearchParams p = asParams(ctx, "append");
            p.pairs.add(new String[]{str(ctx, args, 0), str(ctx, args, 1)});
            p.changed();
            return Terms.UNDEFINED;
        });
        install("delete", 1, (ctx, args) -> {
            JsUrlSearchParams p = asParams(ctx, "delete");
            String name = str(ctx, args, 0);
            String value = args.length > 1 && args[1] != Terms.UNDEFINED ? str(ctx, args, 1) : null;
            p.pairs.removeIf(pair -> pair[0].equals(name) && (value == null || pair[1].equals(value)));
            p.changed();
            return Terms.UNDEFINED;
        });
        install("get", 1, (ctx, args) -> {
            String name = str(ctx, args, 0);
            for (String[] pair : asParams(ctx, "get").pairs) {
                if (pair[0].equals(name)) {
                    return pair[1];
                }
            }
            return null;
        });
        install("getAll", 1, (ctx, args) -> {
            String name = str(ctx, args, 0);
            List<Object> values = new ArrayList<>();
            for (String[] pair : asParams(ctx, "getAll").pairs) {
                if (pair[0].equals(name)) {
                    values.add(pair[1]);
                }
            }
            return values;
        });
        install("has", 1, (ctx, args) -> {
            String name = str(ctx, args, 0);
            String value = args.length > 1 && args[1] != Terms.UNDEFINED ? str(ctx, args, 1) : null;
            return asParams(ctx, "has").pairs.stream()
                    .anyMatch(pair -> pair[0].equals(name) && (value == null || pair[1].equals(value)));
        });
        install("set", 2, (ctx, args) -> {
            JsUrlSearchParams p = asParams(ctx, "set");
            String name = str(ctx, args, 0);
            String value = str(ctx, args, 1);
            int first = -1;
            for (int i = 0; i < p.pairs.size(); i++) {
                if (p.pairs.get(i)[0].equals(name)) {
                    if (first < 0) {
                        first = i;
                        p.pairs.get(i)[1] = value;
                    } else {
                        p.pairs.remove(i--);
                    }
                }
            }
            if (first < 0) {
                p.pairs.add(new String[]{name, value});
            }
            p.changed();
            return Terms.UNDEFINED;
        });
        install("sort", 0, (ctx, args) -> {
            JsUrlSearchParams p = asParams(ctx, "sort");
            p.pairs.sort(Comparator.comparing(pair -> pair[0]));
            p.changed();
            return Terms.UNDEFINED;
        });
        install("forEach", 1, (ctx, args) -> {
            JsUrlSearchParams p = asParams(ctx, "forEach");
            if (args.length == 0 || !(args[0] instanceof JsCallable cb)) {
                throw JsErrorException.typeError("The \"callback\" argument must be of type function");
            }
            for (int i = 0; i < p.pairs.size(); i++) {
                String[] pair = p.pairs.get(i);
                cb.call(ctx, new Object[]{pair[1], pair[0], p});
            }
            return Terms.UNDEFINED;
        });
        install("keys", 0, (ctx, args) -> iterator(asParams(ctx, "keys"), 0));
        install("values", 0, (ctx, args) -> iterator(asParams(ctx, "values"), 1));
        JsBuiltinMethod entries = new JsBuiltinMethod("entries", 0, (ctx, args) -> iterator(asParams(ctx, "entries"), 2));
        install("entries", entries);
        install(IterUtils.SYMBOL_ITERATOR, entries);
        install("toString", 0, (ctx, args) -> asParams(ctx, "toString").serialize());
        installAccessor("size", new JsBuiltinMethod("get size", 0, (ctx, args) -> asParams(ctx, "size").pairs.size()),
                null, (byte) (PropertySlot.ENUMERABLE | PropertySlot.CONFIGURABLE | PropertySlot.INTRINSIC));
        install("@@toStringTag", "URLSearchParams");
        installConstructor("URLSearchParams");
    }

    private static String str(Context context, Object[] args, int index) {
        return Terms.toStringCoerce(args.length > index ? args[index] : Terms.UNDEFINED,
                context instanceof CoreContext cc ? cc : null);
    }

    private static JsUrlSearchParams asParams(Context context, String member) {
        if (context.getThisObject() instanceof JsUrlSearchParams p) {
            return p;
        }
        throw JsErrorException.typeError("Value of \"this\" must be of type URLSearchParams (" + member + ")");
    }

    /** {@code kind}: 0 names, 1 values, 2 pairs; it reads the live list, as the spec's iterator does. */
    private static Object iterator(JsUrlSearchParams p, int kind) {
        return IterUtils.toIteratorObject(new JsIterator() {
            int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < p.pairs.size();
            }

            @Override
            public Object next() {
                if (cursor >= p.pairs.size()) {
                    throw new NoSuchElementException();
                }
                String[] pair = p.pairs.get(cursor++);
                return switch (kind) {
                    case 0 -> pair[0];
                    case 1 -> pair[1];
                    default -> {
                        List<Object> entry = new ArrayList<>(2);
                        entry.add(pair[0]);
                        entry.add(pair[1]);
                        yield entry;
                    }
                };
            }
        });
    }

}
