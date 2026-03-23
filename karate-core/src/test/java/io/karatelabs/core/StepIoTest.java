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
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for karate.* IO methods:
 * - readAsString, readAsBytes, readAsStream
 * - write
 * - toAbsolutePath
 */
class StepIoTest {

    // ========== readAsString ==========

    @Test
    void testReadAsString() {
        ScenarioRuntime sr = run("""
            * def content = karate.readAsString('json/cat.json')
            * match content contains 'Billie'
            * match content contains '"age"'
            """);
        assertPassed(sr);
    }

    @Test
    void testReadAsStringHtml() {
        ScenarioRuntime sr = run("""
            * def content = karate.readAsString('markup/main.html')
            * match content contains 'DOCTYPE'
            """);
        assertPassed(sr);
    }

    // ========== readAsBytes ==========

    @Test
    void testReadAsBytes() {
        ScenarioRuntime sr = run("""
            * def bytes = karate.readAsBytes('json/cat.json')
            * match bytes == '#notnull'
            """);
        assertPassed(sr);
        Object bytes = get(sr, "bytes");
        assertTrue(bytes instanceof byte[], "Expected byte[]");
        assertTrue(((byte[]) bytes).length > 0, "Expected non-empty bytes");
    }

    @Test
    void testReadAsBytesContent() {
        ScenarioRuntime sr = run("""
            * def bytes = karate.readAsBytes('json/cat.json')
            """);
        assertPassed(sr);
        byte[] bytes = (byte[]) get(sr, "bytes");
        // Accept both Unix (LF=34 bytes) and Windows (CRLF=37 bytes) line endings
        assertTrue(bytes.length == 34 || bytes.length == 37,
                "Expected 34 (Unix) or 37 (Windows) bytes, got: " + bytes.length);
    }

    // ========== readAsStream ==========

    @Test
    void testReadAsStream() {
        ScenarioRuntime sr = run("""
            * def stream = karate.readAsStream('json/cat.json')
            * match stream == '#notnull'
            """);
        assertPassed(sr);
        Object stream = get(sr, "stream");
        assertTrue(stream instanceof InputStream, "Expected InputStream");
    }

    @Test
    void testReadAsStreamWithJavaProperties(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("test.properties");
        Files.writeString(propsFile, "hello=world\nfoo=bar\n");
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature:
            Scenario:
            * def readProps =
            \"\"\"
            function(path) {
              var stream = karate.readAsStream(path);
              var props = new java.util.Properties();
              props.load(stream);
              return props;
            }
            \"\"\"
            * def props = readProps('test.properties')
            * match props == { hello: 'world', foo: 'bar' }
            """);
        io.karatelabs.gherkin.Feature feature = io.karatelabs.gherkin.Feature.read(
                io.karatelabs.common.Resource.from(featureFile));
        io.karatelabs.gherkin.Scenario scenario = feature.getSections().getFirst().getScenario();
        KarateJs karate = new KarateJs(io.karatelabs.common.Resource.from(tempDir), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        sr.call();
        assertPassed(sr);
    }

    // ========== toAbsolutePath ==========

    @Test
    void testToAbsolutePath() {
        ScenarioRuntime sr = run("""
            * def path = karate.toAbsolutePath('json/cat.json')
            * match path == '#notnull'
            * match path contains 'cat.json'
            """);
        assertPassed(sr);
        String path = (String) get(sr, "path");
        assertTrue(path.startsWith("/") || path.contains(":"), "Expected absolute path: " + path);
    }

    @Test
    void testToAbsolutePathRelative() {
        ScenarioRuntime sr = run("""
            * def path = karate.toAbsolutePath('markup/main.html')
            * match path contains 'main.html'
            """);
        assertPassed(sr);
    }

    // ========== write ==========

    @Test
    void testWriteString(@TempDir Path tempDir) {
        // Create a Suite with the temp dir as output
        ScenarioRuntime sr = runWithOutputDir(tempDir, """
            * def file = karate.write('hello world', 'test-output.txt')
            * match file == '#notnull'
            """);
        assertPassed(sr);

        // Verify file was written
        Path outputFile = tempDir.resolve("test-output.txt");
        assertTrue(Files.exists(outputFile), "Output file should exist");
        try {
            String content = Files.readString(outputFile);
            assertEquals("hello world", content);
        } catch (Exception e) {
            fail("Failed to read output file: " + e.getMessage());
        }
    }

    @Test
    void testWriteJson(@TempDir Path tempDir) {
        ScenarioRuntime sr = runWithOutputDir(tempDir, """
            * def data = { name: 'test', value: 123 }
            * def file = karate.write(data, 'data.json')
            """);
        assertPassed(sr);

        Path outputFile = tempDir.resolve("data.json");
        assertTrue(Files.exists(outputFile), "Output file should exist");
        try {
            String content = Files.readString(outputFile);
            assertTrue(content.contains("name") && content.contains("test"));
        } catch (Exception e) {
            fail("Failed to read output file: " + e.getMessage());
        }
    }

    @Test
    void testWriteBytes(@TempDir Path tempDir) {
        ScenarioRuntime sr = runWithOutputDir(tempDir, """
            * def bytes = karate.toBytes([72, 101, 108, 108, 111])
            * def file = karate.write(bytes, 'binary.bin')
            """);
        assertPassed(sr);

        Path outputFile = tempDir.resolve("binary.bin");
        assertTrue(Files.exists(outputFile), "Output file should exist");
        try {
            byte[] content = Files.readAllBytes(outputFile);
            assertEquals(5, content.length);
            assertEquals("Hello", new String(content));
        } catch (Exception e) {
            fail("Failed to read output file: " + e.getMessage());
        }
    }

    @Test
    void testWriteCreatesSubdirectories(@TempDir Path tempDir) {
        ScenarioRuntime sr = runWithOutputDir(tempDir, """
            * def file = karate.write('nested content', 'subdir/nested/file.txt')
            """);
        assertPassed(sr);

        Path outputFile = tempDir.resolve("subdir/nested/file.txt");
        assertTrue(Files.exists(outputFile), "Nested output file should exist");
    }

    // ========== Helper for write tests ==========

    private static ScenarioRuntime runWithOutputDir(Path outputDir, String steps) {
        io.karatelabs.gherkin.Feature feature = io.karatelabs.gherkin.Feature.read(
                io.karatelabs.common.Resource.text("Feature:\nScenario:\n" + steps));
        io.karatelabs.gherkin.Scenario scenario = feature.getSections().getFirst().getScenario();
        io.karatelabs.common.Resource root = io.karatelabs.common.Resource.path("src/test/resources");
        KarateJs karate = new KarateJs(root, new InMemoryHttpClient());
        karate.setOutputDir(outputDir.toString());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        sr.call();
        return sr;
    }

}
