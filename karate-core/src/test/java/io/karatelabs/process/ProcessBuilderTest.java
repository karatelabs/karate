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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessBuilderTest {

    @Test
    void testTokenizeSimple() {
        List<String> result = ProcessBuilder.tokenize("echo hello world");
        assertEquals(List.of("echo", "hello", "world"), result);
    }

    @Test
    void testTokenizeSingleQuotes() {
        List<String> result = ProcessBuilder.tokenize("echo 'hello world'");
        assertEquals(List.of("echo", "hello world"), result);
    }

    @Test
    void testTokenizeDoubleQuotes() {
        List<String> result = ProcessBuilder.tokenize("echo \"hello world\"");
        assertEquals(List.of("echo", "hello world"), result);
    }

    @Test
    void testTokenizeMixed() {
        List<String> result = ProcessBuilder.tokenize("git commit -m 'fix: bug' \"hello world\"");
        assertEquals(List.of("git", "commit", "-m", "fix: bug", "hello world"), result);
    }

    @Test
    void testTokenizeEscapedSpace() {
        List<String> result = ProcessBuilder.tokenize("echo hello\\ world");
        assertEquals(List.of("echo", "hello world"), result);
    }

    @Test
    void testTokenizeEscapedQuote() {
        List<String> result = ProcessBuilder.tokenize("echo \\\"hello\\\"");
        assertEquals(List.of("echo", "\"hello\""), result);
    }

    @Test
    void testTokenizeQuotesInsideQuotes() {
        // Double quotes inside single quotes
        List<String> result1 = ProcessBuilder.tokenize("echo 'hello \"world\"'");
        assertEquals(List.of("echo", "hello \"world\""), result1);

        // Single quotes inside double quotes
        List<String> result2 = ProcessBuilder.tokenize("echo \"hello 'world'\"");
        assertEquals(List.of("echo", "hello 'world'"), result2);
    }

    @Test
    void testTokenizeMultipleSpaces() {
        List<String> result = ProcessBuilder.tokenize("echo   hello    world");
        assertEquals(List.of("echo", "hello", "world"), result);
    }

    @Test
    void testTokenizePathWithSpaces() {
        List<String> result = ProcessBuilder.tokenize("cat '/path/to/my file.txt'");
        assertEquals(List.of("cat", "/path/to/my file.txt"), result);
    }

    @Test
    void testTokenizeGitCommit() {
        List<String> result = ProcessBuilder.tokenize("git commit -m \"feat: add new feature\"");
        assertEquals(List.of("git", "commit", "-m", "feat: add new feature"), result);
    }

    @Test
    void testTokenizeWithEquals() {
        List<String> result = ProcessBuilder.tokenize("docker run -e NODE_ENV=production app");
        assertEquals(List.of("docker", "run", "-e", "NODE_ENV=production", "app"), result);
    }

    @Test
    void testTokenizeAdjacentQuotes() {
        // Adjacent quoted segments merge into single token
        List<String> result = ProcessBuilder.tokenize("echo 'hello ''world'");
        assertEquals(List.of("echo", "hello world"), result);
    }

    @Test
    void testTokenizeEmpty() {
        List<String> result = ProcessBuilder.tokenize("");
        assertEquals(List.of(), result);
    }

    @Test
    void testTokenizeOnlySpaces() {
        List<String> result = ProcessBuilder.tokenize("   ");
        assertEquals(List.of(), result);
    }

    @Test
    void testLineMethod() {
        ProcessConfig config = ProcessBuilder.create()
                .line("echo hello world")
                .build();
        assertEquals(List.of("echo", "hello", "world"), config.args());
    }

    @Test
    void testArgsMethod() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "hello", "world")
                .build();
        assertEquals(List.of("echo", "hello", "world"), config.args());
    }

    @Test
    void testWorkingDir() {
        ProcessConfig config = ProcessBuilder.create()
                .args("ls")
                .workingDir("/tmp")
                .build();
        assertEquals(Path.of("/tmp"), config.workingDir());
    }

    @Test
    void testEnvironment() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "$MY_VAR")
                .env("MY_VAR", "hello")
                .env("OTHER", "world")
                .build();
        assertEquals(Map.of("MY_VAR", "hello", "OTHER", "world"), config.env());
    }

    @Test
    void testUseShell() {
        ProcessConfig config = ProcessBuilder.create()
                .line("echo hello | grep hello")
                .useShell(true)
                .build();
        assertTrue(config.useShell());
        // Shell args should be wrapped
        assertTrue(config.args().get(0).equals("sh") || config.args().get(0).equals("cmd"));
    }

    @Test
    void testTimeout() {
        ProcessConfig config = ProcessBuilder.create()
                .args("sleep", "10")
                .timeoutMillis(5000)
                .build();
        assertEquals(Duration.ofMillis(5000), config.timeout());
    }

    @Test
    void testFromMapBasic() {
        Map<String, Object> options = Map.of(
                "line", "echo hello",
                "workingDir", "/tmp"
        );
        ProcessBuilder builder = ProcessBuilder.fromMap(options);
        ProcessConfig config = builder.build();

        assertEquals(List.of("echo", "hello"), config.args());
        assertEquals(Path.of("/tmp"), config.workingDir());
    }

    @Test
    void testFromMapWithArgs() {
        Map<String, Object> options = Map.of(
                "args", List.of("echo", "hello", "world")
        );
        ProcessBuilder builder = ProcessBuilder.fromMap(options);
        ProcessConfig config = builder.build();

        assertEquals(List.of("echo", "hello", "world"), config.args());
    }

    @Test
    void testFromMapWithAllOptions() {
        Map<String, Object> options = Map.of(
                "args", List.of("node", "server.js"),
                "workingDir", "/app",
                "env", Map.of("NODE_ENV", "production"),
                "useShell", false,
                "redirectErrorStream", false,
                "timeout", 30000L
        );
        ProcessBuilder builder = ProcessBuilder.fromMap(options);
        ProcessConfig config = builder.build();

        assertEquals(List.of("node", "server.js"), config.args());
        assertEquals(Path.of("/app"), config.workingDir());
        assertEquals(Map.of("NODE_ENV", "production"), config.env());
        assertFalse(config.useShell());
        assertFalse(config.redirectErrorStream());
        assertEquals(Duration.ofMillis(30000), config.timeout());
    }

    @Test
    void testImmutableConfig() {
        ProcessConfig config = ProcessBuilder.create()
                .args("echo", "hello")
                .build();

        // Args should be immutable
        assertThrows(UnsupportedOperationException.class, () -> config.args().add("world"));
    }

    @Test
    void testWrapWithShell() {
        List<String> wrapped = ProcessBuilder.wrapWithShell(List.of("echo", "hello"));
        assertEquals(3, wrapped.size());
        assertTrue(wrapped.get(0).equals("sh") || wrapped.get(0).equals("cmd"));
        assertTrue(wrapped.get(1).equals("-c") || wrapped.get(1).equals("/c"));
        assertEquals("echo hello", wrapped.get(2));
    }

}
