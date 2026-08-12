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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * One invocation of an {@code async} function, running its body on its own
 * virtual thread under the engine's {@code jsLock}.
 * <p>
 * The caller creates the result promise, spawns this activation, releases
 * {@code jsLock} and parks on the <b>startup-outcome cell</b> — a
 * single-assignment {@code SUSPENDED | COMPLETED | FAILED} slot, not a bare
 * latch, so the caller can tell "ran to completion" from "parked at its first
 * unresolved await" from "never got off the ground". That handshake is what
 * gives spec-shaped synchronous-start semantics without the rev-1 deadlock:
 * the caller gets its promise back the moment the body suspends.
 */
final class AsyncActivation implements Runnable {

    private static final AtomicLong IDS = new AtomicLong();

    /**
     * JVM-wide, monotonic, never reset: false until some engine somewhere spawns
     * an activation. While false, the interpreter's per-loop-iteration poll
     * skips the current-activation lookup entirely, so purely synchronous
     * scripts pay nothing for the async machinery. Same shape as
     * {@code Prototype.anyUserProps}.
     */
    static volatile boolean anyAsync;

    private static final ThreadLocal<AsyncActivation> CURRENT = new ThreadLocal<>();

    enum Outcome {
        /** Parked at its first unresolved await; the caller may proceed. */
        SUSPENDED,
        /** Body ran to completion without suspending. */
        COMPLETED,
        /** Failed before or during startup; the result promise is already settled. */
        FAILED
    }

    final Engine engine;
    final AsyncScope scope;
    private final AsyncToken token;
    private final JsFunctionNode function;
    private final CoreContext functionContext;
    private final Object[] args;
    private final JsPromise result;

    private final AtomicReference<Outcome> outcome = new AtomicReference<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile Thread caller;
    private volatile Thread thread;

    AsyncActivation(Engine engine, AsyncScope scope, AsyncToken token, JsFunctionNode function,
                    CoreContext functionContext, Object[] args, JsPromise result) {
        this.engine = engine;
        this.scope = scope;
        this.token = token;
        this.function = function;
        this.functionContext = functionContext;
        this.args = args;
        this.result = result;
    }

    /** The activation whose body is running on this thread, or null (eval
     *  thread, job execution, host thread). */
    static AsyncActivation current() {
        return anyAsync ? CURRENT.get() : null;
    }

    /**
     * Spawn and hand over. Runs on the caller's thread, holding {@code jsLock}.
     * Ownership of {@code token} transfers to the activation only once its
     * thread has actually started — until then the caller owns the unwind.
     */
    static JsPromise spawn(Engine engine, AsyncScope scope, JsFunctionNode function,
                           CoreContext functionContext, Object[] args) {
        anyAsync = true;
        JsPromise promise = new JsPromise(engine, scope);
        AsyncToken token = scope.acquire("activation");
        if (token == null) {
            // scope already closed — no JS may start here
            throw new EngineInterruptedException();
        }
        AsyncActivation activation =
                new AsyncActivation(engine, scope, token, function, functionContext, args, promise);
        activation.caller = Thread.currentThread();
        scope.addActivation(activation);
        Thread vthread;
        try {
            vthread = Thread.ofVirtual()
                    .name("js-async-", IDS.incrementAndGet())
                    .unstarted(activation);
            activation.thread = vthread;
            vthread.start();
        } catch (Throwable e) {
            // start failure: the caller releases the token and settles the promise
            scope.removeActivation(activation);
            promise.settle(true, AsyncSupport.reasonOf(e), token);
            activation.finished.countDown();
            return promise;
        }
        // release jsLock BEFORE parking — lock ownership from here belongs to
        // the activation (or nobody), and an interrupted outcome-wait must never
        // execute an unmatched unlock()
        int holds = engine.releaseJsLock();
        try {
            activation.awaitOutcome();
        } catch (EngineInterruptedException e) {
            scope.requestCancel("caller interrupted while starting " + function.name);
            throw e;
        }
        engine.reacquireJsLock(holds);
        return promise;
    }

