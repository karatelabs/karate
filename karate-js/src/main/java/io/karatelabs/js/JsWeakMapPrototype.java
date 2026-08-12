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
 * Singleton prototype for {@link JsWeakMap} instances. Deliberately carries only
 * get / set / has / delete — the spec gives WeakMap no size, no clear, no
 * forEach and no iteration protocol.
 */
class JsWeakMapPrototype extends Prototype {

    static final JsWeakMapPrototype INSTANCE = new JsWeakMapPrototype();

    private JsWeakMapPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("get", 1, this::get);
        install("set", 2, this::set);
        install("has", 1, this::has);
        install("delete", 1, this::delete);
        // Spec tags WeakMap via @@toStringTag rather than a builtin class check.
        install("@@toStringTag", "WeakMap");
    }

    private static JsWeakMap asWeakMap(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsWeakMap m) {
            return m;
        }
        throw JsErrorException.typeError("Method WeakMap.prototype called on incompatible receiver");
    }

    private Object get(Context context, Object[] args) {
        return asWeakMap(context).getValue(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object set(Context context, Object[] args) {
        JsWeakMap m = asWeakMap(context);
        m.setValue(args.length > 0 ? args[0] : Terms.UNDEFINED, args.length > 1 ? args[1] : Terms.UNDEFINED);
        return m;
    }

    private Object has(Context context, Object[] args) {
        return asWeakMap(context).hasKey(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

    private Object delete(Context context, Object[] args) {
        return asWeakMap(context).deleteKey(args.length > 0 ? args[0] : Terms.UNDEFINED);
    }

}
