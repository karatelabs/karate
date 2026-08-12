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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A JS {@code Promise}. Extends {@link JsObject} for the prototype / JS surface,
 * but its <b>settlement state lives exclusively in its
 * {@link CompletableFuture}</b> — the one field foreign threads may touch. The
 * reaction list and every JS-visible side effect are mutated only under
 * {@code jsLock}, by queued jobs.
 * <p>
 * Deliberately not a {@link JsValue}: {@code Engine.toJava} must hand a promise
 * to Java callers unchanged rather than auto-unwrapping it, so a host calling an
 * async JS function receives the promise and decides for itself whether to
 * block ({@link #await()} / {@link #join(Duration)}) or compose
 * ({@link #toFuture()}).
 */
public class JsPromise extends JsObject {

    final Engine engine;
    final AsyncScope scope;

    private final CompletableFuture<Object> cf = new CompletableFuture<>();

    /** Reactions registered before settlement. Touched only under {@code jsLock}. */
    private List<Reaction> reactions;

    /** Set the moment any reaction is attached: a rejection handler handles this
     *  promise outright, and a fulfillment-only {@code .then} transfers the
     *  rejection responsibility to the derived promise, which is tracked in its
     *  own right. Either way this promise is no longer the terminal one. */
    private boolean rejectionHandled;

    private boolean rejectionReported;

    private PromiseView javaView;

    JsPromise(Engine engine, AsyncScope scope) {
        super(null, JsPromisePrototype.INSTANCE);
        this.engine = engine;
        this.scope = scope;
    }

    //=== settlement ===================================================================================================

    /** A reaction to run when this promise settles. */
    interface Reaction {

        void react(boolean rejected, Object value);
    }

    boolean isSettled() {
        return cf.isDone();
    }

    boolean isRejected() {
        return cf.isCompletedExceptionally();
    }

    CompletableFuture<Object> future() {
        return cf;
    }

    /** The settled value, or the rejection reason. Only valid once settled. */
    Object settledValue() {
        try {
            return cf.getNow(null);
        } catch (CompletionException e) {
            return AsyncSupport.reasonOf(e.getCause());
        } catch (RuntimeException e) {
            return AsyncSupport.reasonOf(e);
        }
    }

    /**
     * Settle and hand off. Callable from any thread — the CF transition is the
     * only cross-thread write. When the caller is the engine thread the reaction
     * jobs are published inline; otherwise a flush job carries the JS-visible
     * work back onto the engine thread, per iron rule 2.
     *
     * @param retiring the token this settlement retires (an activation's, a
     *                 subscription's), released only once its successors are
     *                 enqueued — never "settle, enqueue loosely, then release"
     */
    void settle(boolean rejected, Object value, AsyncToken retiring) {
        boolean won = rejected
                ? cf.completeExceptionally(new JsRejectionException(value))
                : cf.complete(value);
        if (!won) {
            if (retiring != null) {
                retiring.release();
            }
            return;
        }
        if (AsyncSupport.onEngineThread(engine)) {
            flush(retiring);
        } else {
            scope.publishSuccessor(retiring, AsyncJob.one(
                    new AsyncJob.Microtask("settle", () -> flush(null))));
        }
    }

    /** Move the pending reactions to the queue. Runs under {@code jsLock}. */
    private void flush(AsyncToken retiring) {
        List<Reaction> pending = reactions;
        reactions = null;
        boolean rejected = isRejected();
        Object value = settledValue();
        if (rejected) {
            scope.trackRejection(this);
        }
        List<AsyncJob> jobs = null;
        if (pending != null && !pending.isEmpty()) {
            jobs = new ArrayList<>(pending.size());
            for (Reaction reaction : pending) {
                jobs.add(reactionJob(reaction, rejected, value));
            }
        }
        scope.publishSuccessor(retiring, jobs);
    }

    private AsyncJob reactionJob(Reaction reaction, boolean rejected, Object value) {
        return new AsyncJob.Microtask("reaction", () -> reaction.react(rejected, value));
    }

    /**
     * Register a reaction. Callbacks go through the queue even when the promise
     * is already settled — "sync code first, callbacks after". Runs under
     * {@code jsLock}.
     */
    void addReaction(Reaction reaction) {
        rejectionHandled = true;
        if (cf.isDone()) {
            scope.publish(reactionJob(reaction, isRejected(), settledValue()));
            return;
        }
        if (reactions == null) {
            reactions = new ArrayList<>(2);
        }
        reactions.add(reaction);
    }

    //=== unhandled-rejection bookkeeping ==============================================================================

    boolean isRejectionHandled() {
        return rejectionHandled;
    }

    void markRejectionHandled() {
        rejectionHandled = true;
    }

    /** Each terminal unhandled rejection is reported exactly once. */
    boolean claimRejectionReport() {
        if (rejectionReported) {
            return false;
        }
        rejectionReported = true;
        return true;
    }

    //=== Java API =====================================================================================================

    /**
     * The same {@link CompletableFuture} instance every time, representing
     * eventual settlement. Scheduler-neutral: it promises no thread, no
     * ordering and no drain, and cancelling it does <b>not</b> cancel the JS
     * activation — it is a view. A rejection surfaces as
     * {@link JsRejectionException}, so a consumer sees it wrapped per
     * {@code CompletableFuture} norms.
     */
    public CompletableFuture<Object> toFuture() {
        synchronized (cf) {
            if (javaView == null) {
                PromiseView view = new PromiseView(this);
                cf.whenComplete((value, error) -> {
                    if (error == null) {
                        view.complete(Engine.toJava(value));
                    } else {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error;
                        view.completeExceptionally(cause);
                    }
                });
                javaView = view;
            }
            return javaView;
        }
    }

    /**
     * Block until settled and return the fulfillment value, throwing
     * {@link JsRejectionException} on rejection. For Java callers only — calling
     * this from the engine thread would wait for a queue that nothing is
     * pumping.
     */
    public Object await() {
        return join(null);
    }

    /** {@link #await()} with a deadline; throws {@link EngineTimeoutException}
     *  if the promise has not settled by then. */
    public Object join(Duration timeout) {
        try {
            return timeout == null
                    ? toFuture().get()
                    : toFuture().get(Math.max(timeout.toMillis(), 0), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineInterruptedException();
        } catch (TimeoutException e) {
            throw new EngineTimeoutException("timed out waiting for promise");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new EngineException(cause.getMessage(), cause);
        }
    }

    /**
     * The reverse association that makes a round trip lossless: handing a
     * promise's own {@link #toFuture()} back into JS recovers the original
     * {@code JsPromise}, independent of the scope's stage cache.
     */
    static final class PromiseView extends CompletableFuture<Object> {

        final JsPromise owner;

        PromiseView(JsPromise owner) {
            this.owner = owner;
        }
    }

    @Override
    public String toString() {
        if (!cf.isDone()) {
            return "Promise { <pending> }";
        }
        return isRejected()
                ? "Promise { <rejected> " + settledValue() + " }"
                : "Promise { " + settledValue() + " }";
    }

}
