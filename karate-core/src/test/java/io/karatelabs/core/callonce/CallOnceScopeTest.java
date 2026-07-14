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
package io.karatelabs.core.callonce;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scope and caching semantics for the {@code call} / {@code callonce} keywords and
 * {@code karate.call()} / {@code karate.callonce()}, aligned with v1 behavior:
 * <ul>
 *   <li>An isolated {@code call}/{@code callonce} result contains ONLY the variables the
 *       called feature defined — inherited caller/config scope must not leak in.</li>
 *   <li>A {@code callonce} inside a feature that is itself invoked via {@code call read()}
 *       is scoped to that call instance, not cached globally across invocations.</li>
 * </ul>
 */
class CallOnceScopeTest {

    @TempDir
    Path tempDir;

    // ========== Result should contain only the called feature's own variables ==========

    @Test
    void testCallResultOnlyContainsCalleeVariables() throws Exception {
        Path helper = tempDir.resolve("helper.feature");
        Files.writeString(helper, """
            @ignore
            Feature: Helper
            Scenario:
            * def helperVar1 = 'value-from-helper'
            * def helperVar2 = 'another-helper-value'
            * def helperNum = 999
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller
            Background:
            * def callerVar1 = 'I am from the caller'
            * def callerVar2 = 42
            * def bigObject = { key1: 'value1', key2: 'value2', key3: 'value3' }

            Scenario: call result should only contain helper's variables
            * def result = call read('helper.feature')
            * match result.helperVar1 == 'value-from-helper'
            * match result.helperVar2 == 'another-helper-value'
            * match result.helperNum == 999
            * match result.callerVar1 == '#notpresent'
            * match result.callerVar2 == '#notpresent'
            * match result.bigObject == '#notpresent'
            """);

        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "call result should not leak caller scope: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceResultOnlyContainsCalleeVariables() throws Exception {
        Path helper = tempDir.resolve("helper.feature");
        Files.writeString(helper, """
            @ignore
            Feature: Helper
            Scenario:
            * def helperVar1 = 'value-from-helper'
            * def helperNum = 999
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller
            Background:
            * def callerVar1 = 'I am from the caller'
            * def bigObject = { key1: 'value1', key2: 'value2' }

            Scenario: callonce result should only contain helper's variables
            * def result = callonce read('helper.feature')
            * match result.helperVar1 == 'value-from-helper'
            * match result.helperNum == 999
            * match result.callerVar1 == '#notpresent'
            * match result.bigObject == '#notpresent'
            """);

        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callonce result should not leak caller scope: " + getFailureMessage(result));
    }

