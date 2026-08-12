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

/**
 * Singleton prototype for {@link JsWeakSet} instances. Deliberately carries only
 * add / has / delete — the spec gives WeakSet no size, no clear, no forEach and
 * no iteration protocol.
 */
class JsWeakSetPrototype extends Prototype {

    static final JsWeakSetPrototype INSTANCE = new JsWeakSetPrototype();

    private JsWeakSetPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("add", 1, this::add);
        install("has", 1, this::has);
        install("delete", 1, this::delete);
        install("@@toStringTag", "WeakSet");
    }

    private static JsWeakSet asWeakSet(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsWeakSet s) {
            return s;
        }
        throw JsErrorException.typeError("Method WeakSet.prototype called on incompatible receiver");
    }

    private Object add(Context context, Object[] args) {
        JsWeakSet s = asWeakSet(context);
        s.addValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
        return s;
    }

    private Object has(Context context, Object[] args) {
        return asWeakSet(context).has(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object delete(Context context, Object[] args) {
        return asWeakSet(context).deleteValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

}
