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
package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.http.HttpClient;
import io.karatelabs.match.Match;
import io.karatelabs.match.Result;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test utilities for runtime tests.
 * Uses Runner internally to ensure production-consistent behavior.
 * Pattern: Create scenarios from Gherkin text blocks and run them with optional mock HTTP.
 */
public class TestUtils {

    /**
     * Run scenario steps with an in-memory HTTP client (no network).
     * Auto-prepends "Feature:\nScenario:\n" for convenience.
     * <pre>
     * run("""
     *     * def a = 1
     *     * match a == 1
     *     """);
     * </pre>
     * For full Gherkin (tags, background, etc.), use {@link #runFeature(String)}.
     */
    public static ScenarioRuntime run(String steps) {
        return run(new InMemoryHttpClient(), steps);
    }

    /**
     * Run scenario steps with a custom HTTP client.
     * Auto-prepends "Feature:\nScenario:\n" for convenience.
     */
    public static ScenarioRuntime run(HttpClient client, String steps) {
        return runFeature(client, "Feature:\nScenario:\n" + steps);
    }

    /**
     * Run a full Gherkin feature with an in-memory HTTP client.
     * Use this when you need tags, background, or other feature-level constructs.
     * <pre>
     * runFeature("""
     *     @smoke
     *     Feature: Test
     *     Background:
     *     * def base = 'http://localhost'
     *     Scenario: Example
     *     * def a = 1
     *     """);
     * </pre>
     */
    public static ScenarioRuntime runFeature(String gherkin) {
        return runFeature(new InMemoryHttpClient(), gherkin);
    }

    /**
     * Run a full Gherkin feature with a custom HTTP client.
     * Uses Runner internally with test-friendly settings.
     */
    public static ScenarioRuntime runFeature(HttpClient client, String gherkin) {
        Path workingDir = Path.of("src/test/resources");
        Feature feature = Feature.read(Resource.text(gherkin, workingDir));
        return runWithRunner(feature, client, workingDir);
    }

    /**
     * Run scenario steps from a specific directory (for file read tests).
     * Uses Runner internally with test-friendly settings.
     */
    public static ScenarioRuntime runFromDir(Path dir, String steps) {
        Feature feature = Feature.read(Resource.text("Feature:\nScenario:\n" + steps, dir));
        return runWithRunner(feature, new InMemoryHttpClient(), dir);
    }

    /**
     * Internal: Run a feature using Runner with test-friendly settings.
     * Captures the ScenarioRuntime via a RunListener.
     */
    private static ScenarioRuntime runWithRunner(Feature feature, HttpClient client, Path workingDir) {
        AtomicReference<ScenarioRuntime> captured = new AtomicReference<>();

        // Listener to capture the ScenarioRuntime
        RunListener listener = event -> {
            if (event.getType() == RunEventType.SCENARIO_EXIT && event instanceof ScenarioRunEvent sre) {
                captured.set(sre.source());
            }
            return true;
        };

        Runner.builder()
                .features(feature)
                .workingDir(workingDir)
                .httpClientFactory(() -> client)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .listener(listener)
                .parallel(1);

        ScenarioRuntime result = captured.get();
        if (result == null) {
            throw new IllegalStateException("No scenarios were executed");
        }
        return result;
    }

    /**
     * Get a variable from the runtime.
     */
    public static Object get(ScenarioRuntime sr, String name) {
        return sr.getVariable(name);
    }

    /**
     * Assert a variable matches an expected value using Karate's match engine.
     */
    public static void matchVar(ScenarioRuntime sr, String name, Object expected) {
        Object actual = sr.getVariable(name);
        Result result = Match.that(actual)._equals(expected);
        if (!result.pass) {
            throw new AssertionError("Variable '" + name + "': " + result.message);
        }
    }

    /**
     * Assert the scenario passed (no failures).
     */
    public static void assertPassed(ScenarioRuntime sr) {
        assertTrue(sr.getResult().isPassed(),
                "Expected pass but: " + sr.getResult().getFailureMessage());
    }

