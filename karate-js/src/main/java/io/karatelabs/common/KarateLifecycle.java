/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The process-wide register of everything Karate has running — HTTP servers, websocket
 * clients, thread pools, browser processes — and the one way to drain all of it.
 *
 * <p>Embedding applications get two options, and should pick exactly one:
 * <ul>
 *   <li>call {@link #shutdownAll(Duration)} at the point their own shutdown sequence
 *       says Karate should be gone (preferred — shutdown ordering stays under the
 *       application's control, and the returned summary is inspectable while logging
 *       still works);</li>
 *   <li>or call {@link #installShutdownHook()} once during startup and let the JVM
 *       trigger the drain — fine for CLIs and tests, less so inside a container where
 *       the shutdown hook competes with the framework's own.</li>
 * </ul>
 *
 * <p>Whichever is chosen, daemon threads (see {@link ThreadUtils}) remain the backstop:
 * a component that is never stopped will not keep the JVM alive, it just skips the
 * polite parts — close frames unsent, callbacks dropped, peers seeing abrupt resets.
 *
 * <h2>Phases</h2>
 * The register moves {@link Phase#RUNNING} → {@link Phase#SHUTTING_DOWN} →
 * {@link Phase#STOPPED} and stays there; {@link #reset()} is the only way back, for
 * tests and for applications that deliberately restart Karate in-process. Once shutdown
 * has begun, {@link #register(Stoppable)} no longer adds to the register — the late
 * arrival is stopped on the spot (logged at warn) rather than being quietly leaked past
 * the drain.
 *
 * <h2>Deadline</h2>
 * The {@code timeout} handed to {@link #shutdownAll(Duration)} is a <b>total</b> deadline
 * for the whole sequence and it is enforced, not merely hoped for: every {@code stop()}
 * runs on an interruptible worker and is abandoned when its slice of the remaining time
 * runs out. The slice is the remaining time divided by the number of components still to
 * go, so a single component that hangs is interrupted after its own share and the ones
 * behind it still get their turn — while components that stop promptly hand their unused
 * time to the rest.
 *
 * <p>Shutdown runs in <b>reverse registration order</b>, on the theory that later
 * arrivals tend to depend on earlier ones (a client connected to a server started
 * before it). Every stop is individually guarded, so one component that throws or hangs
 * cannot strand the rest; each outcome is logged and reported in the returned summary.
 *
 * <p>All methods are thread-safe. Registration is idempotent and identity-based, so a
 * component that registers twice is listed once, and one whose {@code equals} collides
 * with a sibling's is still tracked separately. A second {@link #shutdownAll(Duration)}
 * arriving while one is in flight does not start a second drain — it waits for the one
 * already running and returns its summary.
 */
public final class KarateLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KarateLifecycle.class);

    /** Used by {@link #installShutdownHook()} and {@link #shutdownAll()} — modest on purpose, a JVM
     *  shutdown hook that overstays gets killed by the OS anyway (and by container stop timeouts). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /** Where the register is in its one-way trip from serving to stopped. */
    public enum Phase {
        RUNNING, SHUTTING_DOWN, STOPPED
    }

    /** How stopping one component turned out. */
    public enum Outcome {
        /** {@code stop()} returned normally. */
        STOPPED,
        /** {@code stop()} threw — the component may be partly stopped. */
        FAILED,
        /** {@code stop()} outlived its slice of the deadline and was interrupted, or there was no
         *  time left to attempt it at all. The component is left to the daemon-thread backstop. */
        TIMED_OUT
    }

    /**
     * What happened to one component during {@link #shutdownAll(Duration)}. {@code duration} is
     * wall time spent on that component, so a {@code TIMED_OUT} entry shows the slice it consumed.
     */
    public record StopResult(String name, String kind, Outcome outcome, Duration duration) {

        @Override
        public String toString() {
            return name + " [" + kind + "] " + outcome + " in " + duration.toMillis() + "ms";
        }
    }

    /** Insertion-ordered; identity semantics, hence a list rather than a LinkedHashSet. */
    private static final List<Stoppable> REGISTERED = new ArrayList<>();

    /** Guards {@link #phase}, {@link #deadlineNanos}, {@link #inProgress} and {@link #results}. */
    private static final Object LOCK = new Object();

    private static Phase phase = Phase.RUNNING;
    private static long deadlineNanos;
    private static CompletableFuture<List<StopResult>> inProgress;
    private static List<StopResult> results = List.of();

    /** True on the thread driving a shutdown and on the workers running each {@code stop()} — a
     *  {@code shutdownAll()} from one of those cannot wait for itself to finish. */
    private static final ThreadLocal<Boolean> SHUTDOWN_THREAD = ThreadLocal.withInitial(() -> false);

    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean();

    private static ExecutorService stopPool;

    private KarateLifecycle() {
    }

    /**
     * Add a component to the register — call once it is actually running (bound, connected,
     * spawned), not while it is still being constructed. Registering the same instance again
     * is a no-op, and null is ignored.
     *
     * <p>Once shutdown has begun this does not register anything: the component arrived too late
     * to be drained in order, so it is stopped immediately (logged at warn), bounded by whatever
     * is left of the in-progress deadline — or by {@link #DEFAULT_TIMEOUT} when shutdown has
     * already finished.
     */
    public static void register(Stoppable stoppable) {
        if (stoppable == null) {
            return;
        }
        Phase current;
        long budgetNanos;
        synchronized (LOCK) {
            current = phase;
            if (current == Phase.RUNNING) {
                synchronized (REGISTERED) {
                    if (indexOf(stoppable) < 0) {
                        REGISTERED.add(stoppable);
                    }
                }
                return;
            }
            budgetNanos = current == Phase.SHUTTING_DOWN
                    ? Math.max(deadlineNanos - System.nanoTime(), 0)
                    : DEFAULT_TIMEOUT.toNanos();
        }
        // outside the lock: stopping can block, and the drain needs the lock to record results
        logger.warn("registered while {}, stopping immediately: {}", current, name(stoppable));
        stopBounded(stoppable, budgetNanos);
    }

    /**
     * Remove a component from the register — call from the component's own stop path, so that
     * something which stopped by itself (peer closed the socket, process exited) drops off too.
     * Removing something not registered is a no-op, and null is ignored.
     */
    public static void unregister(Stoppable stoppable) {
        if (stoppable == null) {
            return;
        }
        synchronized (REGISTERED) {
            int index = indexOf(stoppable);
            if (index >= 0) {
                REGISTERED.remove(index);
            }
        }
    }

    /**
     * Everything registered right now, in registration order. A snapshot — the returned list is
     * immutable and does not track later registrations, so it is safe to iterate while components
     * start and stop.
     */
    public static List<Stoppable> running() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED);
        }
    }

    /** Where the register currently is — see {@link Phase}. */
    public static Phase phase() {
        synchronized (LOCK) {
            return phase;
        }
    }

    /**
     * Return the register to {@link Phase#RUNNING} so it accepts registrations again. Meant for
     * tests, and for an application that deliberately restarts Karate inside a live JVM; a normal
     * shutdown never needs it. Anything left over from the previous life is dropped, not stopped.
     *
     * @throws IllegalStateException if a shutdown is in flight
     */
    public static void reset() {
        synchronized (LOCK) {
            if (phase == Phase.SHUTTING_DOWN) {
                throw new IllegalStateException("cannot reset while shutting down");
            }
            phase = Phase.RUNNING;
            results = List.of();
        }
        synchronized (REGISTERED) {
            REGISTERED.clear();
        }
    }

    /**
     * Stop everything registered, in reverse registration order, bounded by
     * {@link #DEFAULT_TIMEOUT}.
     */
    public static List<StopResult> shutdownAll() {
        return shutdownAll(DEFAULT_TIMEOUT);
    }

    /**
     * Stop everything registered, in reverse registration order, and report what happened.
     *
     * <p>{@code timeout} is the total deadline for the sequence. Each component is stopped on an
     * interruptible worker, given the remaining time divided by the number of components left to
     * stop; one that overruns is interrupted, recorded as {@link Outcome#TIMED_OUT} and left to
     * the daemon-thread backstop while the sequence moves on. One that throws is recorded as
     * {@link Outcome#FAILED} and logged at warn. The register is emptied and the phase becomes
     * {@link Phase#STOPPED}, so anything registering afterwards is stopped on arrival.
     *
     * <p>Concurrency: if a shutdown is already in flight this waits for it and returns that
     * summary rather than starting a second one. The exception is a call made <i>from inside</i>
     * a {@code stop()} (or otherwise from the thread driving the shutdown) — waiting there would
     * be waiting on itself, so it returns the results collected so far and the outer sequence
     * carries on.
     *
     * @param timeout total deadline; null or non-positive means {@link #DEFAULT_TIMEOUT}
     * @return one {@link StopResult} per component, in the order they were stopped
     */
    public static List<StopResult> shutdownAll(Duration timeout) {
        Duration limit = (timeout == null || timeout.isNegative() || timeout.isZero()) ? DEFAULT_TIMEOUT : timeout;
        CompletableFuture<List<StopResult>> joinable = null;
        synchronized (LOCK) {
            if (phase == Phase.SHUTTING_DOWN) {
                if (SHUTDOWN_THREAD.get()) {
                    logger.debug("shutdown already in progress on this thread, returning partial summary");
                    return List.copyOf(results);
                }
                joinable = inProgress;
            } else {
                phase = Phase.SHUTTING_DOWN;
                deadlineNanos = System.nanoTime() + limit.toNanos();
                inProgress = new CompletableFuture<>();
                results = new ArrayList<>();
            }
        }
        if (joinable != null) {
            return join(joinable);
        }
        return drain();
    }

    private static List<StopResult> join(CompletableFuture<List<StopResult>> other) {
        try {
            return other.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (LOCK) {
                return List.copyOf(results);
            }
        } catch (ExecutionException e) {
            logger.warn("shutdown in progress failed: {}", e.getCause() == null ? e : e.getCause().getMessage());
            synchronized (LOCK) {
                return List.copyOf(results);
            }
        }
    }

    private static List<StopResult> drain() {
        List<Stoppable> snapshot;
        synchronized (REGISTERED) {
            snapshot = new ArrayList<>(REGISTERED);
            REGISTERED.clear();
        }
        Collections.reverse(snapshot);
        if (!snapshot.isEmpty()) {
            logger.debug("stopping {} registered component(s)", snapshot.size());
        }
        SHUTDOWN_THREAD.set(true);
        try {
            for (int i = 0; i < snapshot.size(); i++) {
                long remaining;
                synchronized (LOCK) {
                    remaining = deadlineNanos - System.nanoTime();
                }
                // an even share of what is left: a hung component forfeits only its own slice
                long budgetNanos = remaining <= 0 ? 0 : remaining / (snapshot.size() - i);
                StopResult result = stopBounded(snapshot.get(i), budgetNanos);
                synchronized (LOCK) {
                    results.add(result);
                }
            }
        } finally {
            SHUTDOWN_THREAD.remove();
            List<StopResult> summary;
            CompletableFuture<List<StopResult>> future;
            synchronized (LOCK) {
                summary = List.copyOf(results);
                results = summary;
                future = inProgress;
                inProgress = null;
                phase = Phase.STOPPED;
            }
            if (future != null) {
                future.complete(summary);
            }
        }
        synchronized (LOCK) {
            return List.copyOf(results);
        }
    }

    /**
     * Run one {@code stop()} on a worker so it can be abandoned, and report the outcome. A worker
     * that ignores its interrupt is left running — its thread is a daemon, so it cannot hold up
     * JVM exit.
     */
    private static StopResult stopBounded(Stoppable stoppable, long budgetNanos) {
        String name = name(stoppable);
        String kind = kind(stoppable);
        long start = System.nanoTime();
        if (budgetNanos <= 0) {
            logger.warn("no time left to stop {}", name);
            return new StopResult(name, kind, Outcome.TIMED_OUT, Duration.ZERO);
        }
        logger.debug("stopping {} [{}]", name, kind);
        Future<?> future = pool().submit(() -> {
            SHUTDOWN_THREAD.set(true);
            try {
                stoppable.stop();
            } finally {
                SHUTDOWN_THREAD.remove();
            }
        });
        Outcome outcome;
        try {
            future.get(budgetNanos, TimeUnit.NANOSECONDS);
            outcome = Outcome.STOPPED;
        } catch (TimeoutException e) {
            future.cancel(true);
            outcome = Outcome.TIMED_OUT;
            logger.warn("timed out stopping {} after {}ms", name, TimeUnit.NANOSECONDS.toMillis(budgetNanos));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            outcome = Outcome.FAILED;
            logger.warn("failed to stop {}: {}", name, cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            outcome = Outcome.TIMED_OUT;
            logger.warn("interrupted while stopping {}", name);
        }
        return new StopResult(name, kind, outcome, Duration.ofNanos(System.nanoTime() - start));
    }

    private static ExecutorService pool() {
        synchronized (LOCK) {
            if (stopPool == null) {
                // cached, so a worker stuck on a stop() that ignores interrupts never blocks the next
                stopPool = Executors.newCachedThreadPool(ThreadUtils.daemonFactory("karate-lifecycle-"));
            }
            return stopPool;
        }
    }

    /**
     * Register a JVM shutdown hook that runs {@link #shutdownAll(Duration)} with
     * {@link #DEFAULT_TIMEOUT} and logs the summary. Opt-in and idempotent — repeat calls install
     * nothing further. Applications that already own a shutdown sequence should call
     * {@link #shutdownAll(Duration)} from it instead, rather than racing their own hook against
     * this one.
     */
    public static void installShutdownHook() {
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Thread hook = new Thread(() -> logSummary(shutdownAll(DEFAULT_TIMEOUT)), "karate-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);
        logger.debug("shutdown hook installed");
    }

    /** One line per outcome bucket, plus the components that did not stop cleanly. */
    private static void logSummary(List<StopResult> summary) {
        if (summary.isEmpty()) {
            return;
        }
        int stopped = 0;
        int failed = 0;
        int timedOut = 0;
        for (StopResult result : summary) {
            switch (result.outcome()) {
                case STOPPED -> stopped++;
                case FAILED -> failed++;
                case TIMED_OUT -> timedOut++;
            }
        }
        logger.info("shutdown complete: {} stopped, {} failed, {} timed out", stopped, failed, timedOut);
    }

    /**
     * Adapt an {@link ExecutorService} to {@link Stoppable}, for a component whose only long-lived
     * resource is its pool. The returned {@code stop()} calls {@code shutdownNow()} and waits (up
     * to a few seconds) for the pool to terminate; it is idempotent, and the executor is exposed
     * via {@link Stoppable#lifecycleExecutor()}. Register it like anything else:
     * {@code KarateLifecycle.register(KarateLifecycle.wrap("my-pool", "executor", pool))}.
     */
    public static Stoppable wrap(String name, String kind, ExecutorService executor) {
        return new ExecutorStoppable(name, kind, executor);
    }

    private static int indexOf(Stoppable stoppable) {
        for (int i = 0; i < REGISTERED.size(); i++) {
            if (REGISTERED.get(i) == stoppable) {
                return i;
            }
        }
        return -1;
    }

    private static String name(Stoppable stoppable) {
        try {
            return stoppable.lifecycleName();
        } catch (Exception e) {
            return stoppable.getClass().getName();
        }
    }

    private static String kind(Stoppable stoppable) {
        try {
            return stoppable.lifecycleKind();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static final class ExecutorStoppable implements Stoppable {

        private static final long DRAIN_MILLIS = 5000;

        private final String name;
        private final String kind;
        private final ExecutorService executor;

        private ExecutorStoppable(String name, String kind, ExecutorService executor) {
            this.name = name;
            this.kind = kind;
            this.executor = executor;
        }

        @Override
        public String lifecycleName() {
            return name;
        }

        @Override
        public String lifecycleKind() {
            return kind;
        }

        @Override
        public ExecutorService lifecycleExecutor() {
            return executor;
        }

        @Override
        public void stop() {
            if (executor == null || executor.isTerminated()) {
                return;
            }
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(DRAIN_MILLIS, TimeUnit.MILLISECONDS)) {
                    logger.warn("executor did not terminate: {}", name);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public String toString() {
            return name;
        }

    }

}
