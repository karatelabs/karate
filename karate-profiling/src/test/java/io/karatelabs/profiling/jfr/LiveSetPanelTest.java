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
