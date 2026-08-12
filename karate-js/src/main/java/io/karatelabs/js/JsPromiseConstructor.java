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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global {@code Promise} constructor. The executor runs <i>synchronously</i>
 * under {@code jsLock}; the {@code resolve} / {@code reject} functions it
 * receives are thread-safe and may be retained and called later from any
 * thread — a foreign caller only completes the future and enqueues a job.
 */
class JsPromiseConstructor extends JsFunction {

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    /** Held so the combinators can identity-check whether {@code Promise.resolve}
     *  is still the untampered built-in and skip the generic call machinery. */
    private final JsCallable resolveDelegate = this::resolveStatic;

    JsPromiseConstructor() {
        this.name = "Promise";
        this.length = 1;
        defineOwn("prototype", JsPromisePrototype.INSTANCE, PropertySlot.INTRINSIC);
        defineOwn("resolve", new JsBuiltinMethod("resolve", 1, resolveDelegate), METHOD_ATTRS);
        defineOwn("reject", new JsBuiltinMethod("reject", 1, (JsCallable) this::rejectStatic), METHOD_ATTRS);
        defineOwn("all", new JsBuiltinMethod("all", 1, (JsCallable) this::all), METHOD_ATTRS);
        defineOwn("allSettled", new JsBuiltinMethod("allSettled", 1, (JsCallable) this::allSettled), METHOD_ATTRS);
        defineOwn("race", new JsBuiltinMethod("race", 1, (JsCallable) this::race), METHOD_ATTRS);
        defineOwn("any", new JsBuiltinMethod("any", 1, (JsCallable) this::any), METHOD_ATTRS);
    }

    private static Engine engineOf(Context context) {
        Engine engine = context == null ? null : context.getEngine();
        if (engine == null) {
            engine = Engine.current();
        }
        if (engine == null) {
            throw JsErrorException.typeError("Promise requires an engine");
        }
        return engine;
    }

    private static AsyncScope scopeOf(Engine engine) {
        AsyncScope scope = engine.currentScope();
        if (scope == null) {
            throw JsErrorException.typeError("Promise requires an active evaluation");
        }
        return scope;
    }

    static JsPromise newPromise(Context context) {
        Engine engine = engineOf(context);
        AsyncActivation.anyAsync = true;
        return new JsPromise(engine, scopeOf(engine));
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        if (callInfo == null || !callInfo.constructor) {
            throw JsErrorException.typeError("Promise constructor cannot be invoked without 'new'");
        }
        Object executor = AsyncSupport.arg(args, 0);
        if (!(executor instanceof JsCallable callable)) {
            throw JsErrorException.typeError("Promise resolver is not a function");
        }
        JsPromise promise = newPromise(context);
        AtomicBoolean claimed = new AtomicBoolean();
        JsCallable resolveFn = (ctx, a) -> {
            if (claimed.compareAndSet(false, true)) {
                AsyncSupport.settleFromCallback(promise, AsyncSupport.arg(a, 0), false);
            }
            return Terms.UNDEFINED;
        };
        JsCallable rejectFn = (ctx, a) -> {
            if (claimed.compareAndSet(false, true)) {
                AsyncSupport.settleFromCallback(promise, AsyncSupport.arg(a, 0), true);
            }
            return Terms.UNDEFINED;
        };
        AsyncSupport.Completion outcome = AsyncSupport.invoke(
                promise.engine, callable, new Object[]{resolveFn, rejectFn}, null);
        if (outcome.threw() && claimed.compareAndSet(false, true)) {
            promise.settle(true, outcome.reason(), null);
        }
        return promise;
    }

    //=== statics ======================================================================================================

    private Object resolveStatic(Context context, Object[] args) {
        Object value = AsyncSupport.arg(args, 0);
        // native-promise identity is preserved
        if (value instanceof JsPromise promise) {
            return promise;
        }
        // a Java stage is not a JS thenable — it crosses once per scope and
        // keeps the identity the scope cache assigned it
        if (value instanceof java.util.concurrent.CompletionStage<?> stage) {
            Engine engine = engineOf(context);
            return AsyncSupport.wrapStage(engine, scopeOf(engine), stage);
        }
        JsPromise promise = newPromise(context);
        AsyncSupport.resolveValue(promise, value, null);
        return promise;
    }