    private void awaitOutcome() {
        while (outcome.get() == null) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new EngineInterruptedException();
            }
            LockSupport.park();
        }
    }

    /** Single CAS. A {@code finally} that finds an outcome already published is a
     *  harmless failed CAS, never a second publication. */
    private void publish(Outcome value) {
        if (outcome.compareAndSet(null, value)) {
            LockSupport.unpark(caller);
        }
    }

    @Override
    public void run() {
        CURRENT.set(this);
        try {
            runBody();
        } finally {
            CURRENT.remove();
            // last-resort publication so a caller can never park forever
            publish(Outcome.FAILED);
            token.release();
            scope.removeActivation(this);
            finished.countDown();
        }
    }

    private void runBody() {
        // gate check BEFORE acquiring jsLock as well as after: a stale activation
        // must not even contend for the lock a dying scope's teardown depends on
        if (!scope.isOpen()) {
            publish(Outcome.FAILED);
            return;
        }
        try {
            engine.jsLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // never started, so no JS ran and nothing is owed to the scope
            result.settle(true, AsyncSupport.cancelledReason(), null);
            publish(Outcome.FAILED);
            return;
        }
        Engine previous = Engine.enter(engine);
        try {
            // gate check after acquiring jsLock — a stale activation exits
            // without executing any JS
            if (!scope.isOpen()) {
                return;
            }
            Object value = function.executeBody(functionContext, functionContext, args);
            if (functionContext.isError()) {
                Object reason = functionContext.getErrorThrown();
                functionContext.reset();
                settle(true, reason);
            } else {
                settle(false, value);
            }
            publish(Outcome.COMPLETED);
        } catch (Throwable e) {
            if (e instanceof FlowControlSignal || AsyncSupport.isHostCancellation(e)) {
                // host cancellation must never become a catchable promise
                // rejection — leave the promise pending, the scope is dying
                publish(Outcome.FAILED);
            } else if (e instanceof Error) {
                // a fatal internal failure: request cancellation through the
                // facade (an atomic mark + control-job enqueue that wakes the
                // pump owner) and exit — this thread must not tear down a scope
                // whose teardown would join it
                scope.requestCancel("fatal error in " + this + ": " + e);
                publish(Outcome.FAILED);
            } else {
                settle(true, AsyncSupport.reasonOf(e));
                publish(Outcome.FAILED);
            }
        } finally {
            Engine.exit(previous);
            // an awaited suspension that unwound abruptly released the lock
            // already, so only unlock what this thread still holds
            if (engine.jsLock.isHeldByCurrentThread()) {
                engine.releaseJsLock();
            }
        }
    }

    /** Settlement retires this activation's token via publishSuccessor, so the
     *  reaction jobs it enqueues exist before the token that permitted them
     *  goes away. */
    private void settle(boolean rejected, Object value) {
        if (rejected) {
            result.settle(true, value, token);
        } else {
            AsyncSupport.resolveValue(result, value, token);
        }
    }

    /**
     * Suspend at an unresolved await. Arm the resumption first (an unpark permit
     * survives an unpark-before-park race; a condition flag would not), publish
     * SUSPENDED so the caller can proceed, then release {@code jsLock} in a
     * {@code finally} — an exception between arming and unlocking must not
     * strand the engine.
     */
    Object awaitOn(JsPromise promise) {
        CompletableFuture<Object> cf = promise.future();
        if (cf.isDone()) {
            // settled-await fast path: no microtask yield (accepted deviation)
            return AsyncSupport.settledResult(promise);
        }
        Thread self = Thread.currentThread();
        int holds;
        cf.whenComplete((value, error) -> LockSupport.unpark(self));
        publish(Outcome.SUSPENDED);
        try {
            AsyncSupport.checkStale(scope);
        } finally {
            holds = engine.releaseJsLock();
        }
        while (!cf.isDone()) {
            if (Thread.interrupted() || !scope.isOpen()) {
                Thread.currentThread().interrupt();
                throw new EngineInterruptedException();
            }
            LockSupport.park();
        }
        // gate check before AND after reacquiring jsLock
        AsyncSupport.checkStale(scope);
        engine.reacquireJsLock(holds);
        AsyncSupport.checkStale(scope);
        return AsyncSupport.settledResult(promise);
    }

    /** Shut the reacquisition gate for this activation: it exits at its next
     *  poll, blocking point, or host-call return. */
    void cancel() {
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            LockSupport.unpark(t);
        }
    }

    /**
     * Bounded but <b>uninterruptible</b> join. The teardown thread is usually the
     * one that was interrupted in the first place, and a further interrupt may
     * land at any point in the join sequence; abandoning the wait there would
     * report a perfectly cooperative activation as stuck and poison the engine
     * for it. Interruption is absorbed, the deadline still holds, and the
     * interrupt status is re-asserted on the way out.
     */
    boolean awaitTermination(long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        boolean interrupted = false;
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return finished.getCount() == 0;
                }
                try {
                    return finished.await(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String toString() {
        Thread t = thread;
        return "activation[" + (function.name == null ? "anonymous" : function.name)
                + "," + (t == null ? "unstarted" : t.getName()) + "]";
    }

}
