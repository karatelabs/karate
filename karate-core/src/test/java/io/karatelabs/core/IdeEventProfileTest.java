package io.karatelabs.core;

import io.karatelabs.driver.Driver;
import io.karatelabs.driver.DriverProvider;
import io.karatelabs.output.ResultListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The IDE event profile ({@code Runner.Builder.ideEvents}): ids on every event, one gap-free sequence
 * across threads, live STEP_LOG chunks, the Examples table index, PROGRESS, and RUN_COMPLETE last.
 */
class IdeEventProfileTest {

    record Seen(RunEvent event, Map<String, Object> envelope, String thread, boolean reportWritten,
                boolean driverShutdown, boolean mockStopped) {
    }

    @Test
    void everyEventCarriesItsIdsInOneGapFreeSequence(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("main.feature"), """
                Feature: profile
                Background:
                * def bg = 1
                Scenario: calls
                * def before = 1
                * call read('called.feature')
                * def after = 1
                Scenario: prints
                * print 'hello from prints'
                * def server = karate.start({ mock: '/mock.feature', port: 0 })
                * print 'mockport=' + server.port
                Scenario: c
                * def c = 1
                Scenario: d
                * def d = 1
                Scenario: e
                * def e = 1
                Scenario Outline: row <v>
                * def v = <v>
                Examples:
                | v |
                | 1 |
                | 2 |
                Examples:
                | v |
                | 3 |
                | 4 |
                """);
        Files.writeString(dir.resolve("called.feature"), """
                Feature: called
                Scenario:
                * print 'hello from called'
                * def y = 1
                """);
        Files.writeString(dir.resolve("mock.feature"), """
                Feature: mock
                Scenario:
                * def response = {}
                """);
        List<Seen> seen = new CopyOnWriteArrayList<>();
        AtomicBoolean suiteEnded = new AtomicBoolean();
        AtomicBoolean driverShutdown = new AtomicBoolean();
        Path summaryPage = dir.resolve("reports").resolve("karate-summary.html");
        Suite[] suiteRef = new Suite[1];
        SuiteResult result = Runner.builder()
                .path(dir.resolve("main.feature").toString())
                .configDir(dir.toString())
                .workingDir(dir)
                .outputDir(dir.resolve("reports"))
                .outputConsoleSummary(false)
                .backupOutputDir(false)
                .ideEvents(true)
                .runId("run-1")
                .driverProvider(new DriverProvider() {
                    @Override
                    public Driver acquire(ScenarioRuntime runtime, Map<String, Object> config) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void release(ScenarioRuntime runtime, Driver driver) {
                    }

                    @Override
                    public void shutdown() {
                        driverShutdown.set(true);
                    }
                })
                .resultListener(new ResultListener() {
                    @Override
                    public void onSuiteEnd(SuiteResult result) {
                        suiteEnded.set(true);
                    }
                })
                .onSuite(suite -> suiteRef[0] = suite)
                .listener(event -> {
                    boolean complete = event.getType() == RunEventType.RUN_COMPLETE;
                    seen.add(new Seen(event, JsonLinesEventWriter.envelope(event), Thread.currentThread().getName(),
                            complete && suiteEnded.get() && Files.exists(summaryPage), driverShutdown.get(),
                            complete && mockStopped(seen)));
                    return true;
                })
                .parallel(4);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());

        // ids: every event names the run; a scenario-scoped event names its scenario
        for (Seen s : seen) {
            assertEquals("run-1", s.envelope().get("runId"), s.event().getType().name());
        }
        // the sequence is the delivery order, 1..N, with no gap, across more than one thread
        long expected = 1;
        for (Seen s : seen) {
            assertEquals(expected++, s.event().getSequence(), "sequence of " + s.event().getType());
            assertEquals(s.event().getSequence(), s.envelope().get("sequence"));
        }
        Set<String> threads = new HashSet<>();
        seen.forEach(s -> threads.add(s.thread()));
        assertTrue(threads.size() > 1, "parallel producers: " + threads);

        // a nested scenario resolves to its calling scenario and step (the `call` step is line 6)
        List<Seen> nested = seen.stream().filter(s -> s.event() instanceof ScenarioRunEvent e
                && e.source().getFeatureRuntime().getCallDepth() == 1).toList();
        assertEquals(2, nested.size(), "ENTER and EXIT of the called scenario");
        Integer callerIndex = executionIndexOf(seen, "calls");
        assertNotNull(callerIndex);
        for (Seen s : nested) {
            assertEquals(callerIndex, s.envelope().get("parentExecutionIndex"));
            assertEquals(6, s.envelope().get("parentStepLine"));
            assertNotEquals(callerIndex, s.envelope().get("executionIndex"));
        }
        Seen nestedStep = seen.stream().filter(s -> s.event() instanceof StepRunEvent e
                && e.scenarioRuntime().getFeatureRuntime().getCallDepth() == 1).findFirst().orElseThrow();
        assertEquals(nested.getFirst().envelope().get("executionIndex"), nestedStep.envelope().get("executionIndex"));
        assertEquals(callerIndex, nestedStep.envelope().get("parentExecutionIndex"));
        assertEquals(6, nestedStep.envelope().get("parentStepLine"));
        Seen nestedFeature = seen.stream().filter(s -> s.event() instanceof FeatureRunEvent e
                && e.source().getCallDepth() == 1).findFirst().orElseThrow();
        assertEquals(callerIndex, nestedFeature.envelope().get("parentExecutionIndex"));

        // a step's log chunk precedes that step's STEP_EXIT, for the nested print too
        List<Seen> logs = seen.stream().filter(s -> s.event() instanceof StepLogRunEvent).toList();
        assertTrue(logs.stream().anyMatch(s -> ((StepLogRunEvent) s.event()).text().contains("hello from prints")));
        assertTrue(logs.stream().anyMatch(s -> ((StepLogRunEvent) s.event()).text().contains("hello from called")));
        for (Seen log : logs) {
            StepLogRunEvent e = (StepLogRunEvent) log.event();
            assertNotNull(e.step(), "a chunk inside a step");
            int at = seen.indexOf(log);
            boolean exitAfter = false;
            for (int i = at + 1; i < seen.size(); i++) {
                if (seen.get(i).event() instanceof StepRunEvent se && se.type() == RunEventType.STEP_EXIT
                        && se.scenarioRuntime() == e.scenarioRuntime() && se.step() == e.step()) {
                    exitAfter = true;
                    break;
                }
            }
            assertTrue(exitAfter, "STEP_EXIT after the chunk: " + e.text());
        }

        // two Examples tables: distinct table indexes, rows restarting per table
        Set<String> rows = new HashSet<>();
        for (Seen s : seen) {
            if (s.event() instanceof ScenarioRunEvent e && e.type() == RunEventType.SCENARIO_EXIT
                    && e.source().getScenario().isOutlineExample()) {
                Map<String, Object> data = e.toJson();
                rows.add(data.get("exampleTableIndex") + ":" + data.get("exampleIndex"));
            }
        }
        assertEquals(Set.of("0:0", "0:1", "1:0", "1:1"), rows);

        // progress counts top-level scenarios against the static total
        List<ProgressRunEvent> progress = seen.stream().filter(s -> s.event() instanceof ProgressRunEvent)
                .map(s -> (ProgressRunEvent) s.event()).toList();
        assertEquals(9, progress.size());
        assertEquals(9, suiteRef[0].getScenarioTotal());
        for (int i = 0; i < progress.size(); i++) {
            assertEquals(i + 1, progress.get(i).completed());
            assertEquals(9, progress.get(i).total());
        }

        // RUN_COMPLETE is last, after the report's last write, the driver shutdown and the mock stop
        Seen last = seen.getLast();
        assertEquals(RunEventType.RUN_COMPLETE, last.event().getType());
        assertTrue(last.reportWritten(), "summary page written before RUN_COMPLETE");
        assertTrue(last.driverShutdown(), "driver provider shut down before RUN_COMPLETE");
        assertTrue(last.mockStopped(), "mock server stopped before RUN_COMPLETE");
        assertEquals(summaryPage.toString(), last.envelope().get("data") instanceof Map<?, ?> m ? m.get("htmlReport") : null);
        assertEquals(1, seen.stream().filter(s -> s.event().getType() == RunEventType.SUITE_EXIT).count());
        assertTrue(seen.indexOf(seen.stream().filter(s -> s.event().getType() == RunEventType.SUITE_EXIT).findFirst().orElseThrow())
                < seen.size() - 1);
    }

    @Test
    void configTimeEventsResolveAndAReentrantCancelKeepsDeliveryOrder(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("karate-config.js"), """
                function fn() {
                  karate.log('cfg log');
                  karate.call('called.feature');
                  karate.configure('afterScenario', function() { karate.log('teardown log'); });
                  return {};
                }
                """);
        Files.writeString(dir.resolve("called.feature"), """
                Feature: called
                Scenario:
                * def y = 1
                """);
        Files.writeString(dir.resolve("main.feature"), """
                Feature: reentrant
                Scenario: one
                * def a = 1
                * def b = 2
                * def c = 3
                """);
        List<Seen> seenByB = new CopyOnWriteArrayList<>();
        Suite[] suiteRef = new Suite[1];
        SuiteResult result = Runner.builder()
                .path(dir.resolve("main.feature").toString())
                .configDir(dir.toString())
                .workingDir(dir)
                .outputDir(dir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .backupOutputDir(false)
                .ideEvents(true)
                .onSuite(suite -> suiteRef[0] = suite)
                .listener(event -> {
                    // listener A cancels from inside the dispatch of the `def b` STEP_ENTER
                    if (event instanceof StepRunEvent e && e.type() == RunEventType.STEP_ENTER && e.step().getLine() == 4) {
                        suiteRef[0].cancel("from a listener");
                    }
                    return true;
                })
                .listener(event -> {
                    seenByB.add(new Seen(event, JsonLinesEventWriter.envelope(event), Thread.currentThread().getName(),
                            false, false, false));
                    return true;
                })
                .parallel(1);
        assertTrue(result.isCancelled());

        // listener B sees every event in sequence order: the re-entrant RUN_CANCEL waits its turn
        long expected = 1;
        for (Seen s : seenByB) {
            assertEquals(expected++, s.event().getSequence(), "sequence of " + s.event().getType());
        }
        int trigger = -1;
        int cancel = -1;
        for (int i = 0; i < seenByB.size(); i++) {
            RunEvent e = seenByB.get(i).event();
            if (e instanceof StepRunEvent se && se.type() == RunEventType.STEP_ENTER && se.step().getLine() == 4) {
                trigger = i;
            }
            if (e.getType() == RunEventType.RUN_CANCEL) {
                cancel = i;
            }
        }
        assertTrue(trigger >= 0 && cancel > trigger, "RUN_CANCEL after the event whose listener cancelled: " + trigger + " < " + cancel);
        assertEquals(RunEventType.RUN_COMPLETE, seenByB.getLast().event().getType());

        // config-time output and the config-time call resolve to the scenario that evaluated the config
        Integer oneIndex = executionIndexOf(seenByB, "one");
        assertNotNull(oneIndex);
        Seen cfgLog = seenByB.stream().filter(s -> s.event() instanceof StepLogRunEvent e && e.text().contains("cfg log"))
                .findFirst().orElseThrow();
        assertEquals(oneIndex, cfgLog.envelope().get("executionIndex"));
        assertNull(((StepLogRunEvent) cfgLog.event()).step(), "config output is not a step's");
        List<Seen> nested = seenByB.stream().filter(s -> s.event() instanceof ScenarioRunEvent e
                && e.source().getFeatureRuntime().getCallDepth() == 1).toList();
        assertEquals(2, nested.size());
        for (Seen s : nested) {
            assertEquals(oneIndex, s.envelope().get("parentExecutionIndex"), "the config-time call's parent");
        }

        // teardown output belongs to the scenario, not to the step that already exited
        Seen teardown = seenByB.stream().filter(s -> s.event() instanceof StepLogRunEvent e && e.text().contains("teardown log"))
                .findFirst().orElseThrow();
        assertNull(((StepLogRunEvent) teardown.event()).step(), "afterScenario output is not attributed to an ended step");
        assertEquals(oneIndex, teardown.envelope().get("executionIndex"));
    }

    @Test
    void aListenerThatCancelsAndThenThrowsStillHasItsCancelDeliveredBeforeComplete(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("main.feature"), """
                Feature: throwing
                Scenario: one
                * def a = 1
                """);
        List<RunEvent> seenByB = new CopyOnWriteArrayList<>();
        Suite[] suiteRef = new Suite[1];
        Suite suite = Runner.builder()
                .path(dir.resolve("main.feature").toString())
                .configDir(dir.toString())
                .workingDir(dir)
                .outputDir(dir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .backupOutputDir(false)
                .ideEvents(true)
                .listener(event -> {
                    if (event.getType() == RunEventType.SUITE_EXIT) {
                        suiteRef[0].cancel("from a throwing listener");
                        throw new RuntimeException("listener failed");
                    }
                    return true;
                })
                .listener(event -> {
                    seenByB.add(event);
                    return true;
                })
                .buildSuite();
        suiteRef[0] = suite;
        RuntimeException thrown = assertThrows(RuntimeException.class, suite::run);
        assertEquals("listener failed", thrown.getMessage());
        List<RunEventType> runEvents = seenByB.stream().map(RunEvent::getType)
                .filter(t -> t == RunEventType.RUN_CANCEL || t == RunEventType.RUN_COMPLETE).toList();
        assertEquals(List.of(RunEventType.RUN_CANCEL, RunEventType.RUN_COMPLETE), runEvents);
        long expected = 1;
        for (RunEvent e : seenByB) {
            assertEquals(expected++, e.getSequence(), "sequence of " + e.getType());
        }
    }

    private static Integer executionIndexOf(List<Seen> seen, String scenarioName) {
        for (Seen s : seen) {
            if (s.event() instanceof ScenarioRunEvent e && e.type() == RunEventType.SCENARIO_ENTER
                    && scenarioName.equals(e.source().getScenario().getName())) {
                return (Integer) s.envelope().get("executionIndex");
            }
        }
        return null;
    }

    private static final Pattern MOCK_PORT = Pattern.compile("mockport=(\\d+)");

    /** True once the mock a scenario started (its port printed to the log) no longer accepts a connection. */
    private static boolean mockStopped(List<Seen> seen) {
        for (Seen s : new ArrayList<>(seen)) {
            if (s.event() instanceof StepLogRunEvent e) {
                Matcher m = MOCK_PORT.matcher(e.text());
                if (m.find()) {
                    try (Socket ignored = new Socket("localhost", Integer.parseInt(m.group(1)))) {
                        return false;
                    } catch (Exception e2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
