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

import io.karatelabs.common.KarateLifecycle;
import io.karatelabs.common.ThreadUtils;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The async runtime: activation spawn, await semantics, promise adoption, the
 * job pump, timers, and the unhandled-rejection policy. Everything here runs
 * either on the engine thread (holding {@code jsLock}) or, where explicitly
 * marked, on a foreign completion thread that may only complete a
 * {@code CompletableFuture}, enqueue a job, or unpark a thread.
 */
final class AsyncSupport {

    private static final Logger logger = LoggerFactory.getLogger(AsyncSupport.class);

    /** Pump wake-up interval — bounds how long the drain sleeps before
     *  re-checking interruption, cancellation and the drain cap. */
    private static final long POLL_MILLIS = 25;

    private static final Object[] EMPTY_ARGS = new Object[0];

    private static final Node JOB_NODE = new Node(NodeType.ROOT);

    private AsyncSupport() {
    }

    //=== small helpers ================================================================================================

    /** True when the caller is the thread currently entitled to run JS. */
    static boolean onEngineThread(Engine engine) {
        return engine.jsLock.isHeldByCurrentThread();
    }

    /** Host cancellation (including the drain cap) — must be rethrown before any
     *  catch-and-reject conversion at every async boundary. */
    static boolean isHostCancellation(Throwable e) {
        return e instanceof EngineInterruptedException;
    }

    static void checkStale(AsyncScope scope) {
        if (!scope.isOpen()) {
            throw new EngineInterruptedException();
        }
    }

    /**
     * Post-host-return fence. The gate checks around suspension cover parked
     * activations; an activation already inside a host call has passed them, and
     * host code may swallow or clear the interrupt. So every host-call return
     * re-tests scope closure before any further JS runs or anything is settled.
     */
    static void hostReturnFence() {
        AsyncActivation activation = AsyncActivation.current();
        if (activation != null && !activation.scope.isOpen()) {
            Thread.currentThread().interrupt();
            throw new EngineInterruptedException();
        }
    }

    static Object reasonOf(Throwable e) {
        if (e instanceof JsErrorException jex) {
            return jex.payload;
        }
        if (e instanceof JsRejectionException jre) {
            return jre.getReason();
        }
        return Interpreter.buildCaughtError(e);
    }

    static Object cancelledReason() {
        return JsErrorException.error("async activation cancelled").payload;
    }

    /** Signals an awaited rejection back to {@code AWAIT_EXPR}, which re-throws
     *  it through the ordinary JS throw path so {@code try/catch} sees it. */
    static final class AwaitRejection extends RuntimeException {

        final transient Object reason;

        AwaitRejection(Object reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }

    /** The value of a settled promise; a rejection unwinds as a JS throw. Also
     *  the point where observing a rejection marks it handled. */
    static Object settledResult(JsPromise promise) {
        promise.markRejectionHandled();
        Object value = promise.settledValue();
        if (promise.isRejected()) {
            throw new AwaitRejection(value);
        }
        return value;
    }

    static CoreContext newJobContext(Engine engine) {
        return new CoreContext(engine.root(), null, 0, JOB_NODE, ContextScope.GLOBAL, engine.bindings);
    }

    /** Outcome of invoking a JS callback from engine code: either a value, or
     *  the JS value it threw. */
    record Completion(Object value, Object reason, boolean threw) {
    }

    /**
     * Invoke a JS callable from engine code, under {@code jsLock}. Both throw
     * shapes are normalized — the cooperative {@code CoreContext} error state
     * used by the interpreter, and a Java {@link JsErrorException} raised by a
     * built-in. Host cancellation and flow-control signals are rethrown first.
     */
    static Completion invoke(Engine engine, JsCallable fn, Object[] args, Object thisObject) {
        CoreContext ctx = newJobContext(engine);
        if (thisObject != null) {
            ctx.thisObject = thisObject;
        }
        try {
            Object value = fn.call(ctx, args);
            hostReturnFence();
            if (ctx.isError()) {
                return new Completion(null, ctx.getErrorThrown(), true);
            }
            return new Completion(value, null, false);
        } catch (RuntimeException e) {
            if (e instanceof FlowControlSignal || isHostCancellation(e)) {
                throw e;
            }
            if (e instanceof AwaitRejection ar) {
                return new Completion(null, ar.reason, true);
            }
            return new Completion(null, reasonOf(e), true);
        }
    }