    private Object rejectStatic(Context context, Object[] args) {
        JsPromise promise = newPromise(context);
        promise.settle(true, AsyncSupport.arg(args, 0), null);
        return promise;
    }

    //=== combinator prologue ==========================================================================================

    /**
     * Carries an abrupt completion out of the combinator loop so the single
     * catch site can run IteratorClose and reject the result promise. The
     * engine signals a JS {@code throw} cooperatively (a stop-flag on the
     * context) rather than by unwinding, so the flag is converted here — the
     * loop has to leave the context clean either way, since the combinator
     * itself completes normally with a rejected promise.
     */
    private static final class Abrupt extends RuntimeException {

        final transient Object reason;

        Abrupt(Object reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }

    /** Converts a pending cooperative throw into an {@link Abrupt}, clearing the
     *  flag — from here on the error travels as the rejection reason. */
    private static void checkAbrupt(Context context) {
        if (context instanceof CoreContext cc && cc.isError()) {
            Object reason = cc.getErrorThrown();
            cc.reset();
            throw new Abrupt(reason);
        }
    }

    /** IteratorClose with the surrounding completion already abrupt: whatever
     *  {@code return()} does is discarded, the original reason wins. */
    private static void closeQuietly(JsIterator iter, Context context) {
        try {
            iter.close(context, true);
        } catch (RuntimeException e) {
            if (e instanceof FlowControlSignal || AsyncSupport.isHostCancellation(e)) {
                throw e;
            }
            // IteratorClose failing is swallowed per spec 7.4.6 step 4
        }
        if (context instanceof CoreContext cc && cc.isStopped()) {
            cc.reset(); // close() re-parks the completion it saw — we own the reason now
        }
    }

    private static Object callWithThis(Context context, JsCallable fn, Object thisObj, Object[] args) {
        if (context instanceof CoreContext cc) {
            Object savedThis = cc.thisObject;
            cc.thisObject = thisObj;
            try {
                return fn.call(cc, args);
            } finally {
                cc.thisObject = savedThis;
            }
        }
        return fn.call(context, args);
    }

    private static CoreContext coreOf(Context context) {
        return context instanceof CoreContext cc ? cc : null;
    }

    /**
     * The combinator's {@code this} value, i.e. spec {@code C}. Honored so
     * {@code Promise.all.call(C, …)} consults C's own {@code resolve}; an absent
     * receiver (a Java host call) falls back to the intrinsic. The result promise
     * is still always a native one — NewPromiseCapability over a subclass
     * constructor is not modelled — but IsConstructor(C) is enforced, since that
     * is the one part of the capability step observable from here.
     */
    private ObjectLike constructorOf(Context context) {
        Object thisValue = context.getThisObject();
        if (thisValue == null || thisValue == Terms.UNDEFINED || thisValue == this) {
            return this;
        }
        if (thisValue instanceof ObjectLike obj
                && thisValue instanceof JsCallable callable && callable.isConstructable()) {
            return obj;
        }
        throw JsErrorException.typeError("Promise combinator called on a non-constructor");
    }

