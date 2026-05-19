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

import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.ScenarioOutline;
import io.karatelabs.gherkin.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for emitting "coverage atoms" — CoverageItem-shaped records — alongside
 * RunEvent JSONL output. Atoms describe the taxonomy of what ran (feature →
 * scenario / outline → outline-example) and travel with SCENARIO_EXIT events
 * so the receiver can build a per-run requirements tree without needing to
 * read the source.
 *
 * <p>Slug resolution order (cross-run stable identity):
 * <ol>
 *   <li>Feature + scenario name → {@code <feature-path>:<scenario-name>}.</li>
 *   <li>Outline-example → {@code <outline-slug>:<exampleIndex>} when the
 *       scenario is a generated outline row (token-substituted name would
 *       work but breaks if Examples data changes; the index is more stable).</li>
 *   <li>Unnamed scenarios → {@code <feature-path>::L<line>}.</li>
 * </ol>
 *
 * <p>Rename / refactor stability is handled by a future slug-aliasing
 * mechanism on the receiver — not by a tag opt-in on the source. An earlier
 * design used an {@code @id=<value>} tag as a first-precedence override; that
 * was removed because {@code @id=} is a generic tag many teams already use
 * for their own conventions (ticket IDs, story keys, internal nomenclature),
 * and silently co-opting it into our cross-run-identity model coupled their
 * tag conventions to our data model in a way they never asked for.
 */
public final class CoverageAtom {

    private CoverageAtom() {}

    public static String featureSlug(Feature feature) {
        if (feature == null || feature.getResource() == null) {
            return "(unknown)";
        }
        return feature.getResource().getRelativePath();
    }

    public static String scenarioSlug(Scenario scenario, Feature feature) {
        if (scenario.isOutlineExample()) {
            ScenarioOutline outline = parentOutline(scenario);
            return outlineSlug(outline, feature) + ":" + scenario.getExampleIndex();
        }
        String featurePath = featureSlug(feature);
        String name = scenario.getName();
        if (name != null && !name.isEmpty()) {
            return featurePath + ":" + name;
        }
        return featurePath + "::L" + scenario.getLine();
    }

    public static String outlineSlug(ScenarioOutline outline, Feature feature) {
        if (outline == null) {
            return featureSlug(feature) + "::outline";
        }
        String name = outline.getName();
        String featurePath = featureSlug(feature);
        if (name != null && !name.isEmpty()) {
            return featurePath + ":" + name;
        }
        return featurePath + "::L" + outline.getLine();
    }

    /** Returns the parent ScenarioOutline if the scenario was generated from one, else null. */
    public static ScenarioOutline parentOutline(Scenario scenario) {
        FeatureSection section = scenario.getSection();
        return section == null ? null : section.getScenarioOutline();
    }

    /**
     * Compute the ancestor chain (oldest first) and emit it as coverage atoms.
     * For a plain scenario this returns 2 atoms (feature + scenario). For an
     * outline-example it returns 3 (feature + outline + outline-example).
     *
     * @param scenario the executed scenario
     * @param feature the parent feature
     * @param reportDisabled when true, drop description fields (mirrors @report=false redaction
     *                       in {@link ScenarioRunEvent})
     */
    public static List<Map<String, Object>> chain(Scenario scenario, Feature feature, boolean reportDisabled) {
        List<Map<String, Object>> out = new ArrayList<>(3);
        out.add(featureAtom(feature, reportDisabled));
        ScenarioOutline outline = parentOutline(scenario);
        if (scenario.isOutlineExample() && outline != null) {
            out.add(outlineAtom(outline, feature, reportDisabled));
            out.add(outlineExampleAtom(scenario, outline, feature, reportDisabled));
        } else {
            out.add(scenarioAtom(scenario, feature, reportDisabled));
        }
        return out;
    }

    static Map<String, Object> featureAtom(Feature feature, boolean reportDisabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", featureSlug(feature));
        m.put("kind", "feature");
        m.put("name", feature != null ? feature.getName() : null);
        m.put("description", reportDisabled ? null : (feature != null ? feature.getDescription() : null));
        m.put("tags", tagTexts(feature != null ? feature.getTags() : null));
        m.put("parentId", null);
        Map<String, Object> meta = new LinkedHashMap<>();
        if (feature != null) meta.put("line", feature.getLine());
        m.put("metadata", meta);
        return m;
    }

    static Map<String, Object> scenarioAtom(Scenario scenario, Feature feature, boolean reportDisabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", scenarioSlug(scenario, feature));
        m.put("kind", "scenario");
        m.put("name", scenario.getName());
        m.put("description", reportDisabled ? null : scenario.getDescription());
        m.put("tags", tagTexts(scenario.getTagsEffective()));
        m.put("parentId", featureSlug(feature));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("line", scenario.getLine());
        m.put("metadata", meta);
        return m;
    }

    static Map<String, Object> outlineAtom(ScenarioOutline outline, Feature feature, boolean reportDisabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", outlineSlug(outline, feature));
        m.put("kind", "outline");
        m.put("name", outline.getName());
        m.put("description", reportDisabled ? null : outline.getDescription());
        // Outline tags include feature tags (manually merged — no getTagsEffective on outline)
        m.put("tags", mergedTagTexts(feature != null ? feature.getTags() : null, outline.getTags()));
        m.put("parentId", featureSlug(feature));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("line", outline.getLine());
        meta.put("numExamples", outline.getNumScenarios());
        m.put("metadata", meta);
        return m;
    }

    static Map<String, Object> outlineExampleAtom(Scenario example, ScenarioOutline outline,
                                                  Feature feature, boolean reportDisabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", scenarioSlug(example, feature));
        m.put("kind", "outline-example");
        m.put("name", example.getName());
        m.put("description", reportDisabled ? null : example.getDescription());
        m.put("tags", tagTexts(example.getTagsEffective()));
        m.put("parentId", outlineSlug(outline, feature));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("line", example.getLine());
        meta.put("exampleIndex", example.getExampleIndex());
        m.put("metadata", meta);
        return m;
    }

    public static List<String> tagTexts(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(tags.size());
        for (Tag t : tags) {
            if (t != null && t.getText() != null && !t.getText().isEmpty()) {
                out.add(t.getText());
            }
        }
        return out;
    }

    private static List<String> mergedTagTexts(List<Tag> a, List<Tag> b) {
        if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) return List.of();
        List<String> out = new ArrayList<>();
        if (a != null) for (Tag t : a) if (t != null && t.getText() != null) out.add(t.getText());
        if (b != null) for (Tag t : b) if (t != null && t.getText() != null) out.add(t.getText());
        return out;
    }
}
