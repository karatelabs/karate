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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The async state of one outermost {@code Engine.eval} — and the single
 * linearizable facade every piece of that state goes through. Scope
 * <i>object identity</i> is the generation stamp (deliberately not
 * {@code ContextRoot.evalId}, a wrapping {@code short} that nested evals
 * increment); every job, timer, subscription and activation holds a reference
 * to the scope it belongs to.
 * <p>
 * Everything below — scope state, token acquisition, enqueue, quiescence,
 * close — is guarded by the one monitor, so a foreign completion thread can
 * never publish into a closed scope, the live count can never go negative, and
 * quiescence can never be observed with work already in flight. The monitor
 * doubles as the pump owner's wait set: any enqueue (including the cancellation
 * control job) wakes an owner blocked on an empty queue.
 * <p>
 * This is <b>not</b> {@code jsLock}. Holding the facade monitor never implies
 * the right to run JS, and it is never held across JS execution.
 */
final class AsyncScope {

    private final Engine engine;
    private final Object lock = new Object();

    private boolean closed;
    private boolean cancelRequested;
    private String cancelReason;
    private int liveTokens;

    // all lazily allocated: a scope that never sees an async construct stays
    // one small object, which is what keeps the synchronous hot path free
    private ArrayDeque<AsyncJob> microtasks;
    private ArrayDeque<AsyncJob> macrotasks;
    private List<AsyncActivation> activations;
    private Map<Integer, TimerRecord> timers;
    private Map<CompletionStage<?>, JsPromise> stageCache;
    private List<JsPromise> trackedRejections;

    private final AtomicInteger timerIds = new AtomicInteger();

    AsyncScope(Engine engine) {
        this.engine = engine;
    }

    Engine engine() {
        return engine;
    }

    boolean isOpen() {
        synchronized (lock) {
            return !closed;
        }
    }

    boolean isCancelRequested() {
        synchronized (lock) {
            return cancelRequested;
        }
    }

    String cancelReason() {
        synchronized (lock) {
            return cancelReason;
        }
    }

    /** Quiescence — no live tokens AND both queues empty, read as one operation
     *  rather than as a separate queue read and counter read. */
    boolean isQuiescent() {
        synchronized (lock) {
            return liveTokens == 0 && queuesEmpty();
        }
    }

    private boolean queuesEmpty() {
        return (microtasks == null || microtasks.isEmpty()) && (macrotasks == null || macrotasks.isEmpty());
    }

    /** The queue a job belongs in — the one place the microtask/macrotask split
     *  is decided. Caller holds {@code lock}. */
    private ArrayDeque<AsyncJob> queueFor(AsyncJob job) {
        if (job.macrotask()) {
            if (macrotasks == null) {
                macrotasks = new ArrayDeque<>();
            }
            return macrotasks;
        }
        if (microtasks == null) {
            microtasks = new ArrayDeque<>();
        }
        return microtasks;
    }

    int liveTokenCount() {
        synchronized (lock) {
            return liveTokens;
        }
    }

    /** A fresh token, or null when the scope is already closed. */
    AsyncToken acquire(String kind) {
        synchronized (lock) {
            if (closed) {
                return null;
            }
            liveTokens++;
            return new AsyncToken(this, kind);
        }
    }

    /** Called only by {@link AsyncToken#release()} — the CAS there is what makes
     *  this idempotent. */
    void decrement() {
        synchronized (lock) {
            liveTokens--;
            lock.notifyAll();
        }
    }

    /**
     * The core operation. Atomically either (a) the scope is open — acquire a
     * token per successor job, enqueue them, <i>then</i> release
     * {@code oldToken}; or (b) the scope is closed — reject the publication,
     * release only {@code oldToken}, and drop the work.
     *
     * @return true when the work was published
     */
    boolean publishSuccessor(AsyncToken oldToken, List<AsyncJob> work) {
        synchronized (lock) {
            boolean published = false;
            if (!closed && work != null && !work.isEmpty()) {
                for (AsyncJob job : work) {
                    liveTokens++;
                    job.token = new AsyncToken(this, "job");
                    queueFor(job).add(job);
                }
                published = true;
                lock.notifyAll();
            }
            if (oldToken != null) {
                oldToken.release();
            }
            return published;
        }
    }

