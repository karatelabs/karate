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
    void aFeatureThatFailsBeforeItsScenariosRunIsIndexedFromTheSuiteCounter(@TempDir Path dir) {
        Feature a = Feature.read(Resource.text("Feature: a\nScenario: one\n* def x = 1\n"));
        Feature b = Feature.read(Resource.text("Feature: b\nScenario: two\n* def y = 1\n"));
        // a FEATURE_ENTER listener failure escapes FeatureRuntime.call — the Suite's synthetic result path
        RunListener listener = event -> {
            if (event.getType() == RunEventType.FEATURE_ENTER && event instanceof FeatureRunEvent fre
                    && "b".equals(fre.source().getFeature().getName())) {
                throw new IllegalStateException("enter boom");
            }
            return true;
        };
        SuiteResult result = Runner.builder()
                .features(a, b)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
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
    }
}
