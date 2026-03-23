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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scenario-level events (SCENARIO_ENTER, SCENARIO_EXIT).
 */
public record ScenarioRunEvent(
        RunEventType type,
        ScenarioRuntime source,
        ScenarioResult result,  // null for ENTER
        long timeStamp
) implements RunEvent {

    public static ScenarioRunEvent enter(ScenarioRuntime sr) {
        return new ScenarioRunEvent(RunEventType.SCENARIO_ENTER, sr, null, System.currentTimeMillis());
    }

    public static ScenarioRunEvent exit(ScenarioRuntime sr, ScenarioResult result) {
        return new ScenarioRunEvent(RunEventType.SCENARIO_EXIT, sr, result, System.currentTimeMillis());
    }

    @Override
    public RunEventType getType() {
        return type;
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (source != null && source.getScenario() != null) {
            var scenario = source.getScenario();
            var fr = source.getFeatureRuntime();
            if (fr != null && fr.getFeature() != null && fr.getFeature().getResource() != null) {
                map.put("feature", fr.getFeature().getResource().getRelativePath());
            }
            map.put("name", scenario.getName());
            map.put("line", scenario.getLine());
            map.put("refId", scenario.getRefId());
        }
        if (type == RunEventType.SCENARIO_EXIT && result != null) {
            map.put("passed", !result.isFailed());
            map.put("durationMillis", result.getDurationMillis());
        }
        return map;
    }
}
