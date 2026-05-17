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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.karatelabs.core.TestUtils.assertFailed;
import static io.karatelabs.core.TestUtils.assertPassed;
import static io.karatelabs.core.TestUtils.runFeature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@code configure onStepFailure} hook and its interaction with
 * {@code continueOnStepFailure}. Driver-less — exercises the failure pipeline
 * with pure match-failure steps so it runs without testcontainers / browser.
 */
class OnStepFailureHookTest {

    @Test
    void hookFires_withInfoAndAttachesEmbedToFailedStep() {
        AtomicReference<String> capturedError = new AtomicReference<>();
        AtomicReference<String> capturedScenario = new AtomicReference<>();
        AtomicReference<Object> capturedStep = new AtomicReference<>();
        AtomicInteger invocations = new AtomicInteger();

        ScenarioRuntime sr = runFeature("""
                Feature:
                Background:
                * configure onStepFailure = info => { karate.set('_capturedError', info.error); karate.set('_capturedScenario', info.scenarioName); karate.set('_capturedStepText', info.step.text); info.embed('boom-bytes', 'text/plain', 'failure-note.txt') }
                Scenario: failure with embed
                * match 1 == 2
                """);

        assertFailed(sr);
        // Hook ran — vars set in JS reach the scenario bindings
        capturedError.set((String) sr.getVariable("_capturedError"));
        capturedScenario.set((String) sr.getVariable("_capturedScenario"));
        capturedStep.set(sr.getVariable("_capturedStepText"));
        assertNotNull(capturedError.get(), "info.error should be exposed");
        assertTrue(capturedError.get().contains("match failed") || capturedError.get().contains("not equal"),
                "info.error should describe the match failure, got: " + capturedError.get());
        assertEquals("failure with embed", capturedScenario.get());
        assertEquals("1 == 2", capturedStep.get());

        // Embed attached directly to the failed step
        List<StepResult> steps = sr.getResult().getStepResults();
        StepResult failed = steps.stream().filter(StepResult::isFailed).findFirst().orElse(null);
        assertNotNull(failed, "expected a failed step");
        List<StepResult.Embed> embeds = failed.getEmbeds();
        assertNotNull(embeds, "embed should be attached to failed step");
        assertEquals(1, embeds.size(), "exactly one embed");
        assertEquals("text/plain", embeds.getFirst().getMimeType());
        assertEquals("failure-note.txt", embeds.getFirst().getName());

        invocations.incrementAndGet();
    }

    @Test
    void infoProceed_convertsHardFailToSoftAssert() {
        // continueOnStepFailure is the default (false). Without the hook, the
        // first failed step stops execution. info.proceed() flips this scenario
        // into soft-assert mode for THIS failure: execution continues, but the
        // scenario still ends in the failed state because the failure was real.
        ScenarioRuntime sr = runFeature("""
                Feature:
                Background:
                * configure onStepFailure = info => info.proceed()
                Scenario:
                * def reached = 0
                * def reached = 1
                * match reached == 99
                * def reached = 2
                """);

        // Execution continued past the failed match — `reached` got bumped to 2
        assertEquals(2, sr.getVariable("reached"),
                "info.proceed() should have allowed execution past the match failure");
        // But the scenario is still failed — proceed() only changes the stop policy
        assertTrue(sr.getResult().isFailed(),
                "scenario should still be marked failed: proceed() is not a pardon");
    }

    @Test
    void infoStop_overridesContinueOnStepFailure() {
        // continueOnStepFailure=true would normally soft-assert every failure.
        // info.stop() forces THIS failure to hard-stop instead.
        ScenarioRuntime sr = runFeature("""
                Feature:
                Background:
                * configure continueOnStepFailure = true
                * configure onStepFailure = info => info.stop()
                Scenario:
                * def reached = 0
                * def reached = 1
                * match reached == 99
                * def reached = 2
                """);

        assertEquals(1, sr.getVariable("reached"),
                "info.stop() should have halted execution at the failed match");
        assertTrue(sr.getResult().isFailed());
    }

    @Test
    void hookException_doesNotEscalate() {
        // A buggy hook must not turn into a second scenario failure. The
        // original step failure is what surfaces; the hook throw is warn-logged.
        ScenarioRuntime sr = runFeature("""
                Feature:
                Background:
                * configure onStepFailure = info => { throw 'hook is buggy' }
                Scenario:
                * match 1 == 2
                """);

        assertFailed(sr);
        // The recorded error should be from the failed step, not the hook
        String msg = sr.getResult().getFailureMessage();
        assertTrue(msg != null && !msg.contains("hook is buggy"),
                "scenario error should be the step failure, not the hook throw: " + msg);
    }

    @Test
    void hook_doesNotFireWhenStepPasses() {
        AtomicInteger calls = new AtomicInteger();
        ScenarioRuntime sr = runFeature("""
                Feature:
                Background:
                * configure onStepFailure = info => karate.set('_hookCalls', (karate.get('_hookCalls') || 0) + 1)
                Scenario:
                * match 1 == 1
                * def x = 'ok'
                """);

        assertPassed(sr);
        Object hookCalls = sr.getVariable("_hookCalls");
        assertTrue(hookCalls == null || ((Number) hookCalls).intValue() == 0,
                "hook should not fire when no step fails");
        calls.incrementAndGet();
    }
}
