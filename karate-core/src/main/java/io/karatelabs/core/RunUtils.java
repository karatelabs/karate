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

import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.ScenarioOutline;
import io.karatelabs.gherkin.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for run-event JSON shaping — cross-run-stable slug computation
 * and tag-text extraction. Used by {@link FeatureRunEvent}, {@link ScenarioRunEvent},
 * and {@link OutlineRunEvent} to keep identity + tag fields consistent across the
 * JSONL stream so receivers can stitch by slug without re-reading source files.
 *
 * <p>Slug resolution:</p>
 * <ol>
 *   <li>Feature → its relative path.</li>
 *   <li>Named scenario → {@code <feature-path>:<scenario-name>}.</li>
 *   <li>Unnamed scenario → {@code <feature-path>::L<line>}.</li>
 *   <li>Outline → name-based against feature path (line fallback).</li>
 *   <li>Outline-example → {@code <outline-slug>:<exampleIndex>}.</li>
 * </ol>
 *
 * <p>Rename / refactor stability is handled by a future slug-aliasing mechanism
 * on the receiver — not by a tag opt-in on the source. An earlier design used an
 * {@code @id=<value>} tag as a first-precedence override; that was removed because
 * {@code @id=} is a generic tag many teams already use for their own conventions
 * (ticket IDs, story keys, internal nomenclature), and silently co-opting it into
 * our cross-run-identity model coupled their tag conventions to our data model in
 * a way they never asked for.</p>
 */
public final class RunUtils {

    private RunUtils() {}

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

    /**
     * Concatenate text from two Tag lists (feature-level + outline-level for
     * outlines that don't expose {@code getTagsEffective}).
     */
    public static List<String> mergedTagTexts(List<Tag> a, List<Tag> b) {
        if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) return List.of();
        List<String> out = new ArrayList<>();
        if (a != null) for (Tag t : a) if (t != null && t.getText() != null) out.add(t.getText());
        if (b != null) for (Tag t : b) if (t != null && t.getText() != null) out.add(t.getText());
        return out;
    }
}
