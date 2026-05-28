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
import io.karatelabs.gherkin.ScenarioOutline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scenario-outline-level event (OUTLINE_ENTER). Fires once per outline section
 * before any of its generated examples run, so receivers can record the outline's
 * identity + metadata as a first-class node and stitch outline-example scenarios
 * to it by {@code outlineSlug}. No OUTLINE_EXIT exists today — outline completion
 * is implied by the last outline-example's SCENARIO_EXIT.
 */
public record OutlineRunEvent(
        FeatureRuntime source,
        ScenarioOutline outline,
        long timeStamp
) implements RunEvent {

    public static OutlineRunEvent enter(FeatureRuntime fr, ScenarioOutline outline) {
        return new OutlineRunEvent(fr, outline, System.currentTimeMillis());
    }

    @Override
    public RunEventType getType() {
        return RunEventType.OUTLINE_ENTER;
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        Feature feature = source != null ? source.getFeature() : null;
        if (feature != null && feature.getResource() != null) {
            map.put("feature", feature.getResource().getRelativePath());
        }
        if (outline != null) {
            map.put("slug", RunUtils.outlineSlug(outline, feature));
            map.put("name", outline.getName());
            map.put("description", outline.getDescription());
            map.put("line", outline.getLine());
            map.put("numExamples", outline.getNumScenarios());
            // Outline-level tags include the feature's tags merged in — outlines
            // don't have a getTagsEffective() like scenarios do.
            map.put("tags", RunUtils.mergedTagTexts(
                    feature != null ? feature.getTags() : null,
                    outline.getTags()));
        }
        map.put("callDepth", source != null ? source.getCallDepth() : 0);
        return map;
    }
}
