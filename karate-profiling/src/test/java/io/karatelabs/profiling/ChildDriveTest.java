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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The join loop, which had no test and cost a night's soak.
 *
 * <p>It joined each worker with a flat 30-second timeout <em>in a loop over workers</em>, so the
 * real cap on a run was {@code threads x 30s} — 240 s at eight threads. A {@code --duration 7h}
 * soak therefore ran for four minutes, exited 0, and produced a clean digest with a plausible
 * summary and no warning anywhere. The only tell was {@code elapsedMs=240007} in a line nobody
 * had reason to read closely.
 *
 * <p>What makes that bug worth a test rather than a fix is its shape: <b>it degrades silently and
 * proportionally to thread count</b>, so it looks fine in every short run and gets worse exactly
 * where verification is most expensive. The manual check that confirmed the fix was a single
 * 90-second run — the same kind of check that missed it originally.
 */
class ChildDriveTest {

    /** Counts iterations and returns immediately. */
    private static final class CountingWorkload implements Workload {

        final AtomicLong iterations = new AtomicLong();

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public String describe() {
            return "counts iterations, for tests";
        }

        @Override
        public void iterate(int vu, long iteration) {
            iterations.incrementAndGet();
        }
    }

    /** Blocks forever inside {@code iterate}, which is what a wedged workload looks like. */
    private static final class WedgedWorkload implements Workload {

        final CountDownLatch entered;
        final CountDownLatch release = new CountDownLatch(1);

        WedgedWorkload(int threads) {
            entered = new CountDownLatch(threads);
        }

        @Override
        public String name() {
            return "wedged";
        }

        @Override
        public String describe() {
            return "never returns, for tests";
        }

        @Override
        public void iterate(int vu, long iteration) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testADurationBoundedRunStopsAtItsWindowAndIsNotTruncated() {
        CountingWorkload workload = new CountingWorkload();
        long start = System.nanoTime();
        Child.Result result = Child.drive(workload, 4, -1, Duration.ofMillis(300), "measured", 5);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result.truncated(), "workers that return promptly are not a truncation");
        assertTrue(result.completed() > 0, "the window should have completed some iterations");
        assertEquals(0, result.errors());
        assertTrue(elapsedMs < 3_000,
                "a 300ms window must not take seconds to unwind, got " + elapsedMs + "ms");
    }

    /**
     * The regression test proper. Eight wedged workers and a one-second grace: the join must cost
     * about one second in total, not one second <em>per worker</em>. Under the old code this took
     * {@code threads x grace}, and the assertion below is what would have caught it — the margin
     * is deliberately wide enough that only the per-worker shape fails it.
     */
    @Test
    void testTheJoinDeadlineIsForTheWholeLoopNotPerWorker() throws Exception {
        int threads = 8;
        long graceSeconds = 1;
        WedgedWorkload workload = new WedgedWorkload(threads);
        long start = System.nanoTime();
        Child.Result result = Child.drive(workload, threads, -1, Duration.ofMillis(200),
                "measured", graceSeconds);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        workload.release.countDown();

        assertTrue(result.truncated(),
                "workers still running past the grace must mark the run truncated");
        // window (200ms) + grace (1s) + slack. threads x grace would be 8s.
        assertTrue(elapsedMs < 4_000, "the join waited " + elapsedMs
                + "ms — a whole-loop deadline should have cost about "
                + (200 + graceSeconds * 1000) + "ms, and " + (threads * graceSeconds)
                + "s means it is joining per worker again");
    }

    /**
     * {@code completed} counts iterations <em>claimed</em>, so every wedged worker had already
     * incremented the counter before blocking. Reporting those as completed overstates the run at
     * the one moment its numbers are least trustworthy.
     */
    @Test
    void testTruncatedRunsDoNotCountStragglersAsCompleted() throws Exception {
        int threads = 4;
        WedgedWorkload workload = new WedgedWorkload(threads);
        Child.Result result = Child.drive(workload, threads, -1, Duration.ofMillis(200),
                "measured", 1);
        workload.release.countDown();

        assertTrue(result.truncated());
        assertEquals(0, result.completed(),
                "all four workers were still inside iterate(), so nothing completed");
    }

    /**
     * An iteration bound is honoured exactly, and such a run joins without an artificial cap —
     * its backstop is the parent's {@code --timeout}, which dumps thread state before killing.
     */
    @Test
    void testAnIterationBoundedRunCompletesExactlyItsBudget() {
        CountingWorkload workload = new CountingWorkload();
        Child.Result result = Child.drive(workload, 4, 500, null, "measured", 1);

        assertFalse(result.truncated());
        assertEquals(500, result.completed());
        assertEquals(500, workload.iterations.get(),
                "the iteration bound is a budget, not an approximation");
    }
}
