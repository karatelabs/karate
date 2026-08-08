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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leak panel must be able to say <em>rising</em>, and a healthy run cannot make it.
 *
 * <p>This exists because the panel's first version could not. It compared the first descriptor
 * reading with the last — and the last probe runs after the workload has stopped, so a run whose
 * descriptors climbed toward {@code ulimit -n} for two hours and fell back at shutdown ended where
 * it started and was reported flat. A soak is expensive and produces one number; a panel that can
 * only ever print "flat" would have passed every one of them.
 */
class LiveSetPanelTest {

    private static String panel(Path runDir, String... probes) throws IOException {
        Files.write(runDir.resolve("stdout.log"), java.util.List.of(probes));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendLiveSet(md, runDir);
        return md.toString();
    }

    private static String suite(int done, int of, long elapsedMs) {
        return io.karatelabs.profiling.workload.SuiteSoakWorkload.SUITE_PREFIX
                + "suites=" + done + "/" + of + " scenarios=1 expected=1 passed=1 failed=0"
                + " requests=3 reports=all elapsedMs=" + elapsedMs;
    }

    /** A boundary reading the workload asked for, exactly as Child.probeOnce writes it. */
    private static String labelled(long elapsedMs, long liveBytes, long fds, String label) {
        return probe(elapsedMs, liveBytes, fds, false) + " label=" + label;
    }

    private static String probe(long elapsedMs, long liveBytes, long fds, boolean isFinal) {
        return "PROFILING-LIVE-SET elapsedMs=" + elapsedMs + " liveBytes=" + liveBytes
                + " fds=" + fds + " fdsAfterGc=" + fds + " collections=2 valid=true"
                + (isFinal ? " final=true" : "");
    }

    /**
     * The shape that used to read as healthy: a steady climb under load, then the drop that every
     * run gets when its client shuts down. The peak is what cannot be walked back.
     */
    @Test
    void testAClimbThatSnapsBackAtShutdownIsStillReportedAsRising() throws IOException {
        String md = panel(Files.createTempDirectory("rising"),
                probe(20_000, 20_000_000, 200, false),
                probe(40_000, 20_000_000, 400, false),
                probe(60_000, 20_000_000, 900, false),
                probe(61_000, 19_000_000, 205, true));
        assertTrue(md.contains("200 / 900 / 900"),
                "first/peak/last must be read over the loaded probes, not the shutdown one: " + md);
        assertTrue(md.contains("**rising, investigate**"),
                "a climb from 200 to 900 is the finding this panel exists for: " + md);
        assertTrue(md.contains("*(load stopped)*"),
                "the post-shutdown probe must be shown and labelled rather than dropped");
    }

    /**
     * A repeated-suites run, and the false positive that produced this code.
     *
     * <p>Retention climbs within a suite by design and collapses at the boundary, so first-versus-last
     * measures one designed ramp. The real four-suite soak read <b>+207.7 MB (36%)</b> that way, on a
     * run whose floors were flat — an authoritative leak claim about the healthiest possible result.
     */
    @Test
    void testFourRampsReturningToTheSameFloorAreNotDrift() throws IOException {
        Path dir = Files.createTempDirectory("suites");
        Files.write(dir.resolve("stdout.log"), java.util.List.of(
                probe(300_000, 583_000_000, 154, false),
                probe(1_500_000, 765_000_000, 156, false),
                probe(1_801_000, 551_000_000, 156, false),   // floor after suite 1
                probe(3_302_000, 778_000_000, 157, false),
                probe(3_602_000, 538_000_000, 157, false),   // floor after suite 2
                probe(5_103_000, 783_000_000, 157, false),
                probe(5_403_000, 541_000_000, 157, false),   // floor after suite 3
                suite(1, 4, 1_772_000), suite(2, 4, 3_542_000), suite(3, 4, 5_311_000)));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendLiveSet(md, dir);
        String panel = md.toString();
        assertTrue(panel.contains("nothing detectably survived a suite"),
                "three floors within a few MB of each other is the healthy answer: " + panel);
        assertTrue(panel.contains("within tolerance ("),
                "the verdict must state the tolerance it was measured against, not assert an absolute: "
                        + panel);
        assertFalse(panel.contains("| drift |"),
                "global drift is meaningless across suite boundaries and must not be printed: " + panel);
        assertTrue(panel.contains("floor after each suite"), panel);
    }

