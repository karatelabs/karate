package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every scenario execution carries one run-unique {@code executionIndex} — the same value on its
 * SCENARIO_ENTER, its SCENARIO_EXIT and its FEATURE_EXIT result entry — so a stream consumer can join
 * the three without state outside the stream. A result entry core emits with no ENTER (an error during
 * scenario iteration) carries one too.
 */
class ExecutionIndexTest {

    @Test
    void everyExecutionCarriesOneRunUniqueIndexAcrossItsEvents(@TempDir Path dir) throws Exception {
        Feature a = Feature.read(Resource.text("""
                Feature: a
                Scenario: one
                * def x = 1
                Scenario Outline: rows
                * def y = <v>
                Examples:
                | v |
                | 1 |
                | 2 |
                """));
        Feature b = Feature.read(Resource.text("""
                Feature: b
                Scenario Outline: dyn
                * def z = 1
                Examples:
                | read('missing-rows.json') |
                """));
        List<Map<String, Object>> enters = new ArrayList<>();
        List<Map<String, Object>> exits = new ArrayList<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        RunListener listener = event -> {
            if (event instanceof ScenarioRunEvent sre) {
                (event.getType() == RunEventType.SCENARIO_ENTER ? enters : exits).add(sre.toJson());
            }
            if (event instanceof FeatureRunEvent fre && event.getType() == RunEventType.FEATURE_EXIT) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) fre.toJson().get("scenarioResults");
                entries.addAll(results);
            }
            return true;
        };
        Runner.builder()
                .features(a, b)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .outputDir(dir)
                .listener(listener)
                .parallel(1);

        assertEquals(3, enters.size(), enters::toString);
        assertEquals(3, exits.size());
        assertEquals(4, entries.size(), "the three that ran plus the iteration error: " + entries);
        Set<Integer> seen = new HashSet<>();
        for (Map<String, Object> entry : entries) {
            Object index = entry.get("executionIndex");
            assertInstanceOf(Integer.class, index, "every result entry carries one: " + entry);
            assertTrue(seen.add((Integer) index), "run-unique: " + entries);
        }
        for (int i = 0; i < enters.size(); i++) {
            Object index = enters.get(i).get("executionIndex");
            assertNotNull(index, enters.get(i)::toString);
            assertEquals(index, exits.get(i).get("executionIndex"), "ENTER and EXIT agree");
            assertTrue(seen.contains(index), "and the result entry carries the same one");
        }
        Map<String, Object> errored = entries.stream().filter(e -> Boolean.TRUE.equals(e.get("failed")))
                .findFirst().orElseThrow();
        assertFalse(enters.stream().anyMatch(e -> errored.get("executionIndex").equals(e.get("executionIndex"))),
                "the iteration error never entered, yet it is indexed");
    }

    @Test
    void aFeatureThatFailsBeforeItsScenariosRunIsIndexedFromTheSuiteCounter(@TempDir Path dir) throws Exception {
        Feature a = Feature.read(Resource.text("Feature: a\nScenario: one\n* def x = 1\n"));
        Feature b = Feature.read(Resource.text("Feature: b\nScenario: two\n* def y = 1\n"));
        // a FEATURE_ENTER listener failure escapes FeatureRuntime.call — the Suite's synthetic result path
        List<Map<String, Object>> exits = new ArrayList<>();
        RunListener listener = event -> {
            if (event.getType() == RunEventType.FEATURE_ENTER && event instanceof FeatureRunEvent fre
                    && "b".equals(fre.source().getFeature().getName())) {
                throw new IllegalStateException("enter boom");
            }
            if (event.getType() == RunEventType.FEATURE_EXIT && event instanceof FeatureRunEvent fre) {
                exits.add(fre.toJson());
            }
            return true;
        };
        SuiteResult result = Runner.builder()
                .features(a, b)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .outputJsonLines(true)
                .backupOutputDir(false)
                .outputDir(dir)
                .listener(listener)
                .parallel(1);
        Set<Integer> seen = new HashSet<>();
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                assertTrue(sr.getExecutionIndex() > 0, fr.getFeature().getName() + " carries an index");
                assertTrue(seen.add(sr.getExecutionIndex()), "run-unique across the synthetic entry");
            }
        }
        assertEquals(2, seen.size());
        // the synthetic result reaches the stream exactly once, indexed
        List<Map<String, Object>> synthetic = exits.stream().filter(e -> String.valueOf(e).contains("enter boom")).toList();
        assertEquals(1, synthetic.size(), "one FEATURE_EXIT for the failed feature: " + exits);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) synthetic.get(0).get("scenarioResults");
        assertTrue(seen.contains(entries.get(0).get("executionIndex")), entries::toString);
        long written = Files.readAllLines(dir.resolve("karate-json").resolve("karate-events.jsonl")).stream()
                .filter(line -> line.contains("\"FEATURE_EXIT\"") && line.contains("enter boom")).count();
        assertEquals(1, written, "written once to the JSONL");
    }

    @Test
    void aFeatureExitListenerFailureLeavesTheFeaturesResultInTheStreamExactlyOnce(@TempDir Path dir) throws Exception {
        Feature b = Feature.read(Resource.text("Feature: b\nScenario: two\n* def y = 1\n"));
        RunListener listener = event -> {
            if (event.getType() == RunEventType.FEATURE_EXIT) {
                throw new IllegalStateException("exit boom");   // a global listener, ahead of the JSONL writer
            }
            return true;
        };
        SuiteResult result = Runner.builder()
                .features(b)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .outputJsonLines(true)
                .backupOutputDir(false)
                .outputDir(dir)
                .listener(listener)
                .parallel(1);
        List<String> exits = Files.readAllLines(dir.resolve("karate-json").resolve("karate-events.jsonl")).stream()
                .filter(line -> line.contains("\"FEATURE_EXIT\"")).toList();
        assertEquals(1, exits.size(), "the feature's result reached the stream exactly once: " + exits);
        assertTrue(exits.get(0).contains("\"name\":\"two\"") && !exits.get(0).contains("exit boom"),
                "the real result, not a synthetic failure: " + exits.get(0));
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);
        assertFalse(sr.isFailed(), "the feature passed; the listener failed — logged, not a failure of the feature");
        assertTrue(sr.getExecutionIndex() > 0);
    }

    @Test
    void aScenarioQueuedPastASuiteAbortIsIndexed(@TempDir Path dir) {
        Feature f = Feature.read(Resource.text("""
                Feature: f
                Scenario: one
                * def x = 1
                Scenario: two
                * def y = 1
                Scenario: three
                * def z = 1
                """));
        java.util.concurrent.atomic.AtomicReference<Suite> suite = new java.util.concurrent.atomic.AtomicReference<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        Set<Object> entered = new HashSet<>();
        RunListener listener = event -> {
            if (event.getType() == RunEventType.SCENARIO_ENTER && event instanceof ScenarioRunEvent sre) {
                entered.add(sre.toJson().get("executionIndex"));
                suite.get().abort();   // the first to enter aborts the suite; a queued scenario returns aborted
            }
            if (event instanceof FeatureRunEvent fre && event.getType() == RunEventType.FEATURE_EXIT) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) fre.toJson().get("scenarioResults");
                entries.addAll(results);
            }
            return true;
        };
        Runner.builder()
                .features(f)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .outputDir(dir)
                .onSuite(suite::set)
                .listener(listener)
                .parallel(2);
        assertEquals(3, entries.size(), entries::toString);
        Set<Object> seen = new HashSet<>();
        for (Map<String, Object> entry : entries) {
            assertInstanceOf(Integer.class, entry.get("executionIndex"), "every entry, the aborted one included: " + entry);
            assertTrue(seen.add(entry.get("executionIndex")));
        }
        assertTrue(entered.size() < 3, "at least one scenario was queued past the abort: " + entered);
    }
}
