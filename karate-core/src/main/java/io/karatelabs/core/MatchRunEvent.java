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
import io.karatelabs.match.Match;
import io.karatelabs.match.Result;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assertion-level events (MATCH_EXIT).
 * <p>
 * Enables commercial extensions to capture what a test actually asserted — the expression strings as
 * written, the evaluated values and the match outcome — for reports like assertion evidence or
 * assertion-strength grading.
 * <p>
 * Fired by the {@code match} keyword, the {@code karate.match("...")} JS API (both route through
 * {@code StepExecutor.evalMatchString}) and the {@code assert} keyword. An {@code assert} carries no
 * {@link Match.Type}: its {@link #actualExpr()} is the asserted expression, {@link #actual()} the
 * evaluated boolean, and expected is absent.
 * <p>
 * Usage with pattern matching:
 * <pre>
 * listener = event -> switch (event) {
 *     case MatchRunEvent e when e.type() == MATCH_EXIT -> {
 *         Step step = e.getCurrentStep();
 *         boolean pass = e.result().pass;
 *         // Process assertion data
 *         yield true;
 *     }
 *     default -> true;
 * };
 * </pre>
 * <p>
 * The return value of a listener is ignored — the assertion has already been evaluated.
 */
public record MatchRunEvent(
        RunEventType type,
        Match.Type matchType,   // null for the assert keyword
        String actualExpr,
        String expectedExpr,    // the doc-string text when the expected came from one; null for assert
        Object actual,
        Object expected,
        Result result,
        ScenarioRuntime scenarioRuntime,
        long timeStamp
) implements RunEvent {

    /**
     * Creates a MATCH_EXIT event fired after the match is evaluated. Fires for a failed match too,
     * before the keyword path throws.
     */
    public static MatchRunEvent exit(Match.Type matchType, String actualExpr, String expectedExpr,
                                     Object actual, Object expected, Result result, ScenarioRuntime sr) {
        return new MatchRunEvent(RunEventType.MATCH_EXIT, matchType, actualExpr, expectedExpr,
                actual, expected, result, sr, System.currentTimeMillis());
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
        map.put("matchType", matchType == null ? null : matchType.name());
        map.put("actualExpr", actualExpr);
        map.put("expectedExpr", expectedExpr);
        if (result != null) {
            map.put("pass", result.pass);
            map.put("message", result.message);
        }
        return map;
    }

    /**
     * Convenience method to get the currently executing step.
     * Use this to correlate an assertion to the step that made it.
     */
    public Step getCurrentStep() {
        return scenarioRuntime != null ? scenarioRuntime.getCurrentStep() : null;
    }

}