    boolean publish(AsyncJob job) {
        return publishSuccessor(null, AsyncJob.one(job));
    }

    /**
     * Next job to run, waiting up to {@code timeoutMillis} for one to appear.
     * Returns null when the scope closed, went quiescent, or the wait expired —
     * the pump owner re-evaluates its own exit condition in all three cases.
     * <p>
     * <b>The microtask queue has strict priority.</b> Since the pump asks for
     * one job at a time, that single rule is the HTML microtask checkpoint: the
     * microtask queue is drained to exhaustion — including microtasks enqueued
     * by microtasks — before the synchronous script's first timer runs, and
     * again after every timer callback returns.
     * <p>
     * The caller must NOT hold {@code jsLock} here.
     */
    AsyncJob takeJob(long timeoutMillis) {
        synchronized (lock) {
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
            while (true) {
                if (microtasks != null && !microtasks.isEmpty()) {
                    return microtasks.poll();
                }
                if (macrotasks != null && !macrotasks.isEmpty()) {
                    return macrotasks.poll();
                }
                if (closed || liveTokens == 0) {
                    return null;
                }
                long remaining = (deadline - System.nanoTime()) / 1_000_000L;
                if (remaining <= 0) {
                    return null;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new EngineInterruptedException();
                }
            }
        }
    }

    //=== activations ==================================================================================================

    void addActivation(AsyncActivation activation) {
        synchronized (lock) {
            if (activations == null) {
                activations = new ArrayList<>(2);
            }
            activations.add(activation);
        }
    }

    void removeActivation(AsyncActivation activation) {
        synchronized (lock) {
            if (activations != null) {
                activations.remove(activation);
            }
        }
    }

    /**
     * Activation-initiated cancellation. The activation must not tear the scope
     * down itself — it would join its own thread — so the request is published
     * through this facade as a control job: marking the request and waking the
     * pump owner are one atomic operation, race-safe whether the owner is
     * already blocked on the empty queue or has not begun waiting yet.
     */
    void requestCancel(String reason) {
        synchronized (lock) {
            if (closed || cancelRequested) {
                return;
            }
            cancelRequested = true;
            cancelReason = reason;
            AsyncJob control = new AsyncJob.Control(reason);
            liveTokens++;
            control.token = new AsyncToken(this, "control");
            queueFor(control).add(control);
            lock.notifyAll();
        }
    }

    //=== timers =======================================================================================================

    /** CAS state machine for one {@code setTimeout} record: a fired timer and a
     *  cancelled one are decided by a single {@code LIVE → FIRED|CANCELLED}
     *  winner, so a callback can never run after {@code clearTimeout} returned. */
    static final class TimerRecord {

        private static final int LIVE = 0;
        private static final int FIRED = 1;
        private static final int CANCELLED = 2;

        private final AtomicInteger state = new AtomicInteger(LIVE);
        /** The owning scope, so whoever retires the record — {@code clearTimeout},
         *  scope teardown, or the scheduler's own lifecycle stop — can also drop
         *  it from the scope's timer map. */
        final AsyncScope scope;
        final AsyncToken token;
        final int id;
        volatile ScheduledFuture<?> future;

        TimerRecord(AsyncScope scope, int id, AsyncToken token) {
            this.scope = scope;
            this.id = id;
            this.token = token;
        }

        boolean fire() {
            return state.compareAndSet(LIVE, FIRED);
        }

        boolean cancel() {
            return state.compareAndSet(LIVE, CANCELLED);
        }

        boolean isLive() {
            return state.get() == LIVE;
        }
    }

    int nextTimerId() {
        return timerIds.incrementAndGet();
    }

    void addTimer(TimerRecord record) {
        synchronized (lock) {
            if (timers == null) {
                timers = new LinkedHashMap<>();
            }
            timers.put(record.id, record);
        }
    }

    TimerRecord removeTimer(int id) {
        synchronized (lock) {
            return timers == null ? null : timers.remove(id);
        }
    }

    //=== CompletionStage identity cache ===============================================================================