    /**
     * The labelled path — the mechanism the boundary probes exist for, and the one the digest
     * prefers. It has to beat inference rather than merely coexist with it: a boundary probe is
     * printed just BEFORE its suite marker, so its timestamp lands fractionally on the wrong side
     * and any timestamp rule would silently pick a later timed probe instead. The fixture puts a
     * misleading timed probe exactly where that mistake would land.
     */
    @Test
    void testLabelledBoundaryProbesBeatTheTimedOnes() throws IOException {
        Path dir = Files.createTempDirectory("labelled");
        Files.write(dir.resolve("stdout.log"), java.util.List.of(
                probe(1_500_000, 765_000_000, 156, false),
                labelled(1_771_000, 806_000_000, 156, "suite-1-peak"),
                labelled(1_771_500, 551_000_000, 156, "suite-1-floor"),
                probe(1_900_000, 999_000_000, 156, false),   // the probe inference would grab
                labelled(3_541_000, 814_000_000, 157, "suite-2-peak"),
                labelled(3_541_500, 552_000_000, 157, "suite-2-floor"),
                suite(1, 2, 1_772_000), suite(2, 2, 3_542_000)));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendLiveSet(md, dir);
        String panel = md.toString();
        assertTrue(panel.contains("measured at the boundary"),
                "labelled floors must be used and said to be measured: " + panel);
        assertFalse(panel.contains("inferred from timed probes"), panel);
        assertTrue(panel.contains("| released at each suite end |"),
                "peak minus floor is the number the boundary probes buy: " + panel);
        assertTrue(panel.contains("within tolerance"),
                "551 vs 552 MB is flat: " + panel);
    }

    /**
     * The labelled boundary probe is taken after the suite returns, so it reads what the
     * {@code SuiteResult} still holds — reliably LESS than the run reached mid-suite, because a
     * suite in flight also holds the machinery running it. The panel used to print only the
     * boundary reading, so "peak" understated the heap the run actually needed.
     */
    @Test
    void testThePeakRowIsTheMidSuiteMaximumNotTheBoundaryReading() throws IOException {
        Path dir = Files.createTempDirectory("peaks");
        Files.write(dir.resolve("stdout.log"), java.util.List.of(
                // MiB-aligned so the assertions below can name the formatted values exactly.
                // MiB-aligned so the assertions below can name the formatted values exactly, and
                // the second suite's maximum is the LARGER of the two on purpose: with the peaks
                // descending, an implementation that just took the top two probes overall and
                // sorted them would pass without segmenting anything.
                probe(300_000, 540L << 20, 156, false),
                probe(1_500_000, 610L << 20, 156, false),   // suite 1's high-water mark
                labelled(1_771_000, 517L << 20, 156, "suite-1-peak"),
                labelled(1_771_500, 13L << 20, 147, "suite-1-floor"),
                probe(3_300_000, 618L << 20, 156, false),   // suite 2's, and the global max
                labelled(3_541_000, 517L << 20, 157, "suite-2-peak"),
                labelled(3_541_500, 13L << 20, 147, "suite-2-floor"),
                suite(1, 2, 1_772_000), suite(2, 2, 3_542_000)));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendLiveSet(md, dir);
        String panel = md.toString();
        assertTrue(panel.contains("| peak within each suite | 610.0 MB / 618.0 MB"),
                "the segment maxima are the peaks, not the 517 MB boundary readings: " + panel);
        assertTrue(panel.contains("lower bound on the true peak"),
                "a sampled maximum must not be quoted as the peak: " + panel);
        // The boundary reading keeps its own job — what the suite released when it was dropped.
        assertTrue(panel.contains("| released at each suite end |"), panel);
    }

