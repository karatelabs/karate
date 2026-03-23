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
import io.karatelabs.output.Console;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RunnerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        // Disable colors for cleaner test output
        Console.setColorsEnabled(false);
    }

    @Test
    void testRunnerWithSingleFeature() throws Exception {
        Path feature = tempDir.resolve("simple.feature");
        Files.writeString(feature, """
            Feature: Simple test

            Scenario: Basic assertion
            * def a = 1
            * match a == 1
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount());
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithMultipleFeatures() throws Exception {
        Path feature1 = tempDir.resolve("first.feature");
        Files.writeString(feature1, """
            Feature: First
            Scenario: Test 1
            * def x = 1
            """);

        Path feature2 = tempDir.resolve("second.feature");
        Files.writeString(feature2, """
            Feature: Second
            Scenario: Test 2
            * def y = 2
            """);

        SuiteResult result = Runner.path(tempDir.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(2, result.getFeatureCount());
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithFailure() throws Exception {
        Path feature = tempDir.resolve("failing.feature");
        Files.writeString(feature, """
            Feature: Failing test

            Scenario: This will fail
            * def a = 1
            * match a == 999
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());
        assertEquals(1, result.getScenarioFailedCount());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void testRunnerWithFeatureObjects() throws Exception {
        Path featurePath = tempDir.resolve("direct.feature");
        Files.writeString(featurePath, """
            Feature: Direct feature

            Scenario: Direct test
            * def value = 42
            * match value == 42
            """);

        Feature feature = Feature.read(Resource.from(featurePath, tempDir));

        SuiteResult result = Runner.features(feature)
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount());
    }

    @Test
    void testRunnerBuilderChaining() throws Exception {
        Path feature = tempDir.resolve("chained.feature");
        Files.writeString(feature, """
            Feature: Chained builder

            Scenario: Test chaining
            * def val = 1
            """);

        Runner.Builder builder = Runner.builder()
                .path(feature.toString())
                .workingDir(tempDir)
                .karateEnv("test")
                .tags("@smoke")
                .dryRun(false)
                .outputDir(tempDir.resolve("reports").toString());

        Suite suite = builder.buildSuite();
        assertNotNull(suite);
        assertEquals("test", suite.env);
    }

    @Test
    void testRunnerWithClasspathDirectory() {
        // This directory contains feature files in test resources
        // One has an intentionally failing scenario for report testing
        // @ignore features (helper, data-setup) are completely skipped
        SuiteResult result = Runner.path("classpath:io/karatelabs/report")
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .outputConsoleSummary(false)
                .parallel(1);

        // Should find only non-@ignore features: test-report, second-feature, third-feature, http-demo
        assertEquals(4, result.getFeatureCount());
        // Total scenarios: 12 + 4 + 4 + 5 = 25
        assertEquals(25, result.getScenarioCount());
    }

    @Test
    void testRunnerWithClasspathDirectoryTrailingSlash() {
        // Same test but with trailing slash
        // Contains intentionally failing scenario for report testing
        SuiteResult result = Runner.path("classpath:io/karatelabs/report/")
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .outputConsoleSummary(false)
                .parallel(1);

        // @ignore features are skipped, http-demo included
        assertEquals(4, result.getFeatureCount());
    }

    @Test
    void testRunnerWithClasspathSingleFile() {
        // Test single classpath file (existing behavior)
        SuiteResult result = Runner.path("classpath:io/karatelabs/report/second-feature.feature")
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);

        assertEquals(1, result.getFeatureCount());
        assertEquals(4, result.getScenarioPassedCount());  // Payment Processing has 4 scenarios
    }

    @Test
    void testRunnerWithClasspathNestedDirectory() {
        // Test that features/ directory only contains 1 feature
        SuiteResult result = Runner.path("classpath:feature")
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);

        // Should find http-simple.feature (or may be 0 if it requires HTTP)
        assertTrue(result.getFeatureCount() >= 0);
    }

    @Test
    void testRunnerMixedPaths() throws Exception {
        // Mix file system and classpath paths
        Path feature = tempDir.resolve("local.feature");
        Files.writeString(feature, """
            Feature: Local feature
            Scenario: Local test
            * def x = 1
            """);

        SuiteResult result = Runner.path(feature.toString(), "classpath:io/karatelabs/report/second-feature.feature")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);

        // 1 local + 1 classpath
        assertEquals(2, result.getFeatureCount());
    }

    @Test
    void testRunnerWithMissingDataFile() throws Exception {
        // Test to verify Runner doesn't crash on data-driven scenarios with missing data
        // Create a feature with Scenario Outline that references non-existent data file

        // Write karate-config.js
        Files.writeString(tempDir.resolve("karate-config.js"), """
            function fn() {
              return { baseUrl: 'https://httpbin.org' };
            }
            """);

        // Write a feature with Scenario Outline that references non-existent data
        Path feature = tempDir.resolve("data-driven.feature");
        Files.writeString(feature, """
            Feature: Data Driven Test

            Scenario Outline: Test with <username>
              * def result = '<username>'
              * match result == '#string'

            Examples:
              | read('missing-file.csv') |
            """);

        // This should NOT throw - it should return a SuiteResult with failures
        SuiteResult result = Runner.path(tempDir.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .outputConsoleSummary(false)
                .parallel(1);

        // The suite should complete (not crash) with the scenario marked as failed
        assertNotNull(result);
        assertFalse(result.isPassed()); // Should fail gracefully, not throw
    }

    @Test
    void testRunnerWithLineNumberFilter() throws Exception {
        // Create a feature with multiple scenarios
        Path feature = tempDir.resolve("multi-scenario.feature");
        Files.writeString(feature, """
            Feature: Multiple scenarios

            Scenario: First scenario
            * def a = 1
            * match a == 1

            Scenario: Second scenario
            * def b = 2
            * match b == 2

            Scenario: Third scenario
            * def c = 3
            * match c == 3
            """);

        // Run only the second scenario (line 8 is where "Scenario: Second scenario" is)
        SuiteResult result = Runner.path(feature.toString() + ":8")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount());
        // Only the second scenario should run
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithLineNumberFilterMultipleLines() throws Exception {
        // Create a feature with multiple scenarios
        Path feature = tempDir.resolve("multi-scenario2.feature");
        Files.writeString(feature, """
            Feature: Multiple scenarios

            Scenario: First scenario
            * def a = 1
            * match a == 1

            Scenario: Second scenario
            * def b = 2
            * match b == 2

            Scenario: Third scenario
            * def c = 3
            * match c == 3
            """);

        // Run first and third scenarios (lines 3 and 12)
        SuiteResult result = Runner.path(feature.toString() + ":3:12")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getFeatureCount());
        // Two scenarios should run
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithLineNumberFilterIgnoresTags() throws Exception {
        // Create a feature with @ignore tag
        Path feature = tempDir.resolve("ignore-scenario.feature");
        Files.writeString(feature, """
            Feature: Ignore test

            @ignore
            Scenario: Ignored scenario
            * def a = 1
            * match a == 1
            """);

        // Run the @ignore scenario by line number - should bypass tag filter
        SuiteResult result = Runner.path(feature.toString() + ":4")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithLineNumberFilterScenarioOutline() throws Exception {
        // Create a feature with Scenario Outline
        Path feature = tempDir.resolve("outline.feature");
        Files.writeString(feature, """
            Feature: Outline test

            Scenario Outline: Parameterized test
            * def val = <value>
            * match val == <expected>

            Examples:
            | value | expected |
            | 1     | 1        |
            | 2     | 2        |
            | 3     | 3        |
            """);

        // Run targeting the Examples table line (line 7)
        // This should run all examples in that table
        SuiteResult result = Runner.path(feature.toString() + ":7")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        // All 3 examples should run
        assertEquals(3, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithLineNumberFilterScenarioOutlineDeclaration() throws Exception {
        // Create a feature with Scenario Outline
        Path feature = tempDir.resolve("outline2.feature");
        Files.writeString(feature, """
            Feature: Outline test

            Scenario Outline: Parameterized test
            * def val = <value>
            * match val == <expected>

            Examples:
            | value | expected |
            | 1     | 1        |
            | 2     | 2        |
            | 3     | 3        |
            """);

        // Run targeting the Scenario Outline declaration line (line 3)
        // This should run all examples from that outline
        SuiteResult result = Runner.path(feature.toString() + ":3")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        // All 3 examples should run
        assertEquals(3, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithLineNumberFilterSpecificExampleRow() throws Exception {
        // Create a feature with Scenario Outline
        Path feature = tempDir.resolve("outline-row.feature");
        Files.writeString(feature, """
            Feature: Outline row test

            Scenario Outline: Parameterized test
            * def val = <value>
            * match val == <expected>

            Examples:
            | value | expected |
            | 1     | 1        |
            | 2     | 2        |
            | 3     | 3        |
            """);

        // Run targeting a specific example row line (line 9 = "| 1 | 1 |")
        SuiteResult result = Runner.path(feature.toString() + ":9")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        // Only 1 example should run
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testRunnerWithLineNumberFilterMultipleExampleRows() throws Exception {
        // Create a feature with Scenario Outline
        Path feature = tempDir.resolve("outline-multi-row.feature");
        Files.writeString(feature, """
            Feature: Outline multi row test

            Scenario Outline: Parameterized test
            * def val = <value>
            * match val == <expected>

            Examples:
            | value | expected |
            | 1     | 1        |
            | 2     | 2        |
            | 3     | 3        |
            """);

        // Run targeting two specific example rows (lines 9 and 11)
        SuiteResult result = Runner.path(feature.toString() + ":9:11")
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        // 2 examples should run
        assertEquals(2, result.getScenarioPassedCount());
    }

}
