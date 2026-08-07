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
package io.karatelabs.profiling;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the comparison arithmetic to the numbers docs/PROFILING.md §10 publishes.
 *
 * <p>The published tables were derived by hand, and this class exists so that the automated
 * derivation and the hand one are known to agree — the tool was written precisely because a hand
 * scrape had already shifted a column once, and a tool that quietly disagrees with the record it
 * replaces is worse than the scrape it replaced.
 *
 * <p>The inputs below are the mock's own reported figures for the 10 ms tier's three pairs, taken
 * from the run directories §10 names. The expectations are the table's own cells.
 */
class CompareTest {

    /** 8 users, 4000 requested iterations, two requests each, as every 10 ms cell was run. */
    private static Compare.Run run(String arm, double servedPerSecond, double sleepMicrosMean) {
        return new Compare.Run(Path.of("gatling-http-" + arm + "-2026-08-06-000000"), arm, 10,
                false, "", 0, 8,
                4000, 8000, servedPerSecond, sleepMicrosMean, 8, arm.equals("plain") ? 8 : 4000,
                0, -1, 15.2, 56, 71, 10);
    }

    /**
     * §10's 10 ms tier: three pairs at +0.48 / +0.93 / +0.38 ms added per iteration, mean +0.59.
     * The formula is a closed loop's own identity — throughput is users ÷ iteration time — so
     * inverting the measured rate gives back the per-iteration serial time being compared.
     */
    @Test
    void testAddedMillisReproducesThePublishedTenMillisecondTier() {
        assertEquals(0.48, added(534.8, 526.4), 0.005);
        assertEquals(0.93, added(547.5, 530.7), 0.005);
        assertEquals(0.38, added(590.8, 582.6), 0.005);
    }

    /**
     * The sleep-parity correction §10 requires before believing a difference. In all three pairs
     * the mock slept less on the karate side, which hands that arm iteration time the other did
     * not get — so correcting moves the figure <i>against</i> Karate, from 0.59 to 0.82 ms, and
     * the uncorrected number is the conservative one.
     */
    @Test
    void testSleepCorrectionReproducesThePublishedCorrectedFigures() {
        assertEquals(0.92, corrected(534.8, 14180, 526.4, 13957), 0.01);
        assertEquals(1.15, corrected(547.5, 13909, 530.7, 13799), 0.01);
        assertEquals(0.40, corrected(590.8, 12260, 582.6, 12250), 0.01);
    }

    /**
     * Requests per iteration is derived from what the mock served over the iterations Gatling
     * actually ran, never assumed to be two — §10 records a 500-iteration cell that ran 504,
     * because the requested total is rounded up to a multiple of the user count.
     */
    @Test
    void testRequestsPerIterationRoundsIterationsUpToTheUserCount() {
        Compare.Run rounded = new Compare.Run(Path.of("gatling-http-karate-2026-08-06-000000"),
                "karate", 10, false, "", 0, 8, 500, 1008, 471.8, 13000, 8, 504, 0, -1, 2.1, 56, 71, 10);
        assertEquals(2.0, rounded.requestsPerIteration(), 0.001);
    }

    /**
     * A TLS run and a plaintext one at the same latency are not the same experiment, and neither is
     * an equivalence control and the ordinary pair it exists to be compared against. Before this,
     * both blended into one mean with nothing in the output to say so.
     */
    @Test
    void testShapeSeparatesTlsAndEquivalenceControlsFromOrdinaryRuns() {
        Compare.Run plain = run("plain", 500, 0);
        Compare.Run karate = run("karate", 500, 0);
        assertEquals("plaintext", plain.armShape());

        Compare.Run overTls = new Compare.Run(Path.of("gatling-http-karate-2026-08-06-000000"),
                "karate", 10, true, "", 0, 8, 4000, 8000, 500, 0, 8, 4000, 0, -1, 15.2, 56, 71, 10);
        assertEquals("TLS", overTls.armShape());

        // A control pair is ONE control arm against one ordinary arm. Requiring both arms to carry
        // the variant is what emptied both control tables on the bench: the pairing a control
        // exists to make is asymmetric, so the variant describes the pair and not either side.
        Compare.Run fat = new Compare.Run(Path.of("gatling-http-plain-fat-2026-08-06-000000"),
                "plain", 10, false, "fat", 0, 8, 4000, 8000, 500, 0, 8, 8, 0, -1, 15.2, 56, 71, 10);
        assertEquals(fat.armShape(), karate.armShape(),
                "a fat control and an ordinary karate arm must still PAIR — same transport");
        Compare.Pair fatPair = new Compare.Pair(fat, karate, "p→k");
        assertEquals("plaintext, fat control", fatPair.shape());
        assertNotEquals(fatPair.shape(), new Compare.Pair(plain, karate, "p→k").shape(),
                "a fat control must not BUCKET with the ordinary pair it is the control for");

        Compare.Run lean = new Compare.Run(Path.of("gatling-http-karate-lean-2026-08-06-000000"),
                "karate", 10, false, "lean", 0, 8, 4000, 8000, 500, 0, 8, 4000, 0, -1, 15.2, 56, 71, 10);
        assertEquals("plaintext, lean control", new Compare.Pair(plain, lean, "p→k").shape());
        assertTrue(new Compare.Pair(fat, lean, "p→k").bothArmsAreControls(),
                "two controls have no ordinary reference between them");
    }

    /**
     * The body-size tier is a slope, so its cells must not average across sizes. Two matrices at
     * the same latency tier and different payloads are two experiments — the same failure the
     * transport check exists to prevent, one axis over.
     */
    @Test
    void testBodySizeSeparatesOneTierFromAnother() {
        Compare.Run small = new Compare.Run(Path.of("gatling-body-karate-2026-08-06-000000"),
                "karate", 50, false, "", 1024, 8, 4000, 8000, 500, 0, 8, 4000, 0, -1, 15.2, 56, 71, 10);
        Compare.Run large = new Compare.Run(Path.of("gatling-body-karate-2026-08-06-000001"),
                "karate", 50, false, "", 65536, 8, 4000, 8000, 500, 0, 8, 4000, 0, -1, 15.2, 56, 71, 10);
        assertEquals("plaintext, 1024-byte body", small.armShape());
        assertNotEquals(small.armShape(), large.armShape(),
                "a 1 KB matrix must not average together with a 64 KB one");
        assertEquals("plaintext", run("plain", 500, 0).armShape(),
                "a run predating the tier carries no body-size qualifier");
    }

    private static double added(double plainRate, double karateRate) {
        return new Compare.Pair(run("plain", plainRate, 0), run("karate", karateRate, 0), "p→k")
                .addedMillis();
    }

    private static double corrected(double plainRate, double plainSleep,
                                    double karateRate, double karateSleep) {
        return new Compare.Pair(run("plain", plainRate, plainSleep),
                run("karate", karateRate, karateSleep), "p→k").correctedMillis();
    }

}