    /**
     * Scope-scoped identity for wrapped Java stages: the same stage yields the
     * same {@code JsPromise} within one eval scope, and the whole cache is
     * dropped at scope close so a long-lived shared engine cannot accumulate
     * stage/wrapper graphs. Weak keys were rejected in review — the wrapper's CF
     * graph can strongly reach the stage and defeat them. Reference-equality
     * keys, because {@code CompletionStage.equals} must never merge distinct
     * stages. Identity across scopes is explicitly not promised.
     */
    JsPromise cachedStage(CompletionStage<?> stage) {
        synchronized (lock) {
            return stageCache == null ? null : stageCache.get(stage);
        }
    }

    void cacheStage(CompletionStage<?> stage, JsPromise promise) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (stageCache == null) {
                stageCache = new IdentityHashMap<>();
            }
            stageCache.put(stage, promise);
        }
    }

    int stageCacheSize() {
        synchronized (lock) {
            return stageCache == null ? 0 : stageCache.size();
        }
    }

    //=== unhandled rejections =========================================================================================

    /** Called under {@code jsLock} when a promise settles rejected. Handledness
     *  is decided later, at quiescence, so a {@code .catch} queued after the
     *  rejection still counts. */
    void trackRejection(JsPromise promise) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (trackedRejections == null) {
                trackedRejections = new ArrayList<>(2);
            }
            trackedRejections.add(promise);
        }
    }

    List<JsPromise> trackedRejections() {
        synchronized (lock) {
            return trackedRejections == null ? List.of() : new ArrayList<>(trackedRejections);
        }
    }

    //=== teardown =====================================================================================================

    /**
     * Fenced, awaited teardown. (1) the facade goes CLOSED — from this instant
     * every late {@code publishSuccessor} is rejected and the activation
     * reacquisition gate is shut; (2) live timers are CAS-cancelled; (3) queued
     * jobs are discarded and their tokens released; (4) every live activation is
     * interrupted and <i>waited for</i>, bounded by the teardown deadline.
     * <p>
     * The caller (the pump owner) must not hold {@code jsLock} — a running
     * activation needs it to reach its next exit point. Returns the activations
     * still alive at the deadline; a non-empty list poisons the engine, because
     * a preserved stack that outlives its scope must never touch a later eval.
     */
    List<AsyncActivation> close(String reason, long teardownMillis) {
        List<AsyncActivation> live;
        List<AsyncJob> pending = null;
        List<TimerRecord> timerSnapshot = null;
        synchronized (lock) {
            if (closed) {
                return List.of();
            }
            closed = true;
            if (cancelReason == null) {
                cancelReason = reason;
            }
            live = activations == null ? List.of() : new ArrayList<>(activations);
            if (!queuesEmpty()) {
                pending = new ArrayList<>();
                if (microtasks != null) {
                    pending.addAll(microtasks);
                    microtasks.clear();
                }
                if (macrotasks != null) {
                    pending.addAll(macrotasks);
                    macrotasks.clear();
                }
            }
            if (timers != null && !timers.isEmpty()) {
                timerSnapshot = new ArrayList<>(timers.values());
                timers.clear();
            }
            stageCache = null;
            lock.notifyAll();
        }
        if (timerSnapshot != null) {
            for (TimerRecord record : timerSnapshot) {
                AsyncSupport.retireTimer(record);
            }
        }
        if (pending != null) {
            for (AsyncJob job : pending) {
                if (job.token != null) {
                    job.token.release();
                }
            }
        }
        if (live.isEmpty()) {
            return List.of();
        }
        long deadline = System.nanoTime() + teardownMillis * 1_000_000L;
        for (AsyncActivation activation : live) {
            activation.cancel();
        }
        List<AsyncActivation> stuck = new ArrayList<>(0);
        // Teardown is usually driven by the very interrupt it is responding to,
        // so clear this thread's interrupt for the bounded join — otherwise every
        // wait fails instantly and a perfectly cooperative activation is misread
        // as stuck (and the engine poisoned for it). Restored before returning.
        boolean wasInterrupted = Thread.interrupted();
        try {
            for (AsyncActivation activation : live) {
                long remaining = (deadline - System.nanoTime()) / 1_000_000L;
                if (!activation.awaitTermination(Math.max(remaining, 1))) {
                    stuck.add(activation);
                }
                // an interrupt that arrives mid-join is recorded here and cleared
                // again: one interrupt must not make every remaining wait fail
                // instantly and misclassify cooperative activations as stuck
                wasInterrupted |= Thread.interrupted();
            }
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return stuck;
    }

}
