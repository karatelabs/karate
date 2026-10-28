package io.karatelabs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Suite#cancel}: each fixture is cancelled while work is in flight and must end within a second
 * with every unfinished scenario cancelled, never passed, and an HTTP call in flight aborted.
 */
class SuiteCancelTest {

    static final long BUDGET_MILLIS = 1000;

    /** Accepts one request and never answers; {@code clientGone} fires when the client drops the connection. */
    static final class HangingStub implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final CountDownLatch received = new CountDownLatch(1);
        final CountDownLatch clientGone = new CountDownLatch(1);

        HangingStub() throws IOException {
            Thread thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    InputStream in = socket.getInputStream();
                    byte[] buf = new byte[4096];
                    if (in.read(buf) > 0) {
                        received.countDown();
                    }
                    while (in.read(buf) != -1) {
                        // the client holds the connection until it aborts
                    }
                } catch (IOException ignored) {
                    // the socket closes with the server when the test ends
                } finally {
                    clientGone.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        String url() {
            return "http://localhost:" + server.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    /** Answers every request with 200 at once. */
    static final class OkStub implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final AtomicInteger requests = new AtomicInteger();

        OkStub() throws IOException {
            Thread thread = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.getInputStream().read(new byte[4096]);
                        requests.incrementAndGet();
                        OutputStream out = socket.getOutputStream();
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                                .getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } catch (IOException ignored) {
                        // closed
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        String url() {
            return "http://localhost:" + server.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    /** A run on its own thread, cancelled from the test thread once {@code trigger} fires. */
    static final class Run {
        final Suite suite;
        final Future<SuiteResult> future;
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        Run(Suite suite) {
            this.suite = suite;
            this.future = executor.submit(suite::run);
        }

        /** Cancels after the trigger and returns the millis from the cancel call to the run's end. */
        long cancelWhen(CountDownLatch trigger) throws Exception {
            assertTrue(trigger.await(15, TimeUnit.SECONDS), "the fixture reached its trigger");
            return cancelNow();
        }

        /** Cancels once {@code condition} holds (polled) and returns the millis from the cancel call to the run's end. */
        long cancelWhen(java.util.function.BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!condition.getAsBoolean()) {
                assertTrue(System.nanoTime() < deadline, "the fixture reached its trigger");
                Thread.sleep(5);
            }
            return cancelNow();
        }

        private long cancelNow() throws Exception {
            long t0 = System.nanoTime();
            assertTrue(suite.cancel("test cancel"));
            assertFalse(suite.cancel("second"), "idempotent");
            future.get(15, TimeUnit.SECONDS);
            executor.shutdown();
            return (System.nanoTime() - t0) / 1_000_000;
        }

        SuiteResult result() throws Exception {
            return future.get();
        }
    }

    private static Runner.Builder builder(Path dir) {
        return Runner.builder()
                .path(dir.resolve("main.feature").toString())
                .configDir(dir.toString())
                .workingDir(dir)
                .outputDir(dir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .backupOutputDir(false);
    }

    /** The outcome every cancelled run must show: on time, the pre-run total accounted for once each, never passed. */
    private static void assertCancelledOutcome(Run run, long elapsed) throws Exception {
        SuiteResult result = run.result();
        System.out.println("[SuiteCancelTest] run ended " + elapsed + " ms after cancel");
        assertTrue(result.isCancelled(), "suite result cancelled");
        int passed = result.getScenarioPassedCount();
        int failed = result.getScenarioFailedCount();
        int cancelled = result.getScenarioCancelledCount();
        assertEquals(run.suite.getScenarioTotal(), passed + failed + cancelled,
                "the pre-run selected total, each scenario counted once");
        assertEquals(result.getScenarioCount(), passed + failed + cancelled);
        assertFalse(result.isPassed(), "a cancelled run is not passed");
        assertEquals(Boolean.FALSE, result.summaryJson().get("passed"));
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isCancelled()) {
                    assertFalse(sr.isPassed(), sr.getScenario().getName());
                    assertFalse(sr.isFailed(), sr.getScenario().getName() + " cancelled is exclusive");
                }
            }
            if (fr.isCancelled()) {
                assertFalse(fr.isPassed(), fr.getDisplayName());
                assertEquals(Boolean.FALSE, fr.toJson().get("passed"), fr.getDisplayName());
            }
        }
        assertEquals("test cancel", result.getCancelReason());
        assertTrue(result.getScenarioCancelledCount() > 0, "an unfinished scenario reads cancelled");
        assertEquals(Boolean.TRUE, result.summaryJson().get("cancelled"));
        assertTrue(elapsed < BUDGET_MILLIS, "ended " + elapsed + " ms after cancel");
    }

    @Test
    void aCallLoopOf2000RowsStopsAtItsNextIteration(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("main.feature"), """
                Feature: loop
                Scenario: loop
                * def rows = []
                * eval for (var i = 0; i < 2000; i++) rows.push({ i: i })
                * call read('called.feature') rows
                * def after = 1
                """);
        Files.writeString(dir.resolve("called.feature"), """
                Feature: called
                Scenario:
                * def x = __loop
                * karate.pause(1)
                """);
        CountDownLatch trigger = new CountDownLatch(1);
        AtomicInteger nestedEnters = new AtomicInteger();
        Suite suite = builder(dir).listener(event -> {
            if (event instanceof ScenarioRunEvent e && e.type() == RunEventType.SCENARIO_ENTER
                    && e.source().getFeatureRuntime().getCallDepth() == 1 && nestedEnters.incrementAndGet() == 100) {
                trigger.countDown();
            }
            return true;
        }).buildSuite(1);
        Run run = new Run(suite);
        long elapsed = run.cancelWhen(trigger);
        SuiteResult result = run.result();
        assertCancelledOutcome(run, elapsed);
        assertEquals(1, suite.getScenarioTotal());
        assertEquals(1, result.getScenarioCancelledCount());
        assertTrue(nestedEnters.get() < 2000, "loop stopped at " + nestedEnters.get());
        ScenarioResult loop = result.getFeatureResults().getFirst().getScenarioResults().getFirst();
        assertTrue(loop.isCancelled());
        assertFalse(loop.isPassed());
        List<StepResult> steps = loop.getStepResults();
        assertTrue(steps.get(2).isCancelled(), "the call step: " + steps.get(2).getStatus());
        assertTrue(steps.get(3).isSkipped(), "the step after it: " + steps.get(3).getStatus());
    }

