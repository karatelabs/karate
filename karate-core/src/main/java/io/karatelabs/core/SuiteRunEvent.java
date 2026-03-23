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
 * Suite-level events (SUITE_ENTER, SUITE_EXIT).
 */
public record SuiteRunEvent(
        RunEventType type,
        Suite source,
        SuiteResult result,  // null for ENTER
        long timeStamp
) implements RunEvent {

    public static SuiteRunEvent enter(Suite suite) {
        return new SuiteRunEvent(RunEventType.SUITE_ENTER, suite, null, System.currentTimeMillis());
    }

    public static SuiteRunEvent exit(Suite suite, SuiteResult result) {
        return new SuiteRunEvent(RunEventType.SUITE_EXIT, suite, result, System.currentTimeMillis());
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
        if (type == RunEventType.SUITE_EXIT && result != null) {
            return result.toJson();
        }
        // SUITE_ENTER data
        if (source != null) {
            map.put("env", source.env);
            map.put("threads", source.threadCount);
        }
        return map;
    }
}