    /**
     * Assert the scenario failed.
     */
    public static void assertFailed(ScenarioRuntime sr) {
        assertTrue(sr.getResult().isFailed(), "Expected failure but scenario passed");
    }

    /**
     * Assert the scenario failed with a message containing the given text.
     */
    public static void assertFailedWith(ScenarioRuntime sr, String messageContains) {
        assertTrue(sr.getResult().isFailed(), "Expected failure but scenario passed");
        String msg = sr.getResult().getFailureMessage();
        assertTrue(msg != null && msg.contains(messageContains),
                "Expected failure message to contain '" + messageContains + "' but was: " + msg);
    }

    // ========== Log Utilities ==========

    /**
     * Get all logs from scenario execution (concatenated from all steps).
     * Line endings are normalized to \n for cross-platform consistency.
     */
    public static String getLogs(ScenarioRuntime sr) {
        StringBuilder sb = new StringBuilder();
        for (StepResult step : sr.getResult().getStepResults()) {
            String log = step.getLog();
            if (log != null && !log.isEmpty()) {
                sb.append(log);
            }
        }
        return normalizeLineEndings(sb.toString());
    }

    /**
     * Get log from a specific step (0-indexed).
     * Line endings are normalized to \n for cross-platform consistency.
     */
    public static String getStepLog(ScenarioRuntime sr, int stepIndex) {
        java.util.List<StepResult> steps = sr.getResult().getStepResults();
        if (stepIndex < 0 || stepIndex >= steps.size()) {
            return "";
        }
        String log = steps.get(stepIndex).getLog();
        return log == null ? "" : normalizeLineEndings(log);
    }

    /**
     * Assert that scenario logs contain expected text.
     */
    public static void assertLogContains(ScenarioRuntime sr, String expected) {
        String logs = getLogs(sr);
        assertTrue(logs.contains(expected),
                "Expected log to contain '" + expected + "' but was:\n" + logs);
    }

    /**
     * Normalize line endings to \n (for Windows compatibility).
     */
    public static String normalizeLineEndings(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n");
    }

    // ========== Suite Creation Utilities ==========

    /**
     * Create a test Suite from Feature objects with test-friendly defaults.
     * Equivalent to the old Suite.of(Feature...) pattern.
     * <p>
     * Test defaults: no console summary, no HTML reports, no backup, skip tag filtering.
     */
    public static Suite createTestSuite(Feature... features) {
        return Runner.builder()
                .features(features)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .skipTagFiltering(true)
                .buildSuite();
    }

    /**
     * Create a test Suite from paths with test-friendly defaults.
     * Equivalent to the old Suite.of(Path, String...) pattern.
     */
    public static Suite createTestSuite(Path workingDir, String... paths) {
        return Runner.builder()
                .path(paths)
                .workingDir(workingDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .skipTagFiltering(true)
                .buildSuite();
    }

    /**
     * Run tests from Feature objects and return SuiteResult.
     * Equivalent to the old Suite.of(Feature...).run() pattern.
     */
    public static SuiteResult runTestSuite(Feature... features) {
        return createTestSuite(features).run();
    }

    /**
     * Run tests from paths and return SuiteResult.
     * Equivalent to the old Suite.of(Path, String...).run() pattern.
     */
    public static SuiteResult runTestSuite(Path workingDir, String... paths) {
        return createTestSuite(workingDir, paths).run();
    }

    /**
     * Get a Runner.Builder pre-configured with test-friendly defaults.
     * Use this when you need to customize the Suite further.
     * <p>
     * Example:
     * <pre>
     * SuiteResult result = TestUtils.testBuilder()
     *     .path("features/")
     *     .parallel(4);
     * </pre>
     */
    public static Runner.Builder testBuilder() {
        return Runner.builder()
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .skipTagFiltering(true);
    }

}
