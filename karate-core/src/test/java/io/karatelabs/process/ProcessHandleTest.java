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
package io.karatelabs.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProcessHandleTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testSimpleCommand() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "hello")
                .build();
        ProcessHandle handle = ProcessHandle.start(config);
        int exitCode = handle.waitSync();

        assertEquals(0, exitCode);
        assertTrue(handle.getStdOut().contains("hello"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testStdoutCapture() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "line1")
                .build();
        ProcessHandle handle = ProcessHandle.start(config);
        handle.waitSync();

        String output = handle.getStdOut();
        assertTrue(output.contains("line1"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testExitCode() {
        ProcessConfig config = ProcessBuilder.create()
                .args("sh", "-c", "exit 42")
                .build();
        ProcessHandle handle = ProcessHandle.start(config);
        int exitCode = handle.waitSync();

        assertEquals(42, exitCode);
        assertEquals(42, handle.getExitCode());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testWorkingDirectory() {
        ProcessConfig config = ProcessBuilder.create()
                .args("pwd")
                .workingDir("/tmp")
                .build();
        ProcessHandle handle = ProcessHandle.start(config);
        handle.waitSync();

        // On macOS /tmp is a symlink to /private/tmp
        String output = handle.getStdOut().trim();
        assertTrue(output.equals("/tmp") || output.equals("/private/tmp"),
                "Expected /tmp or /private/tmp but got: " + output);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testEnvironmentVariable() {
        ProcessConfig config = ProcessBuilder.create()
                .line("sh -c 'echo $MY_VAR'")
                .env("MY_VAR", "hello_world")
                .build();
        ProcessHandle handle = ProcessHandle.start(config);
        handle.waitSync();

        assertTrue(handle.getStdOut().contains("hello_world"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testListener() {
        List<String> lines = new ArrayList<>();

        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "hello")
                .listener(lines::add)
                .build();

        ProcessHandle handle = ProcessHandle.start(config);
        handle.waitSync();

        assertTrue(lines.stream().anyMatch(e -> e.contains("hello")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testOnStdOutChaining() {
        List<String> lines = new ArrayList<>();

        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "test")
                .build();

        ProcessHandle handle = ProcessHandle.create(config);
        handle.onStdOut(lines::add);
        handle.start();
        handle.waitSync();

        assertTrue(lines.stream().anyMatch(e -> e.contains("test")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testDeferredStart() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "deferred")
                .build();

        // Create without starting
        ProcessHandle handle = ProcessHandle.create(config);

        // Process should not be running yet
        assertThrows(NullPointerException.class, handle::isAlive);

        // Now start
        handle.start();
        handle.waitSync();

        assertTrue(handle.getStdOut().contains("deferred"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testCloseProcess() throws InterruptedException {
        ProcessConfig config = ProcessBuilder.create()
                .args("sleep", "60")
                .build();

        ProcessHandle handle = ProcessHandle.start(config);
        assertTrue(handle.isAlive());

        handle.close();
        Thread.sleep(100); // Give it a moment to terminate

        assertFalse(handle.isAlive());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testPid() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "pid_test")
                .build();
        ProcessHandle handle = ProcessHandle.start(config);

        long pid = handle.getPid();
        assertTrue(pid > 0);

        handle.waitSync();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testWaitForOutput() {
        List<String> outputLines = new ArrayList<>();

        ProcessConfig config = ProcessBuilder.create()
                .args("sh", "-c", "echo 'starting'; sleep 0.1; echo 'ready'; sleep 0.1; echo 'done'")
                .listener(outputLines::add)
                .build();

        ProcessHandle handle = ProcessHandle.start(config);

        // Wait until we see "ready"
        String readyLine = handle.waitForOutput(
                line -> line != null && line.contains("ready"),
                5000);

        assertNotNull(readyLine);
        assertTrue(readyLine.contains("ready"));

        handle.close();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testWaitForOutputTimeout() {
        ProcessConfig config = ProcessBuilder.create()
                .args("sleep", "10")
                .build();

        ProcessHandle handle = ProcessHandle.start(config);

        // This should timeout since sleep doesn't produce output
        assertThrows(RuntimeException.class, () ->
                handle.waitForOutput(line -> true, 100));

        handle.close(true);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testSignal() {
        AtomicInteger signalReceived = new AtomicInteger(0);

        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "test")
                .build();

        ProcessHandle handle = ProcessHandle.start(config);
        handle.setSignalConsumer(result -> signalReceived.incrementAndGet());

        handle.signal("test_signal");
        assertEquals(1, signalReceived.get());

        handle.waitSync();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testSeparateStderr() {
        List<String> stdoutLines = new ArrayList<>();
        List<String> stderrLines = new ArrayList<>();

        ProcessConfig config = ProcessBuilder.create()
                .args("sh", "-c", "echo 'out'; echo 'err' >&2")
                .redirectErrorStream(false)
                .listener(stdoutLines::add)
                .errorListener(stderrLines::add)
                .build();

        ProcessHandle handle = ProcessHandle.start(config);
        handle.waitSync();

        assertTrue(stdoutLines.stream().anyMatch(l -> l.contains("out")));
        assertTrue(stderrLines.stream().anyMatch(l -> l.contains("err")));
    }

}
