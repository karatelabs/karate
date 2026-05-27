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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StepCompareImageTest {

    @TempDir
    Path tempDir;

    @Test
    void testCompareImage() throws Exception {
        Path feature = tempDir.resolve("a.feature");
        Files.writeString(feature, """
            Feature: Image comparison
            Background:
            # Enable PDF report (options are "all" to include passed and failed or "mismatched" for failed only)
            * def imageComparisonOpts = { report: "all" }
            * configure imageComparison = imageComparisonOpts

            # Simulate screenshots with small difference
            # 3x3 blue square with one pink pixel in the center
            * def baselineBytes = new Uint8Array(java.util.Base64.getDecoder().decode('iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAFklEQVR4AWPgSj4BQSDWf4b/UBYEAQCo6AunFOcG7wAAAABJRU5ErkJggg=='))
            # 3x3 blue square with one yellow pixel in the center
            * def latestBytes = new Uint8Array(java.util.Base64.getDecoder().decode('iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAFUlEQVR4AWPgSj4BQSDW//8gEiEGAKnnC6fIccDRAAAAAElFTkSuQmCC'))
            
            Scenario: Comparing the same image should pass
            * compareImage { baseline: #(baselineBytes), latest: #(baselineBytes) }
            
            Scenario: Comparing different images should fail
            * compareImage { baseline: #(baselineBytes), latest: #(latestBytes) }
            
            Scenario: Comparing different images while ignoring dynamic area should pass
            * def ignoredBoxes = [{ top: 1, bottom: 2, left: 1, right: 2 }]
            * compareImage { baseline: #(baselineBytes), latest: #(baselineBytes), options: { ignoredBoxes: ignoredBoxes } }
            
            Scenario: Comparing different images with custom failure threshold should pass
            * compareImage { baseline: #(baselineBytes), latest: #(latestBytes), options: { failureThreshold: 11.2 } }
            
            Scenario: Comparing different images with mismatchShouldPass flag should report but pass
            * karate.configure('imageComparison', Object.assign(imageComparisonOpts, { mismatchShouldPass: true }))
            * def result = karate.compareImage(baselineBytes, latestBytes)
            * match result.isMismatch == true
            * def result = karate.compareImage(null, latestBytes)
            * match result.isBaselineMissing == true
            """);

        Path outputDir = Path.of(tempDir.toString(), "target", "karate-reports");
        Path imgComparisonReport = outputDir.resolve("image-comparison.pdf");

        SuiteResult result = Runner.builder()
                .path(feature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .outputDir(outputDir)
                .backupOutputDir(false)
                .skipTagFiltering(true)
                .buildSuite()
                .run();

        assertEquals(4, result.getScenarioPassedCount());
        assertEquals(1, result.getScenarioFailedCount());
        assertTrue(Files.isRegularFile(imgComparisonReport));
    }

}