    /**
     * An inferred floor carries up to a probe interval of the next suite's ramp, and a different
     * amount per suite — on E1's shape that is tens of MB against a 4 MB tolerance, which is
     * enough to print a leak verdict for placement noise. The fallback has to widen its tolerance
     * by what the run's own slope says the placement can carry.
     */
    @Test
    void testAnInferredFloorWidensItsToleranceByTheRampItCanCarry() throws IOException {
        Path dir = Files.createTempDirectory("carry");
        Files.write(dir.resolve("stdout.log"), java.util.List.of(
                probe(300_000, 600_000_000, 154, false),
                probe(600_000, 640_000_000, 154, false),     // +40 MB per 300s interval of ramp
                probe(1_801_000, 551_000_000, 156, false),
                probe(2_101_000, 591_000_000, 156, false),
                probe(3_602_000, 570_000_000, 157, false),   // 19 MB above the first floor
                suite(1, 3, 1_772_000), suite(2, 3, 3_542_000)));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendLiveSet(md, dir);
        String panel = md.toString();
        assertTrue(panel.contains("inferred from timed probes"),
                "the panel must say the floors were inferred: " + panel);
        assertTrue(panel.contains("within tolerance"),
                "19 MB of drift against ~40 MB of possible placement carry is not a finding: " + panel);
    }

    /** And the panel must still be able to say the bad thing when a floor genuinely steps up. */
    @Test
    void testAFloorThatStepsUpEverySuiteIsReportedAsRising() throws IOException {
        Path dir = Files.createTempDirectory("leaky");
        Files.write(dir.resolve("stdout.log"), java.util.List.of(
                probe(300_000, 583_000_000, 154, false),
                probe(1_801_000, 551_000_000, 156, false),
                probe(3_602_000, 651_000_000, 157, false),
                probe(5_403_000, 751_000_000, 158, false),
                suite(1, 4, 1_772_000), suite(2, 4, 3_542_000), suite(3, 4, 5_311_000)));
        StringBuilder md = new StringBuilder();
        JfrDigest.appendLiveSet(md, dir);
        assertTrue(md.toString().contains("**rising, investigate**: something survived a suite"),
                "a floor climbing 200 MB across three suites is the finding: " + md);
    }

    /** Descriptors move by a few on their own; a leak claim from that would be noise. */
    @Test
    void testSmallJitterIsNotCalledRising() throws IOException {
        String md = panel(Files.createTempDirectory("jitter"),
                probe(20_000, 20_000_000, 200, false),
                probe(40_000, 20_000_000, 202, false),
                probe(60_000, 20_000_000, 201, false));
        assertTrue(md.contains("— flat"), "±2 descriptors is not a leak: " + md);
        // The panel PROSE contains the word "rising" — assert on the row's marker, not the page.
        assertFalse(md.contains("**rising, investigate**"),
                "a zero-tolerance comparison prints noise as a finding");
    }

    /**
     * Drift is the question the panel answers, and the shutdown probe is not part of it — reading
     * it as the endpoint reports the teardown drop as if the workload had freed memory.
     */
    @Test
    void testDriftExcludesTheProbeTakenAfterTheLoadStopped() throws IOException {
        String md = panel(Files.createTempDirectory("drift"),
                probe(20_000, 20_000_000, 200, false),
                probe(40_000, 20_000_000, 200, false),
                probe(41_000, 10_000_000, 130, true));
        assertTrue(md.contains("under load"), "drift must say what window it covers: " + md);
        assertFalse(md.contains("-9.5 MB"), "the shutdown drop must not be reported as drift: " + md);
        assertTrue(md.contains("3 (2 under load, 1 after shutdown)"),
                "the probe count must say the last one is not part of the trend: " + md);
    }
}
