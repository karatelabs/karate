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

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ScenarioRuntime#evalAsStep(String)} — runs a Karate step (with or
 * without a Gherkin prefix) against an existing runtime's variable / HTTP state.
 */
class EvalAsStepTest {

    @Test
    void testJsExpressionWithoutPrefix() {
        ScenarioRuntime sr = run("""
            * def result = null
            * def myFunc = function(x){ result = x }
            """);
        StepResult r = sr.evalAsStep("myFunc('hello')");
        assertTrue(r.isPassed(), () -> "step failed: " + r.getErrorMessage());
        assertEquals("hello", get(sr, "result"));
    }

    @Test
    void testStarPrefixJsExpression() {
        ScenarioRuntime sr = run("""
            * def result = null
            * def myFunc = function(x){ result = x }
            """);
        StepResult r = sr.evalAsStep("* myFunc('input')");
        assertTrue(r.isPassed(), () -> "step failed: " + r.getErrorMessage());
        assertEquals("input", get(sr, "result"));
    }

    @Test
    void testGivenPrefixDef() {
        // exercises full step semantics: 'def' assigns a variable in the runtime
        ScenarioRuntime sr = run("* def initial = 1");
        StepResult r = sr.evalAsStep("Given def x = 42");
        assertTrue(r.isPassed(), () -> "step failed: " + r.getErrorMessage());
        assertEquals(42, get(sr, "x"));
    }

    @Test
    void testWhenPrefixMatchPasses() {
        ScenarioRuntime sr = run("* def x = 7");
        StepResult r = sr.evalAsStep("When match x == 7");
        assertTrue(r.isPassed(), () -> "step failed: " + r.getErrorMessage());
    }

    @Test
    void testThenPrefixMatchFails() {
        ScenarioRuntime sr = run("* def x = 7");
        StepResult r = sr.evalAsStep("Then match x == 99");
        assertTrue(r.isFailed(), "match against wrong value should fail");
    }

    @Test
    void testAndPrefixDef() {
        ScenarioRuntime sr = run("* def y = 0");
        StepResult r = sr.evalAsStep("And def y = 100");
        assertTrue(r.isPassed(), () -> "step failed: " + r.getErrorMessage());
        assertEquals(100, get(sr, "y"));
    }

    @Test
    void testStarPrefixPrint() {
        ScenarioRuntime sr = run("* def msg = 'hello'");
        StepResult r = sr.evalAsStep("* print msg");
        assertTrue(r.isPassed(), () -> "step failed: " + r.getErrorMessage());
    }

    @Test
    void testMutationPersistsAcrossEvalCalls() {
        ScenarioRuntime sr = run("* def counter = 0");
        StepResult r1 = sr.evalAsStep("* def counter = counter + 1");
        StepResult r2 = sr.evalAsStep("* def counter = counter + 1");
        assertTrue(r1.isPassed(), () -> "step failed: " + r1.getErrorMessage());
        assertTrue(r2.isPassed(), () -> "step failed: " + r2.getErrorMessage());
        assertEquals(2, get(sr, "counter"));
    }

    @Test
    void testInvalidStepReturnsFailedResult() {
        ScenarioRuntime sr = run("* def x = 1");
        // 'def' without '=' is invalid
        StepResult r = sr.evalAsStep("* def y");
        assertTrue(r.isFailed(), "invalid def should yield a failed StepResult");
        assertNotNull(r.getError());
    }

    @Test
    void testUnknownKeywordReturnsFailedResult() {
        ScenarioRuntime sr = run("* def x = 1");
        StepResult r = sr.evalAsStep("* nosuchkeyword foo bar");
        assertTrue(r.isFailed(), "unknown keyword should yield a failed StepResult");
        assertNotNull(r.getError());
    }

    @Test
    void testFakeStepDoesNotCauseSideEffects() {
        // evalAsStep must not append to the scenario's recorded step results.
        ScenarioRuntime sr = run("""
            * def x = 1
            * def y = 2
            """);
        int beforeCount = sr.getResult().getStepResults().size();
        StepResult r = sr.evalAsStep("* def z = 3");
        assertTrue(r.isPassed());
        int afterCount = sr.getResult().getStepResults().size();
        assertEquals(beforeCount, afterCount, "evalAsStep must not append to scenario step results");
        assertEquals(3, get(sr, "z"));
    }

}
