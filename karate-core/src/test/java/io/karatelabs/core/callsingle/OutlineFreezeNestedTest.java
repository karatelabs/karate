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
package io.karatelabs.core.callsingle;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Outline row freeze, with a nested shared-scope {@code call} / {@code callonce} inside the
 * callSingle target: the nested call re-bound the inherited example-row columns, which the
 * target's result then reported as its own.
 */
class OutlineFreezeNestedTest {

    @TempDir
    Path tempDir;

    private SuiteResult run(String sharedBody) throws Exception {
        Files.writeString(tempDir.resolve("inner.feature"), """
                @ignore
                Feature: inner
                Scenario:
                * def innerValue = 'inner'
                """);
        Files.writeString(tempDir.resolve("shared.feature"), sharedBody);
        Path main = tempDir.resolve("outline.feature");
        Files.writeString(main, """
                Feature: outline row values must not freeze

                Background:
                  * karate.set( karate.callSingle('shared.feature') )

                Scenario Outline: Row <rowNum> keeps its own colValue
                  * def expectedList = ['row1', 'row2', 'row3', 'row4']
                  * match colValue == expectedList[__num]

                  Examples:
                  | colValue! | rowNum! |
                  | 'row1'    | 1       |
                  | 'row2'    | 2       |
                  | 'row3'    | 3       |
                  | 'row4'    | 4       |
                """);
        return Runner.builder()
                .path(main.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);
    }

    @Test
    void nestedCallOnceInTarget() throws Exception {
        SuiteResult r = run("""
                @ignore
                Feature: shared with nested callonce
                Scenario:
                * callonce read('inner.feature')
                * def serviceName = 'TestService'
                """);
        assertTrue(r.isPassed(), "row freeze with nested callonce target");
    }

    @Test
    void nestedCallInTarget() throws Exception {
        SuiteResult r = run("""
                @ignore
                Feature: shared with nested call
                Scenario:
                * call read('inner.feature')
                * def serviceName = 'TestService'
                """);
        assertTrue(r.isPassed(), "row freeze with nested call target");
    }

    @Test
    void nestedSharedScopeDefOfInheritedValueStaysInResult() throws Exception {
        // the nested callee's own def counts even when it binds the identical inherited value
        Files.writeString(tempDir.resolve("inner.feature"), """
                @ignore
                Feature: inner
                Scenario:
                * def x = seed
                """);
        Files.writeString(tempDir.resolve("mid.feature"), """
                @ignore
                Feature: mid
                Scenario:
                * call read('inner.feature')
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: caller
                Scenario:
                * def x = 'seed-value'
                * def result = call read('mid.feature') { seed: '#(x)' }
                * match result == { seed: 'seed-value', x: 'seed-value' }
                """);
        SuiteResult r = Runner.builder()
                .path(main.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);
        assertTrue(r.isPassed(), "nested def of an inherited value dropped from the result");
    }
}
