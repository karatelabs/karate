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
import io.karatelabs.http.ServerTestHarness;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTML report generation.
 * <p>
 * <b>For Report Development:</b>
 * <pre>
 * mvn test -Dtest=HtmlReportWriterTest#testHtmlReportGeneration -q
 * open target/karate-report-dev/karate-summary.html
 * </pre>
 * <p>
 * See also: /docs/HTML_REPORTS.md for the full development guide.
 */
class HtmlReportWriterTest {

    private static final Path OUTPUT_DIR = Path.of("target/karate-report-dev");

    private static ServerTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        harness = new ServerTestHarness("classpath:io/karatelabs/report");
        harness.start();
        // Set up mock API endpoints for http-demo.feature
        harness.setHandler(ctx -> {
            String path = ctx.request().getPath();
            var response = ctx.response();
            response.setHeader("Content-Type", "application/json");

            if (path.equals("/api/users") && ctx.request().getMethod().equals("GET")) {
                response.setBody(Map.of(
                    "users", List.of(
                        Map.of("id", 1, "name", "Alice", "role", "admin"),
                        Map.of("id", 2, "name", "Bob", "role", "user")
                    ),
                    "total", 2
                ));
            } else if (path.equals("/api/users") && ctx.request().getMethod().equals("POST")) {
                response.setStatus(201);
                response.setBody(Map.of("id", 3, "name", "Charlie", "created", true));
            } else if (path.startsWith("/api/users/")) {
                String id = path.substring("/api/users/".length());
                response.setBody(Map.of("id", Integer.parseInt(id), "name", "User " + id, "active", true));
            } else if (path.equals("/api/status")) {
                response.setBody(Map.of("status", "healthy", "version", "2.0.0"));
            } else {
                response.setStatus(404);
                response.setBody(Map.of("error", "Not found", "path", path));
            }
            return response;
        });
    }

    @AfterAll
    static void afterAll() {
        if (harness != null) {
            harness.stop();
        }
    }


    /**
     * Main dev test - run this to regenerate HTML reports after template changes.
     * <p>
     * Reports written to: target/karate-report-dev/
     * <p>
     * Quick run: mvn test -Dtest=HtmlReportWriterTest#testHtmlReportGeneration -q
     */
    @Test
    void testHtmlReportGeneration() throws Exception {
        Console.setColorsEnabled(true);
        // Run features from test-classes directory with parallel for timeline
        String testResourcesDir = "target/test-classes/io/karatelabs/report";
        SuiteResult result = Runner.path(testResourcesDir)
                .configDir(testResourcesDir)  // Load karate-config.js for baseUrl
                .systemProperty("karate.server.port", String.valueOf(harness.getPort()))
                .outputDir(OUTPUT_DIR)
                .outputJsonLines(true)
                .parallel(3);  // parallel for timeline testing

        // Verify the run completed (feature count may vary with @ignore features)
        assertTrue(result.getFeatureCount() >= 4, "Should have at least 4 features (including http-demo)");
        assertTrue(result.getScenarioPassedCount() >= 17, "Should have many passing scenarios including HTTP tests");
        assertTrue(result.getScenarioFailedCount() >= 4, "Should have @wip failing scenarios (including match failure demos)");
        assertTrue(result.getScenarioSkippedCount() >= 2,
                "Should have at least 2 skipped scenarios from skipped-scenarios.feature");

        // Verify HTML reports were generated
        assertTrue(Files.exists(OUTPUT_DIR.resolve("karate-summary.html")));
        assertTrue(Files.exists(OUTPUT_DIR.resolve("karate-timeline.html")));
        assertTrue(Files.exists(OUTPUT_DIR.resolve("index.html")));
        assertTrue(Files.exists(OUTPUT_DIR.resolve(HtmlReportListener.SUBFOLDER)));
        assertTrue(Files.exists(OUTPUT_DIR.resolve("res/bootstrap.min.css")));
        assertTrue(Files.exists(OUTPUT_DIR.resolve("res/favicon.ico")));

        // Verify JSON Lines file was created
        assertTrue(Files.exists(OUTPUT_DIR.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));

        // Verify summary HTML has skipped keys and the synthetic @skipped tag
        String summaryHtml = Files.readString(OUTPUT_DIR.resolve("karate-summary.html"));
        assertTrue(summaryHtml.contains("\"scenario_skipped\""),
                "summary JSON should include scenario_skipped key");
        assertTrue(summaryHtml.contains("@skipped"),
                "summary JSON should include synthetic @skipped tag from skipped scenarios");
    }

    @Test
    void testSkippedTagAndCountOnAbort(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("abort.feature");
        Files.writeString(feature, """
                Feature: Abort Skipped Test

                Scenario: Aborted scenario
                * karate.abort()
                * def x = 1
                * match x == 1

                Scenario: Normal scenario
                * def y = 2
                * match y == 2
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getScenarioSkippedCount(), "Aborted scenario should count as skipped");
        assertEquals(2, result.getScenarioPassedCount(), "Passed count unchanged (skipped is additive subset)");
        assertEquals(0, result.getScenarioFailedCount());

        // Inlined JSON in HTML should contain the synthetic @skipped tag
        String html = Files.readString(reportDir.resolve("karate-summary.html"));
        assertTrue(html.contains("@skipped"), "synthetic @skipped tag should appear in summary JSON");
        assertTrue(html.contains("\"scenario_skipped\": 1"),
                "scenario_skipped count should appear in summary JSON");
    }

    @Test
    void testNoSkippedKeyWhenNoSkippedScenarios(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("clean.feature");
        Files.writeString(feature, """
                Feature: No Skipped Test

                Scenario: First
                * def a = 1
                * match a == 1

                Scenario: Second
                * def b = 2
                * match b == 2
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(0, result.getScenarioSkippedCount());

        String html = Files.readString(reportDir.resolve("karate-summary.html"));
        assertTrue(html.contains("\"scenario_skipped\": 0"),
                "scenario_skipped: 0 should be present (hero card x-show hides it)");
        assertFalse(html.contains("@skipped"),
                "synthetic @skipped tag should not appear when no scenarios are skipped");
    }

    @Test
    void testHtmlReportWithEnv() {
        Path outputDir = Path.of("target/karate-report-dev-env");
        String testResourcesDir = "target/test-classes/io/karatelabs/report";

        SuiteResult result = Runner.path(testResourcesDir)
                .karateEnv("staging")
                .outputDir(outputDir)
                .outputJsonLines(true)  // opt-in for JSON Lines
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(Files.exists(outputDir.resolve("karate-summary.html")));
        assertTrue(Files.exists(outputDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));
    }

    @Test
    void testHtmlContainsInlinedJson(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Inlined JSON Test

            Scenario: Test scenario
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);

        // Verify HTML contains the JSON data placeholder replacement
        String summaryHtml = Files.readString(reportDir.resolve("karate-summary.html"));
        assertTrue(summaryHtml.contains("<script id=\"karate-data\" type=\"application/json\">"));
        assertTrue(summaryHtml.contains("\"feature_count\""));
        assertTrue(summaryHtml.contains("x-data=\"KarateReport.summaryData()\""));

        // Verify feature page also has inlined JSON
        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        assertTrue(Files.exists(featuresDir));
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        assertTrue(featureFiles.length > 0);

        String featureHtml = Files.readString(featuresDir.resolve(featureFiles[0]));
        assertTrue(featureHtml.contains("<script id=\"karate-data\" type=\"application/json\">"));
        assertTrue(featureHtml.contains("x-data=\"KarateReport.featureData()\""));
    }

    @Test
    void testJsonLinesFormat(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: JSON Lines Format Test

            Scenario: First scenario
            * def a = 1

            Scenario: Second scenario
            * def b = 2
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)  // opt-in to JSON Lines
                .outputConsoleSummary(false)
                .parallel(1);

        // Verify JSON Lines format (new event envelope format)
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        String[] lines = jsonl.trim().split("\n");

        assertTrue(lines.length >= 3, "Should have at least 3 event lines");

        // First line should be SUITE_ENTER
        assertTrue(lines[0].contains("\"type\":\"SUITE_ENTER\""));
        assertTrue(lines[0].contains("\"version\":\"" + Globals.KARATE_VERSION + "\""));

        // Should have FEATURE_EXIT events
        boolean hasFeatureExit = false;
        for (String line : lines) {
            if (line.contains("\"type\":\"FEATURE_EXIT\"")) {
                hasFeatureExit = true;
                assertTrue(line.contains("\"scenarioResults\""));
                break;
            }
        }
        assertTrue(hasFeatureExit, "Should have FEATURE_EXIT event");

        // Last line should be SUITE_EXIT
        assertTrue(lines[lines.length - 1].contains("\"type\":\"SUITE_EXIT\""));
        assertTrue(lines[lines.length - 1].contains("\"summary\""));
    }

    @Test
    void testReportAggregation(@TempDir Path tempDir) throws Exception {
        // Create two feature files and run them separately
        Path feature1 = tempDir.resolve("feature1.feature");
        Files.writeString(feature1, """
            Feature: Aggregation Test 1
            Scenario: Test 1
            * def a = 1
            """);

        Path feature2 = tempDir.resolve("feature2.feature");
        Files.writeString(feature2, """
            Feature: Aggregation Test 2
            Scenario: Test 2
            * def b = 2
            """);

        Path run1Dir = tempDir.resolve("run1");
        Path run2Dir = tempDir.resolve("run2");
        Path combinedDir = tempDir.resolve("combined");

        // Run features separately with JSON Lines enabled for aggregation
        Runner.path(feature1.toString())
                .workingDir(tempDir)
                .outputDir(run1Dir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Runner.path(feature2.toString())
                .workingDir(tempDir)
                .outputDir(run2Dir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        // Verify both JSON Lines files exist
        assertTrue(Files.exists(run1Dir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));
        assertTrue(Files.exists(run2Dir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));

        // Aggregate reports
        HtmlReport.aggregate()
                .json(run1Dir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"))
                .json(run2Dir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"))
                .outputDir(combinedDir)
                .generate();

        // Verify combined report
        assertTrue(Files.exists(combinedDir.resolve("karate-summary.html")));
        assertTrue(Files.exists(combinedDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));

        // Verify the combined JSON Lines has both features
        String combinedJsonl = Files.readString(combinedDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(combinedJsonl.contains("Aggregation Test 1"));
        assertTrue(combinedJsonl.contains("Aggregation Test 2"));

        // Count FEATURE_EXIT events
        int featureCount = 0;
        for (String line : combinedJsonl.split("\n")) {
            if (line.contains("\"type\":\"FEATURE_EXIT\"")) {
                featureCount++;
            }
        }
        assertEquals(2, featureCount, "Combined report should have 2 FEATURE_EXIT events");
    }

    // ========== Scenario Name Substitution Tests ==========

    @Test
    void testOutlinePlaceholderSubstitutionInScenarioName(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("outline-name.feature");
        Files.writeString(feature, """
            Feature: Outline Name Substitution

            Scenario Outline: Testing <name> with value <value>
            * def result = '<name>'
            * match result == name

            Examples:
            | name  | value |
            | foo   | 1     |
            | bar   | 2     |
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed(), "All scenarios should pass");
        assertEquals(2, result.getScenarioPassedCount());

        // Verify JSON Lines contains the substituted scenario names
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(jsonl.contains("Testing foo with value 1"),
            "Should contain substituted scenario name for first example");
        assertTrue(jsonl.contains("Testing bar with value 2"),
            "Should contain substituted scenario name for second example");
    }

    @Test
    void testBacktickScenarioNameInterpolation(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("backtick-name.feature");
        Files.writeString(feature, """
            Feature: Backtick Name Interpolation

            Scenario: `result is ${1+1}`
            * def a = 2
            * match a == 2
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getScenarioPassedCount());

        // Verify EXIT events contain evaluated scenario name
        // Note: SCENARIO_ENTER fires before steps run, so it has the unevaluated name - that's expected
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));

        // Check SCENARIO_EXIT and FEATURE_EXIT events have evaluated name
        for (String line : jsonl.split("\n")) {
            if (line.contains("SCENARIO_EXIT") || line.contains("FEATURE_EXIT")) {
                assertTrue(line.contains("result is 2"),
                    "EXIT events should contain evaluated name 'result is 2': " + line);
                assertFalse(line.contains("result is ${1+1}"),
                    "EXIT events should not contain unevaluated template: " + line);
            }
        }
    }

    @Test
    void testBacktickScenarioNameWithVariable(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("backtick-var.feature");
        Files.writeString(feature, """
            Feature: Backtick With Variable

            Scenario: `status is ${status}`
            * def status = 'active'
            * match status == 'active'
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        // Verify JSON Lines contains the evaluated scenario name with variable value
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(jsonl.contains("status is active"),
            "Should contain evaluated scenario name with variable value");
    }

    @Test
    void testBacktickScenarioNameInOutline(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("backtick-outline.feature");
        Files.writeString(feature, """
            Feature: Backtick In Outline

            Scenario Outline: `testing ${name} = ${value * 2}`
            * def result = value * 2
            * match result == value * 2

            Examples:
            | name  | value |
            | foo   | 5     |
            | bar   | 10    |
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(2, result.getScenarioPassedCount());

        // Verify JSON Lines contains the evaluated scenario names with outline variables
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(jsonl.contains("testing foo = 10"),
            "Should contain evaluated name for first example (5 * 2 = 10)");
        assertTrue(jsonl.contains("testing bar = 20"),
            "Should contain evaluated name for second example (10 * 2 = 20)");
    }

    @Test
    void testOutlineDollarBracePlaceholderInScenarioName(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("dollar-brace-outline.feature");
        Files.writeString(feature, """
            Feature: Dollar Brace Placeholder

            Scenario Outline: using title: ${title}
            * def result = title
            * match result == title

            Examples:
            | title |
            | One   |
            | Two   |
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed(), "All scenarios should pass");
        assertEquals(2, result.getScenarioPassedCount());

        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(jsonl.contains("using title: One"),
            "Should contain substituted scenario name for first example");
        assertTrue(jsonl.contains("using title: Two"),
            "Should contain substituted scenario name for second example");
        assertFalse(jsonl.contains("${title}"),
            "Should not contain unresolved placeholder");
    }

    @Test
    void testScenarioExitHasPositiveDuration(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("duration-test.feature");
        Files.writeString(feature, """
            Feature: Duration Test

            Scenario: check duration
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        for (String line : jsonl.split("\n")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            String type = (String) envelope.get("type");
            if ("SCENARIO_EXIT".equals(type) || "FEATURE_EXIT".equals(type)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                if (data.containsKey("durationMillis")) {
                    long duration = ((Number) data.get("durationMillis")).longValue();
                    assertTrue(duration >= 0,
                        type + " durationMillis should be non-negative, was: " + duration);
                }
            }
        }
    }

    @Test
    void testBacktickEvalFailureWarningInReport(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("backtick-fail.feature");
        Files.writeString(feature, """
            Feature: Backtick Eval Failure

            Scenario: `result is ${undefinedVariable}`
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        // Scenario should still pass (eval failure doesn't fail the test)
        assertTrue(result.isPassed());

        // Original name should be preserved (with backticks) in the report
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(jsonl.contains("`result is ${undefinedVariable}`"),
            "Original name should be preserved when eval fails");
        // Warning should appear in the step log
        assertTrue(jsonl.contains("Failed to evaluate scenario name"),
            "Warning should appear in report log");
    }

}
