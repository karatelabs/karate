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

import java.util.List;

/**
 * Global {@code WeakMap} constructor — mirrors {@link JsMapConstructor}: no
 * {@code new} is a TypeError, and construction from an iterable of
 * {@code [key, value]} pairs invokes {@code this.set(k, v)} through the
 * prototype chain so a user-overridden {@code WeakMap.prototype.set} is honored.
 */
class JsWeakMapConstructor extends JsFunction {

    JsWeakMapConstructor() {
        this.name = "WeakMap";
        defineOwn("prototype", JsWeakMapPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        boolean isNew = callInfo != null && callInfo.constructor;
        if (!isNew) {
            throw JsErrorException.typeError("Constructor WeakMap requires 'new'");
        }
        JsWeakMap map = new JsWeakMap();
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return map;
        }
        Object setFn = map.getMember("set");
        if (!(setFn instanceof JsCallable adder)) {
            throw JsErrorException.typeError("WeakMap.prototype.set is not callable");
        }
        JsIterator iter = IterUtils.getIterator(args[0], context);
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object savedThis = cc != null ? cc.thisObject : null;
        try {
            while (iter.hasNext()) {
                Object entry = iter.next();
                if (!(entry instanceof List) && !(entry instanceof ObjectLike)) {
                    throw JsErrorException.typeError("Iterator value " + entry + " is not an entry object");
                }
                Object k;
                Object v;
                if (entry instanceof List<?> list) {
                    k = list.isEmpty() ? Terms.UNDEFINED : list.get(0);
                    v = list.size() < 2 ? Terms.UNDEFINED : list.get(1);
                } else {
                    ObjectLike ol = (ObjectLike) entry;
                    k = ol.getMember("0");
                    v = ol.getMember("1");
                    if (k == null) k = Terms.UNDEFINED;
                    if (v == null) v = Terms.UNDEFINED;
                }
                if (cc != null) cc.thisObject = map;
                adder.call(context, new Object[]{k, v});
                if (cc != null && cc.isError()) {
                    return map;
                }
            }
        } finally {
            if (cc != null) cc.thisObject = savedThis;
        }
        return map;
    }

}
