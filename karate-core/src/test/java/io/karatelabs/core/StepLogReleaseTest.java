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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A step's captured log and embeds must not outlive the feature that produced them.
 *
 * <p>The sibling of {@link CallResultReleaseTest}, for the larger of the two retentions: call
 * results are the nesting, these are the <em>bytes</em>. A {@link SuiteResult} holds every
 * feature until the run ends, so an unreleased log keeps the rendered request and response of
 * every step — and an unreleased embed keeps a raw {@code byte[]} screenshot — reachable for the
 * whole suite, long after the only things that read them have finished.
 *
 * <p>What these tests have to pin is that the release takes <b>only</b> that. Reports are
 * generated from the result while it is still whole, so a release that ran too early, or took
 * too much, would not fail anything — it would quietly produce thinner reports, which is the
 * failure mode worth a test rather than a comment.
 */
class StepLogReleaseTest {

    @TempDir
    Path tempDir;

    private Path writeFeature() throws IOException {
        Path feature = tempDir.resolve("logged.feature");
        Files.writeString(feature, """
                Feature: logged
                Scenario:
                * print 'hello from the step'
                * karate.embed('an embedded asset', 'text/plain')
                * def answer = 42
                * match answer == 42
                """);
        return feature;
    }

    private SuiteResult run(Path feature, boolean retainStepLogs, boolean html) {
        return Runner.builder()
                .path(feature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(html)
                .outputDir(tempDir.resolve("reports"))
                .backupOutputDir(false)
                .retainStepLogs(retainStepLogs)
                .parallel(1);
    }

    /** Fails with the scenario's own message — a bare isPassed() assertion says nothing useful. */
    private static void assertPassed(SuiteResult result) {
        if (!result.isPassed()) {
            String why = result.getFeatureResults().stream()
                    .flatMap(fr -> fr.getScenarioResults().stream())
                    .map(ScenarioResult::getFailureMessage)
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse("(no failure message)");
            throw new AssertionError("the fixture feature failed: " + why);
        }
    }

    private static long stepsWithLog(SuiteResult result) {
        return result.getFeatureResults().stream()
                .flatMap(fr -> fr.getScenarioResults().stream())
                .flatMap(sr -> sr.getStepResults().stream())
                .filter(step -> step.getLog() != null && !step.getLog().isEmpty())
                .count();
    }

    private static long stepsWithEmbeds(SuiteResult result) {
        return result.getFeatureResults().stream()
                .flatMap(fr -> fr.getScenarioResults().stream())
                .flatMap(sr -> sr.getStepResults().stream())
                .filter(step -> step.getEmbeds() != null && !step.getEmbeds().isEmpty())
                .count();
    }

    @Test
    void testLogsAndEmbedsAreReleasedByDefault() throws IOException {
        SuiteResult result = run(writeFeature(), false, false);
        assertPassed(result);
        assertEquals(0, stepsWithLog(result),
                "captured step logs should not survive the end of the feature by default");
        assertEquals(0, stepsWithEmbeds(result),
                "embeds hold raw bytes and are the larger half — they must go with the log");
    }

    /**
     * The escape hatch, and the reason it exists: reading a step's log from a {@code SuiteResult}
     * after the run is a real thing to want, and there is otherwise no way back.
     */
    @Test
    void testLogsAndEmbedsSurviveWhenRetained() throws IOException {
        SuiteResult result = run(writeFeature(), true, false);
        assertPassed(result);
        assertTrue(stepsWithLog(result) > 0,
                "retainStepLogs(true) should keep the captured output reachable");
        assertEquals(1, stepsWithEmbeds(result),
                "and the embed attached by karate.embed()");
    }

    /**
     * Releasing must take only the bytes. Status, timing, the source step and failure messages
     * are what summaries and failure reporting are built from; touching those would be a silent
     * reporting regression wearing the costume of a memory saving.
     */
    @Test
    void testReleaseLeavesEverythingElseIntact() throws IOException {
        Path feature = writeFeature();
        SuiteResult released = run(feature, false, false);
        SuiteResult retained = run(feature, true, false);

        var releasedSteps = released.getFeatureResults().get(0)
                .getScenarioResults().get(0).getStepResults();
        var retainedSteps = retained.getFeatureResults().get(0)
                .getScenarioResults().get(0).getStepResults();

        assertEquals(retainedSteps.size(), releasedSteps.size());
        for (int i = 0; i < releasedSteps.size(); i++) {
            StepResult step = releasedSteps.get(i);
            assertNotNull(step.getStep(), "step " + i + " lost its source step");
            assertEquals(retainedSteps.get(i).getStatus(), step.getStatus(),
                    "step " + i + " changed status");
            assertEquals(retainedSteps.get(i).getDurationNanos(), step.getDurationNanos() >= 0
                    ? retainedSteps.get(i).getDurationNanos() : -1, "step " + i + " lost its timing");
            assertNull(step.getLog(), "step " + i + " kept its log");
        }
        assertEquals(released.getScenarioCount(), retained.getScenarioCount());
        assertEquals(released.getScenarioPassedCount(), retained.getScenarioPassedCount());
    }

    /**
     * The whole safety argument in one assertion: the writers consume at {@code onFeatureEnd},
     * the release happens after it, so the report on disk is identical either way. If the
     * release ever moves ahead of the listeners, this is what catches it — and it catches it as
     * missing content rather than as an exception, which is how it would actually present.
     */
    @Test
    void testTheHtmlReportIsUnaffectedByTheRelease() throws IOException {
        Path feature = writeFeature();
        SuiteResult released = run(feature, false, true);
        assertPassed(released);
        Path reportDir = tempDir.resolve("reports/feature-html");
        assertTrue(Files.isDirectory(reportDir), "no html report was written: " + reportDir);
        String html;
        try (var paths = Files.list(reportDir)) {
            Path page = paths.filter(p -> p.getFileName().toString().endsWith(".html"))
                    .findFirst().orElseThrow(() -> new AssertionError("no feature page in " + reportDir));
            html = Files.readString(page);
        }
        assertTrue(html.contains("hello from the step"),
                "the print output must reach the report even though the log was released after");
        assertFalse(html.isBlank());
    }
}
