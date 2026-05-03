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
        assertTrue(result.getFeatureCount() >= 5, "Should have at least 5 features (including http-demo and hooks-demo)");
        assertTrue(result.getScenarioPassedCount() >= 19, "Should have many passing scenarios including HTTP tests");
        assertTrue(result.getScenarioFailedCount() >= 6, "Should have @wip failing scenarios (including match failure + hook demos)");
        assertTrue(result.getScenarioSkippedCount() >= 2,
                "Should have at least 2 skipped scenarios from skipped-scenarios.feature");

        // Every failed scenario must expose location + source line for the summary -
        // except scenarios whose only failure is a lifecycle hook (hooks are JS, not Gherkin,
        // so they have no source-line location to point at).
        result.getFeatureResults().forEach(fr ->
                fr.getScenarioResults().stream().filter(sr -> sr.isFailed()).forEach(sr -> {
                    var firstFailed = sr.getStepResults().stream()
                            .filter(io.karatelabs.core.StepResult::isFailed)
                            .findFirst()
                            .orElse(null);
                    if (firstFailed != null && firstFailed.isHook()) {
                        return;
                    }
                    assertNotNull(sr.getFailedStepLocation(),
                            "failed scenario '" + sr.getScenario().getName() + "' missing step location");
                    String stepText = sr.getFailedStepText();
                    assertNotNull(stepText,
                            "failed scenario '" + sr.getScenario().getName() + "' missing step source text");
                    assertTrue(stepText.startsWith("*") || stepText.matches("(?i)^(given|when|then|and|but) .*"),
                            "step text should carry its Gherkin prefix: " + stepText);
                }));

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
    void testTagExcludedFeaturesAreNotReported(@TempDir Path tempDir) throws Exception {
        // Reproduces the karate-todo bug: a feature whose feature-level tag (e.g. @external)
        // causes every scenario to be excluded by a suite-level negative tag selector must not
        // surface in the HTML summary as a 0-scenario "passed" row, must not produce an empty
        // per-feature HTML file, and must not inflate the feature count.
        Path external = tempDir.resolve("external.feature");
        Files.writeString(external, """
                @external
                Feature: External API

                Scenario: fetch
                * def a = 1
                """);

        Path todo = tempDir.resolve("todo.feature");
        Files.writeString(todo, """
                @todo
                Feature: Work In Progress

                Scenario: later
                * def a = 1
                """);

        Path real = tempDir.resolve("real.feature");
        Files.writeString(real, """
                Feature: Real

                Scenario: runs
                * def a = 1
                * match a == 1
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(
                        external.toString(), todo.toString(), real.toString())
                .tags("~@external", "~@todo")
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount(), "excluded features must not count as features");
        assertEquals(1, result.getScenarioPassedCount());

        // Only the "real" feature should have an HTML page written to feature-html/
        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        assertEquals(1, featureFiles.length,
                "should not emit per-feature HTML for tag-excluded features, got: "
                        + String.join(",", featureFiles));

        // The summary's inlined JSON must not reference the excluded feature names
        String summaryHtml = Files.readString(reportDir.resolve("karate-summary.html"));
        assertFalse(summaryHtml.contains("External API"),
                "summary must not list tag-excluded feature");
        assertFalse(summaryHtml.contains("Work In Progress"),
                "summary must not list tag-excluded feature");
        assertTrue(summaryHtml.contains("Real"));
    }

    @Test
    void testScenarioLevelTagStillRunsFeature(@TempDir Path tempDir) throws Exception {
        // Regression guard: when the excluded tag is on a scenario (not the feature),
        // at least one other scenario in the feature still matches, so the feature must run.
        Path feature = tempDir.resolve("mixed.feature");
        Files.writeString(feature, """
                Feature: Mixed

                @external
                Scenario: skipped
                * def a = 1

                Scenario: runs
                * def b = 2
                * match b == 2
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .tags("~@external")
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount());
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testLineFilterBypassesFeatureTagExclusion(@TempDir Path tempDir) throws Exception {
        // Regression guard: line-number targeting must bypass tag filters even when the
        // feature-level tag would otherwise cause the whole feature to be pre-filtered.
        Path feature = tempDir.resolve("targeted.feature");
        Files.writeString(feature, """
                @external
                Feature: Targeted

                Scenario: pick me
                * def a = 1
                * match a == 1
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString() + ":4")
                .tags("~@external")
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount());
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testDryRunSkipsStepsButGeneratesReport(@TempDir Path tempDir) throws Exception {
        // Dry-run should report every non-@setup step as passed without executing it, while
        // @setup scenarios run fully so dynamic outlines still resolve. The "dry-" prefix in
        // outline row names proves karate.dryRun was observable inside the @setup scenario.
        Path feature = tempDir.resolve("dryrun.feature");
        Files.writeString(feature, """
                Feature: Dry Run Example

                  @setup
                  Scenario:
                    * def tag = karate.dryRun ? 'dry' : 'live'
                    * def rows = ([{ name: tag + '-alpha' }, { name: tag + '-beta' }])

                  Scenario: would fail for real
                    * url 'http://127.0.0.1:1'
                    * method get
                    * match response == { never: 'matches' }

                  Scenario Outline: outline <name>
                    * match 1 == 2

                    Examples:
                      | karate.setup().rows |
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .dryRun(true)
                .parallel(1);

        assertTrue(result.isPassed(), "dry-run should mark all scenarios as passed");
        assertEquals(0, result.getScenarioFailedCount());
        // 1 regular + 2 outline rows resolved from @setup data = 3 passing non-setup scenarios
        assertEquals(3, result.getScenarioPassedCount(),
                "setup-driven outline should still expand its rows under dry-run");

        // Reports generated as usual
        assertTrue(Files.exists(reportDir.resolve("karate-summary.html")));
        assertTrue(Files.exists(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl")));

        // @setup scenario ran fully (outline rows exist) AND karate.dryRun was true inside it
        // (rows carry the "dry-" prefix). If @setup had been skipped, no outline rows would appear.
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertTrue(jsonl.contains("outline dry-alpha"),
                "outline row should resolve from @setup and carry the dry-run marker");
        assertTrue(jsonl.contains("outline dry-beta"));
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

    // ========== beforeScenario / afterScenario Hook Reporting ==========

    @Test
    void testHookStepsRenderInReport(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("hooks-basic.feature");
        Files.writeString(feature, """
                Feature: Hook Steps Basic

                Scenario: body runs between hooks
                * configure afterScenario = function(){ karate.log('after-done') }
                * def x = 1
                * match x == 1
                """);
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
                function fn() {
                  karate.configure('beforeScenario', function(){ karate.log('before-done') });
                  return {};
                }
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getScenarioPassedCount(), "hook steps must not inflate scenario counts");
        assertEquals(0, result.getScenarioFailedCount());

        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        assertEquals(1, featureFiles.length);
        String featureHtml = Files.readString(featuresDir.resolve(featureFiles[0]));

        assertTrue(featureHtml.contains("\"hook\": \"beforeScenario\""),
                "feature HTML should expose beforeScenario hook marker");
        assertTrue(featureHtml.contains("\"hook\": \"afterScenario\""),
                "feature HTML should expose afterScenario hook marker");
    }

    @Test
    void testHookCallRendersNestedFeature(@TempDir Path tempDir) throws Exception {
        Path setup = tempDir.resolve("setup.feature");
        Files.writeString(setup, """
                Feature: Setup Helper

                Scenario: helper
                * def helper = 'ok'
                * match helper == 'ok'
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: Main

                Scenario: body only
                * def a = 1
                * match a == 1
                """);
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
                function fn() {
                  karate.configure('beforeScenario', function(){
                    karate.call('setup.feature');
                  });
                  return {};
                }
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(main.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        assertEquals(1, featureFiles.length);
        String featureHtml = Files.readString(featuresDir.resolve(featureFiles[0]));

        assertTrue(featureHtml.contains("\"hook\": \"beforeScenario\""),
                "feature HTML should expose beforeScenario hook marker");
        assertTrue(featureHtml.contains("Setup Helper"),
                "feature HTML should include the nested called feature's name");
        assertTrue(featureHtml.contains("\"hasCallResults\": true"),
                "hook step should carry nested call results");
    }

    @Test
    void testBeforeScenarioFailureAppearsInReport(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("before-fail.feature");
        Files.writeString(feature, """
                Feature: Before Hook Failure

                Scenario: body is skipped when before-hook fails
                * def x = 1
                * match x == 1
                """);
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
                function fn() {
                  karate.configure('beforeScenario', function(){
                    karate.fail('before boom');
                  });
                  return {};
                }
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertFalse(result.isPassed());
        assertEquals(1, result.getScenarioFailedCount());

        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        String featureHtml = Files.readString(featuresDir.resolve(featureFiles[0]));

        assertTrue(featureHtml.contains("\"hook\": \"beforeScenario\""));
        assertTrue(featureHtml.contains("before boom"),
                "feature HTML should surface the hook failure message");
        // Body steps that never ran must be marked skipped so the report shows them greyed out.
        assertTrue(featureHtml.contains("\"status\": \"skipped\""),
                "body steps should be marked skipped when before-hook halts the scenario");
    }

    @Test
    void testAfterScenarioMatchFailureAppearsInReport(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("after-match-fail.feature");
        Files.writeString(feature, """
                Feature: After Hook Match Failure

                Background:
                  * configure afterScenario =
                  \"\"\"
                  function(){
                    var r = karate.match('en', 'enrt');
                    if (!r.pass) karate.fail('E2E validation failed: ' + r.message);
                  }
                  \"\"\"

                Scenario: passing body
                * def x = 1
                * match x == 1
                """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertFalse(result.isPassed());
        assertEquals(1, result.getScenarioFailedCount());

        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        String featureHtml = Files.readString(featuresDir.resolve(featureFiles[0]));

        assertTrue(featureHtml.contains("\"hook\": \"afterScenario\""));
        assertTrue(featureHtml.contains("E2E validation failed"),
                "feature HTML should surface the hook failure message");
    }

    @Test
    void testHookStepCapturesOnlyHookLog(@TempDir Path tempDir) throws Exception {
        // Regression guard: LogContext must be clean at hook entry so the hook step's log
        // captures only hook output, not lingering body-step output.
        Path feature = tempDir.resolve("hook-log-isolation.feature");
        Files.writeString(feature, """
                Feature: Hook Log Isolation

                Scenario: body and after emit distinct logs
                * configure afterScenario = function(){ karate.log('HOOK_ONLY_LINE') }
                * def x = 1
                * karate.log('BODY_ONLY_LINE')
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

        // Find the afterScenario hook entry in the first SCENARIO_EXIT event and extract
        // just its stepLog string value (not a fuzzy slice which would pick up later events).
        int hookIdx = jsonl.indexOf("\"hook\":\"afterScenario\"");
        assertTrue(hookIdx > 0, "afterScenario hook should appear in event stream");
        int stepLogKey = jsonl.indexOf("\"stepLog\":\"", hookIdx);
        assertTrue(stepLogKey > 0, "hook step should carry a stepLog field");
        int valueStart = stepLogKey + "\"stepLog\":\"".length();
        int valueEnd = jsonl.indexOf("\"", valueStart);
        String hookLog = jsonl.substring(valueStart, valueEnd);

        assertTrue(hookLog.contains("HOOK_ONLY_LINE"),
                "hook step log should contain the hook's log line: " + hookLog);
        assertFalse(hookLog.contains("BODY_ONLY_LINE"),
                "hook step log must not contain body step log output: " + hookLog);
    }

    // ========== configure logging + @report=false ==========
    //
    // Each test below writes an HTML report to a unique sub-directory under target/
    // so you can open them side-by-side to visually compare cases:
    //
    //   target/karate-logging-tests/<test-name>/karate-summary.html
    //
    // Cases covered:
    //   - testLoggingPrettyOn            : default, JSON bodies pretty-printed
    //   - testLoggingPrettyOff           : `pretty: false`, bodies single-line
    //   - testLoggingMaskHeaders         : Authorization redacted to ***
    //   - testLoggingMaskJsonPaths       : $.password redacted in body
    //   - testLoggingMaskRegex           : Bearer token regex redacted
    //   - testLoggingMaskEnableForUri    : /health unmasked, /api/secret masked
    //   - testLoggingReportLevelWarn     : INFO content (HTTP, print, karate.log) absent
    //   - testReportTagFalsePassing      : tagged scenario hidden; counts preserved
    //   - testReportTagFalseFailing      : tagged scenario shown with redacted message
    //   - testLoggingMidTestFlipAndRestore : level flip auto-reverts at scenario end
    //   - testLoggingDeepMerge           : Background mask survives mid-test level flip
    //   - testLoggingV1KeyDeprecation    : old keys warn but do not fail
    //   - testReportLogLevelHardRemoved  : `report.logLevel` raises a migration error

    private static final Path LOGGING_TESTS_DIR = Path.of("target/karate-logging-tests");

    private Path loggingTestDir(String name) {
        return LOGGING_TESTS_DIR.resolve(name);
    }

    private Path writeFeatureCallingHarness(Path tempDir, String featureBody) throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, featureBody.replace("__PORT__", String.valueOf(harness.getPort())));
        return feature;
    }

    @Test
    void testLoggingPrettyOn(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Pretty On (default)
                Scenario: get users with default pretty bodies
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * method get
                * status 200
                """);
        Path reportDir = loggingTestDir("pretty-on");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        // Default pretty body: indented multi-line; the formatter uses 2-space indent
        // and emits each key on its own line. The HTML inlines this captured log inside
        // a JSON string so newlines round-trip as the literal escape sequence "\n".
        assertTrue(stepLog.contains("\\n  \\\"users\\\":") || stepLog.contains("\\n    \\\"users\\\":"),
                "default pretty-printed body should be multi-line, was: " + stepLog);
    }

    @Test
    void testLoggingPrettyOff(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Pretty Off
                Background:
                * configure logging = { pretty: false }
                Scenario: get users with compact bodies
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * method get
                * status 200
                """);
        Path reportDir = loggingTestDir("pretty-off");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        // pretty:false collapses whitespace -> body has no key-newline pattern
        assertFalse(stepLog.contains("\\n  \\\"users\\\":"),
                "pretty:false body should not contain the multi-line indented form, was: " + stepLog);
        // ...but the body must still be there (just compact)
        assertTrue(stepLog.contains("\\\"users\\\":["),
                "pretty:false should produce single-line JSON in the captured log, was: " + stepLog);
    }

    @Test
    void testLoggingMaskHeaders(@TempDir Path tempDir) throws Exception {
        // Use system properties for the secret values so they don't appear in the source
        // text (which would also render in the report). Mask covers HTTP logs only — it
        // doesn't, and shouldn't, redact values that appear in feature-file source.
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Mask Headers
                Background:
                * configure logging = { mask: { headers: ['Authorization', 'X-Secret'] } }
                Scenario: auth header redacted in report
                * def authToken = karate.properties['test.auth.token']
                * def secretValue = karate.properties['test.secret.value']
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * header Authorization = 'Bearer ' + authToken
                * header X-Secret = secretValue
                * header X-Visible = 'this-should-show'
                * method get
                * status 200
                """);
        Path reportDir = loggingTestDir("mask-headers");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .systemProperty("test.auth.token", "eyJhbGciOiJIUzI1NiJ9.SECRET.TOKEN")
                .systemProperty("test.secret.value", "plain-secret-value")
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        assertFalse(stepLog.contains("eyJhbGciOiJIUzI1NiJ9.SECRET.TOKEN"),
                "raw bearer token must not appear in the captured HTTP log");
        assertFalse(stepLog.contains("plain-secret-value"),
                "X-Secret value must be redacted in the captured log");
        assertTrue(stepLog.contains("this-should-show"),
                "non-masked header value should pass through");
        assertTrue(stepLog.contains("Authorization: ***"), "Authorization header should be masked");
        assertTrue(stepLog.contains("X-Secret: ***"), "X-Secret header should be masked");
    }

    // Repro for issue #2826 — mask configured via karate.configure() inside karate-config.js
    // is silently dropped because ScenarioRuntime.call() resets LogContext to a fresh instance
    // AFTER karate-config.js has already populated it during the constructor's initEngine().
    @Test
    void testIssue2826MaskInKarateConfigJs(@TempDir Path tempDir) throws Exception {
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
                function fn() {
                  karate.configure('logging', {
                    pretty: true,
                    mask: {
                      headers: ['Authorization', 'X-Secret'],
                      jsonPaths: ['$..token'],
                      patterns: [{ regex: 'Bearer [A-Za-z0-9._-]+', replacement: 'Bearer ***' }],
                      replacement: '***'
                    }
                  });
                  return {};
                }
                """);
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, ("""
                Feature: Mask via karate-config.js
                Scenario: secrets must still be masked
                * def authToken = karate.properties['test.auth.token']
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * header Authorization = 'Bearer ' + authToken
                * header X-Secret = 'plain-secret-value'
                * method get
                * status 200
                """).replace("__PORT__", String.valueOf(harness.getPort())));
        Path reportDir = loggingTestDir("issue-2826-config-js");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .systemProperty("test.auth.token", "eyJhbGciOiJIUzI1NiJ9.SECRET.TOKEN")
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        assertFalse(stepLog.contains("eyJhbGciOiJIUzI1NiJ9.SECRET.TOKEN"),
                "raw bearer token MUST NOT appear when mask is set in karate-config.js — "
                        + "got log: " + stepLog);
        assertFalse(stepLog.contains("plain-secret-value"),
                "X-Secret value must be redacted in the captured log");
        assertTrue(stepLog.contains("Authorization: ***"),
                "Authorization header should be masked");
    }

    @Test
    void testLoggingMaskJsonPaths(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Mask JSON Paths
                Background:
                * configure logging = { mask: { jsonPaths: ['$.password', '$..token'] } }
                Scenario: body fields redacted in report
                * def pw = karate.properties['test.password']
                * def tok = karate.properties['test.token']
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * request { username: 'alice', password: '#(pw)', meta: { token: '#(tok)' } }
                * method post
                * status 201
                """);
        Path reportDir = loggingTestDir("mask-jsonpaths");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .systemProperty("test.password", "hunter2-secret")
                .systemProperty("test.token", "shhh-secret")
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        assertTrue(stepLog.contains("alice"), "non-masked username field should appear");
        assertFalse(stepLog.contains("hunter2-secret"), "password value must not appear");
        assertFalse(stepLog.contains("shhh-secret"), "nested token value must not appear");
        assertTrue(stepLog.contains("\\\"password\\\": \\\"***\\\""),
                "password should be redacted to *** in the JSON body");
    }

    @Test
    void testLoggingMaskRegex(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Mask Regex
                Background:
                * configure logging = { mask: { patterns: [{ regex: 'Bearer [A-Za-z0-9._-]+', replacement: 'Bearer ***' }] } }
                Scenario: bearer pattern redacted in HTTP log
                * def tok = karate.properties['test.bearer']
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * header Authorization = 'Bearer ' + tok
                * method get
                """);
        Path reportDir = loggingTestDir("mask-regex");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .systemProperty("test.bearer", "abc-def-ghi-token")
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        assertFalse(stepLog.contains("Bearer abc-def-ghi-token"),
                "raw bearer token must not appear in the captured HTTP log");
        assertTrue(stepLog.contains("Bearer ***"), "regex replacement should appear");
    }

    @Test
    void testLoggingMaskEnableForUri(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Mask Enable For URI
                Background:
                * configure logging = ({ mask: { headers: ['Authorization'], enableForUri: function(uri){ return uri.indexOf('users') >= 0 } } })
                Scenario: only /api/users masked, /api/status passes through
                * def tokA = karate.properties['test.users.token']
                * def tokB = karate.properties['test.status.token']
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * header Authorization = 'Bearer ' + tokA
                * method get
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/status'
                * header Authorization = 'Bearer ' + tokB
                * method get
                """);
        Path reportDir = loggingTestDir("mask-enable-for-uri");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .systemProperty("test.users.token", "secret-for-users")
                .systemProperty("test.status.token", "secret-for-status")
                .parallel(1);
        assertTrue(result.isPassed());

        // Two HTTP method steps -> two captured logs. Concatenate them and check both.
        String allLogs = extractAllHttpStepLogs(reportDir);
        assertFalse(allLogs.contains("secret-for-users"),
                "/api/users header value should be masked, was: " + allLogs);
        assertTrue(allLogs.contains("secret-for-status"),
                "/api/status header value should NOT be masked (filter excludes), was: " + allLogs);
    }

    @Test
    void testLoggingReportLevelWarn(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Report Level Warn Silences INFO
                Background:
                * configure logging = { report: 'warn' }
                Scenario: INFO content filtered out
                * print 'INFO_PRINT_LINE_should_be_filtered'
                * karate.log('INFO_KARATE_LOG_LINE_should_be_filtered')
                * karate.logger.warn('WARN_LINE_should_appear')
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * method get
                """);
        Path reportDir = loggingTestDir("report-warn");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());

        // Look at the captured per-step logs only — the original step text always appears
        // in `text` and is NOT what `report` filters. report filters captured output
        // (LogContext buffer entries written by print, karate.log, HTTP one-liners).
        String allLogs = extractAllStepLogs(reportDir);
        assertFalse(allLogs.contains("INFO_PRINT_LINE_should_be_filtered"),
                "INFO print line must be filtered from captured log when report:'warn'");
        assertFalse(allLogs.contains("INFO_KARATE_LOG_LINE_should_be_filtered"),
                "INFO karate.log line must be filtered from captured log when report:'warn'");
        assertTrue(allLogs.contains("WARN_LINE_should_appear"),
                "WARN line should still be captured");
        // HTTP request/response body (logged at INFO via LogContext.log) should ALSO be
        // filtered from the captured log even though SLF4J still emits at INFO.
        assertFalse(allLogs.contains("\\\"users\\\":["),
                "HTTP body (INFO via LogContext) should be filtered when report:'warn'");
    }

    @Test
    void testReportTagFalsePassing(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("report-false-pass.feature");
        Files.writeString(feature, """
                Feature: Report False - Passing
                Scenario: visible
                * def visibleVar = 'normal-step-text'
                * match visibleVar == 'normal-step-text'

                @report=false
                Scenario: hidden warmup
                * def secret = 'TOP_SECRET_VALUE'
                * match secret == 'TOP_SECRET_VALUE'
                """);
        Path reportDir = loggingTestDir("report-false-passing");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());
        // Both scenarios still count toward pass total
        assertEquals(2, result.getScenarioPassedCount(),
                "@report=false scenarios still count toward suite totals");

        String html = readFeatureHtml(reportDir);
        assertTrue(html.contains("normal-step-text"),
                "the un-tagged scenario's steps should render normally");
        assertFalse(html.contains("TOP_SECRET_VALUE"),
                "@report=false scenario's step content must not appear in the HTML");
        assertTrue(html.contains("\"reportDisabled\": true") || html.contains("\"reportDisabled\":true"),
                "scenario row should carry the reportDisabled marker");

        // JSONL must also redact step results
        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertFalse(jsonl.contains("TOP_SECRET_VALUE"),
                "JSONL must not leak step content for @report=false scenarios");
    }

    @Test
    void testReportTagFalseFailing(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("report-false-fail.feature");
        Files.writeString(feature, """
                Feature: Report False - Failing
                @report=false
                Scenario: failing with sensitive content
                * def token = 'sensitive-bearer-12345'
                * match token == 'wrong-expected-value-99999'
                """);
        Path reportDir = loggingTestDir("report-false-failing");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputJsonLines(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertFalse(result.isPassed(), "scenario must still fail");
        assertEquals(1, result.getScenarioFailedCount(),
                "@report=false failure must still count");

        String html = readFeatureHtml(reportDir);
        assertFalse(html.contains("sensitive-bearer-12345"),
                "actual token value must not appear in HTML");
        assertFalse(html.contains("wrong-expected-value-99999"),
                "expected value (which can also leak context) must not appear");
        assertTrue(html.contains("output suppressed by @report=false"),
                "redacted error message should appear");

        String jsonl = Files.readString(reportDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve("karate-events.jsonl"));
        assertFalse(jsonl.contains("sensitive-bearer-12345"));
        assertTrue(jsonl.contains("output suppressed by @report=false"));
    }

    @Test
    void testLoggingMidTestFlipAndRestore(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("level-flip.feature");
        Files.writeString(feature, """
                Feature: Mid-Test Level Flip With Auto Restore
                Scenario: A flips level mid-flow
                * print 'AAA_BEFORE_FLIP_INFO'
                * configure logging = { report: 'error' }
                * print 'AAA_AFTER_FLIP_should_be_FILTERED'
                * karate.logger.error('AAA_ERROR_passes_through')

                Scenario: B starts with default level (auto-restored from A)
                * print 'BBB_INFO_should_appear'
                """);
        Path reportDir = loggingTestDir("level-flip");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());

        String allLogs = extractAllStepLogs(reportDir);
        assertTrue(allLogs.contains("AAA_BEFORE_FLIP_INFO"),
                "INFO before the flip should be captured");
        assertFalse(allLogs.contains("AAA_AFTER_FLIP_should_be_FILTERED"),
                "INFO after raising level to error should be filtered");
        assertTrue(allLogs.contains("AAA_ERROR_passes_through"),
                "ERROR-level log should always pass through");
        assertTrue(allLogs.contains("BBB_INFO_should_appear"),
                "scenario B should run at the restored (default) level, capturing INFO again");
    }

    @Test
    void testLoggingDeepMerge(@TempDir Path tempDir) throws Exception {
        Path feature = writeFeatureCallingHarness(tempDir, """
                Feature: Deep Merge of `configure logging`
                Background:
                * configure logging = { mask: { headers: ['Authorization'] }, pretty: false }
                Scenario: subsequent configure keeps mask + pretty
                * def tok = karate.properties['test.merge.token']
                * configure logging = { report: 'debug' }
                * url 'http://127.0.0.1:__PORT__'
                * path 'api/users'
                * header Authorization = 'Bearer ' + tok
                * method get
                * status 200
                """);
        Path reportDir = loggingTestDir("deep-merge");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .systemProperty("test.merge.token", "to-be-redacted-merge")
                .parallel(1);
        assertTrue(result.isPassed());

        String stepLog = extractFirstHttpStepLog(reportDir);
        // Mask survived the second configure call
        assertFalse(stepLog.contains("to-be-redacted-merge"),
                "mask set in Background should survive a later partial `configure logging`");
        assertTrue(stepLog.contains("Authorization: ***"),
                "Authorization should still be masked after partial logging update");
        // pretty:false also survived (compact body) — body has no multi-line indented form
        assertFalse(stepLog.contains("\\n  \\\"users\\\":"),
                "pretty:false set in Background should survive a later partial `configure logging`");
    }

    @Test
    void testLoggingV1KeyDeprecation(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("v1-keys.feature");
        Files.writeString(feature, """
                Feature: V1 Deprecated Keys Are No-Op
                Scenario: old keys produce a warn but do not fail
                * configure logPrettyRequest = true
                * configure logPrettyResponse = false
                * configure printEnabled = false
                * configure lowerCaseResponseHeaders = true
                * def x = 1
                * match x == 1
                """);
        Path reportDir = loggingTestDir("v1-keys");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed(),
                "deprecated v1 configure keys must remain no-ops, not throw");
    }

    @Test
    void testReportLogLevelHardRemoved(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("report-loglevel-removed.feature");
        Files.writeString(feature, """
                Feature: Removed report.logLevel
                Scenario: error message points at migration
                * configure report = { logLevel: 'warn' }
                * def x = 1
                """);
        Path reportDir = loggingTestDir("report-loglevel-removed");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .parallel(1);
        // The configure step itself should fail with our migration error
        assertFalse(result.isPassed(),
                "configure report = { logLevel } must surface a migration error");
        String errors = String.join("\n", result.getErrors());
        assertTrue(errors.contains("'configure report = { logLevel: ... }' is no longer supported")
                        || errors.contains("'configure logging = { report:"),
                "error should mention the new `configure logging = { report: ... }` form, got: " + errors);
    }

    private static String readFeatureHtml(Path reportDir) throws Exception {
        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list();
        assertNotNull(featureFiles);
        assertTrue(featureFiles.length > 0, "expected at least one per-feature HTML page");
        return Files.readString(featuresDir.resolve(featureFiles[0]));
    }

    /**
     * Concatenate every captured "logs" string from every step in the per-feature
     * HTML's inlined data. Returned with the original JSON-string escaping intact
     * (newlines as {@code \n}, quotes as {@code \"}) so tests can match exactly what
     * lives inside the report buffer.
     */
    private static String extractAllStepLogs(Path reportDir) throws Exception {
        String html = readFeatureHtml(reportDir);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"logs\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group(1)).append('\n');
        }
        return sb.toString();
    }

    /** Captured logs from the FIRST step in the report that has any (typically the HTTP method step). */
    private static String extractFirstHttpStepLog(Path reportDir) throws Exception {
        String html = readFeatureHtml(reportDir);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"logs\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(html);
        if (!m.find()) {
            throw new AssertionError("no captured step log found in: " + reportDir);
        }
        return m.group(1);
    }

    private static String extractAllHttpStepLogs(Path reportDir) throws Exception {
        return extractAllStepLogs(reportDir);
    }

}
