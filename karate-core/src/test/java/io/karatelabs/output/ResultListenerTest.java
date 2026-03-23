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

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResultListener streaming.
 */
class ResultListenerTest {

    @TempDir
    Path tempDir;

    @Test
    void testListenerLifecycle() throws Exception {
        List<String> events = new ArrayList<>();

        ResultListener listener = new ResultListener() {
            @Override
            public void onSuiteStart(Suite suite) {
                events.add("suiteStart:" + suite.features.size());
            }

            @Override
            public void onSuiteEnd(SuiteResult result) {
                events.add("suiteEnd:" + result.getScenarioCount());
            }

            @Override
            public void onFeatureStart(Feature feature) {
                events.add("featureStart:" + feature.getName());
            }

            @Override
            public void onFeatureEnd(FeatureResult result) {
                events.add("featureEnd:" + result.getFeature().getName());
            }

            @Override
            public void onScenarioStart(Scenario scenario) {
                events.add("scenarioStart:" + scenario.getName());
            }

            @Override
            public void onScenarioEnd(ScenarioResult result) {
                events.add("scenarioEnd:" + result.getScenario().getName());
            }
        };

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Listener Test
            Scenario: First scenario
            * def a = 1
            Scenario: Second scenario
            * def b = 2
            """);

        Suite suite = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .resultListener(listener)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .buildSuite();
        suite.run();

        // Verify all events fired
        assertTrue(events.contains("suiteStart:1"));
        assertTrue(events.contains("suiteEnd:2"));
        assertTrue(events.contains("featureStart:Listener Test"));
        assertTrue(events.contains("featureEnd:Listener Test"));
        assertTrue(events.contains("scenarioStart:First scenario"));
        assertTrue(events.contains("scenarioStart:Second scenario"));
        assertTrue(events.contains("scenarioEnd:First scenario"));
        assertTrue(events.contains("scenarioEnd:Second scenario"));

        // Verify order: suiteStart comes first, suiteEnd comes last
        assertEquals("suiteStart:1", events.get(0));
        assertEquals("suiteEnd:2", events.get(events.size() - 1));

        // Verify scenarioStart comes before scenarioEnd for each scenario
        int start1 = events.indexOf("scenarioStart:First scenario");
        int end1 = events.indexOf("scenarioEnd:First scenario");
        assertTrue(start1 < end1, "scenarioStart should come before scenarioEnd");
    }

    @Test
    void testListenerViaRunner() throws Exception {
        List<String> events = new ArrayList<>();

        ResultListener listener = new ResultListener() {
            @Override
            public void onScenarioEnd(ScenarioResult result) {
                events.add("scenario:" + result.getScenario().getName() + ":" + (result.isPassed() ? "passed" : "failed"));
            }
        };

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Runner Listener Test
            Scenario: Test scenario
            * def a = 1
            * match a == 1
            """);

        Runner.path(featureFile.toString())
                .workingDir(tempDir)
                .resultListener(listener)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertEquals(1, events.size());
        assertEquals("scenario:Test scenario:passed", events.get(0));
    }

    @Test
    void testMultipleListeners() throws Exception {
        List<String> events1 = new ArrayList<>();
        List<String> events2 = new ArrayList<>();

        ResultListener listener1 = new ResultListener() {
            @Override
            public void onScenarioEnd(ScenarioResult result) {
                events1.add("listener1:" + result.getScenario().getName());
            }
        };

        ResultListener listener2 = new ResultListener() {
            @Override
            public void onScenarioEnd(ScenarioResult result) {
                events2.add("listener2:" + result.getScenario().getName());
            }
        };

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Multiple Listeners
            Scenario: Test
            * def a = 1
            """);

        Suite suite = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .resultListener(listener1)
                .resultListener(listener2)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .buildSuite();
        suite.run();

        // Both listeners should receive events
        assertEquals(1, events1.size());
        assertEquals(1, events2.size());
        assertEquals("listener1:Test", events1.get(0));
        assertEquals("listener2:Test", events2.get(0));
    }

    @Test
    void testListenerReceivesFailedScenarios() throws Exception {
        List<ScenarioResult> results = new ArrayList<>();

        ResultListener listener = new ResultListener() {
            @Override
            public void onScenarioEnd(ScenarioResult result) {
                results.add(result);
            }
        };

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Failure Test
            Scenario: Passing scenario
            * def a = 1
            * match a == 1
            Scenario: Failing scenario
            * def b = 1
            * match b == 999
            """);

        Suite suite = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .resultListener(listener)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .buildSuite();
        suite.run();

        assertEquals(2, results.size());
        assertTrue(results.get(0).isPassed());
        assertTrue(results.get(1).isFailed());
    }

}
