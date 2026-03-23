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
 * Error event (ERROR).
 */
public record ErrorRunEvent(
        ScenarioRuntime scenarioRuntime,
        Throwable error,
        long timeStamp
) implements RunEvent {

    public static ErrorRunEvent of(Throwable error, ScenarioRuntime sr) {
        return new ErrorRunEvent(sr, error, System.currentTimeMillis());
    }

    @Override
    public RunEventType getType() {
        return RunEventType.ERROR;
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (scenarioRuntime != null) {
            var fr = scenarioRuntime.getFeatureRuntime();
            if (fr != null && fr.getFeature() != null && fr.getFeature().getResource() != null) {
                map.put("feature", fr.getFeature().getResource().getRelativePath());
            }
            if (scenarioRuntime.getScenario() != null) {
                map.put("scenario", scenarioRuntime.getScenario().getName());
            }
        }
        if (error != null) {
            map.put("message", error.getMessage());
            map.put("type", error.getClass().getSimpleName());
        }
        return map;
    }
}
