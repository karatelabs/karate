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
package io.karatelabs.profiling.jfr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suite panel must be able to say <em>this run failed</em>, and a clean run must not make it.
 *
 * <p>The same control {@code LiveSetPanelTest} is for the leak panel, and for the same reason. A
 * {@code Suite} reports a failed scenario in its result rather than by throwing, so every other
 * field in a soak's digest — {@code errors=0}, exit 0, a full heap series — reads healthy for a run
 * in which nothing worked. This panel is the only place that can contradict them, so what has to be
 * pinned is that it <em>can</em>: a panel that only ever prints "100 passed" would have signed off
 * on a two-hour run that measured the retention of work that never happened.
 */
class SuiteOutcomePanelTest {

    private static Path runDirWith(String name, String... lines) throws IOException {
        Path dir = Files.createTempDirectory(name);
        Files.write(dir.resolve("stdout.log"), List.of(lines));
        return dir;
    }

    private static String suiteLine(int done, int of, long scenarios, long failed, long elapsedMs) {
        return suiteLine(done, of, scenarios, scenarios, failed, elapsedMs);
    }

    private static String suiteLine(int done, int of, long scenarios, long expected,
                                    long failed, long elapsedMs) {
        long passed = scenarios - failed;
        return io.karatelabs.profiling.workload.SuiteSoakWorkload.SUITE_PREFIX
                + "suites=" + done + "/" + of + " scenarios=" + scenarios + " expected=" + expected
                + " passed=" + passed + " failed=" + failed
                + " requests=" + passed * io.karatelabs.profiling.workload.SuiteSoakWorkload.REQUESTS_PER_SCENARIO
                + " reports=html+jsonl+junit+cucumber elapsedMs=" + elapsedMs;
    }

    private static String outcome(Path runDir) {
        StringBuilder md = new StringBuilder();
        JfrDigest.appendSuiteOutcome(md, runDir);
        return md.toString();
    }

    @Test
    void testAFailedScenarioIsReportedEvenThoughTheRunExitedCleanly() throws IOException {
        String md = outcome(runDirWith("failed", suiteLine(1, 1, 40_000, 137, 7_200_000)));
        assertTrue(md.contains("39863 / **137**"),
                "the failure count is the whole point of the panel: " + md);
        assertTrue(md.contains("**137 of 40000 scenarios failed (0.34%)"),
                "a failure must be called out in prose, and proportionately — the allowance knob"
                        + " means a tolerated few must not read like a wrecked run: " + md);
    }

    @Test
    void testACleanRunSaysNothingAlarming() throws IOException {
        String md = outcome(runDirWith("clean", suiteLine(3, 3, 30_000, 0, 7_200_000)));
        assertTrue(md.contains("| suites completed | 3/3 |"),
                "a repeated-suites run must report how many suites it got through: " + md);
        assertFalse(md.contains("**This run had scenario failures"),
                "a clean run must not carry the warning, or the warning means nothing");
    }

    /**
     * The line is written per suite and is cumulative, so a run killed by its timeout mid-suite
     * still reports what it completed. The digest takes the last line, which is the furthest one.
     */
    @Test
    void testAKilledRunReportsTheSuitesItCompleted() throws IOException {
        String md = outcome(runDirWith("killed",
                suiteLine(1, 4, 10_000, 0, 1_800_000),
                suiteLine(2, 4, 20_000, 0, 3_600_000)));
        assertTrue(md.contains("| suites completed | 2/4 |"),
                "the last cumulative line is the state to report: " + md);
        assertTrue(md.contains("| scenarios run / generated | 20000 / 20000 |"), md);
    }

    /**
     * The blind spot one notch over from a failed scenario: a scenario that never ran. Nothing
     * fails, so nothing throws and every other panel reads healthy — this is the only place that
     * can contradict them, and it could not until it was given the generated count to compare.
     */
    @Test
    void testAnEmptyRunIsCalledOutRatherThanReportedAsZeroFailures() throws IOException {
        String md = outcome(runDirWith("empty", suiteLine(1, 1, 0, 40_000, 0, 900_000)));
        assertTrue(md.contains("executed no scenarios at all"),
                "zero scenarios and zero failures is the failure, not a clean run: " + md);
        assertTrue(md.contains("Nothing here is a measurement"), md);
    }

    @Test
    void testAShortRunIsCalledOutEvenWithNoFailures() throws IOException {
        String md = outcome(runDirWith("short", suiteLine(1, 1, 39_000, 40_000, 0, 900_000)));
        assertTrue(md.contains("1000 generated scenarios never ran"),
                "the gap between generated and executed must be named: " + md);
    }

    /**
     * Zero expected against zero served is two counts of nothing. Printing "they agree" for it
     * puts a green tick on the run the panel above has just called empty.
     */
    @Test
    void testReconciliationRefusesToAgreeWithAnEmptyRun() throws IOException {
        Path empty = runDirWith("recon-empty", suiteLine(1, 1, 0, 40_000, 0, 900_000));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendSuiteReconciliation(md, empty, 0);
        assertFalse(md.toString().contains("they agree"),
                "0 == 0 is not agreement, it is an empty run: " + md);
        assertTrue(md.toString().contains("nothing to reconcile"), md.toString());
    }

    /** No suite line at all — every other workload — must leave the digest untouched. */
    @Test
    void testThePanelIsAbsentForWorkloadsThatDoNotRunASuite() throws IOException {
        assertTrue(outcome(runDirWith("none", "PROFILING-SUMMARY completed=1 errors=0")).isEmpty(),
                "a panel that renders empty headings for every other workload is noise");
    }

    /**
     * Reconciliation across the two counts that cannot see each other. With failures present the
     * comparison is meaningless — a failed scenario stops partway, so its request count is unknown —
     * and saying so is better than printing a difference that looks like a dropped request.
     */
    @Test
    void testReconciliationAgreesAndDisagreesForTheRightReasons() throws IOException {
        Path clean = runDirWith("recon-ok", suiteLine(1, 1, 100, 0, 3_000));
        StringBuilder agree = new StringBuilder();
        JfrDigest.appendSuiteReconciliation(agree, clean, 300);
        assertTrue(agree.toString().contains("**they agree**"), agree.toString());

        StringBuilder disagree = new StringBuilder();
        JfrDigest.appendSuiteReconciliation(disagree, clean, 298);
        assertTrue(disagree.toString().contains("**they disagree by 2**"), disagree.toString());

        Path failed = runDirWith("recon-failed", suiteLine(1, 1, 100, 4, 3_000));
        StringBuilder withFailures = new StringBuilder();
        JfrDigest.appendSuiteReconciliation(withFailures, failed, 290);
        assertTrue(withFailures.toString().contains("not comparable"),
                "with failures present the counts must not be compared: " + withFailures);
    }
}
