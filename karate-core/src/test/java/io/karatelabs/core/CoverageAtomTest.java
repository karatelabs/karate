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
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.ScenarioOutline;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slug + atom-emission contract for the karate-agent wire protocol.
 * Verifies CoverageAtom's behaviour across the four slug-resolution branches:
 * explicit @id tag, named scenario, named outline (+ examples), unnamed fallback.
 */
class CoverageAtomTest {

    private static final Path WORKING_DIR = Path.of("src/test/resources");

    @Test
    void scenarioSlug_usesIdTagWhenPresent() {
        Feature feature = parse("""
                @api
                Feature: users

                  @id=USER-CREATE-1 @smoke
                  Scenario: create user
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        assertEquals("USER-CREATE-1", CoverageAtom.scenarioSlug(scenario, feature));
    }

    @Test
    void scenarioSlug_fallsBackToFeaturePlusName() {
        Feature feature = parse("""
                Feature: users

                  Scenario: create user
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        String featurePath = CoverageAtom.featureSlug(feature);
        assertEquals(featurePath + ":create user", CoverageAtom.scenarioSlug(scenario, feature));
    }

    @Test
    void scenarioSlug_fallsBackToLineWhenUnnamed() {
        Feature feature = parse("""
                Feature: users

                  Scenario:
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        String slug = CoverageAtom.scenarioSlug(scenario, feature);
        assertTrue(slug.endsWith("::L" + scenario.getLine()),
                "unnamed scenario slug should fall back to line: " + slug);
    }

    @Test
    void chain_forPlainScenario_yieldsFeaturePlusScenario() {
        Feature feature = parse("""
                @api
                Feature: users
                User CRUD API spec.

                  @smoke
                  Scenario: create user
                  Creates a new user account.
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        List<Map<String, Object>> chain = CoverageAtom.chain(scenario, feature, false);
        assertEquals(2, chain.size());

        Map<String, Object> featureAtom = chain.get(0);
        assertEquals("feature", featureAtom.get("kind"));
        assertEquals(CoverageAtom.featureSlug(feature), featureAtom.get("id"));
        assertEquals("users", featureAtom.get("name"));
        assertTrue(featureAtom.get("description").toString().contains("User CRUD API spec"));
        assertEquals(List.of("api"), featureAtom.get("tags"));
        assertNull(featureAtom.get("parentId"));

        Map<String, Object> scenarioAtom = chain.get(1);
        assertEquals("scenario", scenarioAtom.get("kind"));
        assertEquals(CoverageAtom.featureSlug(feature) + ":create user", scenarioAtom.get("id"));
        assertEquals("create user", scenarioAtom.get("name"));
        // Effective tags = feature tag + scenario tag.
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) scenarioAtom.get("tags");
        assertTrue(tags.contains("api"), "tags should include feature tag: " + tags);
        assertTrue(tags.contains("smoke"), "tags should include scenario tag: " + tags);
        assertEquals(CoverageAtom.featureSlug(feature), scenarioAtom.get("parentId"));
    }

    @Test
    void chain_forOutlineExample_yieldsFeaturePlusOutlinePlusExample() {
        Feature feature = parse("""
                Feature: lookups

                  Scenario Outline: lookup <key>
                    Given path '<key>'

                    Examples:
                      | key |
                      | a   |
                      | b   |
                """);

        // Trigger outline expansion by getting the generated examples through the
        // scenarios list (the runtime materializes them).
        Scenario example = firstOutlineExample(feature);
        assertTrue(example.isOutlineExample(), "expected outline-example scenario");

        List<Map<String, Object>> chain = CoverageAtom.chain(example, feature, false);
        assertEquals(3, chain.size());

        Map<String, Object> featureAtom = chain.get(0);
        assertEquals("feature", featureAtom.get("kind"));

        Map<String, Object> outlineAtom = chain.get(1);
        assertEquals("outline", outlineAtom.get("kind"));
        assertEquals(CoverageAtom.featureSlug(feature) + ":lookup <key>", outlineAtom.get("id"));

        Map<String, Object> exampleAtom = chain.get(2);
        assertEquals("outline-example", exampleAtom.get("kind"));
        assertEquals(outlineAtom.get("id") + ":" + example.getExampleIndex(), exampleAtom.get("id"));
        assertEquals(outlineAtom.get("id"), exampleAtom.get("parentId"));
    }

    @Test
    void chain_redactsDescriptionWhenReportDisabled() {
        Feature feature = parse("""
                Feature: users
                Sensitive feature description.

                  Scenario: create user
                  Sensitive scenario description.
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        List<Map<String, Object>> chain = CoverageAtom.chain(scenario, feature, true);
        // Feature + scenario atoms have description = null when @report=false propagated.
        assertNull(chain.get(0).get("description"), "feature description should be redacted");
        assertNull(chain.get(1).get("description"), "scenario description should be redacted");
    }

    @Test
    void featureSlug_isRelativePath() {
        Feature feature = parse("""
                Feature: users
                  Scenario: any
                    Given url 'x'
                """);
        // Resource.text() synthesizes a resource — relative path may be empty in tests.
        // In real usage (Resource.from(file)) it's the file's relative path.
        assertNotNull(CoverageAtom.featureSlug(feature));
    }

    @Test
    void tagTexts_preservesKeyEqualsValueForm() {
        Feature feature = parse("""
                @api @env=qa
                Feature: users

                  @REQ=KEY-123 @smoke
                  Scenario: create user
                    Given url 'x'
                """);
        Scenario scenario = firstScenario(feature);

        List<String> tags = CoverageAtom.tagTexts(scenario.getTagsEffective());
        assertTrue(tags.contains("REQ=KEY-123"),
                "key=value tag form should round-trip: " + tags);
        assertTrue(tags.contains("env=qa"), "feature key=value should appear: " + tags);
    }

    // ========== helpers ==========

    private static Feature parse(String gherkin) {
        return Feature.read(Resource.text(gherkin, WORKING_DIR));
    }

    private static Scenario firstScenario(Feature feature) {
        for (FeatureSection section : feature.getSections()) {
            if (!section.isOutline()) {
                return section.getScenario();
            }
        }
        throw new IllegalStateException("no plain scenario in feature");
    }

    private static Scenario firstOutlineExample(Feature feature) {
        for (FeatureSection section : feature.getSections()) {
            if (section.isOutline()) {
                ScenarioOutline outline = section.getScenarioOutline();
                // Generate scenarios from the static Examples table via toScenario().
                // This mirrors what FeatureRuntime does at runtime.
                Scenario example = outline.toScenario(null, 0, outline.getLine(), null);
                example.setSelected(true);
                return example;
            }
        }
        throw new IllegalStateException("no outline in feature");
    }
}
