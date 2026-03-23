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
 * Feature-level events (FEATURE_ENTER, FEATURE_EXIT).
 */
public record FeatureRunEvent(
        RunEventType type,
        FeatureRuntime source,
        FeatureResult result,  // null for ENTER
        long timeStamp
) implements RunEvent {

    public static FeatureRunEvent enter(FeatureRuntime fr) {
        return new FeatureRunEvent(RunEventType.FEATURE_ENTER, fr, null, System.currentTimeMillis());
    }

    public static FeatureRunEvent exit(FeatureRuntime fr, FeatureResult result) {
        return new FeatureRunEvent(RunEventType.FEATURE_EXIT, fr, result, System.currentTimeMillis());
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
        if (type == RunEventType.FEATURE_EXIT && result != null) {
            return result.toJson();
        }
        // FEATURE_ENTER data (lightweight)
        Map<String, Object> map = new LinkedHashMap<>();
        if (source != null && source.getFeature() != null) {
            var feature = source.getFeature();
            if (feature.getResource() != null) {
                map.put("path", feature.getResource().getRelativePath());
            }
            map.put("name", feature.getName());
            map.put("line", feature.getLine());
        }
        return map;
    }
}
