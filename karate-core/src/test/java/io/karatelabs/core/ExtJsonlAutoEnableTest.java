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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite-level proof of {@link Ext#requiresJsonlEvents()}: an ext that consumes the
 * karate-events.jsonl stream auto-enables the writer, so a plain `karate run` with
 * boot.ext(...) produces the stream without -f karate:jsonl / outputJsonLines(true).
 * karate-boot.js must therefore evaluate before the JSONL writer is wired in
 * Suite.run().
 */
class ExtJsonlAutoEnableTest {

    @TempDir
    Path tempDir;

    private Path writeTrivialFeature() throws Exception {
        Path feature = tempDir.resolve("trivial.feature");
        Files.writeString(feature, """
            Feature: trivial

            Scenario: pass
            * def x = 1
            """);
        return feature;
    }

    private Path eventsFile(Path reports) {
        return reports.resolve(Suite.KARATE_JSON_SUBFOLDER)
                .resolve(JsonLinesEventWriter.DEFAULT_FILENAME);
    }

    @Test
    void extRequiringJsonlAutoEnablesTheEventStream() throws Exception {
        Files.writeString(tempDir.resolve("karate-boot.js"), """
            boot.ext('jsonl');
            """);
        Path feature = writeTrivialFeature();
        Path reports = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        Path events = eventsFile(reports);
        assertTrue(Files.exists(events),
                "ext with requiresJsonlEvents() should auto-enable karate-events.jsonl");
        String jsonl = Files.readString(events);
        assertTrue(jsonl.contains("SUITE_ENTER"), "stream should carry real run events");
    }

    @Test
    void noExtMeansNoJsonlUnlessRequested() throws Exception {
        // control: without a requesting ext (no boot file at all), the writer
        // stays off by default — the zero-cost path is preserved.
        Path feature = writeTrivialFeature();
        Path reports = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertFalse(Files.exists(eventsFile(reports)),
                "no request, no stream — default behavior unchanged");
    }

    @Test
    void extNotRequiringJsonlDoesNotEnableIt() throws Exception {
        // control: a booted ext whose requiresJsonlEvents() is the default false
        // must not flip the writer on.
        Files.writeString(tempDir.resolve("karate-boot.js"), """
            boot.ext('dummy');
            """);
        Path feature = writeTrivialFeature();
        Path reports = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        assertFalse(Files.exists(eventsFile(reports)),
                "an ext that does not require the stream must not enable it");
    }
}
