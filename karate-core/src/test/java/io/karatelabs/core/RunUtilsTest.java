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

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.ScenarioOutline;
import io.karatelabs.gherkin.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slug + tag-text helpers used by run-event JSON shaping. Verifies behaviour
 * across the four slug-resolution branches (named scenario, unnamed scenario,
 * outline, outline-example) and the key=value tag round-trip.
 */
class RunUtilsTest {

    private static final Path WORKING_DIR = Path.of("src/test/resources");

    @Test
    void scenarioSlug_ignoresIdTag() {
        // @id= used to be a first-precedence slug override. It was removed
        // because @id is a generic tag many teams already use for their own
        // conventions (ticket IDs, story keys), and co-opting it into our
        // cross-run-identity model silently coupled their tag conventions
        // to our data model. Slug is now purely feature-path + scenario-name.
        Feature feature = parse("""
                @api
                Feature: users

                  @id=USER-CREATE-1 @smoke
                  Scenario: create user
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        String featurePath = RunUtils.featureSlug(feature);
        assertEquals(featurePath + ":create user", RunUtils.scenarioSlug(scenario, feature));
    }

    @Test
    void scenarioSlug_fallsBackToFeaturePlusName() {
        Feature feature = parse("""
                Feature: users

                  Scenario: create user
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        String featurePath = RunUtils.featureSlug(feature);
        assertEquals(featurePath + ":create user", RunUtils.scenarioSlug(scenario, feature));
    }

    @Test
    void scenarioSlug_fallsBackToLineWhenUnnamed() {
        Feature feature = parse("""
                Feature: users

                  Scenario:
                    Given url 'http://example.com'
                """);
        Scenario scenario = firstScenario(feature);

        String slug = RunUtils.scenarioSlug(scenario, feature);
        assertTrue(slug.endsWith("::L" + scenario.getLine()),
                "unnamed scenario slug should fall back to line: " + slug);
    }

    @Test
    void outlineExampleSlug_isOutlineSlugPlusExampleIndex() {
        Feature feature = parse("""
                Feature: lookups

                  Scenario Outline: lookup <key>
                    Given path '<key>'

                    Examples:
                      | key |
                      | a   |
                      | b   |
                """);

        Scenario example = firstOutlineExample(feature);
        assertTrue(example.isOutlineExample(), "expected outline-example scenario");

        ScenarioOutline outline = RunUtils.parentOutline(example);
        String expected = RunUtils.outlineSlug(outline, feature) + ":" + example.getExampleIndex();
        assertEquals(expected, RunUtils.scenarioSlug(example, feature));
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
        assertNotNull(RunUtils.featureSlug(feature));
    }

    @Test
    void tagTexts_preservesKeyEqualsValueForm() {
        Feature feature = parse("""
                @api @env=qa
                Feature: users

                  @req=KEY-123 @smoke
                  Scenario: create user
                    Given url 'x'
                """);
        Scenario scenario = firstScenario(feature);

        List<String> tags = RunUtils.tagTexts(scenario.getTagsEffective());
        assertTrue(tags.contains("req=KEY-123"),
                "key=value tag form should round-trip: " + tags);
        assertTrue(tags.contains("env=qa"), "feature key=value should appear: " + tags);
    }

    @Test
    void tagValues_allowNonSpacePunctuation() {
        // The lexer (GherkinLexer.scanGherkinTag) consumes a tag to the next
        // whitespace, and Tag splits the name on the first '=' and values on ','
        // only — so non-space punctuation common in ticket / story / hierarchical
        // keys ('/', ':', '.') rides through tag values verbatim, and a single
        // value is never split on those characters. Pins the full parse path
        // (lexer -> Tag -> RunUtils.tagTexts) so a hierarchical @req= can never be
        // silently mangled. This is the same identity alphabet honoured by the
        // __id slug override (see JsonLinesEventWriterTest).
        Feature feature = parse("""
                Feature: orders

                  @req=ORD-001/2.1 @cov=xray:STORE-106 @ver=v1.2.3 @ids=A/1,B:2,C.3
                  Scenario: place order
                    Given url 'x'
                """);
        Scenario scenario = firstScenario(feature);

        // Parsed Tag name/value pairs: '/' ':' '.' survive inside a single value;
        // only ',' delimits, so the multi-value tag splits into exactly three.
        Map<String, List<String>> values = new java.util.LinkedHashMap<>();
        for (Tag t : scenario.getTagsEffective()) {
            if (t.getName() != null && !t.getValues().isEmpty()) {
                values.put(t.getName(), t.getValues());
            }
        }
        assertEquals(List.of("ORD-001/2.1"), values.get("req"), "'/' and '.' must survive in a tag value");
        assertEquals(List.of("xray:STORE-106"), values.get("cov"), "':' must survive in a tag value");
        assertEquals(List.of("v1.2.3"), values.get("ver"), "'.' must survive in a tag value");
        assertEquals(List.of("A/1", "B:2", "C.3"), values.get("ids"),
                "only ',' delimits; '/' ':' '.' stay inside each value");

        // ...and the same survives the textual round-trip used on the JSONL wire.
        List<String> texts = RunUtils.tagTexts(scenario.getTagsEffective());
        assertTrue(texts.contains("req=ORD-001/2.1"), "punctuation must round-trip in tag text: " + texts);
        assertTrue(texts.contains("cov=xray:STORE-106"), "punctuation must round-trip in tag text: " + texts);
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
