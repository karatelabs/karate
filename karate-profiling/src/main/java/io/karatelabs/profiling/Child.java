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
package io.karatelabs.profiling;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main class of the <em>forked child</em> JVM: runs exactly one workload and exits.
 * Never forks anything itself.
 *
 * <p>The child does no measuring — JFR is attached by JVM flags the parent chose, and the
 * heap dump (if any) is written by the JVM. What the child prints to stdout is only for a
 * human watching a long run, plus the machine-readable summary line the parent parses.
 *
 * <p>Two shutdown properties are deliberate and load-bearing:
 * <ul>
 *   <li><b>No {@code ExecutorService}.</b> {@code newVirtualThreadPerTaskExecutor().close()}
 *       waits indefinitely, so one stuck worker hangs the JVM and the recording is never
 *       flushed. Threads are managed directly and joined with a bound.</li>
 *   <li><b>OOM does not halt the JVM.</b> An {@code OutOfMemoryError} aborts the run and
 *       falls through to a normal {@code System.exit}, because only an orderly shutdown
 *       flushes the JFR recording. {@code -XX:+ExitOnOutOfMemoryError} would leave a
 *       zero-byte {@code run.jfr} — on the workloads whose expected outcome is an OOM.</li>
 * </ul>
 */
public final class Child {

    /** Parsed by the parent out of stdout; keep the format stable. */
    static final String SUMMARY_PREFIX = "PROFILING-SUMMARY ";

    /**
     * Slack allowed for workers to notice the deadline and unwind, past the window itself.
     *
     * <p>It replaces a flat 30-second-per-worker join, which silently truncated every long run:
     * the loop below waits per worker, so the real cap was {@code threads x 30s} — 240 s at 8
     * threads. A {@code --duration 7h} soak therefore ran for four minutes and exited 0, with a
     * clean digest, a plausible summary and no warning anywhere. Discovered by reading a
     * summary that said {@code elapsedMs=240007} when it should have said 25,200,000.
     *
     * <p>The backstop for a genuinely hung workload is the parent's {@code --timeout}, which
     * dumps thread state before killing — a far better diagnostic than this loop giving up.
     */
    private static final long JOIN_GRACE_SECONDS = 120;

