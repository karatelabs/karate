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
package io.karatelabs.output;

import io.karatelabs.common.Json;
import io.karatelabs.core.Globals;
import io.karatelabs.core.Runner;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonLinesReportListenerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
    }

    @Test
    void testJsonLinesFileCreated() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: JSON Lines Test

            Scenario: Passing scenario
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)  // opt-in to JSON Lines
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        // Verify JSON Lines file was created
        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        assertTrue(Files.exists(jsonlPath), "JSON Lines file should exist");

        String content = Files.readString(jsonlPath);
        String[] lines = content.trim().split("\n");

        // Should have at least SUITE_ENTER, FEATURE_EXIT, SUITE_EXIT
        assertTrue(lines.length >= 3, "Should have at least 3 event lines");
    }

    @Test
    void testJsonLinesSuiteHeader() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Suite Header Test
            Scenario: Test
            * def a = 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .karateEnv("dev")
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        // Parse SUITE_ENTER event (first line)
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) Json.of(lines[0]).value();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) envelope.get("data");

        assertEquals("SUITE_ENTER", envelope.get("type"));
        assertEquals("dev", data.get("env"));
        assertEquals(Globals.KARATE_VERSION, data.get("version"));
        assertTrue(envelope.containsKey("timeStamp"));
        assertTrue(data.containsKey("threads"));
    }

    @Test
    void testJsonLinesFeatureLine() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Feature Line Test

            Scenario: Passing scenario
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        // Find FEATURE_EXIT event
        Map<String, Object> featureData = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("FEATURE_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                featureData = data;
                break;
            }
        }
        assertNotNull(featureData, "Should have FEATURE_EXIT event");

        assertEquals("Feature Line Test", featureData.get("name"));
        assertTrue(featureData.get("relativePath").toString().endsWith("test.feature"));

        // Check status
        assertEquals(true, featureData.get("passed"));
        assertTrue(featureData.containsKey("durationMillis"));

        // Check scenarioResults array
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarioResults = (List<Map<String, Object>>) featureData.get("scenarioResults");
        assertEquals(1, scenarioResults.size());

        Map<String, Object> scenario = scenarioResults.get(0);
        assertEquals("Passing scenario", scenario.get("name"));

        // Check stepResults
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stepResults = (List<Map<String, Object>>) scenario.get("stepResults");
        assertEquals(2, stepResults.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> stepResult = (Map<String, Object>) stepResults.get(0).get("result");
        assertEquals("passed", stepResult.get("status"));
    }

    @Test
    void testJsonLinesSuiteEnd() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Suite End Test

            Scenario: Passing
            * def a = 1

            Scenario: Also passing
            * def b = 2
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        // Find SUITE_EXIT event (last line)
        Map<String, Object> summary = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("SUITE_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> s = (Map<String, Object>) data.get("summary");
                summary = s;
                break;
            }
        }
        assertNotNull(summary, "Should have SUITE_EXIT event with summary");

        assertEquals(1, ((Number) summary.get("featuresPassed")).intValue());
        assertEquals(0, ((Number) summary.get("featuresFailed")).intValue());
        assertEquals(2, ((Number) summary.get("scenariosPassed")).intValue());
        assertEquals(0, ((Number) summary.get("scenariosFailed")).intValue());
        assertTrue(summary.containsKey("durationMillis"));
    }

    @Test
    void testJsonLinesWithFailures() throws Exception {
        Path feature = tempDir.resolve("failing.feature");
        Files.writeString(feature, """
            Feature: Failing Test

            Scenario: Passing
            * def a = 1

            Scenario: Failing
            * def b = 2
            * match b == 999
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        // Find FEATURE_EXIT event
        Map<String, Object> featureData = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("FEATURE_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                featureData = data;
                break;
            }
        }
        assertNotNull(featureData);

        // Check status
        assertEquals(true, featureData.get("failed"));

        // Check scenarioResults - one passed, one failed
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarioResults = (List<Map<String, Object>>) featureData.get("scenarioResults");
        assertEquals(2, scenarioResults.size());

        // Find SUITE_EXIT and check summary
        Map<String, Object> summary = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("SUITE_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> s = (Map<String, Object>) data.get("summary");
                summary = s;
                break;
            }
        }
        assertNotNull(summary);
        assertEquals(0, ((Number) summary.get("featuresPassed")).intValue());
        assertEquals(1, ((Number) summary.get("featuresFailed")).intValue());
        assertEquals(1, ((Number) summary.get("scenariosPassed")).intValue());
        assertEquals(1, ((Number) summary.get("scenariosFailed")).intValue());
    }

    @Test
    void testJsonLinesWithTags() throws Exception {
        Path feature = tempDir.resolve("tagged.feature");
        Files.writeString(feature, """
            Feature: Tagged Test

            @smoke @regression
            Scenario: Tagged scenario
            * def a = 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        // Find FEATURE_EXIT event
        Map<String, Object> featureData = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("FEATURE_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                featureData = data;
                break;
            }
        }
        assertNotNull(featureData);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarioResults = (List<Map<String, Object>>) featureData.get("scenarioResults");
        Map<String, Object> scenario = scenarioResults.get(0);

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) scenario.get("tags");
        assertNotNull(tags);
        assertEquals(2, tags.size());
    }

    @Test
    void testJsonLinesMultipleFeatures() throws Exception {
        Path feature1 = tempDir.resolve("feature1.feature");
        Files.writeString(feature1, """
            Feature: Feature One
            Scenario: Test 1
            * def a = 1
            """);

        Path feature2 = tempDir.resolve("feature2.feature");
        Files.writeString(feature2, """
            Feature: Feature Two
            Scenario: Test 2
            * def b = 2
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(tempDir.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        // Should have at least 4 event lines: SUITE_ENTER, 2x FEATURE_EXIT, SUITE_EXIT
        assertTrue(lines.length >= 4);

        // Verify both FEATURE_EXIT events
        int featureCount = 0;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("FEATURE_EXIT".equals(envelope.get("type"))) {
                featureCount++;
            }
        }
        assertEquals(2, featureCount);

        // Check SUITE_EXIT summary totals
        Map<String, Object> summary = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("SUITE_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> s = (Map<String, Object>) data.get("summary");
                summary = s;
                break;
            }
        }
        assertNotNull(summary);
        assertEquals(2, ((Number) summary.get("featuresPassed")).intValue());
        assertEquals(2, ((Number) summary.get("scenariosPassed")).intValue());
    }

    @Test
    void testJsonLinesScenarioExitCarriesError() throws Exception {
        Path feature = tempDir.resolve("failing.feature");
        Files.writeString(feature, """
            Feature: Scenario Error Test

            Scenario: will fail on a match
            * def actual = 1
            * match actual == 999
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        Map<String, Object> scenarioExit = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("SCENARIO_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                scenarioExit = data;
                break;
            }
        }
        assertNotNull(scenarioExit, "should have SCENARIO_EXIT event");
        assertEquals(false, scenarioExit.get("passed"));
        assertEquals(false, scenarioExit.get("skipped"));
        String error = (String) scenarioExit.get("error");
        assertNotNull(error, "SCENARIO_EXIT must carry an error message on failure");
        assertTrue(error.contains("match") || error.contains("999") || error.contains("actual"),
                "error should reference the failed step: " + error);
    }

    @Test
    void testJsonLinesScenarioExitSkipped() throws Exception {
        Path feature = tempDir.resolve("skipped.feature");
        Files.writeString(feature, """
            Feature: Scenario Skip Test

            Scenario: aborts mid-way
            * def a = 1
            * karate.abort()
            * def b = 2
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        Map<String, Object> scenarioExit = null;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            if ("SCENARIO_EXIT".equals(envelope.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                scenarioExit = data;
                break;
            }
        }
        assertNotNull(scenarioExit, "should have SCENARIO_EXIT event");
        assertEquals(true, scenarioExit.get("skipped"), "aborted scenario must report skipped=true");
        assertNull(scenarioExit.get("error"), "skipped scenario must not carry an error");
    }

    @Test
    void testJsonLinesCalledFeatureCallDepth() throws Exception {
        Path called = tempDir.resolve("called.feature");
        Files.writeString(called, """
            @ignore
            Feature: Called Feature

            Scenario: inner
            * def result = 'called-result'
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller Feature

            Scenario: outer
            * def response = call read('called.feature')
            * match response.result == 'called-result'
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(caller.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        int topFeatureEnter = 0, calledFeatureEnter = 0;
        int topFeatureExit = 0, calledFeatureExit = 0;
        int topScenarioEnter = 0, calledScenarioEnter = 0;
        int topScenarioExit = 0, calledScenarioExit = 0;
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            String type = (String) envelope.get("type");
            if (data == null || type == null || !type.startsWith("FEATURE_") && !type.startsWith("SCENARIO_")) {
                continue;
            }
            assertTrue(data.containsKey("callDepth"),
                    "callDepth field must be present on every feature/scenario event: " + type);
            int depth = ((Number) data.get("callDepth")).intValue();
            switch (type) {
                case "FEATURE_ENTER" -> {
                    if (depth == 0) topFeatureEnter++;
                    else if (depth == 1) calledFeatureEnter++;
                }
                case "FEATURE_EXIT" -> {
                    if (depth == 0) topFeatureExit++;
                    else if (depth == 1) calledFeatureExit++;
                }
                case "SCENARIO_ENTER" -> {
                    if (depth == 0) topScenarioEnter++;
                    else if (depth == 1) calledScenarioEnter++;
                }
                case "SCENARIO_EXIT" -> {
                    if (depth == 0) topScenarioExit++;
                    else if (depth == 1) calledScenarioExit++;
                }
            }
        }

        assertEquals(1, topFeatureEnter, "exactly one top-level FEATURE_ENTER");
        assertEquals(1, topFeatureExit, "exactly one top-level FEATURE_EXIT");
        assertEquals(1, topScenarioEnter, "exactly one top-level SCENARIO_ENTER");
        assertEquals(1, topScenarioExit, "exactly one top-level SCENARIO_EXIT");
        assertEquals(1, calledFeatureEnter, "called feature must report callDepth=1 on FEATURE_ENTER");
        assertEquals(1, calledFeatureExit, "called feature must report callDepth=1 on FEATURE_EXIT");
        assertEquals(1, calledScenarioEnter, "called scenario must report callDepth=1 on SCENARIO_ENTER");
        assertEquals(1, calledScenarioExit, "called scenario must report callDepth=1 on SCENARIO_EXIT");
    }

    @Test
    void testJsonLinesNestedCallDepth() throws Exception {
        Path inner = tempDir.resolve("inner.feature");
        Files.writeString(inner, """
            @ignore
            Feature: Inner

            Scenario: deepest
            * def depth = 'inner'
            """);

        Path middle = tempDir.resolve("middle.feature");
        Files.writeString(middle, """
            @ignore
            Feature: Middle

            Scenario: middle
            * call read('inner.feature')
            """);

        Path outer = tempDir.resolve("outer.feature");
        Files.writeString(outer, """
            Feature: Outer

            Scenario: top
            * call read('middle.feature')
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(outer.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonlPath = reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl");
        String[] lines = Files.readString(jsonlPath).trim().split("\n");

        int[] featureEnterByDepth = new int[3];
        int[] scenarioEnterByDepth = new int[3];
        for (String line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            String type = (String) envelope.get("type");
            if (data == null || type == null) continue;
            if (!"FEATURE_ENTER".equals(type) && !"SCENARIO_ENTER".equals(type)) continue;
            int depth = ((Number) data.get("callDepth")).intValue();
            assertTrue(depth >= 0 && depth < featureEnterByDepth.length, "unexpected depth: " + depth);
            if ("FEATURE_ENTER".equals(type)) featureEnterByDepth[depth]++;
            else scenarioEnterByDepth[depth]++;
        }

        assertEquals(1, featureEnterByDepth[0], "one outer feature");
        assertEquals(1, featureEnterByDepth[1], "one middle feature");
        assertEquals(1, featureEnterByDepth[2], "one inner feature");
        assertEquals(1, scenarioEnterByDepth[0], "one outer scenario");
        assertEquals(1, scenarioEnterByDepth[1], "one middle scenario");
        assertEquals(1, scenarioEnterByDepth[2], "one inner scenario");
    }

    @Test
    void testHtmlReportGeneratedWithJsonLines() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: HTML With JSON Lines Test
            Scenario: Test
            * def a = 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)  // opt-in to JSON Lines alongside HTML
                .outputConsoleSummary(false)
                .parallel(1);

        // Verify both JSON Lines and HTML reports exist when both are enabled
        assertTrue(Files.exists(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));
        assertTrue(Files.exists(reportDir.resolve("karate-summary.html")));
        assertTrue(Files.exists(reportDir.resolve("index.html")));
    }

}
