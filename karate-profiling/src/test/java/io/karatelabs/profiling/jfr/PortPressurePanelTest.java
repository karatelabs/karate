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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection panel must not derive a rate from a count that has stopped counting.
 *
 * <p>{@code distinctPeerPorts} is the size of a set with a hard lid — a 65,536-bit bitmap at the
 * mock, drawn from an ephemeral range no larger, with {@code tcp_tw_reuse} recycling ports on
 * purpose. On a long run it converges on the size of the range and stops tracking connections
 * altogether.
 *
 * <p>Measured, on the four-suite soak: ~700,000 connections opened, 32,256 distinct ports
 * reported, and the panel printed <b>"5 connections/s"</b> for a run actually opening ~99/s. That
 * number exists to be checked against the exhaustion ceiling, so the panel was least able to see
 * exactly when the pressure was highest — and it read healthy while doing it.
 */
class PortPressurePanelTest {

    private static String mockStats(long ports, double windowSeconds) {
        return "{\"served\":1050000,\"errors\":0,\"distinctPeerPorts\":" + ports
                + ",\"loadWindowSeconds\":" + windowSeconds + "}";
    }

    /**
     * The bench host's facts, supplied rather than probed.
     *
     * <p>These tests used to call the panel's host-reading overload, so they asserted against
     * whatever machine happened to run them — passing where the ephemeral range could be read and
     * failing where it could not, which is a test that reports the platform rather than the code.
     */
    private static final io.karatelabs.profiling.HostFacts.Network BENCH =
            new io.karatelabs.profiling.HostFacts.Network(64512, 60, 8192, "tcp_tw_reuse=1");

    /** A host whose range could not be read at all — the case that used to disable the guard. */
    private static final io.karatelabs.profiling.HostFacts.Network UNKNOWN =
            new io.karatelabs.profiling.HostFacts.Network(-1, -1, -1, null);

    private static String panel(long ports, double windowSeconds) {
        return panel(ports, windowSeconds, BENCH);
    }

    private static String panel(long ports, double windowSeconds,
                                io.karatelabs.profiling.HostFacts.Network network) {
        StringBuilder md = new StringBuilder();
        JfrDigest.appendPortPressure(md, mockStats(ports, windowSeconds), network);
        return md.toString();
    }

    /** A short run, ports nowhere near the range: the rate is real and worth printing. */
    @Test
    void testAnUnsaturatedCountStillReportsARate() {
        String md = panel(200, 3.1);
        // Not just "connections/s" — network.describe() contains "connections/s sustained", so
        // that substring would pass with the rate removed entirely.
        assertTrue(md.contains("— **65 connections/s**"),
                "an unsaturated count gives a usable rate: " + md);
        assertFalse(md.contains("LOWER BOUND"), md);
    }

    /**
     * The soak's own numbers. The panel must refuse the rate rather than print a 20x under-report,
     * and must say why — an operator who sees "5 connections/s" for a run doing 99 has been told
     * something worse than nothing.
     */
    @Test
    void testASaturatedCountRefusesToDeriveARate() {
        String md = panel(32_256, 7069.7);
        assertTrue(md.contains("LOWER BOUND"),
                "a saturated port count is not a connection count: " + md);
        assertFalse(md.contains("**5 connections/s**"),
                "the derived rate must not be printed once the count has saturated: " + md);
        assertTrue(md.contains("saturated"), md);
    }

    /**
     * A host whose ephemeral range cannot be read must still refuse the rate. The first version of
     * this guard was conditional on the range being known, so on any platform {@code HostFacts}
     * cannot read — which is where a reader has least other context — the misleading rate was
     * printed unchallenged. The mock's own 65,536-port counting ceiling is the platform-independent
     * lid to fall back on.
     */
    @Test
    void testAnUnknownPortRangeStillRefusesASaturatedRate() {
        String md = panel(32_256, 7069.7, UNKNOWN);
        assertTrue(md.contains("LOWER BOUND"),
                "an unreadable host range must fail closed, not disable the check: " + md);
        assertTrue(md.contains("counting ceiling"),
                "and must say which ceiling it fell back to: " + md);
        assertFalse(md.contains("**5 connections/s**"), md);
    }

    /** Fail-closed must not mean fail-always: a small count on an unknown host still gets a rate. */
    @Test
    void testAnUnknownPortRangeStillReportsASmallCount() {
        String md = panel(200, 3.1, UNKNOWN);
        assertTrue(md.contains("— **65 connections/s**"), md);
        assertFalse(md.contains("LOWER BOUND"), md);
    }

    /**
     * A burst that opens more ports than the threshold but finishes inside TIME_WAIT.
     *
     * <p>The count is provably exact there — nothing can have been recycled yet — so refusing a
     * rate is wrong twice over: it suppresses a correct number, and it suppresses the "offered Nx
     * the sustainable rate" warning, which only the unsaturated path prints and which is this
     * panel's most valuable output. The harness's own 0 ms tier is this shape: ~8,000 connections
     * in ~1.9 s.
     */
    @Test
    void testAShortBurstIsExactHoweverManyPortsItUsed() {
        String md = panel(8_000, 1.9, BENCH);
        assertFalse(md.contains("LOWER BOUND"),
                "1.9s is well inside a 60s TIME_WAIT, so no port can have been reused: " + md);
        assertTrue(md.contains("connections/s"), md);
        assertTrue(md.contains("sustainable rate"),
                "and the brevity warning — the point of the panel — must still fire: " + md);
    }
}
