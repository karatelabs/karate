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

import io.karatelabs.gherkin.Step;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One chunk of a step's captured output (STEP_LOG), fired as it is written and so before the step's
 * STEP_EXIT, which still carries the whole log. {@code step} is null for output produced outside a step
 * (karate-config.js, a hook). Fired only under the IDE event profile ({@code Runner.Builder.ideEvents}).
 */
public record StepLogRunEvent(
        ScenarioRuntime scenarioRuntime,
        Step step,
        String text,
        long timeStamp,
        long sequence
) implements RunEvent {

    public static StepLogRunEvent of(ScenarioRuntime sr, Step step, String text) {
        return new StepLogRunEvent(sr, step, text, System.currentTimeMillis(), 0);
    }

    @Override
    public RunEventType getType() {
        return RunEventType.STEP_LOG;
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public long getSequence() {
        return sequence;
    }

    @Override
    public StepLogRunEvent withSequence(long sequence) {
        return new StepLogRunEvent(scenarioRuntime, step, text, timeStamp, sequence);
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (step != null) {
            map.put("line", step.getLine());
        }
        map.put("text", text);
        return map;
    }

}
