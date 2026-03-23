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

import io.karatelabs.gherkin.Step;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step-level events (STEP_ENTER, STEP_EXIT).
 */
public record StepRunEvent(
        RunEventType type,
        Step step,
        ScenarioRuntime scenarioRuntime,
        StepResult result,  // null for ENTER
        long timeStamp
) implements RunEvent {

    public static StepRunEvent enter(Step step, ScenarioRuntime sr) {
        return new StepRunEvent(RunEventType.STEP_ENTER, step, sr, null, System.currentTimeMillis());
    }

    public static StepRunEvent exit(StepResult result, ScenarioRuntime sr) {
        Step step = result != null ? result.getStep() : null;
        return new StepRunEvent(RunEventType.STEP_EXIT, step, sr, result, System.currentTimeMillis());
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
        if (step != null) {
            map.put("line", step.getLine());
            map.put("prefix", step.getPrefix());
            map.put("text", step.getText());
        }
        if (type == RunEventType.STEP_EXIT && result != null) {
            map.put("status", result.getStatus().name().toLowerCase());
            map.put("durationMillis", result.getDurationMillis());
        }
        return map;
    }
}
