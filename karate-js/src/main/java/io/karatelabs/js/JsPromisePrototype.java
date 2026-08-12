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
 * Singleton prototype for {@link JsPromise}. Every reaction registered here goes
 * through the job queue — even when the promise is already settled — so
 * synchronous code always runs before any callback.
 */
class JsPromisePrototype extends Prototype {

    static final JsPromisePrototype INSTANCE = new JsPromisePrototype();

    private final JsCallable thenDelegate = this::then;

    private JsPromisePrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("then", 2, thenDelegate);
        install("catch", 1, this::catchMethod);
        install("finally", 1, this::finallyMethod);
        install("@@toStringTag", "Promise");
        installConstructor("Promise");
    }

    /**
     * True while {@code value} is still the untampered {@code Promise.prototype.then}.
     * The combinators' fast path (register a reaction directly, skipping the
     * user-visible {@code Invoke(p, "then", …)}) is only valid while it holds —
     * an own {@code then} on the element, or a replaced prototype method, is
     * observable and must actually be called.
     */
    static boolean isIntrinsicThen(Object value) {
        return value instanceof JsBuiltinMethod method && method.delegate() == INSTANCE.thenDelegate;
    }

    private static JsPromise asPromise(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsPromise promise) {
            return promise;
        }
        throw JsErrorException.typeError("Method Promise.prototype called on incompatible receiver");
    }

    private static JsCallable callableOrNull(Object value) {
        return value instanceof JsCallable callable ? callable : null;
    }

    private Object then(Context context, Object[] args) {
        JsPromise self = asPromise(context);
        return then(self, callableOrNull(AsyncSupport.arg(args, 0)), callableOrNull(AsyncSupport.arg(args, 1)));
    }

    static JsPromise then(JsPromise self, JsCallable onFulfilled, JsCallable onRejected) {
        JsPromise derived = new JsPromise(self.engine, self.scope);
        self.addReaction((rejected, value) -> {
            JsCallable handler = rejected ? onRejected : onFulfilled;
            if (handler == null) {
                // no handler for this outcome: pass it straight to the derived
                // promise, which is now the one carrying the responsibility
                if (rejected) {
                    derived.settle(true, value, null);
                } else {
                    AsyncSupport.resolveValue(derived, value, null);
                }
                return;
            }
            AsyncSupport.Completion outcome =
                    AsyncSupport.invoke(self.engine, handler, new Object[]{value}, null);
            if (outcome.threw()) {
                derived.settle(true, outcome.reason(), null);
            } else {
                AsyncSupport.resolveValue(derived, outcome.value(), null);
            }
        });
        return derived;
    }

    private Object catchMethod(Context context, Object[] args) {
        JsPromise self = asPromise(context);
        return then(self, null, callableOrNull(AsyncSupport.arg(args, 0)));
    }

    private Object finallyMethod(Context context, Object[] args) {
        JsPromise self = asPromise(context);
        JsCallable onFinally = callableOrNull(AsyncSupport.arg(args, 0));
        if (onFinally == null) {
            return then(self, null, null);
        }
        JsPromise derived = new JsPromise(self.engine, self.scope);
        self.addReaction((rejected, value) -> {
            AsyncSupport.Completion outcome =
                    AsyncSupport.invoke(self.engine, onFinally, new Object[0], null);
            if (outcome.threw()) {
                derived.settle(true, outcome.reason(), null);
            } else if (rejected) {
                // the rethrow path: `finally` re-raises the original rejection,
                // which is why attaching one counts as handling this promise
                derived.settle(true, value, null);
            } else {
                AsyncSupport.resolveValue(derived, value, null);
            }
        });
        return derived;
    }

}
