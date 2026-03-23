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

import io.karatelabs.common.Json;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CucumberJsonWriterTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
    }

    @Test
    void testCucumberJsonGeneration() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Cucumber JSON Test

            Scenario: Passing scenario
            * def a = 1
            * match a == 1

            Scenario: Another passing
            * def b = 2
            * match b == 2
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputCucumberJson(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        // Verify per-feature JSON file was created (named after packageQualifiedName)
        Path jsonPath = reportDir.resolve("cucumber-json/test.json");
        assertTrue(Files.exists(jsonPath), "Cucumber JSON file should exist");

        String jsonStr = Files.readString(jsonPath);

        // Parse and verify structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) (List<?>) Json.of(jsonStr).asList();
        assertEquals(1, features.size());

        Map<String, Object> featureMap = features.get(0);
        assertEquals("cucumber-json-test", featureMap.get("id"));
        assertEquals("Cucumber JSON Test", featureMap.get("name"));
        assertEquals("Feature", featureMap.get("keyword"));
        assertTrue(featureMap.get("uri").toString().endsWith("test.feature"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) featureMap.get("elements");
        assertEquals(2, elements.size());

        // Check first scenario
        Map<String, Object> scenario1 = elements.get(0);
        assertEquals("passing-scenario", scenario1.get("id").toString().split(";")[1]);
        assertEquals("Passing scenario", scenario1.get("name"));
        assertEquals("Scenario", scenario1.get("keyword"));
        assertEquals("scenario", scenario1.get("type"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps1 = (List<Map<String, Object>>) scenario1.get("steps");
        assertEquals(2, steps1.size());

        // Verify step structure
        Map<String, Object> step1 = steps1.get(0);
        assertEquals("* ", step1.get("keyword"));
        // Step name includes Karate keyword and text (e.g., "def a = 1")
        assertEquals("def a = 1", step1.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result1 = (Map<String, Object>) step1.get("result");
        assertEquals("passed", result1.get("status"));
        assertTrue(result1.containsKey("duration"));
    }

    @Test
    void testCucumberJsonWithFailures() throws Exception {
        Path feature = tempDir.resolve("failing.feature");
        Files.writeString(feature, """
            Feature: Failing Test

            Scenario: Passing scenario
            * def a = 1
            * match a == 1

            Scenario: Failing scenario
            * def b = 2
            * match b == 999
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputCucumberJson(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());

        Path jsonPath = reportDir.resolve("cucumber-json/failing.json");
        assertTrue(Files.exists(jsonPath));

        String jsonStr = Files.readString(jsonPath);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) (List<?>) Json.of(jsonStr).asList();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) features.get(0).get("elements");
        assertEquals(2, elements.size());

        // Verify failing scenario has error
        Map<String, Object> failingScenario = elements.get(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) failingScenario.get("steps");

        // Find the failed step
        boolean foundFailedStep = false;
        for (Map<String, Object> step : steps) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepResult = (Map<String, Object>) step.get("result");
            if ("failed".equals(stepResult.get("status"))) {
                foundFailedStep = true;
                assertTrue(stepResult.containsKey("error_message"));
                break;
            }
        }
        assertTrue(foundFailedStep, "Should have a failed step");
    }

    @Test
    void testCucumberJsonWithTags() throws Exception {
        Path feature = tempDir.resolve("tagged.feature");
        Files.writeString(feature, """
            @feature-tag
            Feature: Tagged Feature

            @scenario-tag @smoke
            Scenario: Tagged scenario
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputCucumberJson(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonPath = reportDir.resolve("cucumber-json/tagged.json");
        String jsonStr = Files.readString(jsonPath);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) (List<?>) Json.of(jsonStr).asList();

        // Check feature tags
        Map<String, Object> featureMap = features.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> featureTags = (List<Map<String, Object>>) featureMap.get("tags");
        assertNotNull(featureTags);
        assertEquals(1, featureTags.size());
        assertEquals("@feature-tag", featureTags.get(0).get("name"));

        // Check scenario tags
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) featureMap.get("elements");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarioTags = (List<Map<String, Object>>) elements.get(0).get("tags");
        assertNotNull(scenarioTags);
        assertEquals(2, scenarioTags.size());
    }

    @Test
    void testCucumberJsonNotGeneratedByDefault() throws Exception {
        Path feature = tempDir.resolve("nocucumber.feature");
        Files.writeString(feature, """
            Feature: No Cucumber JSON
            Scenario: Test
            * def a = 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputCucumberJson(false)  // explicitly disabled
                .outputConsoleSummary(false)
                .parallel(1);

        // Cucumber JSON should NOT exist (check for the packageQualifiedName-based file)
        // Note: Karate JSON writes {featureName}.json, Cucumber JSON writes {packageQualifiedName}.json
        // On case-insensitive filesystems, we need distinct names to avoid collision
        assertFalse(Files.exists(reportDir.resolve("cucumber-json/nocucumber.json")),
                "Cucumber JSON file should not exist when outputCucumberJson is false");
    }

    @Test
    void testCucumberJsonWithDocString() throws Exception {
        Path feature = tempDir.resolve("docstring.feature");
        Files.writeString(feature, """
            Feature: DocString Test

            Scenario: With doc string
            * def payload =
            \"\"\"
            {
              "name": "John",
              "age": 30
            }
            \"\"\"
            * match payload.name == 'John'
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputCucumberJson(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonPath = reportDir.resolve("cucumber-json/docstring.json");
        String jsonStr = Files.readString(jsonPath);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) (List<?>) Json.of(jsonStr).asList();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) features.get(0).get("elements");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) elements.get(0).get("steps");

        // Find the step with doc_string
        boolean foundDocString = false;
        for (Map<String, Object> step : steps) {
            if (step.containsKey("doc_string")) {
                foundDocString = true;
                @SuppressWarnings("unchecked")
                Map<String, Object> docString = (Map<String, Object>) step.get("doc_string");
                assertTrue(docString.get("value").toString().contains("John"));
                break;
            }
        }
        assertTrue(foundDocString, "Should have a step with doc_string");
    }

    @Test
    void testCucumberJsonWithDataTable() throws Exception {
        Path feature = tempDir.resolve("table.feature");
        Files.writeString(feature, """
            Feature: Table Test

            Scenario: With table
            * def users =
              | name  | age |
              | John  | 30  |
              | Jane  | 25  |
            * match users[0].name == 'John'
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputCucumberJson(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path jsonPath = reportDir.resolve("cucumber-json/table.json");
        String jsonStr = Files.readString(jsonPath);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) (List<?>) Json.of(jsonStr).asList();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) features.get(0).get("elements");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) elements.get(0).get("steps");

        // Find the step with rows (table)
        boolean foundTable = false;
        for (Map<String, Object> step : steps) {
            if (step.containsKey("rows")) {
                foundTable = true;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) step.get("rows");
                assertTrue(rows.size() >= 2, "Should have at least 2 rows (header + data)");
                break;
            }
        }
        assertTrue(foundTable, "Should have a step with table rows");
    }

}