    /**
     * The spec's {@code Invoke(nextPromise, "then", «resolveElement, reject»)}.
     * Fast path: the element is a native promise still resolving the untampered
     * {@code Promise.prototype.then}, so nothing observable happens and the
     * reaction can be registered on it directly. Otherwise {@code then} really is
     * user code — read it and call it here, so a throwing getter or a throwing
     * {@code then} aborts the combinator loop rather than being absorbed into
     * the element promise (which, with an endless iterator, never terminates).
     * <p>
     * {@code intrinsic} says the value came from the untampered
     * {@code Promise.resolve}, which always hands back a native promise. When it
     * did not, the spec's {@code Invoke} is the whole story: whatever a custom
     * {@code C.resolve} returned must have a callable {@code then}, and a
     * primitive — or an object without one — is a {@code TypeError} that rejects
     * the combinator, not something to wrap and fulfil with.
     */
    private static JsPromise asPromise(Context context, Engine engine, AsyncScope scope,
                                       Object value, boolean intrinsic) {
        Object thenFn = null;
        if (value instanceof ObjectLike obj) {
            thenFn = obj.getMember("then", obj, coreOf(context));
            checkAbrupt(context);
        }
        if (value instanceof JsPromise promise && JsPromisePrototype.isIntrinsicThen(thenFn)) {
            return promise; // native identity is preserved
        }
        JsPromise wrapper = new JsPromise(engine, scope);
        if (thenFn instanceof JsCallable thenCallable) {
            AtomicBoolean claimed = new AtomicBoolean();
            JsCallable resolveFn = (ctx, a) -> {
                if (claimed.compareAndSet(false, true)) {
                    AsyncSupport.settleFromCallback(wrapper, AsyncSupport.arg(a, 0), false);
                }
                return Terms.UNDEFINED;
            };
            JsCallable rejectFn = (ctx, a) -> {
                if (claimed.compareAndSet(false, true)) {
                    AsyncSupport.settleFromCallback(wrapper, AsyncSupport.arg(a, 0), true);
                }
                return Terms.UNDEFINED;
            };
            callWithThis(context, thenCallable, value, new Object[]{resolveFn, rejectFn});
            checkAbrupt(context);
            return wrapper;
        }
        if (!intrinsic) {
            throw JsErrorException.typeError("Promise resolve did not return a thenable");
        }
        if (value instanceof ObjectLike) {
            // `then` was already read above — don't let resolveValue read it a
            // second time (an accessor would fire twice)
            wrapper.settle(false, value, null);
            return wrapper;
        }
        AsyncSupport.resolveValue(wrapper, value, null);
        return wrapper;
    }

    /**
     * The shared combinator prologue — GetPromiseResolve, GetIterator, and the
     * per-element {@code resolve} step (spec 27.2.4.1.1 / 27.2.4.1.2).
     * <p>
     * Every call into user code here can complete abruptly: the {@code resolve}
     * lookup (an accessor), {@code resolve} itself, the {@code @@iterator}
     * method, {@code next()}, and the {@code done} / {@code value} getters. On
     * any of them the iterator is closed (only when it is not already exhausted
     * — an iterator-side throw leaves {@code [[done]]} true, so {@code return()}
     * must <i>not</i> run) and {@code result} is rejected with that reason,
     * mirroring the spec's IfAbruptRejectPromise. Without this the loop had no
     * abort path at all: an iterator that never reports done plus a poisoned
     * {@code Promise.resolve} spun forever.
     *
     * @return the element promises, or {@code null} once {@code result} has been
     * rejected — the caller then simply returns {@code result}
     */
    private List<JsPromise> collect(Context context, Object[] args, JsPromise result) {
        Engine engine = engineOf(context);
        AsyncScope scope = scopeOf(engine);
        AsyncActivation.anyAsync = true;
        // NewPromiseCapability(C) rejects a non-constructor `this` with a
        // synchronous TypeError, ahead of everything the IfAbruptRejectPromise
        // steps below cover. Outside the try for exactly that reason.
        ObjectLike receiver = constructorOf(context);
        Object iterable = AsyncSupport.arg(args, 0);
        List<JsPromise> promises = new ArrayList<>();
        JsIterator iter = null;
        try {
            // GetPromiseResolve: read `resolve` off the constructor exactly once,
            // before iteration begins.
            Object resolveFn = receiver.getMember("resolve", receiver, coreOf(context));
            checkAbrupt(context);
            if (!(resolveFn instanceof JsCallable resolveCallable)) {
                throw JsErrorException.typeError("Promise resolve is not a function");
            }
            boolean intrinsic = receiver == this
                    && resolveFn instanceof JsBuiltinMethod m && m.delegate() == resolveDelegate;
            iter = IterUtils.getIterator(iterable, context);
            checkAbrupt(context);
            while (iter.hasNext()) {
                Object item = iter.next();
                checkAbrupt(context);
                Object next = intrinsic
                        ? resolveStatic(context, new Object[]{item})
                        : callWithThis(context, resolveCallable, receiver, new Object[]{item});
                checkAbrupt(context);
                promises.add(asPromise(context, engine, scope, next, intrinsic));
            }
            // A throw from next() / done / value marks the iterator exhausted and
            // ends the loop quietly — the pending flag is the real completion.
            checkAbrupt(context);
            return promises;
        } catch (RuntimeException e) {
            if (e instanceof FlowControlSignal || AsyncSupport.isHostCancellation(e)) {
                throw e;
            }
            if (iter != null) {
                closeQuietly(iter, context);
            }
            result.settle(true, e instanceof Abrupt abrupt ? abrupt.reason : AsyncSupport.reasonOf(e), null);
            return null;
        }
    }

