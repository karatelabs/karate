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

    private static String panel(long ports, double windowSeconds) {
        StringBuilder md = new StringBuilder();
        JfrDigest.appendPortPressure(md, mockStats(ports, windowSeconds));
        return md.toString();
    }

    /** A short run, ports nowhere near the range: the rate is real and worth printing. */
    @Test
    void testAnUnsaturatedCountStillReportsARate() {
        String md = panel(200, 3.1);
        assertTrue(md.contains("connections/s"), "an unsaturated count gives a usable rate: " + md);
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
}
