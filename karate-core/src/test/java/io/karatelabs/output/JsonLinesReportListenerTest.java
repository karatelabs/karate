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
