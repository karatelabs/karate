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
 * Global {@code Map} constructor. Per spec, {@code Map} called without {@code new}
 * is a TypeError. Construction with an iterable of {@code [key, value]} pairs walks
 * the iterator and invokes {@code this.set(k, v)} via the prototype chain — so a
 * user-overridden {@code Map.prototype.set} is honored during construction.
 */
class JsMapConstructor extends JsFunction {

    static final JsMapConstructor INSTANCE = new JsMapConstructor();

    private JsMapConstructor() {
        this.name = "Map";
        // length=0 — Map() takes optional iterable; spec arity is 0.
        registerForEngineReset();
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "prototype" -> JsMapPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public byte getOwnAttrs(String name) {
        if ("prototype".equals(name)) {
            // Built-in constructor prototype: all-false.
            return 0;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        boolean isNew = callInfo != null && callInfo.constructor;
        if (!isNew) {
            throw JsErrorException.typeError("Constructor Map requires 'new'");
        }
        JsMap map = new JsMap();
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return map;
        }
        // Look up `set` on the freshly-constructed map's prototype chain — honors
        // user-overridden Map.prototype.set per spec 24.1.1.1 step 9.
        Object setFn = map.getMember("set");
        if (!(setFn instanceof JsCallable adder)) {
            throw JsErrorException.typeError("Map.prototype.set is not callable");
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
