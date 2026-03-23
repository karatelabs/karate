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

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HtmlReportListener} - the async HTML report generator.
 */
class HtmlReportListenerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
    }

    @Test
    void testHtmlReportGeneratedByDefault() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: HTML Report Test
            Scenario: Passing test
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        // Run with default settings - HTML enabled, JSON Lines not enabled
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(true)  // default is true anyway
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        // HTML reports should exist
        assertTrue(Files.exists(reportDir.resolve("karate-summary.html")), "Summary HTML should exist");
        assertTrue(Files.exists(reportDir.resolve("index.html")), "Index redirect should exist");
        assertTrue(Files.exists(reportDir.resolve(HtmlReportListener.SUBFOLDER)), "Features directory should exist");

        // JSON Lines should NOT exist (opt-in only)
        assertFalse(Files.exists(reportDir.resolve("karate-results.jsonl")), "JSON Lines should not exist by default");

        // Static resources should be copied
        assertTrue(Files.exists(reportDir.resolve("res/bootstrap.min.css")), "Bootstrap CSS should exist");
        assertTrue(Files.exists(reportDir.resolve("res/alpine.min.js")), "Alpine.js should exist");
    }

    @Test
    void testHtmlReportWithMultipleFeatures() throws Exception {
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
                .outputConsoleSummary(false)
                .parallel(1);

        // Verify feature HTML files were created
        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list((dir, name) -> name.endsWith(".html"));
        assertNotNull(featureFiles);
        assertEquals(2, featureFiles.length, "Should have 2 feature HTML files");
    }

    @Test
    void testHtmlReportDisabled() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: No HTML Test
            Scenario: Test
            * def a = 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputHtmlReport(false)  // disable HTML reports
                .outputConsoleSummary(false)
                .parallel(1);

        // HTML reports should NOT exist when disabled
        assertFalse(Files.exists(reportDir.resolve("karate-summary.html")));
        assertFalse(Files.exists(reportDir.resolve(HtmlReportListener.SUBFOLDER)));
    }

    @Test
    void testSummaryHtmlContainsData() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Summary Content Test

            Scenario: Passing scenario
            * def a = 1

            Scenario: Another passing
            * def b = 2
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputConsoleSummary(false)
                .parallel(1);

        String summaryHtml = Files.readString(reportDir.resolve("karate-summary.html"));

        // Verify the HTML contains inlined JSON data
        assertTrue(summaryHtml.contains("<script id=\"karate-data\" type=\"application/json\">"));
        assertTrue(summaryHtml.contains("x-data=\"KarateReport.summaryData()\""));

        // Verify summary contains feature info
        assertTrue(summaryHtml.contains("Summary Content Test"));
    }

    @Test
    void testFeatureHtmlContainsSteps() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Step Content Test

            Scenario: Test with steps
            * def myVar = 'hello'
            * match myVar == 'hello'
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputConsoleSummary(false)
                .parallel(1);

        // Find the feature HTML file
        Path featuresDir = reportDir.resolve(HtmlReportListener.SUBFOLDER);
        String[] featureFiles = featuresDir.toFile().list((dir, name) -> name.endsWith(".html"));
        assertNotNull(featureFiles);
        assertEquals(1, featureFiles.length);

        String featureHtml = Files.readString(featuresDir.resolve(featureFiles[0]));

        // Verify the HTML contains inlined JSON with step data
        assertTrue(featureHtml.contains("<script id=\"karate-data\" type=\"application/json\">"));
        assertTrue(featureHtml.contains("x-data=\"KarateReport.featureData()\""));
        assertTrue(featureHtml.contains("steps"));
    }

    @Test
    void testFeatureSummaryData() throws Exception {
        // Test that FeatureSummary correctly captures data
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Summary Data Test

            @smoke @regression
            Scenario: Tagged scenario
            * def a = 1

            Scenario: Another scenario
            * def b = 2
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputConsoleSummary(false)
                .parallel(1);

        // Verify suite result
        assertEquals(1, result.getFeatureCount());
        assertEquals(2, result.getScenarioCount());
        assertTrue(result.isPassed());

        // The HtmlReportListener should have created the report with the correct summary
        String summaryHtml = Files.readString(reportDir.resolve("karate-summary.html"));
        assertTrue(summaryHtml.contains("Summary Data Test"));
    }

}
