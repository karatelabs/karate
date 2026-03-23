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

/**
 * Tests for karate.exec() and karate.fork() process execution.
 * Listener functionality is tested in ProcessHandleTest.
 */
class StepExecTest {

    @Test
    void testExec() {
        ScenarioRuntime sr = run("""
            * def result = karate.exec('echo hello')
            * match result contains 'hello'
            * def result2 = karate.exec({ line: 'echo world' })
            * match result2 contains 'world'
            """);
        assertPassed(sr);
    }

    @Test
    void testFork() {
        ScenarioRuntime sr = run("""
            * def proc = karate.fork('echo forked')
            * proc.waitSync()
            * match proc.exitCode == 0
            * match proc.stdOut contains 'forked'
            """);
        assertPassed(sr);
    }

}
