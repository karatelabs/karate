/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Runner.Builder#outputMask}: a transform applied at the write of every report file, so a value the run
 * carried (here in a scenario name, a step and its log) is in none of them.
 */
class OutputMaskTest {

    static final String SECRET = "tok-secret-value-1";

    @TempDir
    Path tempDir;

    @Test
    void everyReportFileIsWrittenThroughTheMask() throws Exception {
        Path feature = tempDir.resolve("masked.feature");
        Files.writeString(feature, """
                Feature: masked
                Scenario: carries %s
                * def token = '%s'
                * print 'sent', token
                """.formatted(SECRET, SECRET));
        Path reports = tempDir.resolve("reports");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputJsonLines(true)
                .outputHtmlReport(true)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .outputMask(text -> text.replace(SECRET, "***"))
                .parallel(1);
        assertTrue(result.isPassed());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(reports)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        List<String> kinds = List.of("karate-events.jsonl", ".html", ".json", ".xml");
        for (String kind : kinds) {
            assertTrue(files.stream().anyMatch(f -> f.getFileName().toString().endsWith(kind)), kind + " written: " + files);
        }
        int masked = 0;
        for (Path f : files) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
            assertFalse(text.contains(SECRET), "the value is in " + reports.relativize(f));
            if (text.contains("carries ***")) {
                masked++;
            }
        }
        assertTrue(masked >= 4, "the events, feature page, cucumber json and junit xml carry the masked name: " + masked);
    }
}
