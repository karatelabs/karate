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
import java.util.List;
import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for karate.info, karate.scenario, karate.feature, karate.tags,
 * karate.tagValues, and karate.scenarioOutline properties.
 */
class StepInfoTest {

    @TempDir
    Path tempDir;

    // ========== karate.info ==========

    @Test
    void testKarateInfoScenarioName() {
        ScenarioRuntime sr = runFeature("""
            Feature: Info Test Feature
            Scenario: my test scenario
            * def info = karate.info
            * match info.scenarioName == 'my test scenario'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateInfoScenarioDescription() {
        ScenarioRuntime sr = runFeature("""
            Feature: Info Test
            Scenario: test with desc
            This is a description
            * def info = karate.info
            * match info.scenarioDescription == 'This is a description'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateInfoErrorMessageIsNull() {
        ScenarioRuntime sr = runFeature("""
            Feature: Info Test
            Scenario: passing test
            * def info = karate.info
            * match info.errorMessage == '#notpresent'
            """);
        assertPassed(sr);
    }

    // ========== karate.scenario ==========

    @Test
    void testKarateScenarioName() {
        ScenarioRuntime sr = runFeature("""
            Feature: Scenario Test
            Scenario: my test scenario
            * match karate.scenario.name == 'my test scenario'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateScenarioDescription() {
        ScenarioRuntime sr = runFeature("""
            Feature: Scenario Test
            Scenario: described scenario
            This is the description
            * match karate.scenario.description == 'This is the description'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateScenarioSectionIndex() {
        ScenarioRuntime sr = runFeature("""
            Feature: Scenario Test
            Scenario: first scenario
            * match karate.scenario.sectionIndex == 0
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateScenarioExampleIndex() {
        ScenarioRuntime sr = runFeature("""
            Feature: Scenario Test
            Scenario: not an outline
            * match karate.scenario.exampleIndex == -1
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateScenarioLine() {
        ScenarioRuntime sr = runFeature("""
            Feature: Scenario Test
            Scenario: test line
            * def line = karate.scenario.line
            * match line == 2
            """);
        assertPassed(sr);
    }

    // ========== karate.feature ==========

    @Test
    void testKarateFeatureName() {
        ScenarioRuntime sr = runFeature("""
            Feature: My Feature Name
            Scenario: test
            * match karate.feature.name == 'My Feature Name'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateFeatureDescription() {
        ScenarioRuntime sr = runFeature("""
            Feature: Feature With Desc
            This is the feature description
            Scenario: test
            * match karate.feature.description == 'This is the feature description'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateFeaturePrefixedPath() {
        ScenarioRuntime sr = runFeature("""
            Feature: Feature Test
            Scenario: test
            * def path = karate.feature.prefixedPath
            * match path == '#string'
            """);
        assertPassed(sr);
    }

    // ========== karate.tags ==========

    @Test
    void testKarateTagsReturnsListOfStrings() {
        ScenarioRuntime sr = runFeature("""
            @smoke
            Feature: Tags Test
            @important
            Scenario: tagged scenario
            * def tags = karate.tags
            * match tags == '#array'
            * match tags contains 'smoke'
            * match tags contains 'important'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagsIncludesFeatureAndScenarioTags() {
        ScenarioRuntime sr = runFeature("""
            @featureTag
            Feature: Tags Test
            @scenarioTag
            Scenario: test
            * def tags = karate.tags
            * match tags contains 'featureTag'
            * match tags contains 'scenarioTag'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagsWithValues() {
        ScenarioRuntime sr = runFeature("""
            @env=dev,staging
            Feature: Tags Test
            Scenario: test
            * def tags = karate.tags
            * match tags contains 'env=dev,staging'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagsEmptyWhenNoTags() {
        ScenarioRuntime sr = runFeature("""
            Feature: No Tags
            Scenario: test
            * def tags = karate.tags
            * match tags == []
            """);
        assertPassed(sr);
    }

    // ========== karate.tagValues ==========

    @Test
    void testKarateTagValuesReturnsMap() {
        ScenarioRuntime sr = runFeature("""
            @env=dev,staging
            Feature: TagValues Test
            Scenario: test
            * def tv = karate.tagValues
            * match tv == '#object'
            * match tv.env == ['dev', 'staging']
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagValuesEmptyListForTagWithoutValue() {
        ScenarioRuntime sr = runFeature("""
            @smoke
            Feature: TagValues Test
            Scenario: test
            * def tv = karate.tagValues
            * match tv.smoke == []
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagValuesMultipleTags() {
        ScenarioRuntime sr = runFeature("""
            @env=dev
            @priority=high,medium
            Feature: TagValues Test
            Scenario: test
            * def tv = karate.tagValues
            * match tv.env == ['dev']
            * match tv.priority == ['high', 'medium']
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagValuesMergesFeatureAndScenarioTags() {
        ScenarioRuntime sr = runFeature("""
            @env=prod
            Feature: TagValues Test
            @priority=high
            Scenario: test
            * def tv = karate.tagValues
            * match tv.env == ['prod']
            * match tv.priority == ['high']
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateTagValuesScenarioOverridesFeature() {
        ScenarioRuntime sr = runFeature("""
            @env=prod
            Feature: TagValues Test
            @env=dev
            Scenario: test
            * def tv = karate.tagValues
            * match tv.env == ['dev']
            """);
        assertPassed(sr);
    }

    // ========== karate.scenarioOutline ==========

    @Test
    void testKarateScenarioOutlineNullForRegularScenario() {
        ScenarioRuntime sr = runFeature("""
            Feature: Outline Test
            Scenario: regular scenario
            * def outline = karate.scenarioOutline
            * match outline == null
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateScenarioOutlineHasProperties() throws Exception {
        Path feature = tempDir.resolve("outline.feature");
        Files.writeString(feature, """
            Feature: Outline Test

            Scenario Outline: outline test
            * def outline = karate.scenarioOutline
            * match outline != null
            * match outline.name == 'outline test'
            * match outline.sectionIndex == 0
            * match outline.exampleTableCount == 1

            Examples:
            | name   |
            | first  |
            | second |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testKarateScenarioExampleDataInOutline() throws Exception {
        Path feature = tempDir.resolve("outline-data.feature");
        Files.writeString(feature, """
            Feature: Outline Data Test

            Scenario Outline: test example
            * def idx = karate.scenario.exampleIndex
            * assert idx >= 0
            * match karate.scenario.exampleData.a == a

            Examples:
            | a   |
            | foo |
            | bar |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioPassedCount());
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

    // ========== karate.os ==========

    @Test
    void testKarateOsType() {
        ScenarioRuntime sr = run("""
            * def os = karate.os
            * match os.type == '#string'
            * match os.name == '#string'
            """);
        assertPassed(sr);
        @SuppressWarnings("unchecked")
        Map<String, Object> os = (Map<String, Object>) get(sr, "os");
        String type = (String) os.get("type");
        assertTrue(type.equals("windows") || type.equals("macosx") || type.equals("linux") || type.equals("unknown"));
    }

    // ========== karate.env ==========

    @Test
    void testKarateEnvNullByDefault() {
        ScenarioRuntime sr = run("""
            * match karate.env == '#notpresent'
            """);
        assertPassed(sr);
    }

    // ========== Combined Tests ==========

    @Test
    void testAllInfoPropertiesAccessible() {
        ScenarioRuntime sr = runFeature("""
            @smoke @env=dev
            Feature: Full Test
            Feature description here
            @important
            Scenario: comprehensive test
            Scenario description
            * def info = karate.info
            * def scenario = karate.scenario
            * def feature = karate.feature
            * def tags = karate.tags
            * def tagValues = karate.tagValues
            * def os = karate.os

            # info checks
            * match info.scenarioName == 'comprehensive test'
            * match info.scenarioDescription == 'Scenario description'

            # scenario checks
            * match scenario.name == 'comprehensive test'
            * match scenario.sectionIndex == 0
            * match scenario.exampleIndex == -1

            # feature checks
            * match feature.name == 'Full Test'
            * match feature.description == 'Feature description here'

            # tags checks
            * match tags contains 'smoke'
            * match tags contains 'env=dev'
            * match tags contains 'important'

            # tagValues checks
            * match tagValues.smoke == []
            * match tagValues.env == ['dev']
            * match tagValues.important == []

            # os checks
            * match os.type == '#string'
            """);
        assertPassed(sr);
    }

}
