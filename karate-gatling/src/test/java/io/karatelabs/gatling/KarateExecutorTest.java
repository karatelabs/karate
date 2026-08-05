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
package io.karatelabs.gatling;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a feature failing inside a Gatling run surfaces the actual failure
 * detail to the logs. The single-feature Gatling run disables the HTML report and the
 * console summary, so {@link KarateExecutor}'s error log is the only place the reason
 * appears — logging just the path (the old behaviour) made failures undiagnosable.
 */
class KarateExecutorTest {

    @SuppressWarnings("unchecked")
    private static final scala.collection.immutable.List<String> NO_GROUPS =
            (scala.collection.immutable.List<String>) (Object) scala.collection.immutable.Nil$.MODULE$;

    @Test
    void failedFeatureLogsTheFailureMessageNotJustThePath() {
        CatsMockServer.start();

        Logger execLogger = (Logger) LoggerFactory.getLogger(KarateExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        execLogger.addAppender(appender);
        try {
            // silent=true: no stats reporting, so the GatlingStatsReporter / groups are unused
            KarateExecutor executor = new KarateExecutor(
                    "classpath:features/cats-create-fail.feature", null, null, true);

            Map<String, Object> gatlingVars = new HashMap<>();
            gatlingVars.put("name", "TestKitty");

            KarateExecutor.ExecutionResult result =
                    executor.execute(gatlingVars, new HashMap<>(), null, "test", NO_GROUPS);

            assertFalse(result.success, "feature was expected to fail");

            List<String> errors = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertFalse(errors.isEmpty(), "expected an ERROR log for the failed feature");

            String logged = String.join("\n", errors);
            assertTrue(logged.contains("cats-create-fail.feature"),
                    "log should name the feature: " + logged);
            // The actual assertion-failure detail must be present, not just the path
            assertTrue(logged.contains("WRONG_NAME_THAT_WILL_NEVER_MATCH"),
                    "log should carry the match-failure detail: " + logged);

            // The summary line must name WHERE and WHY, the two halves that used to be split
            // between the log (reason only) and the Gatling KO message (location only).
            String summaryLine = logged.split("\n", 2)[0];
            assertTrue(summaryLine.contains("cats-create-fail.feature:14"),
                    "summary line should carry file:line: " + summaryLine);
            assertTrue(summaryLine.endsWith("match failed: EQUALS"),
                    "summary line should carry the reason: " + summaryLine);
            // ...and the full diff still follows underneath, where it has room
            assertTrue(logged.contains("not equal (STRING:STRING)"),
                    "log should carry the full match diff: " + logged);
        } finally {
            execLogger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * A scenario that fails <em>before</em> issuing any HTTP request has no deferred HTTP perf
     * event to carry the failure, so without a synthetic event Gatling records no KO and the
     * failure is invisible in the load report. Assert exactly one KO is reported, named after
     * the feature and carrying the failure detail.
     */
    @Test
    void failureBeforeAnyHttpRequestIsReportedAsKo() {
        List<String> reported = new ArrayList<>();
        List<String> kos = new ArrayList<>();
        // capturing reporter: GatlingStatsReporter is a functional interface (logResponse)
        GatlingStatsReporter reporter =
                (scenario, groups, requestName, startTime, endTime, ok, statusCode, errorMessage) -> {
                    reported.add(requestName + " ok=" + ok);
                    if (!ok) {
                        kos.add(requestName + " :: " + errorMessage);
                    }
                };

        // silent=false so the reporter actually receives events
        KarateExecutor executor = new KarateExecutor(
                "classpath:features/pre-http-fail.feature", null, null, false);

        KarateExecutor.ExecutionResult result =
                executor.execute(new HashMap<>(), new HashMap<>(), reporter, "test", NO_GROUPS);

        assertFalse(result.success, "feature was expected to fail");
        assertEquals(1, kos.size(),
                "expected exactly one synthetic KO; reported=" + reported);
        String ko = kos.get(0);
        assertTrue(ko.contains("pre-http-fail.feature"),
                "KO should be named after the feature: " + ko);
        assertTrue(ko.contains("goodbye"),
                "KO should carry the failure detail (failed match step): " + ko);
    }

    /**
     * The KO message must name both where the failure was and why — the same two facts the failure
     * log leads with — but stay one short line: Gatling groups its errors table by this exact
     * string, so a full match diff (which embeds the per-virtual-user actual values) would make
     * every KO a unique row.
     */
    @Test
    void koMessageCarriesLocationAndReasonButNotTheWholeDiff() {
        CatsMockServer.start();
        List<String> kos = new ArrayList<>();
        GatlingStatsReporter reporter =
                (scenario, groups, requestName, startTime, endTime, ok, statusCode, errorMessage) -> {
                    if (!ok) {
                        kos.add(errorMessage);
                    }
                };

        KarateExecutor executor = new KarateExecutor(
                "classpath:features/cats-create-fail.feature", null, null, false);

        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "TestKitty");
        executor.execute(gatlingVars, new HashMap<>(), reporter, "test", NO_GROUPS);

        assertEquals(1, kos.size(), "expected exactly one KO");
        String ko = kos.get(0);
        assertTrue(ko.contains("cats-create-fail.feature:14"), "KO should carry file:line: " + ko);
        assertTrue(ko.contains("match response.name =="), "KO should carry the step text: " + ko);
        assertTrue(ko.endsWith("- match failed: EQUALS"), "KO should carry the reason: " + ko);
        assertFalse(ko.contains("\n"), "KO message must stay a single line: " + ko);
    }
}
