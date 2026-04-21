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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.runTestSuite;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for beforeScenario / afterScenario lifecycle hooks in regular (non-mock) execution.
 * Covers GitHub issues #2796, #2540, #2699, #2239.
 */
public class ScenarioHooksTest {

    @TempDir
    Path tempDir;

    private static int hookCount;
    private static int beforeHookCount;

    @BeforeEach
    void resetCounters() {
        hookCount = 0;
        beforeHookCount = 0;
    }

    public static void incrementHookCount() {
        hookCount++;
    }

    public static void incrementBeforeHookCount() {
        beforeHookCount++;
    }

    // ===== #2796: afterScenario defined in Background fires in regular execution =====

    @Test
    void testAfterScenarioRunsOnPass_fromBackground() throws Exception {
        Path feature = tempDir.resolve("after-pass.feature");
        Files.writeString(feature, """
            Feature: afterScenario fires on pass

            Background:
              * configure afterScenario = function(){ Java.type('io.karatelabs.core.ScenarioHooksTest').incrementHookCount() }

            Scenario: one
              * def x = 1
              * match x == 1

            Scenario: two
              * def y = 2
              * match y == 2
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, hookCount, "afterScenario should fire once per scenario");
    }

    // ===== #2540: afterScenario runs even when the scenario fails =====

    @Test
    void testAfterScenarioRunsOnFail() throws Exception {
        Path feature = tempDir.resolve("after-fail.feature");
        Files.writeString(feature, """
            Feature: afterScenario fires on fail

            Background:
              * configure afterScenario = function(){ Java.type('io.karatelabs.core.ScenarioHooksTest').incrementHookCount() }

            Scenario: failing scenario
              * def x = 1
              * match x == 2
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertFalse(result.isPassed(), "scenario should fail");
        assertEquals(1, hookCount, "afterScenario should fire even when scenario fails");
    }

    // ===== #2699: hook failure fails the scenario by default =====

    @Test
    void testHookMatchFailureFailsScenario() throws Exception {
        Path feature = tempDir.resolve("hook-match-fail.feature");
        Files.writeString(feature, """
            Feature: afterScenario match failure fails scenario

            Background:
              * configure afterScenario =
              \"\"\"
              function(){
                var r = karate.match('en', 'enrt');
                if (!r.pass) karate.fail('E2E validation failed: ' + r.message);
              }
              \"\"\"

            Scenario: passing body but failing hook
              * def x = 1
              * match x == 1
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertFalse(result.isPassed(), "hook failure should fail the scenario by default");
        String msg = getFailureMessage(result);
        assertTrue(msg.contains("E2E validation failed"), "failure message should surface the hook error: " + msg);
    }

    @Test
    void testHookExceptionDoesNotOverrideExistingFailure() throws Exception {
        Path feature = tempDir.resolve("hook-primary.feature");
        Files.writeString(feature, """
            Feature: afterScenario preserves primary error

            Background:
              * configure afterScenario = function(){ karate.fail('HOOK_ERROR') }

            Scenario: primary match fails
              * def x = 1
              * match x == 99
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertFalse(result.isPassed(), "scenario should fail");
        // getFailureMessage returns the FIRST failed step's message - so the primary step error
        // takes precedence over the hook error.
        String msg = getFailureMessage(result);
        assertFalse(msg.contains("HOOK_ERROR"),
                "primary step error should be reported, not the hook error: " + msg);
    }

    // ===== continueOnStepFailure interaction: soft mode runs scenario steps even after beforeScenario fails =====

    @Test
    void testBeforeScenarioFailureHaltsScenarioByDefault() throws Exception {
        Path feature = tempDir.resolve("before-halt.feature");
        Files.writeString(feature, """
            Feature: beforeScenario failure halts scenario

            Background:
              * configure beforeScenario = function(){ karate.fail('setup failed') }

            Scenario: steps should be skipped
              * Java.type('io.karatelabs.core.ScenarioHooksTest').incrementHookCount()
              * def x = 1
            """);

        // Note: beforeScenario set in Background doesn't fire for this scenario (chicken-egg),
        // so use karate-config.js for this one.
        Files.writeString(tempDir.resolve("karate-config.js"), """
            function fn() {
              karate.configure('beforeScenario', function(){ karate.fail('setup failed') });
              return {};
            }
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .skipTagFiltering(true)
                .parallel(1);

        assertFalse(result.isPassed(), "beforeScenario failure should fail scenario");
        assertEquals(0, hookCount, "scenario steps should not run after beforeScenario failure (default)");
    }

    @Test
    void testBeforeScenarioFailureContinuesInSoftMode() throws Exception {
        Files.writeString(tempDir.resolve("karate-config.js"), """
            function fn() {
              karate.configure('continueOnStepFailure', true);
              karate.configure('beforeScenario', function(){ karate.fail('setup failed') });
              return {};
            }
            """);
        Path feature = tempDir.resolve("before-soft.feature");
        Files.writeString(feature, """
            Feature: beforeScenario failure in soft-assertion mode

            Scenario: steps still run after hook failure
              * Java.type('io.karatelabs.core.ScenarioHooksTest').incrementHookCount()
              * def x = 1
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .skipTagFiltering(true)
                .parallel(1);

        assertFalse(result.isPassed(), "scenario should still fail overall");
        assertEquals(1, hookCount,
                "continueOnStepFailure should allow scenario steps to run even after beforeScenario failure");
    }

    // ===== User-controlled suppression: wrap the hook body in try/catch =====

    @Test
    void testHookTryCatchSuppressesFailure() throws Exception {
        // Users who want to suppress hook failures can wrap in try/catch themselves.
        Path feature = tempDir.resolve("hook-try-catch.feature");
        Files.writeString(feature, """
            Feature: user-suppressed hook failure

            Background:
              * configure afterScenario =
              \"\"\"
              function(){
                try { karate.fail('this is suppressed') } catch (e) { karate.log('suppressed:', e) }
              }
              \"\"\"

            Scenario: hook is user-suppressed so scenario passes
              * def x = 1
              * match x == 1
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(),
                "user-wrapped hook failure should not fail the scenario: " + getFailureMessage(result));
    }

    // ===== #2239: beforeScenario hook =====

    @Test
    void testBeforeScenarioFromConfigJs() throws Exception {
        Files.writeString(tempDir.resolve("karate-config.js"), """
            function fn() {
              karate.configure('beforeScenario', function(){ Java.type('io.karatelabs.core.ScenarioHooksTest').incrementBeforeHookCount() });
              return {};
            }
            """);
        Path feature = tempDir.resolve("before-hook.feature");
        Files.writeString(feature, """
            Feature: beforeScenario fires before each scenario

            Scenario: one
              * def x = 1

            Scenario: two
              * def y = 2
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .skipTagFiltering(true)
                .parallel(1);

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, beforeHookCount, "beforeScenario should fire once per scenario");
    }

    @Test
    void testBeforeScenarioInBackgroundDoesNotFireForOwnScenario() throws Exception {
        // beforeScenario runs BEFORE Background, so setting it in Background cannot
        // affect the current scenario. This test documents that behavior.
        Path feature = tempDir.resolve("before-chicken-egg.feature");
        Files.writeString(feature, """
            Feature: beforeScenario in Background is a no-op for the current scenario

            Background:
              * configure beforeScenario = function(){ Java.type('io.karatelabs.core.ScenarioHooksTest').incrementBeforeHookCount() }

            Scenario: one
              * def x = 1

            Scenario: two
              * def y = 2
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        // Each scenario resets config (fresh config per scenario), so the hook never fires.
        assertEquals(0, beforeHookCount,
                "beforeScenario set in Background should not fire for the current scenario - must be set in karate-config.js");
    }

    private String getFailureMessage(SuiteResult result) {
        if (result.isPassed()) return "none";
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return "unknown";
    }

}
