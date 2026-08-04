/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nested {@code karate.call()} results are released once a feature has completed and its
 * listeners have consumed it.
 *
 * <p>Without that, a {@link SuiteResult} holds one full callee result tree per call for the
 * whole run, so memory scales with total scenarios times calls per scenario and a long suite
 * exhausts the heap on retention alone. The reports are unaffected — every report listener
 * serializes its output while the result is still whole — but a caller inspecting a
 * {@code SuiteResult} afterwards sees the nesting gone unless it opts back in.
 *
 * <p>Most tests use {@code TestUtils.createTestSuite}, which opts in, so this is where the
 * default is pinned down.
 */
class CallResultReleaseTest {

    @TempDir
    Path tempDir;

    private Path writeCallerAndCallee() throws IOException {
        Files.writeString(tempDir.resolve("callee.feature"), """
                Feature: callee
                Scenario:
                * def answer = 42
                """);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: caller
                Scenario:
                * def one = karate.call('callee.feature')
                * def two = karate.call('callee.feature')
                * match one.answer == 42
                """);
        return caller;
    }

    private SuiteResult run(Path caller, boolean retain) {
        return Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .retainCallResults(retain)
                .parallel(1);
    }

    private static long countAttachedCallResults(SuiteResult result) {
        return result.getFeatureResults().stream()
                .flatMap(fr -> fr.getScenarioResults().stream())
                .flatMap(sr -> sr.getStepResults().stream())
                .filter(step -> step.getCallResults() != null)
                .mapToLong(step -> step.getCallResults().size())
                .sum();
    }

    @Test
    void testCallResultsAreReleasedByDefault() throws IOException {
        SuiteResult result = run(writeCallerAndCallee(), false);
        assertTrue(result.isPassed());
        assertEquals(0, countAttachedCallResults(result),
                "nested call results should not survive the end of the feature by default");
    }

    @Test
    void testCallResultsSurviveWhenRetained() throws IOException {
        SuiteResult result = run(writeCallerAndCallee(), true);
        assertTrue(result.isPassed());
        assertEquals(2, countAttachedCallResults(result),
                "retainCallResults(true) should keep one attached result per call");
    }

    /**
     * Releasing must take only the nesting. A step's own status, timing and log are what
     * summaries, failure messages and the report itself are built from, so a release that
     * touched those would be a silent reporting regression rather than a memory saving.
     */
    @Test
    void testReleaseLeavesTheStepsThemselvesIntact() throws IOException {
        SuiteResult released = run(writeCallerAndCallee(), false);
        SuiteResult retained = run(writeCallerAndCallee(), true);

        List<StepResult> releasedSteps = released.getFeatureResults().get(0)
                .getScenarioResults().get(0).getStepResults();
        List<StepResult> retainedSteps = retained.getFeatureResults().get(0)
                .getScenarioResults().get(0).getStepResults();

        assertEquals(retainedSteps.size(), releasedSteps.size());
        for (int i = 0; i < releasedSteps.size(); i++) {
            StepResult step = releasedSteps.get(i);
            assertNotNull(step.getStep(), "step " + i + " lost its source step");
            assertEquals(retainedSteps.get(i).getStatus(), step.getStatus(),
                    "step " + i + " changed status");
        }
        assertNull(releasedSteps.get(0).getCallResults());
    }

}