    //=== activations ==================================================================================================

    /** Entry point from {@link JsFunctionNode#bindArgsAndExecute}: an async
     *  invocation returns its promise, never its body's value. */
    static Object callAsync(JsFunctionNode function, CoreContext functionContext, Object[] args) {
        Engine engine = functionContext.getEngine();
        if (engine == null) {
            throw JsErrorException.typeError("async function called outside an engine evaluation");
        }
        AsyncScope scope = engine.currentScope();
        if (scope != null) {
            return AsyncActivation.spawn(engine, scope, function, functionContext, args);
        }
        return hostAsyncCall(engine, function, functionContext, args);
    }

    /**
     * A Java caller invoking an async JS function directly, outside any eval:
     * open a scope for the call, spawn, drain to quiescence, and hand back the
     * promise (never an auto-awaited value — the host decides).
     */
    private static Object hostAsyncCall(Engine engine, JsFunctionNode function,
                                        CoreContext functionContext, Object[] args) {
        engine.checkPoisoned();
        boolean outermost = engine.enterEvalScope();
        AsyncScope scope = engine.currentScope();
        try {
            JsPromise promise;
            engine.jsLock.lock();
            Engine previous = Engine.enter(engine);
            try {
                promise = AsyncActivation.spawn(engine, scope, function, functionContext, args);
            } finally {
                Engine.exit(previous);
                if (engine.jsLock.isHeldByCurrentThread()) {
                    engine.jsLock.unlock();
                }
            }
            drain(engine, scope);
            // the host holds the promise, so its rejection is theirs to observe
            promise.markRejectionHandled();
            reportUnhandledRejections(engine, scope);
            return promise;
        } finally {
            if (outermost) {
                engine.closeScope(scope, "host async call complete");
            }
            engine.exitEvalScope(outermost);
        }
    }

    //=== await ========================================================================================================

    /** {@code await expr}. A non-promise passes through unchanged; a promise
     *  suspends the activation (or pumps, at top level) and resumes with the
     *  fulfillment value, re-throwing a rejection through the JS throw path. */
    static Object await(Object value, CoreContext context) {
        Engine engine = context.getEngine();
        AsyncScope scope = engine == null ? null : engine.currentScope();
        if (scope == null) {
            return value;
        }
        JsPromise promise = toPromise(engine, scope, value);
        if (promise == null) {
            return value;
        }
        AsyncActivation activation = AsyncActivation.current();
        if (activation != null && activation.engine == engine) {
            return activation.awaitOn(promise);
        }
        if (promise.isSettled()) {
            return settledResult(promise);
        }
        // top level: the eval thread becomes the pump, which is what lets
        // activations and callbacks interleave with top-level code
        return pumpUntilSettled(engine, scope, promise);
    }

    /** JsPromise / CompletionStage / thenable → a promise; anything else → null
     *  (await passthrough). */
    static JsPromise toPromise(Engine engine, AsyncScope scope, Object value) {
        if (value instanceof JsPromise promise) {
            return promise;
        }
        if (value instanceof CompletionStage<?> stage) {
            return wrapStage(engine, scope, stage);
        }
        if (value instanceof ObjectLike obj) {
            Object thenFn = readThen(engine, obj);
            if (thenFn instanceof JsCallable callable) {
                JsPromise target = new JsPromise(engine, scope);
                adoptThenable(target, obj, callable, null);
                return target;
            }
        }
        return null;
    }

    //=== the pump =====================================================================================================