    @Test
    void testConfigVariablesDoNotLeakIntoCallResult() throws Exception {
        Path helper = tempDir.resolve("helper.feature");
        Files.writeString(helper, """
            @ignore
            Feature: Helper
            Scenario:
            * def helperVar = 'from-helper'
            """);

        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
            function fn() {
                return { configSecret: 'do-not-leak', configUrl: 'http://localhost' };
            }
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller
            Scenario: config vars must not leak into the call result
            * def result = call read('helper.feature')
            * match result.helperVar == 'from-helper'
            * match result.configSecret == '#notpresent'
            * match result.configUrl == '#notpresent'
            """);

        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .configDir(configJs.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "config vars should not leak into call result: " + getFailureMessage(result));
    }

    @Test
    void testCalleeCanStillReadInheritedVariables() throws Exception {
        // The called feature must still be able to READ inherited caller vars — they are just
        // excluded from the returned result map.
        Path helper = tempDir.resolve("helper.feature");
        Files.writeString(helper, """
            @ignore
            Feature: Helper
            Scenario:
            * def greeting = 'Hello ' + name
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller
            Background:
            * def name = 'World'

            Scenario: callee reads an inherited var but only returns its own
            * def result = call read('helper.feature')
            * match result.greeting == 'Hello World'
            * match result.name == '#notpresent'
            """);

        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callee should read inherited vars but not return them: " + getFailureMessage(result));
    }

    @Test
    void testCalleeReassignedInheritedVariableAppearsInResult() throws Exception {
        // A called feature that REASSIGNS an inherited variable is contributing it — the new
        // value must appear in the result (only untouched inherited scope is filtered out).
        Path helper = tempDir.resolve("helper.feature");
        Files.writeString(helper, """
            @ignore
            Feature: Helper
            Scenario:
            * def shared = 'reassigned-by-helper'
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller
            Background:
            * def shared = 'from-caller'

            Scenario: a callee reassignment of an inherited var is part of the result
            * def result = call read('helper.feature')
            * match result.shared == 'reassigned-by-helper'
            """);

        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callee reassignment should appear in result: " + getFailureMessage(result));
    }

    @Test
    void testCallArgumentIsPartOfResult() throws Exception {
        // An explicit call argument the callee uses is its own input, not inherited caller scope,
        // so it stays in the result (v1 parity).
        Path helper = tempDir.resolve("helper.feature");
        Files.writeString(helper, """
            @ignore
            Feature: Helper
            Scenario:
            * def greeting = 'Hello ' + name
            """);

        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: Caller
            Scenario: the call arg is echoed in the result
            * def result = call read('helper.feature') { name: 'World' }
            * match result.greeting == 'Hello World'
            * match result.name == 'World'
            """);

        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "call argument should be part of result: " + getFailureMessage(result));
    }

    // ========== callonce inside a call read() must be scoped per invocation ==========

    @Test
    void testCallOnceInsideCallReadIsNotCachedGlobally() throws Exception {
        Path prereq = tempDir.resolve("prereq.feature");
        Files.writeString(prereq, """
            @ignore
            Feature: Prereq
            Scenario:
            * def prereqAccount = accountNumber
            """);

        Path setup = tempDir.resolve("setup.feature");
        Files.writeString(setup, """
            @ignore
            Feature: Setup
            Scenario:
            * def data = [{account: 'ACCOUNT-1'}, {account: 'ACCOUNT-2'}, {account: 'ACCOUNT-3'}]
            * def accountNumber = data[dataSourceRow - 1].account
            * def result = callonce read('prereq.feature')
            """);

        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
            Feature: callonce inside call read() should not cache globally

            Scenario Outline: Row <rowNum> should get <expected>
            * def dataSourceRow = <rowNum>
            * call read('setup.feature')
            * match result.prereqAccount == expected

            Examples:
            | rowNum | expected  |
            | 1      | ACCOUNT-1 |
            | 2      | ACCOUNT-2 |
            | 3      | ACCOUNT-3 |
            """);

        SuiteResult result = Runner.builder()
                .path(main.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(),
                "callonce inside call read() should re-execute per invocation: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceStillCachesWithinSameCallInstance() throws Exception {
        // Within a single call instance, callonce must still cache (execute once) — proven with a
        // timestamp captured twice.
        Path prereq = tempDir.resolve("prereq.feature");
        Files.writeString(prereq, """
            @ignore
            Feature: Prereq
            Scenario:
            * def stamp = java.lang.System.nanoTime()
            """);

        Path setup = tempDir.resolve("setup.feature");
        Files.writeString(setup, """
            @ignore
            Feature: Setup
            Scenario:
            * def a = callonce read('prereq.feature')
            * def b = callonce read('prereq.feature')
            * match a.stamp == b.stamp
            """);

        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
            Feature: callonce caches within a single call instance
            Scenario: cached within the same setup call
            * call read('setup.feature')
            """);

        SuiteResult result = Runner.builder()
                .path(main.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(),
                "callonce should still cache within a single call instance: " + getFailureMessage(result));
    }

    private String getFailureMessage(SuiteResult result) {
        if (result.isPassed()) return "none";
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return "unknown";
    }

}