    @Test
    void aRetryUntilPollWakesFromItsSleep(@TempDir Path dir) throws Exception {
        try (OkStub stub = new OkStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: poll
                    Scenario: poll
                    * configure retry = { count: 50, interval: 5000 }
                    * url '%s'
                    * retry until responseStatus == 599
                    * method get
                    * def after = 1
                    """.formatted(stub.url()));
            CountDownLatch trigger = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();
            Suite suite = builder(dir).listener(event -> {
                if (event.getType() == RunEventType.HTTP_EXIT && attempts.incrementAndGet() == 2) {
                    trigger.countDown();
                }
                return true;
            }).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(trigger);
            SuiteResult result = run.result();
            assertCancelledOutcome(run, elapsed);
            assertEquals(2, attempts.get(), "no attempt after the cancel");
            ScenarioResult poll = result.getFeatureResults().getFirst().getScenarioResults().getFirst();
            assertTrue(poll.isCancelled());
            assertTrue(poll.getStepResults().get(3).isCancelled(), "the method step");
        }
    }

    @Test
    void aParallelRunCancelsQueuedScenarios(@TempDir Path dir) throws Exception {
        StringBuilder sb = new StringBuilder("Feature: parallel\n");
        for (int i = 0; i < 40; i++) {
            sb.append("Scenario: s").append(i).append("\n* karate.pause(100)\n* def done = ").append(i).append("\n");
        }
        Files.writeString(dir.resolve("main.feature"), sb.toString());
        CountDownLatch trigger = new CountDownLatch(1);
        AtomicInteger enters = new AtomicInteger();
        Suite suite = builder(dir).listener(event -> {
            if (event.getType() == RunEventType.SCENARIO_ENTER && enters.incrementAndGet() == 8) {
                trigger.countDown();
            }
            return true;
        }).buildSuite(4);
        Run run = new Run(suite);
        long elapsed = run.cancelWhen(trigger);
        SuiteResult result = run.result();
        assertCancelledOutcome(run, elapsed);
        List<ScenarioResult> scenarios = result.getFeatureResults().getFirst().getScenarioResults();
        int passed = (int) scenarios.stream().filter(ScenarioResult::isPassed).count();
        int cancelled = (int) scenarios.stream().filter(ScenarioResult::isCancelled).count();
        assertEquals(40, scenarios.size(), "every selected scenario has a verdict");
        assertEquals(40, suite.getScenarioTotal());
        assertEquals(suite.getScenarioTotal(), passed + result.getScenarioFailedCount() + cancelled);
        assertTrue(passed <= enters.get(), "only a started scenario can pass: " + passed + " of " + enters.get());
        assertTrue(cancelled >= 40 - enters.get(), "the queue was cancelled: " + cancelled);
        assertEquals(40, enters.get(), "a never-started scenario still fires its ENTER");
        for (ScenarioResult sr : scenarios) {
            if (sr.isCancelled()) {
                // queued: no step at all; in flight: stopped at the boundary, the rest skipped
                List<StepResult> steps = sr.getStepResults();
                assertTrue(steps.isEmpty() || steps.getLast().isSkipped(), sr.getScenario().getName() + " " + steps);
                assertFalse(sr.isPassed());
            }
        }
    }

    @Test
    void aCancelDuringKarateConfigJsAbortsItsCall(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("karate-config.js"), """
                    function fn() {
                      karate.call('setup.feature');
                      return { a: 1 };
                    }
                    """);
            Files.writeString(dir.resolve("setup.feature"), """
                    Feature: setup
                    Scenario:
                    * url '%s'
                    * method get
                    """.formatted(stub.url()));
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: main
                    Scenario: one
                    * def x = 1
                    """);
            Suite suite = builder(dir).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            SuiteResult result = run.result();
            assertCancelledOutcome(run, elapsed);
            assertTrue(stub.clientGone.await(BUDGET_MILLIS, TimeUnit.MILLISECONDS), "the HTTP call was aborted");
            ScenarioResult one = result.getFeatureResults().getFirst().getScenarioResults().getFirst();
            assertTrue(one.isCancelled(), "cancelled, not a config failure");
            assertFalse(one.isFailed());
        }
    }

    @Test
    void aCancelDuringCallonceInBackgroundAbortsItsCall(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("setup.feature"), """
                    Feature: setup
                    Scenario:
                    * url '%s'
                    * method get
                    """.formatted(stub.url()));
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: bg
                    Background:
                    * callonce read('setup.feature')
                    Scenario: one
                    * def x = 1
                    Scenario: two
                    * def y = 2
                    """);
            Suite suite = builder(dir).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            SuiteResult result = run.result();
            assertCancelledOutcome(run, elapsed);
            assertTrue(stub.clientGone.await(BUDGET_MILLIS, TimeUnit.MILLISECONDS), "the HTTP call was aborted");
            List<ScenarioResult> scenarios = result.getFeatureResults().getFirst().getScenarioResults();
            assertEquals(0, result.getScenarioPassedCount());
            assertTrue(scenarios.getFirst().isCancelled());
            assertTrue(scenarios.getFirst().getStepResults().getFirst().isCancelled(), "the callonce step");
        }
    }

    @Test
    void featuresNotYetStartedRecordEveryScenarioCancelled(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: first
                    Scenario: hang
                    * url '%s'
                    * method get
                    Scenario: next
                    * def x = 1
                    """.formatted(stub.url()));
            Files.writeString(dir.resolve("second.feature"), """
                    Feature: second
                    Scenario: a
                    * def a = 1
                    Scenario: b
                    * def b = 1
                    """);
            Files.writeString(dir.resolve("third.feature"), """
                    Feature: third
                    @setup
                    Scenario:
                    * def data = [{ v: 1 }, { v: 2 }, { v: 3 }]
                    Scenario Outline: dynamic <v>
                    * def v = <v>
                    Examples:
                    | karate.setup().data |
                    Scenario Outline: static <w>
                    * def w = <w>
                    Examples:
                    | w |
                    | 1 |
                    | 2 |
                    """);
            List<String> exits = new java.util.concurrent.CopyOnWriteArrayList<>();
            Suite suite = builder(dir)
                    .path(dir.resolve("second.feature").toString(), dir.resolve("third.feature").toString())
                    .outputJsonLines(true)
                    .listener(event -> {
                        if (event instanceof ScenarioRunEvent e && e.type() == RunEventType.SCENARIO_EXIT) {
                            exits.add(e.source().getScenario().getName() + ":" + e.result().isCancelled());
                        }
                        return true;
                    })
                    .buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            SuiteResult result = run.result();
            assertCancelledOutcome(run, elapsed);
            // 2 + 2 + (the dynamic table as one + 2 static rows): the pre-count is what the run recorded
            assertEquals(7, suite.getScenarioTotal());
            assertEquals(7, result.getScenarioCount());
            assertEquals(7, result.getScenarioCancelledCount());
            assertEquals(0, result.getScenarioPassedCount());
            assertEquals(3, result.getFeatureResults().size(), "features not yet started still report");
            assertEquals(List.of("hang:true", "next:true", "a:true", "b:true", "dynamic <v>:true",
                    "static 1:true", "static 2:true"), exits, "a SCENARIO_EXIT with the cancelled outcome for each");
            ScenarioResult dynamic = result.getFeatureResults().get(2).getScenarioResults().getFirst();
            assertEquals(-1, dynamic.getScenario().getExampleIndex(), "the outline's own result stands for its rows");
            List<String> lines = Files.readAllLines(dir.resolve("reports").resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
            assertEquals(7, lines.stream().filter(l -> l.startsWith("{\"type\":\"SCENARIO_EXIT\"") && l.contains("\"cancelled\":true")).count());
        }
    }


    @Test
    void everyHttpInvocationAfterCancelIsRefusedNotOnlyTheFirst(@TempDir Path dir) throws Exception {
        try (HangingStub first = new HangingStub(); HangingStub second = new HangingStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: two calls
                    Scenario: js
                    * eval try { karate.http('%s').get() } catch (e) { karate.log('first aborted') } karate.http('%s').get()
                    * def after = 1
                    """.formatted(first.url(), second.url()));
            Suite suite = builder(dir).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(first.received);
            assertCancelledOutcome(run, elapsed);
            assertEquals(1, second.received.getCount(), "the second call was refused, not sent");
        }
    }

    @Test
    void aFinalStepReturningNormallyAfterCancelReadsCancelled(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("main.feature"), """
                Feature: last step
                Scenario: pause
                * def x = 1
                * karate.pause(300)
                """);
        CountDownLatch trigger = new CountDownLatch(1);
        Suite suite = builder(dir).listener(event -> {
            if (event instanceof StepRunEvent e && e.type() == RunEventType.STEP_ENTER && e.step().getLine() == 4) {
                trigger.countDown();
            }
            return true;
        }).buildSuite(1);
        Run run = new Run(suite);
        long elapsed = run.cancelWhen(trigger);
        assertCancelledOutcome(run, elapsed);
        ScenarioResult pause = run.result().getFeatureResults().getFirst().getScenarioResults().getFirst();
        assertTrue(pause.isCancelled(), "not passed: " + pause.getStepResults().get(1).getStatus());
        assertTrue(pause.getStepResults().get(1).isCancelled());
    }

    @Test
    void aCancelDuringADynamicOutlinesSetupRecordsThePlaceholderAndContinues(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: dynamic setup
                    @setup
                    Scenario:
                    * url '%s'
                    * method get
                    * def data = [{ v: 1 }, { v: 2 }]
                    Scenario Outline: dyn <v>
                    * def v = <v>
                    Examples:
                    | karate.setup().data |
                    Scenario: after
                    * def z = 1
                    """.formatted(stub.url()));
            Suite suite = builder(dir).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            assertCancelledOutcome(run, elapsed);
            SuiteResult result = run.result();
            assertEquals(0, result.getScenarioFailedCount(), "cancel during setup is not a failure");
            List<ScenarioResult> scenarios = result.getFeatureResults().getFirst().getScenarioResults();
            assertEquals(List.of("dyn <v>", "after"), scenarios.stream().map(sr -> sr.getScenario().getName()).toList());
            assertEquals(2, result.getScenarioCancelledCount());
        }
    }

    @Test
    void anIgnoredDynamicOutlineCancelledInSetupIsNotRecorded(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: ignored dynamic
                    @setup
                    Scenario:
                    * url '%s'
                    * method get
                    * def data = [{ v: 1 }]
                    @ignore
                    Scenario Outline: dyn <v>
                    * def v = <v>
                    Examples:
                    | karate.setup().data |
                    Scenario: after
                    * def z = 1
                    """.formatted(stub.url()));
            Suite suite = builder(dir).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            assertCancelledOutcome(run, elapsed);
            assertEquals(1, suite.getScenarioTotal(), "the ignored outline is not selected");
            List<ScenarioResult> scenarios = run.result().getFeatureResults().getFirst().getScenarioResults();
            assertEquals(List.of("after"), scenarios.stream().map(sr -> sr.getScenario().getName()).toList());
        }
    }

    @Test
    void anInterruptedFinalizationStillWaitsForAPublishingCancel(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("main.feature"), """
                Feature: barrier
                Scenario: one
                * def a = 1
                * karate.pause(200)
                * def b = 2
                """);
        CountDownLatch trigger = new CountDownLatch(1);
        CountDownLatch cancelEntered = new CountDownLatch(1);
        CountDownLatch releaseCancel = new CountDownLatch(1);
        List<RunEventType> order = new java.util.concurrent.CopyOnWriteArrayList<>();
        Suite suite = builder(dir)
                // the first listener holds RUN_CANCEL open: the cancel is publishing until released
                .listener(event -> {
                    if (event instanceof StepRunEvent e && e.type() == RunEventType.STEP_ENTER && e.step().getLine() == 4) {
                        trigger.countDown();
                    }
                    if (event.getType() == RunEventType.RUN_CANCEL) {
                        cancelEntered.countDown();
                        try {
                            releaseCancel.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return true;
                })
                .listener(event -> {
                    order.add(event.getType());
                    return true;
                })
                .buildSuite(1);
        Thread[] runThread = new Thread[1];
        java.util.concurrent.atomic.AtomicBoolean interruptedAfter = new java.util.concurrent.atomic.AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<SuiteResult> future = executor.submit(() -> {
            runThread[0] = Thread.currentThread();
            try {
                return suite.run();
            } finally {
                interruptedAfter.set(Thread.interrupted());
            }
        });
        assertTrue(trigger.await(15, TimeUnit.SECONDS));
        Thread canceller = new Thread(() -> suite.cancel("test cancel"));
        canceller.start();
        assertTrue(cancelEntered.await(15, TimeUnit.SECONDS), "the cancel is publishing");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (runThread[0] == null || runThread[0].getState() != Thread.State.WAITING) {
            assertTrue(System.nanoTime() < deadline, "the run reached its completion barrier");
            Thread.sleep(5);
        }
        runThread[0].interrupt();
        Thread.sleep(300);
        assertFalse(order.contains(RunEventType.RUN_COMPLETE), "an interrupt does not break the barrier");
        releaseCancel.countDown();
        SuiteResult result = future.get(15, TimeUnit.SECONDS);
        executor.shutdown();
        canceller.join(5000);
        assertTrue(result.isCancelled());
        List<RunEventType> runEvents = order.stream()
                .filter(t -> t == RunEventType.RUN_CANCEL || t == RunEventType.RUN_COMPLETE).toList();
        assertEquals(List.of(RunEventType.RUN_CANCEL, RunEventType.RUN_COMPLETE), runEvents);
        assertTrue(interruptedAfter.get(), "the interrupt is restored once the barrier is passed");
    }

    @Test
    void aCancelAfterAnAbortOnFailureStillEnumeratesEveryScenario(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("karate-config.js"), """
                function fn() { karate.configure('abortSuiteOnFailure', true); return {}; }
                """);
        Files.writeString(dir.resolve("main.feature"), """
                Feature: first
                Scenario: fails
                * assert false
                """);
        Files.writeString(dir.resolve("second.feature"), """
                Feature: second
                Scenario: a
                * karate.pause(300)
                Scenario: b
                * karate.pause(300)
                """);
        for (int threads : new int[]{1, 2}) {
            CountDownLatch trigger = new CountDownLatch(1);
            Suite suite = builder(dir).path(dir.resolve("second.feature").toString()).listener(event -> {
                if (event instanceof ScenarioRunEvent e && e.type() == RunEventType.SCENARIO_EXIT
                        && "fails".equals(e.source().getScenario().getName())) {
                    trigger.countDown();
                }
                return true;
            }).buildSuite(threads);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(trigger);
            assertCancelledOutcome(run, elapsed);
            SuiteResult result = run.result();
            assertEquals(3, suite.getScenarioTotal());
            assertEquals(1, result.getScenarioFailedCount(), "threads " + threads);
            assertEquals(2, result.getFeatureResults().size(), "threads " + threads);
            if (threads == 1) {
                assertEquals(2, result.getScenarioCancelledCount(), "nothing after the failure ran");
            } else {
                // the second feature runs beside the first: what finished before the cancel passed, the rest is cancelled
                assertEquals(2, result.getScenarioPassedCount() + result.getScenarioCancelledCount());
            }
        }
    }

    @Test
    void aFailTagScenarioCancelledIsCancelledNotFailed(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: fail tag
                    @fail
                    Scenario: inverted
                    * url '%s'
                    * method get
                    * assert false
                    """.formatted(stub.url()));
            Suite suite = builder(dir).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            assertCancelledOutcome(run, elapsed);
            ScenarioResult inverted = run.result().getFeatureResults().getFirst().getScenarioResults().getFirst();
            assertTrue(inverted.isCancelled());
            assertFalse(inverted.isFailed());
            assertEquals(1, run.result().getScenarioCancelledCount());
            assertEquals(0, run.result().getScenarioFailedCount());
        }
    }

    @Test
    void aCancelAfterTheRunCompletedIsRefusedAndFiresNothing(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("main.feature"), """
                Feature: done
                Scenario: one
                * def x = 1
                """);
        List<RunEventType> types = new java.util.concurrent.CopyOnWriteArrayList<>();
        Suite suite = builder(dir).listener(event -> {
            types.add(event.getType());
            return true;
        }).buildSuite(1);
        SuiteResult result = suite.run();
        assertTrue(result.isPassed());
        assertFalse(suite.cancel("late"), "the run is final");
        assertFalse(suite.isCancelled());
        assertFalse(result.isCancelled());
        assertEquals(RunEventType.RUN_COMPLETE, types.getLast());
        assertFalse(types.contains(RunEventType.RUN_CANCEL));
    }

    @Test
    void concurrentCallonceWaitersAreCancelledWithTheOwner(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("setup.feature"), """
                    Feature: setup
                    Scenario:
                    * url '%s'
                    * method get
                    """.formatted(stub.url()));
            StringBuilder sb = new StringBuilder("Feature: waiters\nBackground:\n* callonce read('setup.feature')\n");
            for (int i = 0; i < 4; i++) {
                sb.append("Scenario: s").append(i).append("\n* def x = ").append(i).append("\n");
            }
            Files.writeString(dir.resolve("main.feature"), sb.toString());
            Suite suite = builder(dir).buildSuite(4);
            Run run = new Run(suite);
            // the owner is in its HTTP call and the other three are blocked on the callonce lock
            java.util.concurrent.locks.ReentrantLock lock = FeatureRuntime.of(suite, suite.features.getFirst()).getCallOnceLock();
            long elapsed = run.cancelWhen(() -> stub.received.getCount() == 0 && lock.getQueueLength() == 3);
            assertCancelledOutcome(run, elapsed);
            assertTrue(stub.clientGone.await(BUDGET_MILLIS, TimeUnit.MILLISECONDS), "the HTTP call was aborted");
            assertEquals(4, run.result().getScenarioCancelledCount());
            assertEquals(0, run.result().getScenarioPassedCount());
        }
    }

    @Test
    void aCancelDuringAnHttpCallAbortsItAndTheReportsSaySo(@TempDir Path dir) throws Exception {
        try (HangingStub stub = new HangingStub()) {
            Files.writeString(dir.resolve("main.feature"), """
                    Feature: http
                    Scenario: hang
                    * url '%s'
                    * method get
                    * def after = 1
                    """.formatted(stub.url()));
            Suite suite = builder(dir).outputHtmlReport(true).outputJsonLines(true).outputJunitXml(true).buildSuite(1);
            Run run = new Run(suite);
            long elapsed = run.cancelWhen(stub.received);
            SuiteResult result = run.result();
            assertCancelledOutcome(run, elapsed);
            assertTrue(stub.clientGone.await(BUDGET_MILLIS, TimeUnit.MILLISECONDS), "the HTTP call was aborted");
            ScenarioResult hang = result.getFeatureResults().getFirst().getScenarioResults().getFirst();
            assertTrue(hang.isCancelled());
            assertTrue(hang.getStepResults().get(1).isCancelled(), "the method step");
            assertTrue(hang.getStepResults().get(2).isSkipped());
            assertEquals(Boolean.TRUE, hang.toJson().get("cancelled"));
            Path reports = dir.resolve("reports");
            String summary = Files.readString(reports.resolve("karate-summary.html"));
            assertTrue(summary.contains("\"scenario_cancelled\":1"), "summary page counts the cancel");
            assertTrue(summary.contains("\"status\":\"cancelled\""));
            try (var files = Files.walk(reports)) {
                Path xml = files.filter(f -> f.getFileName().toString().endsWith(".xml")).findFirst().orElseThrow();
                String testsuite = Files.readString(xml);
                assertTrue(testsuite.contains("skipped=\"1\""), "the suite counts its skipped testcase: " + testsuite);
                assertTrue(testsuite.contains("<skipped message=\"cancelled\"/>"), testsuite);
            }
            List<String> lines = Files.readAllLines(reports.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("{\"type\":\"RUN_CANCEL\"") && l.contains("\"reason\":\"test cancel\"")));
            String last = lines.getLast();
            assertTrue(last.startsWith("{\"type\":\"RUN_COMPLETE\""), last);
            assertTrue(last.contains("\"cancelled\":true"), last);
            assertTrue(last.contains("karate-summary.html"), "report readiness: " + last);
        }
    }

}