    /** Run one job: acquire jsLock, establish {@code current()}, run, and
     *  release the job's token in a {@code finally} so a throwing job cannot
     *  strand the pump. */
    static void runJob(Engine engine, AsyncJob job) {
        engine.jsLock.lock();
        Engine previous = Engine.enter(engine);
        try {
            job.run(engine);
        } finally {
            Engine.exit(previous);
            engine.jsLock.unlock();
            if (job.token != null) {
                job.token.release();
            }
        }
    }

    /** End-of-eval drain + top-level result unwrap + unhandled-rejection
     *  reporting. Runs on the pump owner (the outermost eval thread), holding no
     *  locks on entry. */
    static Object finishScope(Engine engine, AsyncScope scope, Object result) {
        drain(engine, scope);
        if (result instanceof JsPromise promise) {
            // observed as the eval's own result, so never double-reported
            promise.markRejectionHandled();
            reportUnhandledRejections(engine, scope);
            if (!promise.isSettled()) {
                return promise;
            }
            Object value = promise.settledValue();
            if (promise.isRejected()) {
                // Same policy as any other unhandled rejection — warn-only mode
                // downgrades it and hands the reason back as the eval's result.
                reportFailure(engine, "uncaught (in promise)", value);
            }
            return value;
        }
        reportUnhandledRejections(engine, scope);
        return result;
    }

