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

import io.karatelabs.output.Console;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for failure-reporting output — file path + line number,
 * the offending Gherkin source line, and the printed "failed features:" block.
 * The source-line display is v1 parity (#todo-link) and helps readers locate
 * failures without opening the feature.
 */
class FailureReportingTest {

    @TempDir
    Path tempDir;

    private PrintStream originalOut;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
        originalOut = System.out;
    }

    @AfterEach
    void teardown() {
        Console.setOutput(originalOut);
        Console.setColorsEnabled(true);
    }

    @Test
    void testFailedStepTextAndLocationOnMatchFailure() throws Exception {
        Path feature = tempDir.resolve("fail.feature");
        Files.writeString(feature, """
                Feature: Failing test

                Scenario: bad match
                * def a = 1
                * match a == 999
                """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);

        // Source line is present with the Gherkin prefix
        assertEquals("* match a == 999", sr.getFailedStepText());

        // Location is "path:line" (5 = line of the failing match)
        String location = sr.getFailedStepLocation();
        assertNotNull(location);
        assertTrue(location.endsWith("fail.feature:5"),
                "expected location to end with fail.feature:5, got: " + location);

        // Display-formatted message combines both
        String display = sr.getFailureMessageForDisplay();
        assertNotNull(display);
        assertTrue(display.contains("fail.feature:5"), "expected path+line in display: " + display);
        assertTrue(display.contains("match a == 999"), "expected step text in display: " + display);
    }

    @Test
    void testFailedStepTextNullWhenScenarioPasses() throws Exception {
        Path feature = tempDir.resolve("ok.feature");
        Files.writeString(feature, """
                Feature: Passing test

                Scenario: good
                * def a = 1
                * match a == 1
                """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);
        assertNull(sr.getFailedStepText());
        assertNull(sr.getFailedStepLocation());
        assertNull(sr.getFailureMessageForDisplay());
    }

    @Test
    void testPrintSummaryIncludesFailedStepSourceLine() throws Exception {
        Path feature = tempDir.resolve("cookie-like.feature");
        // Mirrors the shape of the cookie.feature:31 failure observed in CI —
        // a `match` step that we want visible in the console summary.
        Files.writeString(feature, """
                Feature: Failure summary demo

                Scenario: Delete cookie
                * def c = null
                * match c != null
                """);

        // Capture Console output during printSummary()
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        // "failed features:" block header
        assertTrue(output.contains("failed features:"),
                "expected 'failed features:' header in summary output: " + output);

        // Scenario name
        assertTrue(output.contains("- Delete cookie"),
                "expected scenario name in summary output: " + output);

        // path:line for IDE navigation — at least the file:line suffix
        assertTrue(output.contains("cookie-like.feature:5"),
                "expected path:line in summary output: " + output);

        // The offending Gherkin source line (new behavior — v1 parity)
        assertTrue(output.contains("* match c != null"),
                "expected source line '* match c != null' in summary output: " + output);

        // Error message itself
        assertTrue(output.contains("match failed"),
                "expected 'match failed' message in summary output: " + output);
    }

    @Test
    void testPrintSummaryShowsFullErrorMessageWithoutTruncation() throws Exception {
        // Regression: the failed-features console block used to truncate
        // failure messages at 200 chars, hiding the actual diff. We want the full
        // message printed, since match diffs and JS error stacks routinely exceed 200.
        Path feature = tempDir.resolve("long-fail.feature");
        // The body of the karate.fail() string is intentionally > 200 chars and
        // contains a unique tail token so we can assert it survives end-to-end.
        Files.writeString(feature, """
                Feature: Long failure message

                Scenario: long karate.fail message
                * def longMessage = 'Expected [value-A] but got [value-B] — this is the actual diff the user needs to see, but in older builds it would get cut off well before reaching the end TAIL_TOKEN_END'
                * eval karate.fail(longMessage)
                """);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        // The full message survives — both the leading content and the tail token are visible.
        assertTrue(output.contains("Expected [value-A] but got [value-B]"),
                "expected leading diff content in summary output: " + output);
        assertTrue(output.contains("TAIL_TOKEN_END"),
                "expected tail of long message in summary output (regression): " + output);

        // No truncation marker on the failure message line. We isolate the failed-features
        // block to avoid false positives from unrelated "..." elsewhere in summary output.
        int blockStart = output.indexOf("failed features:");
        assertTrue(blockStart >= 0, "expected 'failed features:' block in output: " + output);
        // End the block at the divider that precedes the summary stats.
        int blockEnd = output.indexOf("\n----", blockStart);
        if (blockEnd < 0) {
            blockEnd = output.length();
        }
        String block = output.substring(blockStart, blockEnd);
        assertFalse(block.contains("..."),
                "failed-features block must not truncate the message with '...': " + block);
    }

    @Test
    void testErrorWithLocationDecoratesThrowableForJUnit() throws Exception {
        // Regression: the Throwable re-thrown to JUnit / surefire (and dumped raw in the console
        // [ERROR] line) must carry the failing feature-file location — otherwise the reader only
        // sees engine / HTTP-client internals and cannot tell which .feature step failed.
        Path feature = tempDir.resolve("boom.feature");
        Files.writeString(feature, """
                Feature: Decorated throwable

                Scenario: bad match
                * def a = 1
                * match a == 999
                """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);

        // getError() stays the untouched step error (reports / summary render location separately)
        Throwable raw = sr.getError();
        assertInstanceOf(AssertionError.class, raw);
        assertFalse(raw.getMessage().contains("boom.feature:"),
                "raw getError() must NOT carry the location: " + raw.getMessage());

        // getErrorWithLocation() is what StreamingTestIterator throws — decorated
        Throwable decorated = sr.getErrorWithLocation();
        assertInstanceOf(KarateException.class, decorated);

        // 1) location rides on the message, on its own trailing line
        String message = decorated.getMessage();
        assertTrue(message.startsWith("match failed"),
                "decorated message must keep the original error first: " + message);
        String lastLine = message.substring(message.lastIndexOf('\n') + 1);
        assertTrue(lastLine.endsWith("boom.feature:5"),
                "decorated message must end with the location line 'boom.feature:5', got: " + lastLine);

        // 2) that location line matches the IntelliJ console filter's hyperlink contract
        //    (KarateConsoleFilter.FEATURE_LINE_PATTERN = ^\s*(\S.*\.feature:\d+)$) so it stays
        //    click-to-navigate in a raw stack dump
        assertTrue(lastLine.matches("\\s*\\S.*\\.feature:\\d+"),
                "location line must satisfy the IDE hyperlink pattern: '" + lastLine + "'");

        // 3) the raw, undecorated message is still available for report / summary consumers
        assertEquals(raw.getMessage(), ((KarateException) decorated).getRawMessage());

        // 4) a synthetic <feature> frame sits on top, with the real origin frames preserved below
        StackTraceElement[] trace = decorated.getStackTrace();
        assertTrue(trace.length > 1, "expected the original frames to be preserved beneath the feature frame");
        assertEquals("<feature>", trace[0].getClassName());
        assertEquals(5, trace[0].getLineNumber());
        assertTrue(trace[0].getMethodName().contains("match a == 999"),
                "feature frame should name the offending step: " + trace[0].getMethodName());
        assertTrue(trace[0].getFileName().endsWith("boom.feature"),
                "feature frame fileName should be the feature path: " + trace[0].getFileName());
        boolean hasKarateOriginFrame = false;
        for (int i = 1; i < trace.length; i++) {
            if (trace[i].getClassName().startsWith("io.karatelabs")) {
                hasKarateOriginFrame = true;
                break;
            }
        }
        assertTrue(hasKarateOriginFrame, "original in-code stack frames must be preserved beneath <feature>");
    }

    @Test
    void testPrintSummaryRendersCommentAboveStepAndStripsItFromMessage() throws Exception {
        // A Gherkin comment above a match/assert is used as the assertion label. In the console
        // summary it should appear on its own line ABOVE the step — where it sits in the feature —
        // and NOT be duplicated at the front of the error message.
        Path feature = tempDir.resolve("labelled.feature");
        Files.writeString(feature, """
                Feature: Comment label placement

                Scenario: bad
                * def actual = { id: 1 }
                # user profile should match expected values
                * match actual == { id: 2 }
                """);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        int locationIdx = output.indexOf("labelled.feature:6");
        int commentIdx = output.indexOf("# user profile should match expected values");
        int sourceIdx = output.indexOf("* match actual ==");
        int errorIdx = output.indexOf("match failed");

        assertTrue(commentIdx > 0, "comment line missing from summary: " + output);
        assertTrue(locationIdx < commentIdx, "location must come before the comment");
        assertTrue(commentIdx < sourceIdx, "comment must sit directly above the step line, like the feature");
        assertTrue(sourceIdx < errorIdx, "step line must come before the error message");

        // the label must NOT be duplicated inside the error message block — only one occurrence
        assertEquals(commentIdx, output.lastIndexOf("# user profile should match expected values"),
                "comment label should appear exactly once (not also prepended to the error): " + output);
    }

    @Test
    void testPrintSummaryShowsDocStringOfFailedStep() throws Exception {
        // A match whose RHS is a docstring shows only "* match actual ==" as the step line, which
        // reads as truncated. The summary should render the triple-quoted block under the step,
        // as it appears in the feature.
        Path feature = tempDir.resolve("doc.feature");
        Files.writeString(feature, """
                Feature: Docstring RHS

                Scenario: match against a docstring
                * def actual = { id: 123 }
                * match actual ==
                \"\"\"
                { "id": 456 }
                \"\"\"
                """);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        int stepIdx = output.indexOf("* match actual ==");
        int docOpenIdx = output.indexOf("\"\"\"");
        int docBodyIdx = output.indexOf("{ \"id\": 456 }");
        int errorIdx = output.indexOf("match failed");

        assertTrue(stepIdx > 0, "step line missing: " + output);
        assertTrue(docBodyIdx > 0, "docstring body must be shown in the summary: " + output);
        assertTrue(stepIdx < docOpenIdx, "docstring block must come after the step line");
        assertTrue(docBodyIdx < errorIdx, "docstring must be shown before the error diff");

        // the exposed docstring keeps its triple-quote delimiters
        String docString = result.getFeatureResults().get(0).getScenarioResults().get(0).getFailedStepDocString();
        assertNotNull(docString);
        assertTrue(docString.startsWith("\"\"\"\n") && docString.endsWith("\n\"\"\""),
                "docstring should be wrapped in its \"\"\" delimiters: " + docString);
    }

    @Test
    void testJsFailureFromFileEmitsIdeClickableStderrLink() throws Exception {
        // A JS error thrown from a real .js file (not inline) must emit an IDE-clickable
        // file://<path>.js:line:col reference to stderr so the reader can jump straight to the
        // offending line in the JS source (not just the calling feature).
        Files.writeString(tempDir.resolve("helper.js"), """
                function() {
                  var x = 1;
                  return someUndefinedThing(x);
                }
                """);
        Path feature = tempDir.resolve("calls-js.feature");
        Files.writeString(feature, """
                Feature: js file failure

                Scenario: call a js file that throws
                * def fn = read('helper.js')
                * def result = call fn
                """);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, "UTF-8"));
        SuiteResult result;
        try {
            result = Runner.path(feature.toString())
                    .workingDir(tempDir)
                    .outputDir(tempDir.resolve("reports"))
                    .outputConsoleSummary(false)
                    .parallel(1);
        } finally {
            System.setErr(originalErr);
        }

        assertTrue(result.isFailed());
        String err = capturedErr.toString("UTF-8");
        // file://...helper.js:<line>:<col> — the second line of the JS body is where the ReferenceError fires
        assertTrue(err.matches("(?s).*file://\\S*helper\\.js:\\d+:\\d+.*"),
                "expected an IDE-clickable file://...helper.js:line:col link on stderr, got: " + err);

        // and the report-facing failure message keeps the File: reference to the JS source
        ScenarioResult sr = result.getFeatureResults().get(0).getScenarioResults().get(0);
        assertTrue(sr.getFailureMessage().contains("helper.js"),
                "failure message should name the offending JS file: " + sr.getFailureMessage());
    }

    @Test
    void testPrintSummaryOrdersLocationSourceThenError() throws Exception {
        Path feature = tempDir.resolve("order.feature");
        Files.writeString(feature, """
                Feature: Order of failure lines

                Scenario: bad
                * def v = 1
                * match v == 2
                """);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Console.setOutput(new PrintStream(captured, true, "UTF-8"));

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(true)
                .parallel(1);

        assertTrue(result.isFailed());
        String output = captured.toString("UTF-8");

        int locationIdx = output.indexOf("order.feature:5");
        int sourceIdx = output.indexOf("* match v == 2");
        int errorIdx = output.indexOf("match failed");

        assertTrue(locationIdx > 0, "location line missing");
        assertTrue(sourceIdx > 0, "source line missing");
        assertTrue(errorIdx > 0, "error line missing");
        assertTrue(locationIdx < sourceIdx,
                "location line must come before source line (IDE plugins read top-down)");
        assertTrue(sourceIdx < errorIdx,
                "source line must come before error message");
    }
}
