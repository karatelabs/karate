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
        WorkloadContext context = new WorkloadContext(threads, iterations, mockUrl);

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
            System.out.println(SUMMARY_PREFIX
                    + "completed=" + self.completed
                    + " errors=" + self.errors
                    + " elapsedMs=" + ((System.nanoTime() - selfStart) / 1_000_000)
                    + " peakHeapBytes=" + peakHeapBytes()
                    + " " + selfCpu.describe()
                    + " oom=" + self.oom);
            if (self.firstFailure != null) {
                self.firstFailure.printStackTrace(System.out);
            }
            System.exit(self.oom || self.errors > 0 ? 1 : 0);
        }

        if (!warmup.isZero()) {
            System.out.println("[child] warmup " + RunShape.format(warmup) + " (excluded from the recording)");
            Result discarded = drive(workload, threads, -1, warmup);
            System.out.println("[child] warmup done, " + discarded.completed + " iterations discarded");
        }

        System.out.println("[child] measuring");
        long startNanos = System.nanoTime();
        // Opened here rather than at process start, which is the whole point: warmup, class
        // loading and JIT are on the other side of this line, and a CPU total that includes them
        // measures the JVM waking up rather than the workload. See SelfCpu.
        SelfCpu.Window cpu = SelfCpu.open();
        Result result = drive(workload, threads, iterations, duration);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        String cpuDescription = cpu.describe();

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
                + " oom=" + result.oom);
        if (result.firstFailure != null) {
            System.out.println("[child] first failure: " + result.firstFailure);
            result.firstFailure.printStackTrace(System.out);
        }
        // Explicit, so a lingering non-daemon thread somewhere in Karate cannot keep the
        // JVM alive past the point where the run is over.
        System.exit(result.oom || result.errors > 0 ? 1 : 0);
    }

    /** One pass, on this thread, for a workload that drives its own concurrency. */
    private static Result driveOnce(Workload workload) {
        try {
            workload.iterate(0, 0);
            return new Result(1, 0, false, null);
        } catch (OutOfMemoryError e) {
            return new Result(0, 0, true, e);
        } catch (Throwable t) {
            return new Result(0, 1, false, t);
        }
    }

    /**
     * Drive {@code threads} virtual threads until the iteration budget is spent or the
     * window closes, whichever bound was given.
     */
    private static Result drive(Workload workload, int threads, long iterations, Duration window) {
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
                : deadlineNanos + TimeUnit.SECONDS.toNanos(JOIN_GRACE_SECONDS);
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
            System.out.println("[child] WARNING: " + stillAlive + " of " + threads
                    + " workers still running at the join deadline — this run was TRUNCATED and its"
                    + " duration is not the window that was asked for");
        }

        long completed = Math.min(next.get(), iterations < 0 ? Long.MAX_VALUE : iterations);
        return new Result(completed, errors.get(), oom.get(), firstFailure.get());
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

    private record Result(long completed, long errors, boolean oom, Throwable firstFailure) {
    }

}