    public static void main(String[] args) {
        String name = required("karate.profiling.workload");
        int threads = Integer.getInteger("karate.profiling.threads", 16);
        long iterations = Long.getLong("karate.profiling.iterations", -1);
        String durationText = System.getProperty("karate.profiling.duration");
        Duration duration = durationText == null ? null : RunShape.parseDuration(durationText);
        Duration warmup = RunShape.parseDuration(System.getProperty("karate.profiling.warmup", "0s"));
        String mockUrl = System.getProperty("karate.profiling.mockUrl");

        Workload workload = Workloads.get(name);
        WorkloadContext context = new WorkloadContext(threads, iterations, duration, mockUrl);

        System.out.println("[child] workload=" + name + " threads=" + threads
                + (duration == null ? " iterations=" + iterations : " duration=" + RunShape.format(duration))
                + " warmup=" + RunShape.format(warmup));

        workload.setup(context);
        startHeapSampler();

        if (workload.drivesOwnConcurrency()) {
            // The workload owns the suite, so it owns the concurrency too. Warmup is skipped:
            // there is exactly one unit of work and running it twice would double the run.
            System.out.println("[child] workload drives its own concurrency — one pass, no warmup");
            long selfStart = System.nanoTime();
            SelfCpu.Window selfCpu = SelfCpu.open();
            Result self = driveOnce(workload);
            long selfElapsedMs = (System.nanoTime() - selfStart) / 1_000_000;
            // A self-driving workload owns its own clock, so when a window was asked for, nothing
            // here enforced it — this branch printed truncated=false unconditionally, on faith.
            // That is exactly the shape of the failure that made a 7-hour soak report success
            // after four minutes: the run ends early for its own reasons and every field in the
            // summary still reads healthy. Check the elapsed time against the window that was
            // requested, and say so when they disagree.
            boolean selfTruncated = false;
            if (duration != null) {
                long requestedMs = duration.toMillis();
                // 5% short of the window, or more. Gatling stops when its last virtual user
                // finishes the iteration it is in, so landing a little under is normal; landing
                // well under means the injection profile did not carry the duration at all.
                if (selfElapsedMs < requestedMs - requestedMs / 20) {
                    selfTruncated = true;
                    System.out.println("[child] !! ran " + selfElapsedMs + "ms of a requested "
                            + requestedMs + "ms window — the workload did not honour --duration."
                            + " Its numbers describe a shorter run than the one you asked for.");
                }
            }
            System.out.println(SUMMARY_PREFIX
                    + "completed=" + self.completed
                    + " errors=" + self.errors
                    + " elapsedMs=" + selfElapsedMs
                    + " peakHeapBytes=" + peakHeapBytes()
                    + " " + selfCpu.describe()
                    + " cpus=" + Runtime.getRuntime().availableProcessors()
                    + " truncated=" + selfTruncated
                    + " oom=" + self.oom);
            if (self.firstFailure != null) {
                self.firstFailure.printStackTrace(System.out);
            }
            // Truncation exits non-zero for the same reason the iteration-bounded path does: an
            // incomplete run must not read as a complete one to whatever is scripting it. Kept as
            // its own term rather than folded into the error count, which the summary above has
            // already printed truthfully.
            System.exit(self.oom || self.errors > 0 || selfTruncated ? 1 : 0);
        }

        if (!warmup.isZero()) {
            // Deliberately not "excluded from the recording": the child does not know whether a
            // JFR delay was applied, and below JFR's one-second floor there is none. The parent
            // warns in that case and the digest's warmup row states it; this line just says what
            // is about to run.
            System.out.println("[child] warmup " + RunShape.format(warmup));
            Result discarded = drive(workload, threads, -1, warmup, "warmup");
            System.out.println("[child] warmup done, " + discarded.completed + " iterations discarded");
            // A bad warmup invalidates the measurement that follows it, so the run stops here
            // rather than producing a digest that looks comparable.
            //
            //   truncated — stragglers are still inside iterate(). They do not stop when the
            //     warmup's window closes; they run on into the measured window, burning CPU,
            //     loading the mock, and (once delay= expires) writing their events into the very
            //     recording the warmup exists to stay out of. Worse, if they finish before the
            //     measured window closes, the final result says truncated=false and exit 0 — a
            //     contaminated run that reads as a clean one.
            //   oom / errors — the workload is broken at this shape. Measuring it anyway spends
            //     the window to reach the same conclusion, and warmup errors are not counted in
            //     the measured result, so continuing would hide them.
            if (discarded.truncated || discarded.oom || discarded.errors > 0) {
                System.out.println("[child] ABORTED: the warmup "
                        + (discarded.truncated ? "did not finish within its window"
                        : discarded.oom ? "ran out of memory"
                        : "raised " + discarded.errors + " error(s)")
                        + " — refusing to measure, because the measured window would be"
                        + " contaminated and would not say so");
                System.out.println(SUMMARY_PREFIX
                        + "completed=0"
                        + " errors=" + discarded.errors
                        + " elapsedMs=0"
                        + " peakHeapBytes=" + peakHeapBytes()
                        + " cpus=" + Runtime.getRuntime().availableProcessors()
                        + " truncated=" + discarded.truncated
                        + " abortedInWarmup=true"
                        + " oom=" + discarded.oom);
                if (discarded.firstFailure != null) {
                    discarded.firstFailure.printStackTrace(System.out);
                }
                System.exit(1);
            }
        }

        // JFR's delay= is measured from JVM LAUNCH, not from here, so whatever the child spent
        // on class loading, workload lookup and setup() is time the recording already started
        // burning through the warmup allowance. Report the actual offset so the digest can say
        // whether the warmup was really excluded instead of assuming it. See appendRunSummary.
        long sinceLaunchMs = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        System.out.println(MEASURING_PREFIX + "sinceJvmStartMs=" + sinceLaunchMs
                + " warmupMs=" + warmup.toMillis());
        System.out.println("[child] measuring");
        long startNanos = System.nanoTime();
        Thread liveSetProbe = null;
        if (Boolean.getBoolean("karate.profiling.soak")) {
            System.out.println("[child] live-set probe every " + liveSetIntervalSeconds()
                    + "s (full GC, then measure what survived — the JFR heap floor cannot tell"
                    + " retention from promoted garbage; see startLiveSetProbe)");
            liveSetProbe = startLiveSetProbe(startNanos);
        }
        // Opened here rather than at process start, which is the whole point: warmup, class
        // loading and JIT are on the other side of this line, and a CPU total that includes them
        // measures the JVM waking up rather than the workload. See SelfCpu.
        SelfCpu.Window cpu = SelfCpu.open();
        Result result = drive(workload, threads, iterations, duration, "measured");
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        String cpuDescription = cpu.describe();
        if (liveSetProbe != null) {
            liveSetProbe.interrupt();
            try {
                // Join before the final reading. Interrupt alone does not stop a probe that has
                // already woken, so its two System.gc() calls and its output could interleave
                // with the final one — two readings racing for the same heap.
                liveSetProbe.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // A final reading with the workload stopped. On a run that completed, no iteration is
            // in flight and this is the cleanest number the run produces — but on a TRUNCATED one
            // stragglers are still inside iterate() holding whatever they hold, so read this the
            // way the outcome row tells you to.
            probeOnce(startNanos, true);
        }

        try {
            workload.teardown();
        } catch (Throwable t) {
            System.out.println("[child] teardown failed: " + t);
        }

        long peakHeapBytes = peakHeapBytes();
        System.out.println(SUMMARY_PREFIX
                + "completed=" + result.completed
                + " errors=" + result.errors
                + " elapsedMs=" + elapsedMs
                + " peakHeapBytes=" + peakHeapBytes
                // Captured before teardown, which is not the workload and can be slow.
                + " " + cpuDescription
                // The CHILD's core count, which is not always the parent's: -XX:ActiveProcessorCount
                // changes what this JVM believes it has, and the digest divides CPU by it.
                + " cpus=" + Runtime.getRuntime().availableProcessors()
                // In the summary line, not just the log, so the digest's outcome row and the
                // exit code can both reflect it. A truncated run's elapsedMs is window + grace,
                // which is close enough to the requested window to pass a glance.
                + " truncated=" + result.truncated
                + " oom=" + result.oom);
        if (result.firstFailure != null) {
            System.out.println("[child] first failure: " + result.firstFailure);
            result.firstFailure.printStackTrace(System.out);
        }
        // Explicit, so a lingering non-daemon thread somewhere in Karate cannot keep the
        // JVM alive past the point where the run is over. Truncation exits non-zero: the run
        // did not do what it was asked to do, and a caller scripting a matrix or a soak should
        // find out from the status, not by reading a log.
        System.exit(result.oom || result.errors > 0 || result.truncated ? 1 : 0);
    }

    /** One pass, on this thread, for a workload that drives its own concurrency. */
    private static Result driveOnce(Workload workload) {
        try {
            workload.iterate(0, 0);
            return new Result(1, 0, false, false, null);
        } catch (OutOfMemoryError e) {
            return new Result(0, 0, true, false, e);
        } catch (Throwable t) {
            return new Result(0, 1, false, false, t);
        }
    }

    /**
     * Drive {@code threads} virtual threads until the iteration budget is spent or the
     * window closes, whichever bound was given.
     */
    private static Result drive(Workload workload, int threads, long iterations, Duration window,
                                String phase) {
        return drive(workload, threads, iterations, window, phase, JOIN_GRACE_SECONDS);
    }

    /**
     * Package-private and grace-parameterised so the join can be tested. The bug this replaced
     * shipped unnoticed and cost a night's soak; a two-minute constant is not something a test
     * can wait out, and "verified by one manual 90-second run" is how it went unnoticed the first
     * time. Production always passes {@link #JOIN_GRACE_SECONDS}.
     */
    static Result drive(Workload workload, int threads, long iterations, Duration window,
                        String phase, long graceSeconds) {
        AtomicLong next = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Boolean> oom = new AtomicReference<>(false);
        long deadlineNanos = window == null ? Long.MAX_VALUE : System.nanoTime() + window.toNanos();

        List<Thread> workers = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            int vu = i;
            workers.add(Thread.ofVirtual().name("vu-" + vu).start(() -> {
                while (true) {
                    if (System.nanoTime() >= deadlineNanos) {
                        return;
                    }
                    long iteration = next.getAndIncrement();
                    if (iterations >= 0 && iteration >= iterations) {
                        return;
                    }
                    try {
                        workload.iterate(vu, iteration);
                    } catch (OutOfMemoryError e) {
                        // Record and stop, but let the JVM shut down normally so JFR flushes.
                        oom.set(true);
                        firstFailure.compareAndSet(null, e);
                        return;
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                        firstFailure.compareAndSet(null, t);
                    }
                }
            }));
        }

        Thread progress = startProgressReporter(next, deadlineNanos, iterations);
        // One deadline for the whole join, not one per worker — and derived from the run's own
        // window rather than a constant, so a long run waits as long as it asked to.
        long joinDeadlineNanos = window == null
                ? Long.MAX_VALUE
                : deadlineNanos + TimeUnit.SECONDS.toNanos(graceSeconds);
        for (Thread worker : workers) {
            try {
                if (joinDeadlineNanos == Long.MAX_VALUE) {
                    // Iteration-bounded: no artificial cap. A workload that never returns is the
                    // parent's --timeout to handle, and it reports far more than a silent stop.
                    worker.join();
                } else {
                    long remaining = joinDeadlineNanos - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    worker.join(Duration.ofNanos(remaining));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        progress.interrupt();

        // Never leave this silent. If workers are still running, the numbers below describe a
        // window that was cut short, and every panel downstream would read as a completed run.
        long stillAlive = workers.stream().filter(Thread::isAlive).count();
        if (stillAlive > 0) {
            if ("warmup".equals(phase)) {
                // Distinct wording, because the consequence is different and worse. A measured
                // run that truncates loses time it asked for; a *warmup* straggler runs on into
                // the measured window — burning CPU, loading the mock, and (once the recording's
                // delay= expires) contributing its events to the very recording the warmup exists
                // to stay out of. Nothing downstream can tell that happened.
                System.out.println("[child] WARNING: " + stillAlive + " of " + threads
                        + " warmup workers were still running when the warmup ended — they will"
                        + " run on into the measured window and pollute it");
            } else {
                System.out.println("[child] WARNING: " + stillAlive + " of " + threads
                        + " workers still running at the join deadline — this run was TRUNCATED and"
                        + " its duration is not the window that was asked for");
            }
        }

        // next.getAndIncrement() counts iterations *claimed*, so a worker still inside iterate()
        // has already been counted. Subtracting the stragglers keeps "completed" honest at the
        // exact moment the number is least trustworthy. Bounded by thread count, so small — but
        // a count that overstates on a truncated run is the wrong way round.
        long claimed = Math.min(next.get(), iterations < 0 ? Long.MAX_VALUE : iterations);
        long completed = Math.max(0, claimed - stillAlive);
        return new Result(completed, errors.get(), oom.get(), stillAlive > 0, firstFailure.get());
    }

    /** A heartbeat, so a watching human (or parent) can tell "still working" from "hung". */
    private static Thread startProgressReporter(AtomicLong counter, long deadlineNanos, long iterations) {
        return Thread.ofVirtual().name("progress").start(() -> {
            long lastCount = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                long count = counter.get();
                long heapMb = usedHeapBytes() / (1024 * 1024);
                System.out.println("[child] " + count + (iterations >= 0 ? "/" + iterations : "")
                        + " iterations (+" + (count - lastCount) + ") heap=" + heapMb + "m");
                lastCount = count;
                if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos) {
                    return;
                }
            }
        });
    }

    private static long usedHeapBytes() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    /**
     * Highest used-heap reading observed by the sampler.
     *
     * <p>Deliberately <em>not</em> the sum of {@code MemoryPoolMXBean.getPeakUsage()} across
     * heap pools: those peaks occur at different moments, so summing them double-counts and
     * can report more than {@code -Xmx}, which is how the flaw announces itself. A sampled
     * maximum is bounded by the real heap and comparable between runs.
     *
     * <p>Reported for orientation only. The authoritative view of how memory behaved is the
     * heap-after-GC series in the digest — a peak alone cannot distinguish churn from
     * retention, and under saturation it just pins to the ceiling and stops discriminating.
     */
    private static long peakHeapBytes() {
        return PEAK_HEAP.get();
    }

    private static final AtomicLong PEAK_HEAP = new AtomicLong();

    /** Samples used heap continuously; a 5s progress tick alone would miss most of the curve. */
    /** Emitted once, when the measured window opens. The digest checks the JFR delay against it. */
    static final String MEASURING_PREFIX = "PROFILING-MEASURING ";

    /** Emitted once per probe; the digest turns these into the live-set series. Keep stable. */
    static final String LIVE_SET_PREFIX = "PROFILING-LIVE-SET ";

    /**
     * The only trustworthy leak detector for a long run, and it exists because the obvious one
     * lied.
     *
     * <p>The digest's heap-after-GC panel reads {@code jdk.GCHeapSummary} at {@code when="After
     * GC"} and calls a rising floor a leak. That rule is <b>unsound under a generational collector
     * whose old generation is never collected</b>, which is the normal state of a soak. Measured:
     * a one-hour run at ~9,900 iterations/s performed 104,980 collections and <b>every one of them
     * was a G1 young evacuation pause</b> — no concurrent cycle, no mixed, no full. With
     * {@code -Xmx768m} against a ~7 MB live set, G1's initiating heap-occupancy threshold (~45%,
     * so ~345 MB) is never approached, so nothing ever collects the old generation. Promoted
     * garbage therefore accumulates monotonically and the "floor" climbs in a straight line —
     * 11.4 MB to 27.8 MB over that hour — which reads exactly like a leak and is not one. A class
     * histogram after a forced full GC put the live set at 7.8, 7.2, 8.6, 7.2 MB over the same
     * kind of window: flat.
     *
     * <p>So this probe forces a full collection and records what genuinely survived it. A rise
     * here is a leak; a rise in the JFR floor is not evidence of one. The cost is a stop-the-world
     * pause every {@link #LIVE_SET_INTERVAL_SECONDS} seconds, which is why it is soak-only —
     * a full GC would distort any run whose question is throughput or pause time.
     */
    private static Thread startLiveSetProbe(long startNanos) {
        checkExplicitGcIsUsable();
        Thread probe = Thread.ofPlatform().daemon().name("live-set-probe").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(liveSetIntervalSeconds());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                probeOnce(startNanos, false);
            }
        });
        return probe;
    }

    /**
     * One reading, with proof that the collection it depends on actually happened.
     *
     * <p>The proof is the point. A probe that assumes {@code System.gc()} collects, and is wrong,
     * reports resident heap under a panel headed "this is the leak panel" — which is precisely the
     * silent-authoritative failure that the two detectors this replaced both had. Demonstrated by
     * a review: under {@code -XX:+DisableExplicitGC} this probe reported 48.6 → 84.5 → 95.9 MB
     * over six seconds on a workload whose live set was flat at ~10 MB, with nothing anywhere
     * saying the readings were meaningless.
     *
     * <p>So every reading carries {@code valid=}. A collection count that did not move across the
     * two {@code System.gc()} calls means they did nothing, and the digest refuses the series
     * rather than drawing it.
     */
    private static void probeOnce(long startNanos, boolean isFinal) {
        long before = oldGenCollections();
        // Two passes: the first may leave objects that only became unreachable during it, and a
        // soak's whole question is whether the second number keeps climbing.
        System.gc();
        System.gc();
        long after = oldGenCollections();
        // Two System.gc() calls, so two old-generation collections are expected; one is
        // accepted because a collector may coalesce them, but zero is not.
        boolean collected = after > before && !explicitGcUnusable;
        System.out.println(LIVE_SET_PREFIX
                + "elapsedMs=" + ((System.nanoTime() - startNanos) / 1_000_000)
                + " liveBytes=" + usedHeapBytes()
                + " collections=" + (after - before)
                + " valid=" + collected
                + (isFinal ? " final=true" : ""));
        if (!collected) {
            System.out.println("[child] WARNING: System.gc() collected nothing — this live-set "
                    + "reading is resident heap, not a live set, and means nothing about a leak");
        }
    }

    /**
     * Collections by the <b>old-generation</b> collector only.
     *
     * <p>It used to sum every collector, and that cannot prove what the probe needs. The workload
     * runs throughout, so an ordinary young collection lands in that sum and marks the reading
     * valid even when {@code System.gc()} did precisely nothing — the exact false-valid the flag
     * exists to catch. Only an old-generation collection can have produced a live set, so only
     * that counter is evidence.
     */
    private static long oldGenCollections() {
        long total = 0;
        for (java.lang.management.GarbageCollectorMXBean bean
                : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (YOUNG_ONLY_COLLECTOR_BEANS.contains(bean.getName())) {
                continue;
            }
            long count = bean.getCollectionCount();
            if (count > 0) {
                total += count;
            }
        }
        return total;
    }

    /**
     * {@link java.lang.management.GarbageCollectorMXBean#getName()} values that collect the young
     * generation only. These are JMX bean names and are NOT the same strings as JFR's
     * {@code jdk.GarbageCollection.name} — checked per collector on JDK 24 rather than assumed.
     * Anything not listed is treated as touching the old generation, which errs toward calling a
     * probe valid, so the VM-option check below is the real guard.
     */
    private static final java.util.Set<String> YOUNG_ONLY_COLLECTOR_BEANS = java.util.Set.of(
            "G1 Young Generation", "PS Scavenge", "Copy", "ParNew", "ZGC Minor Cycles",
            "ZGC Minor Pauses");

    /**
     * Two JVM flags quietly turn the probe into a resident-heap meter, and both arrive through
     * {@code --jvm-flag}, which exists for exactly this kind of constrained experiment.
     * {@code DisableExplicitGC} makes {@code System.gc()} a no-op; {@code
     * ExplicitGCInvokesConcurrent} turns it into a concurrent cycle that returns before it has
     * compacted or run mixed evacuations, so the reading includes floating garbage. Said once, at
     * startup, where an operator is still watching — the per-reading {@code valid=} flag catches
     * the first case empirically, but nothing empirical catches the second.
     */
    private static void checkExplicitGcIsUsable() {
        for (String option : new String[]{"DisableExplicitGC", "ExplicitGCInvokesConcurrent"}) {
            if ("true".equals(vmOption(option))) {
                // Machine-readable, not just a log line. ExplicitGCInvokesConcurrent in particular
                // cannot be caught empirically — the concurrent cycle returns before it has
                // compacted, so collection counts move and the reading still means nothing — so
                // this flag is the only thing standing between it and an authoritative panel.
                explicitGcUnusable = true;
                System.out.println("[child] WARNING: -XX:+" + option + " is set, so the live-set "
                        + "probe cannot measure a live set. Every reading will be marked "
                        + "valid=false and the digest will refuse to plot them.");
            }
        }
    }

    /** Set once at probe start; makes every subsequent reading report {@code valid=false}. */
    private static volatile boolean explicitGcUnusable;

    /** The VM's own view of a flag, or null if it cannot be read (non-HotSpot, or restricted). */
    private static String vmOption(String name) {
        try {
            com.sun.management.HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(
                    com.sun.management.HotSpotDiagnosticMXBean.class);
            return bean == null ? null : bean.getVMOption(name).getValue();
        } catch (RuntimeException | Error e) {
            return null;
        }
    }

    private static final long LIVE_SET_INTERVAL_SECONDS = 300;

    /**
     * Overridable so the probe can be exercised in a run short enough to watch. Five minutes is
     * the right cadence for a soak and the wrong one for verifying that the plumbing works.
     */
    private static long liveSetIntervalSeconds() {
        long seconds = Long.getLong("karate.profiling.liveSetSeconds", LIVE_SET_INTERVAL_SECONDS);
        // A non-positive override makes TimeUnit.sleep throw, which would kill the probe thread
        // and leave a soak with exactly one reading — and nothing would say why.
        return seconds > 0 ? seconds : LIVE_SET_INTERVAL_SECONDS;
    }

    private static Thread startHeapSampler() {
        Thread sampler = Thread.ofVirtual().name("heap-sampler").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                PEAK_HEAP.accumulateAndGet(usedHeapBytes(), Math::max);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        return sampler;
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("missing required system property: " + key);
        }
        return value;
    }

    /**
     * @param truncated workers were still running when the join deadline passed, so {@code
     *                  completed} and the elapsed time describe a window that was cut short.
     *                  Carried all the way to the summary line and the exit code, because a
     *                  warning printed to stdout is not a signal: on an eight-hour soak it is one
     *                  line among thousands, and the elapsed time in this case reads as
     *                  {@code window + grace}, which looks approximately right.
     */
    record Result(long completed, long errors, boolean oom, boolean truncated,
                  Throwable firstFailure) {
    }

}
