package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-thread listeners (RunListenerFactory) in a parallel run: a scenario runs on its own
 * scenario-executor thread, and its SCENARIO / STEP events must reach a listener created on that
 * same thread. Before the fix they reached none: the listeners were set up on the feature thread only.
 */
class ThreadListenerParallelTest {

    record Received(String type, String scenario, Thread createdOn, Thread firedOn) {
    }

    @Test
    void scenarioEventsReachAListenerCreatedOnTheScenarioThread(@TempDir Path dir) {
        Feature feature = Feature.read(Resource.text("""
                Feature: parallel
                Scenario: one
                * def x = 1
                Scenario: two
                * def x = 2
                Scenario: three
                * def x = 3
                Scenario Outline: row <v>
                * def y = <v>
                Examples:
                | v |
                | 1 |
                | 2 |
                """));
        List<Received> received = new CopyOnWriteArrayList<>();
        List<Thread> created = new CopyOnWriteArrayList<>();
        SuiteResult result = Runner.builder()
                .features(feature)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .outputDir(dir)
                .listenerFactory(() -> {
                    Thread createdOn = Thread.currentThread();
                    created.add(createdOn);
                    return event -> {
                        if (event instanceof ScenarioRunEvent sre) {
                            received.add(new Received(event.getType().name(), sre.source().getScenario().getName(),
                                    createdOn, Thread.currentThread()));
                        } else if (event.getType() == RunEventType.STEP_ENTER) {
                            received.add(new Received(event.getType().name(), null, createdOn, Thread.currentThread()));
                        }
                        return true;
                    };
                })
                .parallel(3);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
        List<String> names = List.of("one", "two", "three", "row 1", "row 2");
        for (String type : List.of("SCENARIO_ENTER", "SCENARIO_EXIT")) {
            List<String> seen = received.stream().filter(r -> r.type().equals(type)).map(Received::scenario).sorted().toList();
            assertEquals(names.stream().sorted().toList(), seen, type + " reaches a per-thread listener once per scenario");
        }
        assertEquals(5, received.stream().filter(r -> r.type().equals("STEP_ENTER")).count(), "step events too");
        for (Received r : received) {
            assertSame(r.createdOn(), r.firedOn(), "fired on the thread its listener was created for: " + r);
        }
        // one listener set for the feature thread, plus one per scenario thread
        assertEquals(1 + names.size(), created.size(), created::toString);
    }

    @Test
    void aListenerFactoryThatThrowsOnAScenarioThreadDoesNotHangTheRun(@TempDir Path dir) {
        // more scenarios than permits: a leaked permit would park the rest for ever
        StringBuilder sb = new StringBuilder("Feature: throwing factory\n");
        for (int i = 0; i < 6; i++) {
            sb.append("Scenario: s").append(i).append("\n* def x = ").append(i).append("\n");
        }
        Feature feature = Feature.read(Resource.text(sb.toString()));
        java.util.concurrent.atomic.AtomicInteger creates = new java.util.concurrent.atomic.AtomicInteger();
        SuiteResult result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(60), () -> Runner.builder()
                .features(feature)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .outputDir(dir)
                .listenerFactory(() -> {
                    if (creates.incrementAndGet() > 1) { // the feature thread's succeeds, the scenario threads' fail
                        throw new IllegalStateException("no listener for you");
                    }
                    return event -> true;
                })
                .parallel(2));
        assertEquals(1 + 6, creates.get(), "the feature thread, then every scenario thread tried");
        assertTrue(result.isFailed(), "the scenarios whose listeners failed are reported");
    }

}