    //=== combinators ==================================================================================================

    private Object all(Context context, Object[] args) {
        JsPromise result = newPromise(context);
        List<JsPromise> promises = collect(context, args, result);
        if (promises == null) {
            return result;
        }
        int total = promises.size();
        if (total == 0) {
            AsyncSupport.resolveValue(result, new JsArray(new ArrayList<>()), null);
            return result;
        }
        List<Object> values = new ArrayList<>(java.util.Collections.nCopies(total, Terms.UNDEFINED));
        AtomicInteger remaining = new AtomicInteger(total);
        for (int i = 0; i < total; i++) {
            int index = i;
            promises.get(i).addReaction((rejected, value) -> {
                if (rejected) {
                    result.settle(true, value, null);
                    return;
                }
                values.set(index, value);
                if (remaining.decrementAndGet() == 0) {
                    result.settle(false, new JsArray(values), null);
                }
            });
        }
        return result;
    }

    private Object allSettled(Context context, Object[] args) {
        JsPromise result = newPromise(context);
        List<JsPromise> promises = collect(context, args, result);
        if (promises == null) {
            return result;
        }
        int total = promises.size();
        if (total == 0) {
            result.settle(false, new JsArray(new ArrayList<>()), null);
            return result;
        }
        List<Object> values = new ArrayList<>(java.util.Collections.nCopies(total, Terms.UNDEFINED));
        AtomicInteger remaining = new AtomicInteger(total);
        for (int i = 0; i < total; i++) {
            int index = i;
            promises.get(i).addReaction((rejected, value) -> {
                JsObject entry = new JsObject();
                if (rejected) {
                    entry.putMember("status", "rejected");
                    entry.putMember("reason", value);
                } else {
                    entry.putMember("status", "fulfilled");
                    entry.putMember("value", value);
                }
                values.set(index, entry);
                if (remaining.decrementAndGet() == 0) {
                    result.settle(false, new JsArray(values), null);
                }
            });
        }
        return result;
    }

    private Object race(Context context, Object[] args) {
        JsPromise result = newPromise(context);
        List<JsPromise> promises = collect(context, args, result);
        if (promises == null) {
            return result;
        }
        for (JsPromise promise : promises) {
            promise.addReaction((rejected, value) -> {
                if (rejected) {
                    result.settle(true, value, null);
                } else {
                    AsyncSupport.resolveValue(result, value, null);
                }
            });
        }
        return result;
    }

    private Object any(Context context, Object[] args) {
        JsPromise result = newPromise(context);
        List<JsPromise> promises = collect(context, args, result);
        if (promises == null) {
            return result;
        }
        int total = promises.size();
        if (total == 0) {
            result.settle(true, aggregateError(new ArrayList<>()), null);
            return result;
        }
        List<Object> errors = new ArrayList<>(java.util.Collections.nCopies(total, Terms.UNDEFINED));
        AtomicInteger remaining = new AtomicInteger(total);
        for (int i = 0; i < total; i++) {
            int index = i;
            promises.get(i).addReaction((rejected, value) -> {
                if (!rejected) {
                    result.settle(false, value, null);
                    return;
                }
                errors.set(index, value);
                if (remaining.decrementAndGet() == 0) {
                    result.settle(true, aggregateError(errors), null);
                }
            });
        }
        return result;
    }

    private static JsError aggregateError(List<Object> errors) {
        JsError error = new JsError(JsErrorPrototype.AGGREGATE_ERROR);
        byte attrs = (byte) (WRITABLE | CONFIGURABLE);
        error.defineOwn("message", "All promises were rejected", attrs);
        error.defineOwn("errors", new JsArray(errors), attrs);
        return error;
    }

}
