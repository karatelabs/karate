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

import io.karatelabs.common.KarateLifecycle.Outcome;
import io.karatelabs.common.KarateLifecycle.Phase;
import io.karatelabs.common.KarateLifecycle.StopResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class KarateLifecycleTest {

    @AfterEach
    void cleanup() {
        // the register is process-wide: hand the next test a RUNNING, empty one
        KarateLifecycle.reset();
    }

    static List<String> names(List<StopResult> results) {
        return results.stream().map(StopResult::name).toList();
    }

    static Outcome outcomeOf(List<StopResult> results, String name) {
        return results.stream().filter(r -> r.name().equals(name)).map(StopResult::outcome).findFirst().orElse(null);
    }

    static class Fake implements Stoppable {

        final String name;
        final List<String> stopped;
        final Runnable onStop;
        final AtomicInteger stopCount = new AtomicInteger();

        Fake(String name, List<String> stopped) {
            this(name, stopped, null);
        }

        Fake(String name, List<String> stopped, Runnable onStop) {
            this.name = name;
            this.stopped = stopped;
            this.onStop = onStop;
        }

        @Override
        public String lifecycleName() {
            return name;
        }

        @Override
        public String lifecycleKind() {
            return "fake";
        }

        @Override
        public void stop() {
            stopCount.incrementAndGet();
            stopped.add(name);
            KarateLifecycle.unregister(this);
            if (onStop != null) {
                onStop.run();
            }
        }
    }

    @Test
    void testShutdownAllStopsInReverseOrder() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        KarateLifecycle.register(new Fake("a", stopped));
        KarateLifecycle.register(new Fake("b", stopped));
        KarateLifecycle.register(new Fake("c", stopped));
        assertEquals(Phase.RUNNING, KarateLifecycle.phase());
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        assertEquals(List.of("c", "b", "a"), stopped);
        assertEquals(List.of("c", "b", "a"), names(results));
        assertTrue(KarateLifecycle.running().isEmpty());
        assertEquals(Phase.STOPPED, KarateLifecycle.phase());
    }

    @Test
    void testSummaryReportsKindOutcomeAndDuration() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        KarateLifecycle.register(new Fake("ok", stopped));
        KarateLifecycle.register(new Fake("boom", stopped, () -> {
            throw new RuntimeException("no");
        }));
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        assertEquals(2, results.size());
        for (StopResult result : results) {
            assertEquals("fake", result.kind());
            assertFalse(result.duration().isNegative());
        }
        assertEquals(Outcome.FAILED, outcomeOf(results, "boom"));
        assertEquals(Outcome.STOPPED, outcomeOf(results, "ok"));
        assertThrows(UnsupportedOperationException.class, () -> results.add(null));
    }

    @Test
    void testRegisterAndUnregisterAreIdempotent() {
        Fake a = new Fake("a", new ArrayList<>());
        Fake b = new Fake("b", new ArrayList<>());
        KarateLifecycle.register(a);
        KarateLifecycle.register(a);
        KarateLifecycle.register(b);
        assertEquals(List.of(a, b), KarateLifecycle.running());
        KarateLifecycle.unregister(a);
        KarateLifecycle.unregister(a);
        assertEquals(List.of(b), KarateLifecycle.running());
        KarateLifecycle.unregister(new Fake("never-registered", new ArrayList<>()));
        KarateLifecycle.register(null);
        KarateLifecycle.unregister(null);
        assertEquals(List.of(b), KarateLifecycle.running());
    }

    @Test
    void testEqualInstancesAreTrackedSeparately() {
        // identity, not equals — two components that compare equal are still two things to stop
        class Same implements Stoppable {
            @Override
            public String lifecycleName() {
                return "same";
            }

            @Override
            public String lifecycleKind() {
                return "fake";
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Same;
            }

            @Override
            public int hashCode() {
                return 42;
            }
        }
        KarateLifecycle.register(new Same());
        KarateLifecycle.register(new Same());
        assertEquals(2, KarateLifecycle.running().size());
    }

    @Test
    void testFailingStopDoesNotBlockOthers() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        KarateLifecycle.register(new Fake("a", stopped));
        KarateLifecycle.register(new Fake("boom", stopped, () -> {
            throw new RuntimeException("no");
        }));
        KarateLifecycle.register(new Fake("c", stopped));
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        assertEquals(List.of("c", "boom", "a"), stopped);
        assertEquals(List.of("c", "boom", "a"), names(results));
        assertTrue(KarateLifecycle.running().isEmpty());
    }

    @Test
    @Timeout(30)
    void testHungStopDoesNotStarveLaterComponents() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        KarateLifecycle.register(new Fake("a", stopped));
        KarateLifecycle.register(new Fake("b", stopped));
        KarateLifecycle.register(new Fake("hung", stopped, () -> {
            entered.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        long start = System.nanoTime();
        // "hung" is stopped first (reverse order) and forfeits only its share of the deadline
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofMillis(1500));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertEquals(0, entered.getCount());
        assertEquals(List.of("hung", "b", "a"), names(results));
        assertEquals(Outcome.TIMED_OUT, outcomeOf(results, "hung"));
        assertEquals(Outcome.STOPPED, outcomeOf(results, "b"));
        assertEquals(Outcome.STOPPED, outcomeOf(results, "a"));
        assertTrue(stopped.containsAll(List.of("b", "a")), "later components must still be stopped: " + stopped);
        assertTrue(elapsedMillis < 3000, "total deadline not enforced, took " + elapsedMillis + "ms");
    }

    @Test
    @Timeout(30)
    void testReentrantShutdownFromWithinStopDoesNotDeadlock() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        AtomicReference<List<StopResult>> reentrant = new AtomicReference<>();
        KarateLifecycle.register(new Fake("a", stopped));
        KarateLifecycle.register(new Fake("reentrant", stopped,
                () -> reentrant.set(KarateLifecycle.shutdownAll(Duration.ofSeconds(5)))));
        long start = System.nanoTime();
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertEquals(List.of("reentrant", "a"), names(results));
        assertEquals(Outcome.STOPPED, outcomeOf(results, "reentrant"));
        assertEquals(Outcome.STOPPED, outcomeOf(results, "a"));
        // the inner call saw the shutdown already in flight and returned what was done so far
        assertNotNull(reentrant.get());
        assertTrue(elapsedMillis < 5000, "reentrant call blocked, took " + elapsedMillis + "ms");
    }

    @Test
    @Timeout(30)
    void testConcurrentShutdownJoinsTheOneInFlight() throws Exception {
        List<String> stopped = new CopyOnWriteArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        KarateLifecycle.register(new Fake("slow", stopped, () -> {
            entered.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        AtomicReference<List<StopResult>> first = new AtomicReference<>();
        Thread driver = new Thread(() -> first.set(KarateLifecycle.shutdownAll(Duration.ofSeconds(10))), "test-driver");
        driver.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        // an external caller waits for the drain in flight rather than starting a second one
        List<StopResult> second = KarateLifecycle.shutdownAll(Duration.ofSeconds(10));
        driver.join(10_000);
        assertEquals(List.of("slow"), names(second));
        assertEquals(first.get(), second);
        assertEquals(1, stopped.size());
    }

    @Test
    void testRegisterWhileShuttingDownStopsImmediately() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        Fake late = new Fake("late", stopped);
        KarateLifecycle.register(new Fake("a", stopped, () -> KarateLifecycle.register(late)));
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        assertEquals(List.of("a"), names(results));
        assertEquals(1, late.stopCount.get(), "a late arrival must be stopped, not left running");
        assertTrue(KarateLifecycle.running().isEmpty());
    }

    @Test
    void testRegisterAfterShutdownStopsImmediately() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        assertEquals(Phase.STOPPED, KarateLifecycle.phase());
        Fake late = new Fake("late", stopped);
        KarateLifecycle.register(late);
        assertEquals(1, late.stopCount.get());
        assertTrue(KarateLifecycle.running().isEmpty());
        KarateLifecycle.reset();
        assertEquals(Phase.RUNNING, KarateLifecycle.phase());
        Fake fresh = new Fake("fresh", stopped);
        KarateLifecycle.register(fresh);
        assertEquals(List.of(fresh), KarateLifecycle.running());
        assertEquals(0, fresh.stopCount.get());
    }

    @Test
    void testShutdownAllIsSafeWhenEmptyAndRepeated() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        Fake a = new Fake("a", stopped);
        KarateLifecycle.register(a);
        assertEquals(1, KarateLifecycle.shutdownAll(Duration.ofSeconds(5)).size());
        assertTrue(KarateLifecycle.shutdownAll(Duration.ofSeconds(5)).isEmpty());
        assertEquals(1, a.stopCount.get());
    }

    @Test
    void testRunningIsAnIsolatedSnapshot() {
        Fake a = new Fake("a", new ArrayList<>());
        KarateLifecycle.register(a);
        List<Stoppable> snapshot = KarateLifecycle.running();
        KarateLifecycle.register(new Fake("b", new ArrayList<>()));
        KarateLifecycle.unregister(a);
        assertEquals(List.of(a), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(a));
    }

    @Test
    void testWrapDrainsExecutor() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2, ThreadUtils.daemonFactory("test-wrap-"));
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            ran.incrementAndGet();
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Stoppable wrapped = KarateLifecycle.wrap("test-pool", "executor", executor);
        assertEquals("test-pool", wrapped.lifecycleName());
        assertEquals("executor", wrapped.lifecycleKind());
        assertSame(executor, wrapped.lifecycleExecutor());
        KarateLifecycle.register(wrapped);
        assertEquals(List.of(wrapped), KarateLifecycle.running());
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
        assertEquals(Outcome.STOPPED, outcomeOf(results, "test-pool"));
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, ran.get());
        wrapped.stop();  // idempotent
    }

    @Test
    void testCloseDelegatesToStop() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        Fake a = new Fake("a", stopped);
        a.close();
        assertEquals(List.of("a"), stopped);
    }

    @Test
    void testInstallShutdownHookIsIdempotent() {
        KarateLifecycle.installShutdownHook();
        KarateLifecycle.installShutdownHook();
        assertTrue(KarateLifecycle.running().isEmpty());
    }

}
