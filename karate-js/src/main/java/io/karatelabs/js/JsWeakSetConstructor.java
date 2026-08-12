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
 * Global {@code WeakSet} constructor — mirrors {@link JsSetConstructor}: no
 * {@code new} is a TypeError, and construction from an iterable invokes
 * {@code this.add(v)} through the prototype chain so a user-overridden
 * {@code WeakSet.prototype.add} is honored.
 */
class JsWeakSetConstructor extends JsFunction {

    JsWeakSetConstructor() {
        this.name = "WeakSet";
        defineOwn("prototype", JsWeakSetPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        boolean isNew = callInfo != null && callInfo.constructor;
        if (!isNew) {
            throw JsErrorException.typeError("Constructor WeakSet requires 'new'");
        }
        JsWeakSet set = new JsWeakSet();
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return set;
        }
        Object addFn = set.getMember("add");
        if (!(addFn instanceof JsCallable adder)) {
            throw JsErrorException.typeError("WeakSet.prototype.add is not callable");
        }
        JsIterator iter = IterUtils.getIterator(args[0], context);
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object savedThis = cc != null ? cc.thisObject : null;
        try {
            while (iter.hasNext()) {
                Object v = iter.next();
                if (cc != null) cc.thisObject = set;
                adder.call(context, new Object[]{v});
                if (cc != null && cc.isError()) {
                    return set;
                }
            }
        } finally {
            if (cc != null) cc.thisObject = savedThis;
        }
        return set;
    }

}
