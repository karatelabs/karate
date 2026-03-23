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

import io.karatelabs.common.Resource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for karate.exec() and karate.fork() process execution.
 * Tests both Gherkin (feature files) and pure JavaScript usage.
 */
class ProcessTest {

    @TempDir
    Path tempDir;

    // ========== karate.exec() via Gherkin ==========

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecSimpleCommand() throws Exception {
        Path featureFile = tempDir.resolve("exec-simple.feature");
        Files.writeString(featureFile, """
            Feature: exec simple command
            Scenario: run echo
              * def output = karate.exec('echo hello')
              * match output contains 'hello'
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "exec simple command should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecWithArgs() throws Exception {
        Path featureFile = tempDir.resolve("exec-args.feature");
        Files.writeString(featureFile, """
            Feature: exec with args array
            Scenario: run echo with args
              * def output = karate.exec(['echo', 'hello', 'world'])
              * match output contains 'hello world'
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "exec with args should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecWithOptions() throws Exception {
        Path featureFile = tempDir.resolve("exec-options.feature");
        Files.writeString(featureFile, """
            Feature: exec with options map
            Scenario: run command with working directory
              * def output = karate.exec({ line: 'pwd', workingDir: '/tmp' })
              * match output contains 'tmp'
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "exec with options should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecWithEnvVar() throws Exception {
        Path featureFile = tempDir.resolve("exec-env.feature");
        Files.writeString(featureFile, """
            Feature: exec with environment variable
            Scenario: run with custom env
              * def output = karate.exec({ line: 'sh -c "echo $MY_VAR"', env: { MY_VAR: 'test-value' } })
              * match output contains 'test-value'
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "exec with env var should pass");
    }

    // ========== karate.fork() via Gherkin ==========

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkBasic() throws Exception {
        Path featureFile = tempDir.resolve("fork-basic.feature");
        Files.writeString(featureFile, """
            Feature: fork basic
            Scenario: fork and wait
              * def proc = karate.fork({ args: ['echo', 'forked'] })
              * proc.waitSync()
              * match proc.stdOut contains 'forked'
              * match proc.exitCode == 0
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "fork basic should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkWithListener() throws Exception {
        Path featureFile = tempDir.resolve("fork-listener.feature");
        Files.writeString(featureFile, """
            Feature: fork with listener
            Scenario: fork with event listener
              * def proc = karate.fork({ args: ['echo', 'hello'] })
              * proc.waitSync()
              * match proc.stdOut contains 'hello'
              * match proc.exitCode == 0
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "fork with listener should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkDeferredStart() throws Exception {
        Path featureFile = tempDir.resolve("fork-deferred.feature");
        Files.writeString(featureFile, """
            Feature: fork deferred start
            Scenario: create process without starting
              * def proc = karate.fork({ args: ['echo', 'deferred'], start: false })
              * proc.start()
              * proc.waitSync()
              * match proc.stdOut contains 'deferred'
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "fork deferred start should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkWaitForOutput() throws Exception {
        Path featureFile = tempDir.resolve("fork-wait-output.feature");
        Files.writeString(featureFile, """
            Feature: fork waitForOutput
            Scenario: wait for specific output
              * def proc = karate.fork({ args: ['sh', '-c', 'sleep 0.2 && echo ready'] })
              * def line = proc.waitForOutput(function(line) { return line && line.indexOf('ready') >= 0 }, 5000)
              * match line contains 'ready'
              * proc.close()
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "fork waitForOutput should pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkClose() throws Exception {
        Path featureFile = tempDir.resolve("fork-close.feature");
        Files.writeString(featureFile, """
            Feature: fork close
            Scenario: close running process
              * def proc = karate.fork({ args: ['sleep', '60'] })
              * match proc.alive == true
              * proc.close()
              * def java = Java.type('java.lang.Thread')
              * java.sleep(100)
              * match proc.alive == false
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "fork close should pass");
    }

    // ========== karate.exec() via pure JavaScript ==========

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var output = karate.exec('echo hello-from-js');
            output;
            """;
        Object result = karate.engine.eval(js);
        assertTrue(result.toString().contains("hello-from-js"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExecWithOptionsViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var output = karate.exec({
                args: ['echo', 'hello', 'world'],
                workingDir: '/tmp'
            });
            output;
            """;
        Object result = karate.engine.eval(js);
        assertTrue(result.toString().contains("hello world"));
    }

    // ========== karate.fork() via pure JavaScript ==========

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var proc = karate.fork({ args: ['echo', 'forked-from-js'] });
            proc.waitSync();
            var result = { stdOut: proc.stdOut, exitCode: proc.exitCode };
            result;
            """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) karate.engine.eval(js);
        assertTrue(result.get("stdOut").toString().contains("forked-from-js"));
        assertEquals(0, result.get("exitCode"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkWithListenerViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var proc = karate.fork({ args: ['echo', 'event-test'] });
            proc.waitSync();
            proc.stdOut;
            """;
        Object result = karate.engine.eval(js);
        assertTrue(result.toString().contains("event-test"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkDeferredStartViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var proc = karate.fork({ args: ['echo', 'deferred-js'], start: false });
            proc.start();
            proc.waitSync();
            proc.stdOut;
            """;
        Object result = karate.engine.eval(js);
        assertTrue(result.toString().contains("deferred-js"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkWaitForOutputViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var proc = karate.fork({
                args: ['sh', '-c', 'echo starting; sleep 0.1; echo ready; sleep 0.1; echo done']
            });
            var line = proc.waitForOutput(function(line) {
                return line && line.indexOf('ready') >= 0;
            }, 5000);
            proc.close();
            line;
            """;
        Object result = karate.engine.eval(js);
        assertTrue(result.toString().contains("ready"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkCloseViaJs() throws InterruptedException {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var proc = karate.fork({ args: ['sleep', '60'] });
            var wasAlive = proc.alive;
            proc.close();
            { wasAlive: wasAlive };
            """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) karate.engine.eval(js);
        assertTrue((Boolean) result.get("wasAlive"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testForkPidViaJs() {
        KarateJs karate = new KarateJs(Resource.path(""));
        String js = """
            var proc = karate.fork({ args: ['echo', 'pid-test'] });
            var pid = proc.pid;
            proc.waitSync();
            pid;
            """;
        Object result = karate.engine.eval(js);
        assertTrue(((Number) result).longValue() > 0);
    }

}
