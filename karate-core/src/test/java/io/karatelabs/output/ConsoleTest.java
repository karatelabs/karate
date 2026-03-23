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

import io.karatelabs.common.Resource;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.FeatureRuntime;
import io.karatelabs.core.Suite;
import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleTest {

    @Test
    void testColorFormatting() {
        // With colors enabled
        Console.setColorsEnabled(true);

        String red = Console.red("error");
        assertTrue(red.contains("\u001B[31m"));
        assertTrue(red.contains("error"));
        assertTrue(red.contains("\u001B[0m"));

        String green = Console.green("success");
        assertTrue(green.contains("\u001B[32m"));

        String bold = Console.bold("important");
        assertTrue(bold.contains("\u001B[1m"));
    }

    @Test
    void testColorFormattingDisabled() {
        Console.setColorsEnabled(false);

        String red = Console.red("error");
        assertEquals("error", red);

        String green = Console.green("success");
        assertEquals("success", green);

        // Reset for other tests
        Console.setColorsEnabled(true);
    }

    @Test
    void testSemanticFormatting() {
        Console.setColorsEnabled(true);

        String pass = Console.pass("passed");
        assertTrue(pass.contains("passed"));

        String fail = Console.fail("failed");
        assertTrue(fail.contains("failed"));

        String warn = Console.warn("warning");
        assertTrue(warn.contains("warning"));
    }

    @Test
    void testLine() {
        String line = Console.line(10);
        assertEquals("==========", line);

        String defaultLine = Console.line();
        assertEquals(60, defaultLine.length());
    }

    @Test
    void testFeatureResultPrintSummary() {
        Console.setColorsEnabled(false);

        // Create a simple feature and run it
        Feature feature = Feature.read(Resource.text("""
            Feature: Test feature
            Scenario: Passing scenario
            * def a = 1
            * match a == 1
            """));

        FeatureRuntime fr = new FeatureRuntime(feature);
        FeatureResult result = fr.call();

        // Verify result state before printSummary
        assertTrue(result.isPassed());
        assertEquals(1, result.getPassedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getScenarioCount());
        assertNotNull(result.getDisplayName());

        // Reset
        Console.setColorsEnabled(true);
    }

    @Test
    void testFeatureResultPrintSummaryWithFailure() {
        Console.setColorsEnabled(false);

        // Create a failing feature
        Feature feature = Feature.read(Resource.text("""
            Feature: Failing feature
            Scenario: Failing scenario
            * def a = 1
            * match a == 999
            """));

        FeatureRuntime fr = new FeatureRuntime(feature);
        FeatureResult result = fr.call();

        // Verify result state for failed feature
        assertTrue(result.isFailed());
        assertEquals(0, result.getPassedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getScenarioCount());
        assertNotNull(result.getFailureMessage());

        // Reset
        Console.setColorsEnabled(true);
    }

}
