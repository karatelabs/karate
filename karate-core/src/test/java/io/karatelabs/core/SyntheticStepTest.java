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

import io.karatelabs.output.LogContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic steps appended at runtime via {@link LogContext#step} — an out-of-band producer (running
 * inside an {@code * eval}) contributes a real, named step row to the scenario's result, surviving into
 * the report / JSONL, with a status that is reported and, when failed, fails the scenario without a throw.
 */
class SyntheticStepTest {

    /** The display text of a step result (real or synthetic) as it lands in the report JSON. */
    private static String text(StepResult sr) {
        return (String) ((Map<?, ?>) sr.toJson().get("step")).get("text");
    }

    /** The synthetic (and non-hook fake) steps in a scenario's result, in order. */
    private static List<StepResult> synthetic(ScenarioResult result) {
        return result.getStepResults().stream()
                .filter(s -> s.getStep() == null && !s.isHook())
                .toList();
    }

    // ---- the LogContext buffer, in isolation -------------------------------------------------------

    @Test
    void bufferCollectsAppendedStepsThenClears() {
        LogContext ctx = new LogContext();
        StepResult ok = ctx.step("first", true);
        byte[] json = "{\"k\":1}".getBytes(StandardCharsets.UTF_8);
        StepResult bad = ctx.step("second", false, List.of(new StepResult.Embed(json, "application/json", "evidence")));

        List<StepResult> collected = ctx.collectPendingSteps();
        assertEquals(2, collected.size());
        assertSame(ok, collected.get(0));
        assertTrue(ok.isPassed());
        assertTrue(bad.isFailed());

        // the embed rides on THIS step (not the producing step)
        assertNotNull(bad.getEmbeds());
        assertEquals(1, bad.getEmbeds().size());

        // the display text is the given text, distinct from the (empty) log — not the fakeSuccess overlap
        assertEquals("second", text(bad));

        // collect is drain-once
        assertNull(ctx.collectPendingSteps());
    }

    // ---- the drain into the running scenario -------------------------------------------------------

    @Test
    void appendedStepsLandInTheScenarioRightAfterTheirProducingStep() {
        ScenarioRuntime sr = TestUtils.run("""
                * def LC = Java.type('io.karatelabs.output.LogContext')
                * eval LC.get().step('checked the total', true)
                * eval LC.get().step('checked the tax', true)
                """);
        ScenarioResult result = sr.getResult();
        assertTrue(result.isPassed(), "passed synthetic steps do not change a passing verdict");

        List<StepResult> synth = synthetic(result);
        assertEquals(2, synth.size());
        assertEquals("checked the total", text(synth.get(0)));
        assertEquals("checked the tax", text(synth.get(1)));

        // ordering: each synthetic step sits immediately after its producing `* eval` step
        List<StepResult> all = result.getStepResults();
        int evalIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("checked the total".equals(text(all.get(i)))) {
                evalIdx = i;
                break;
            }
        }
        assertTrue(evalIdx > 0, "the synthetic step is not first — a real * eval produced it");
        assertNotNull(all.get(evalIdx - 1).getStep(), "the step before it is the real producing step");
    }

    @Test
    void aFailedAppendedStepFailsTheScenarioWithNoThrow() {
        ScenarioRuntime sr = TestUtils.run("""
                * def LC = Java.type('io.karatelabs.output.LogContext')
                * eval LC.get().step('APR matches the oracle', false)
                * print 'the eval step itself did not throw'
                """);
        ScenarioResult result = sr.getResult();

        // the eval step returned normally (no throw), yet the scenario is FAILED purely from the
        // appended failed step — the report/totals see a failure a producer signalled by a step.
        assertTrue(result.isFailed(), "a failed synthetic step fails its scenario");
        List<StepResult> synth = synthetic(result);
        assertEquals(1, synth.size());
        assertTrue(synth.get(0).isFailed());
        assertEquals("APR matches the oracle", text(synth.get(0)));
    }

    @Test
    void appendedStepsSurviveIntoTheScenarioJson() {
        ScenarioRuntime sr = TestUtils.run("""
                * def LC = Java.type('io.karatelabs.output.LogContext')
                * eval LC.get().step('evidence row', true)
                """);
        // FeatureResult.toJson() is the single report source of truth — the synthetic step must be in it
        Map<String, Object> json = sr.getResult().toJson();
        String dump = json.toString();
        assertTrue(dump.contains("evidence row"), "the appended step is missing from the scenario JSON: " + dump);
    }
}
