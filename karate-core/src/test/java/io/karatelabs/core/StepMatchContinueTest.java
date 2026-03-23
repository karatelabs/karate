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

class StepMatchContinueTest {

    @Test
    void testContinueOnStepFailure() {
        // Multiple failures continue, then fail when flipping to false
        ScenarioRuntime sr = run("""
            * def tmp = 'foo'
            * configure continueOnStepFailure = true
            * match tmp == 'bar'
            * match tmp == 'baz'
            * def reached = true
            * configure continueOnStepFailure = false
            * def unreached = true
            """);
        assertFailed(sr);
        // 'reached' should be set because we continue past failures
        assertEquals(true, get(sr, "reached"));
        // 'unreached' should NOT be set because we fail on flip to false
        assertNull(get(sr, "unreached"));
    }

    @Test
    void testContinueOnStepFailureAtEnd() {
        // Failures at scenario end trigger failure
        ScenarioRuntime sr = run("""
            * configure continueOnStepFailure = true
            * match 1 == 2
            * def reached = true
            """);
        assertFailed(sr);
        // 'reached' should be set because we continue past the failure
        assertEquals(true, get(sr, "reached"));
    }

    @Test
    void testContinueOnStepFailureNoFailures() {
        // When there are no failures, everything passes
        ScenarioRuntime sr = run("""
            * def tmp = 'foo'
            * configure continueOnStepFailure = true
            * match tmp == 'foo'
            * configure continueOnStepFailure = false
            * def after = true
            """);
        assertPassed(sr);
        assertEquals(true, get(sr, "after"));
    }

    @Test
    void testContinueOnStepFailureWithAssert() {
        // Assert failures also continue when enabled
        ScenarioRuntime sr = run("""
            * configure continueOnStepFailure = true
            * assert 1 == 2
            * def reached = true
            """);
        assertFailed(sr);
        assertEquals(true, get(sr, "reached"));
    }

}
