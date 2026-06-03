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
package io.karatelabs.junit6;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Karate JUnit 6 module.
 */
class KarateJUnit6Test {

    /**
     * Test using the fluent API with @TestFactory.
     */
    @TestFactory
    Stream<DynamicNode> testFluentApi() {
        return Karate.run("sample")
                .relativeTo(getClass())
                .hierarchical(true)
                .stream();
    }

    /**
     * Test using flat mode (no feature containers).
     */
    @TestFactory
    Stream<DynamicNode> testFlatMode() {
        return Karate.run("sample")
                .relativeTo(getClass())
                .hierarchical(false)
                .stream();
    }

    /**
     * Test using the @Karate.Test annotation.
     * Note: Return type must be Iterable<DynamicNode> for JUnit 6 static validation.
     */
    @Karate.Test
    Iterable<DynamicNode> testAnnotation() {
        return Karate.run("sample").relativeTo(getClass());
    }

    /**
     * The builder exposes outputJunitXml (v1 JUnit5 parity), and enabling it produces
     * JUnit XML reports when run through the streaming bridge.
     */
    @Test
    void testOutputJunitXml() throws IOException, InterruptedException {
        Path outputDir = Path.of("target/junit6-xml-test");
        if (Files.exists(outputDir)) {
            try (Stream<Path> paths = Files.walk(outputDir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        // drain the stream so the suite runs to completion and reports are flushed
        Karate.run("sample")
                .relativeTo(getClass())
                .outputDir(outputDir.toString())
                .outputJunitXml(true)
                .stream()
                .forEach(node -> { });
        // per-feature XML is written asynchronously; the streaming consumer can be released
        // (SuiteEnd) just before the last write lands, so poll briefly for the report file
        Path junitXmlDir = outputDir.resolve("junit-xml");
        assertTrue(hasXmlReport(junitXmlDir), "no JUnit XML report written to " + junitXmlDir);
    }

    private static boolean hasXmlReport(Path junitXmlDir) throws IOException, InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (Files.isDirectory(junitXmlDir)) {
                try (Stream<Path> paths = Files.list(junitXmlDir)) {
                    if (paths.anyMatch(p -> p.toString().endsWith(".xml"))) {
                        return true;
                    }
                }
            }
            Thread.sleep(100);
        }
        return false;
    }

}