    static void drain(Engine engine, AsyncScope scope) {
        long deadlineNanos = drainDeadline(engine);
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new EngineInterruptedException();
            }
            if (scope.isCancelRequested()) {
                throw new EngineInterruptedException();
            }
            if (scope.isQuiescent()) {
                return;
            }
            if (deadlineNanos != 0 && System.nanoTime() > deadlineNanos) {
                throw new EngineTimeoutException("async drain timed out");
            }
            AsyncJob job = scope.takeJob(POLL_MILLIS);
            if (job instanceof AsyncJob.Control control) {
                // teardown is the pump owner's job, never the requesting
                // activation's — it would be joining its own thread
                job.token.release();
                throw new EngineInterruptedException(control.reason);
            }
            if (job != null) {
                runJob(engine, job);
            }
        }
    }

    private static long drainDeadline(Engine engine) {
        Duration cap = engine.asyncDrainTimeout();
        return cap == null ? 0 : System.nanoTime() + cap.toNanos();
    }

    /** Top-level await: release jsLock, pump, and re-check the target. */
    static Object pumpUntilSettled(Engine engine, AsyncScope scope, JsPromise promise) {
        long deadlineNanos = drainDeadline(engine);
        int holds = engine.releaseJsLock();
        // On an abrupt exit the lock is deliberately NOT reacquired: a running
        // activation may hold it, and this thread is on its way to tearing the
        // scope down. Every enclosing frame unlocks conditionally for that reason.
        while (!promise.isSettled()) {
            if (Thread.currentThread().isInterrupted() || scope.isCancelRequested()) {
                throw new EngineInterruptedException();
            }
            if (deadlineNanos != 0 && System.nanoTime() > deadlineNanos) {
                throw new EngineTimeoutException("async drain timed out");
            }
            AsyncJob job = scope.takeJob(POLL_MILLIS);
            if (job instanceof AsyncJob.Control control) {
                job.token.release();
                throw new EngineInterruptedException(control.reason);
            }
            if (job != null) {
                runJob(engine, job);
            } else if (scope.isQuiescent() && !promise.isSettled()) {
                throw new EngineException("await on a promise that can never settle", null);
            }
        }
        engine.reacquireJsLock(holds);
        return settledResult(promise);
    }

    //=== adoption =====================================================================================================

    /**
     * The one adoption operation, shared by the {@code Promise} constructor's
     * resolve, {@code Promise.resolve}, async-function return, {@code .then}
     * results and {@code CompletionStage} wrapping. JS-level rules run before
     * any CF composition.
     */
    static void resolveValue(JsPromise target, Object value, AsyncToken retiring) {
        if (value == target) {
            target.settle(true, JsErrorException.typeError("Chaining cycle detected for promise").payload, retiring);
            return;
        }
        if (value instanceof JsPromise inner) {
            adopt(target, inner, retiring);
            return;
        }
        if (value instanceof CompletionStage<?> stage) {
            adopt(target, wrapStage(target.engine, target.scope, stage), retiring);
            return;
        }
        if (value instanceof ObjectLike obj) {
            Object thenFn;
            try {
                thenFn = readThen(target.engine, obj);
            } catch (RuntimeException e) {
                if (e instanceof FlowControlSignal || isHostCancellation(e)) {
                    throw e;
                }
                target.settle(true, reasonOf(e), retiring);
                return;
            }
            if (thenFn instanceof JsCallable callable) {
                adoptThenable(target, obj, callable, retiring);
                return;
            }
        }
        target.settle(false, value, retiring);
    }

    /** Read {@code then} exactly once, through a property access that may itself
     *  throw (a poisoned getter). */
    private static Object readThen(Engine engine, ObjectLike obj) {
        CoreContext ctx = newJobContext(engine);
        Object thenFn = obj.getMember("then", obj, ctx);
        if (ctx.isError()) {
            throw new JsErrorException(asJsError(ctx.getErrorThrown()));
        }
        return thenFn;
    }

    private static JsError asJsError(Object reason) {
        return reason instanceof JsError je ? je : JsErrorException.error(String.valueOf(reason)).payload;
    }

    /** Adoption, not nesting: a returned promise's eventual state becomes the
     *  target's, and the target never fulfils with a promise. */
    private static void adopt(JsPromise target, JsPromise inner, AsyncToken retiring) {
        inner.markRejectionHandled();
        if (inner.isSettled()) {
            target.settle(inner.isRejected(), inner.settledValue(), retiring);
            return;
        }
        inner.addReaction((rejected, value) -> {
            if (rejected) {
                target.settle(true, value, null);
            } else {
                resolveValue(target, value, null);
            }
        });
        if (retiring != null) {
            retiring.release();
        }
    }

    /** Thenable adoption: first call wins, repeated calls are ignored, and a
     *  {@code then} that throws after already resolving is swallowed per spec. */
    private static void adoptThenable(JsPromise target, Object thenable, JsCallable then, AsyncToken retiring) {
        AtomicBoolean claimed = new AtomicBoolean();
        JsCallable resolveFn = (ctx, args) -> {
            if (claimed.compareAndSet(false, true)) {
                settleFromCallback(target, arg(args, 0), false);
            }
            return Terms.UNDEFINED;
        };
        JsCallable rejectFn = (ctx, args) -> {
            if (claimed.compareAndSet(false, true)) {
                settleFromCallback(target, arg(args, 0), true);
            }
            return Terms.UNDEFINED;
        };
        Completion outcome = invoke(target.engine, then, new Object[]{resolveFn, rejectFn}, thenable);
        if (outcome.threw() && claimed.compareAndSet(false, true)) {
            target.settle(true, outcome.reason(), null);
        }
        if (retiring != null) {
            retiring.release();
        }
    }

    /**
     * The resolve/reject functions handed to JS (thenables, {@code new
     * Promise(executor)}) are callable from any thread. A foreign caller may not
     * run JS, so its resolution is routed back through the job queue.
     */
    static void settleFromCallback(JsPromise target, Object value, boolean rejected) {
        if (onEngineThread(target.engine)) {
            if (rejected) {
                target.settle(true, value, null);
            } else {
                resolveValue(target, value, null);
            }
            return;
        }
        target.scope.publish(new AsyncJob.Microtask("resolve", () -> {
            if (rejected) {
                target.settle(true, value, null);
            } else {
                resolveValue(target, value, null);
            }
        }));
    }

    static Object arg(Object[] args, int index) {
        return args != null && args.length > index ? args[index] : Terms.UNDEFINED;
    }

    //=== Java CompletionStage bridge ==================================================================================

    /**
     * Wrap a Java stage as a promise. Identity is scope-scoped; a
     * {@code JsPromise}'s own {@code toFuture()} handed back in always recovers
     * the original promise through the direct reverse association, independent
     * of the cache.
     */
    static JsPromise wrapStage(Engine engine, AsyncScope scope, CompletionStage<?> stage) {
        if (stage instanceof JsPromise.PromiseView view) {
            return view.owner;
        }
        JsPromise cached = scope.cachedStage(stage);
        if (cached != null) {
            return cached;
        }
        JsPromise promise = new JsPromise(engine, scope);
        scope.cacheStage(stage, promise);
        // an armed-but-unsettled subscription is outstanding work: the token is
        // what keeps the eval from going quiescent while a Java future is in
        // flight on some other thread
        AsyncToken token = scope.acquire("subscription");
        if (token == null) {
            return promise;
        }
        stage.whenComplete((value, error) -> {
            if (error == null) {
                promise.settle(false, value, token);
                return;
            }
            try {
                promise.settle(true, reasonFromStage(error), token);
            } catch (EngineInterruptedException e) {
                // host cancellation is never a JS reason
                scope.requestCancel("interop stage cancelled");
                token.release();
            }
        });
        return promise;
    }

    /** Java → JS unwrap rules, in order. Only the engine's own wrapper is ever
     *  unwrapped to a reason; arbitrary Java exceptions never masquerade as JS
     *  rejections. */
    static Object reasonFromStage(Throwable error) {
        Throwable e = error;
        if ((e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof JsRejectionException jre) {
            return jre.getReason();
        }
        if (e instanceof EngineInterruptedException eie) {
            throw eie;
        }
        if (e instanceof CancellationException) {
            return JsErrorException.error("stage was cancelled").payload;
        }
        return Interpreter.buildCaughtError(e);
    }

    //=== timers =======================================================================================================

    private static ScheduledExecutorService scheduler;

    private static synchronized ScheduledExecutorService scheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    ThreadUtils.daemonFactory("js-timer-", false));
            KarateLifecycle.register(KarateLifecycle.wrap("js-timer", "executor", scheduler));
        }
        return scheduler;
    }

    /** Owner resolution is from the call's own context, validated against the
     *  thread's current engine — scheduling without a definite engine is an
     *  error, not a guess. */
    private static Engine timerEngine(Context context) {
        Engine engine = context == null ? null : context.getEngine();
        if (engine == null || engine != Engine.current()) {
            throw JsErrorException.typeError("setTimeout: no engine is executing on this thread");
        }
        return engine;
    }

    static Object setTimeout(Context context, Object[] args) {
        Engine engine = timerEngine(context);
        AsyncScope scope = engine.currentScope();
        if (scope == null) {
            throw JsErrorException.typeError("setTimeout requires an active evaluation");
        }
        Object callback = arg(args, 0);
        if (!(callback instanceof JsCallable target)) {
            throw JsErrorException.typeError("setTimeout: callback is not a function");
        }
        long delay = coerceDelay(arg(args, 1));
        Object[] extra = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : EMPTY_ARGS;
        AsyncToken token = scope.acquire("timer");
        if (token == null) {
            throw new EngineInterruptedException();
        }
        AsyncActivation.anyAsync = true;
        int id = scope.nextTimerId();
        AsyncScope.TimerRecord record = new AsyncScope.TimerRecord(id, token);
        scope.addTimer(record);
        ScheduledFuture<?> future = scheduler().schedule(() -> {
            // foreign thread: may only win the CAS and publish, never run JS
            if (record.fire()) {
                scope.removeTimer(id);
                scope.publishSuccessor(record.token, AsyncJob.one(new AsyncJob.Timer(id, target, extra)));
            }
        }, delay, TimeUnit.MILLISECONDS);
        record.future = future;
        return id;
    }

    static Object clearTimeout(Context context, Object[] args) {
        Engine engine = context == null ? null : context.getEngine();
        AsyncScope scope = engine == null ? null : engine.currentScope();
        if (scope == null) {
            return Terms.UNDEFINED;
        }
        Object id = arg(args, 0);
        Number n = id instanceof Number number ? number : Terms.objectToNumber(id);
        if (n == null || Double.isNaN(n.doubleValue())) {
            return Terms.UNDEFINED;
        }
        AsyncScope.TimerRecord record = scope.removeTimer(n.intValue());
        if (record != null && record.cancel()) {
            ScheduledFuture<?> future = record.future;
            if (future != null) {
                future.cancel(false);
            }
            record.token.release();
        }
        return Terms.UNDEFINED;
    }

    /** NaN / negative / non-numeric → 0; fractional → floored. */
    static long coerceDelay(Object value) {
        Number n;
        try {
            n = Terms.objectToNumber(value);
        } catch (RuntimeException e) {
            return 0;
        }
        if (n == null) {
            return 0;
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || d <= 0) {
            return 0;
        }
        if (Double.isInfinite(d)) {
            return Integer.MAX_VALUE;
        }
        return (long) Math.floor(d);
    }

    static void invokeQueuedCallback(Engine engine, JsCallable callback, Object[] args) {
        Completion outcome = invoke(engine, callback, args, null);
        if (outcome.threw()) {
            reportFailure(engine, "uncaught exception in timer callback", outcome.reason());
        }
    }

    //=== unhandled rejections =========================================================================================

    /**
     * Decided only at scope quiescence, so a later-queued {@code .catch} counts
     * as handled. The first terminal unhandled rejection fails the eval as a
     * JS-level error; the rest are logged. Each is reported exactly once.
     */
    static void reportUnhandledRejections(Engine engine, AsyncScope scope) {
        List<JsPromise> tracked = scope.trackedRejections();
        if (tracked.isEmpty()) {
            return;
        }
        List<JsPromise> terminal = new ArrayList<>(1);
        for (JsPromise promise : tracked) {
            if (!promise.isRejectionHandled() && promise.claimRejectionReport()) {
                terminal.add(promise);
            }
        }
        if (terminal.isEmpty()) {
            return;
        }
        for (int i = 1; i < terminal.size(); i++) {
            warn(engine, "unhandled promise rejection: " + describe(terminal.get(i).settledValue()));
        }
        reportFailure(engine, "unhandled promise rejection", terminal.get(0).settledValue());
    }

    private static void reportFailure(Engine engine, String prefix, Object reason) {
        if (engine.asyncRejectionWarnOnly()) {
            warn(engine, prefix + ": " + describe(reason));
            return;
        }
        throw asEngineException(prefix, reason);
    }

    private static void warn(Engine engine, String message) {
        logger.warn(message);
        java.util.function.Consumer<String> consumer = engine.root().onConsoleLog;
        if (consumer != null) {
            consumer.accept(message);
        }
    }

    static EngineException asEngineException(String prefix, Object reason) {
        String name = null;
        String jsMessage = null;
        if (reason instanceof ObjectLike obj) {
            if (obj.getMember("name") instanceof String s && !s.isEmpty()) {
                name = s;
            }
            if (obj.getMember("message") instanceof String s) {
                jsMessage = s;
            }
        }
        if (jsMessage == null) {
            jsMessage = String.valueOf(reason);
        }
        return new EngineException(prefix + ": " + (name == null ? "" : name + ": ") + jsMessage,
                null, name, jsMessage);
    }

    private static String describe(Object reason) {
        if (reason instanceof ObjectLike obj && obj.getMember("message") instanceof String s) {
            Object name = obj.getMember("name");
            return (name instanceof String n && !n.isEmpty() ? n + ": " : "") + s;
        }
        return String.valueOf(reason);
    }

}
