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

import io.karatelabs.output.Console;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for failure-reporting output — file path + line number,
 * the offending Gherkin source line, and the printed "failed features:" block.
 * The source-line display is v1 parity (#todo-link) and helps readers locate
 * failures without opening the feature.
 */
class FailureReportingTest {

    @TempDir
    Path tempDir;

    private PrintStream originalOut;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
        originalOut = System.out;
    }

    @AfterEach
    void teardown() {
        Console.setOutput(originalOut);
        Console.setColorsEnabled(true);
    }

    @Test
    void testFailedStepTextAndLocationOnMatchFailure() throws Exception {
        Path feature = tempDir.resolve("fail.feature");
        Files.writeString(feature, """
                Feature: Failing test

                Scenario: bad match
                * def a = 1
                * match a == 999
                """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);

        // Source line is present with the Gherkin prefix
        assertEquals("* match a == 999", sr.getFailedStepText());

        // Location is "path:line" (5 = line of the failing match)
        String location = sr.getFailedStepLocation();
        assertNotNull(location);
        assertTrue(location.endsWith("fail.feature:5"),
                "expected location to end with fail.feature:5, got: " + location);

        // Display-formatted message combines both
        String display = sr.getFailureMessageForDisplay();
        assertNotNull(display);
        assertTrue(display.contains("fail.feature:5"), "expected path+line in display: " + display);
        assertTrue(display.contains("match a == 999"), "expected step text in display: " + display);
    }

    @Test
    void testFailedStepTextNullWhenScenarioPasses() throws Exception {
        Path feature = tempDir.resolve("ok.feature");
        Files.writeString(feature, """
                Feature: Passing test

                Scenario: good
                * def a = 1
                * match a == 1
                """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);
        assertNull(sr.getFailedStepText());
        assertNull(sr.getFailedStepLocation());
        assertNull(sr.getFailureMessageForDisplay());
    }

    @Test
    void testPrintSummaryIncludesFailedStepSourceLine() throws Exception {
        Path feature = tempDir.resolve("cookie-like.feature");
        // Mirrors the shape of the cookie.feature:31 failure observed in CI —
        // a `match` step that we want visible in the console summary.
        Files.writeString(feature, """
                Feature: Failure summary demo

                Scenario: Delete cookie
                * def c = null
                * match c != null
                """);

        // Capture Console output during printSummary()
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        // "failed features:" block header
        assertTrue(output.contains("failed features:"),
                "expected 'failed features:' header in summary output: " + output);

        // Scenario name
        assertTrue(output.contains("- Delete cookie"),
                "expected scenario name in summary output: " + output);

        // path:line for IDE navigation — at least the file:line suffix
        assertTrue(output.contains("cookie-like.feature:5"),
                "expected path:line in summary output: " + output);

        // The offending Gherkin source line (new behavior — v1 parity)
        assertTrue(output.contains("* match c != null"),
                "expected source line '* match c != null' in summary output: " + output);

        // Error message itself
        assertTrue(output.contains("match failed"),
                "expected 'match failed' message in summary output: " + output);
    }

    @Test
    void testPrintSummaryOrdersLocationSourceThenError() throws Exception {
        Path feature = tempDir.resolve("order.feature");
        Files.writeString(feature, """
                Feature: Order of failure lines

                Scenario: bad
                * def v = 1
                * match v == 2
                """);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        int locationIdx = output.indexOf("order.feature:5");
        int sourceIdx = output.indexOf("* match v == 2");
        int errorIdx = output.indexOf("match failed");

        assertTrue(locationIdx > 0, "location line missing");
        assertTrue(sourceIdx > 0, "source line missing");
        assertTrue(errorIdx > 0, "error line missing");
        assertTrue(locationIdx < sourceIdx,
                "location line must come before source line (IDE plugins read top-down)");
        assertTrue(sourceIdx < errorIdx,
                "source line must come before error message");
    }
}
