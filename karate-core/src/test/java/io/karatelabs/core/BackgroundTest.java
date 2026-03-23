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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Background feature execution.
 * Background steps run before each scenario in a feature.
 */
class BackgroundTest {

    @TempDir
    Path tempDir;

    @Test
    void testBackgroundRunsBeforeEachScenario() throws Exception {
        Path feature = tempDir.resolve("background.feature");
        Files.writeString(feature, """
            Feature: Background Test

            Background:
            * def shared = 'from-background'

            Scenario: First scenario
            * match shared == 'from-background'

            Scenario: Second scenario
            * match shared == 'from-background'
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioCount());
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testBackgroundVariablesInheritedByScenario() throws Exception {
        Path feature = tempDir.resolve("background-inherit.feature");
        Files.writeString(feature, """
            Feature: Background Inheritance

            Background:
            * def baseUrl = 'http://localhost:8080'
            * def headers = { 'Content-Type': 'application/json' }

            Scenario: Use background variables
            * def fullUrl = baseUrl + '/users'
            * match fullUrl == 'http://localhost:8080/users'
            * match headers['Content-Type'] == 'application/json'
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testBackgroundWithMultipleSteps() throws Exception {
        Path feature = tempDir.resolve("background-multi.feature");
        Files.writeString(feature, """
            Feature: Multi-step Background

            Background:
            * def a = 1
            * def b = 2
            * def c = a + b

            Scenario: Check computed value
            * match c == 3
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testScenarioCanOverrideBackgroundVariable() throws Exception {
        Path feature = tempDir.resolve("background-override.feature");
        Files.writeString(feature, """
            Feature: Override Background

            Background:
            * def value = 'original'

            Scenario: Override the value
            * def value = 'overridden'
            * match value == 'overridden'

            Scenario: Original value still available
            * match value == 'original'
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testBackgroundFailureStopsScenario() throws Exception {
        Path feature = tempDir.resolve("background-fail.feature");
        Files.writeString(feature, """
            Feature: Background Failure

            Background:
            * match 1 == 2

            Scenario: This should not run
            * def x = 1
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertFalse(result.isPassed());
        assertEquals(1, result.getScenarioFailedCount());
    }

    @Test
    void testFeatureWithoutBackground() throws Exception {
        Path feature = tempDir.resolve("no-background.feature");
        Files.writeString(feature, """
            Feature: No Background

            Scenario: Standalone scenario
            * def x = 42
            * match x == 42
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testBackgroundStepsInStepResults() throws Exception {
        Path feature = tempDir.resolve("background-steps.feature");
        Files.writeString(feature, """
            Feature: Background Steps

            Background:
            * def bg = 'background-step'

            Scenario: Check step count
            * def sc = 'scenario-step'
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed());

        // Should have both background and scenario steps in results
        ScenarioResult scenarioResult = result.getFeatureResults().getFirst()
                .getScenarioResults().getFirst();
        // Background step + scenario step = 2 steps total
        assertEquals(2, scenarioResult.getStepResults().size());
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
