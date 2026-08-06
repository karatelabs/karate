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
package io.karatelabs.profiling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mock's histogram is load-bearing: the parity experiment reads the difference between what
 * a client waited and what the server can account for, and a histogram that misreports the second
 * makes the first look like queueing that is not there.
 *
 * <p>These tests exist because the first implementation got exactly that wrong — the forward
 * bucketing and its inverse disagreed by one power of two, so the mock reported holding requests
 * for 29.7 ms that the client saw answered in 14 ms. It was caught only because that number was
 * impossible on its face. A subtler version of the same bug would have been believed.</p>
 */
class MockStatsTest {

    /**
     * Every value must land in a bucket whose reported bound actually contains it. This is the
     * property the round-trip bug violated, and it is worth checking across the whole range
     * rather than at a sample, because the failure was confined to one arm of the mapping.
     */
    @Test
    void testReportedPercentileContainsTheRecordedValue() {
        for (long micros : new long[]{0, 1, 15, 16, 17, 31, 32, 33, 100, 999, 1_000, 9_999,
                10_000, 13_000, 50_000, 999_999, 1_000_000, 60_000_000}) {
            MockStats stats = new MockStats();
            stats.record(micros * 1000, 0);
            long reported = jsonLong(stats.toJson(), "serviceMicrosP50");
            assertTrue(reported >= micros,
                    "reported p50 " + reported + " is below the recorded " + micros + " µs");
            // ~6% resolution above the linear region, plus a microsecond of slack for it
            long allowed = Math.max(micros + micros / 10 + 2, 16);
            assertTrue(reported <= allowed,
                    "reported p50 " + reported + " overstates the recorded " + micros + " µs");
        }
    }

    @Test
    void testPercentilesSeparateTheFastFromTheSlow() {
        MockStats stats = new MockStats();
        for (int i = 0; i < 95; i++) {
            stats.record(1_000_000, 0);       // 1 ms
        }
        for (int i = 0; i < 5; i++) {
            stats.record(100_000_000, 0);     // a 5% tail at 100 ms — one outlier in 100 would
        }                                     // not reach p99, since the 99th value is still fast
        String json = stats.toJson();
        assertEquals(100, jsonLong(json, "served"));
        long p50 = jsonLong(json, "serviceMicrosP50");
        long p99 = jsonLong(json, "serviceMicrosP99");
        assertTrue(p50 < 1_200, "p50 should sit at the 1 ms mass, got " + p50 + " µs");
        assertTrue(p99 >= 100_000, "the 100 ms outlier should reach p99, got " + p99 + " µs");
    }

    /**
     * "Held" is service plus the sleep actually taken, and it is what the client's latency is
     * compared against. Keeping the two separate is the whole point: the injected wait is the
     * simulated network, not the server's cost.
     */
    @Test
    void testHeldCountsTheSleepAndServiceDoesNot() {
        MockStats stats = new MockStats();
        stats.record(500_000, 10_000_000);    // 0.5 ms of work, 10 ms asleep
        String json = stats.toJson();
        assertTrue(jsonLong(json, "serviceMicrosP50") < 600, "service time must exclude the sleep");
        long held = jsonLong(json, "heldMicrosP50");
        assertTrue(held >= 10_500 && held <= 11_600, "held should be work + sleep, got " + held + " µs");
        assertEquals(10_000, jsonLong(json, "sleepMicrosMean"));
    }

    /**
     * Without this, a ramp's later points are still reporting percentiles dominated by its
     * earlier ones — which is how a saturated point can look healthy.
     */
    @Test
    void testResetStartsAFreshWindow() {
        MockStats stats = new MockStats();
        for (int i = 0; i < 100; i++) {
            stats.record(100_000_000, 0);     // 100 ms, enough to dominate any percentile
        }
        stats.reset();
        stats.record(1_000_000, 0);           // 1 ms
        String json = stats.toJson();
        assertEquals(1, jsonLong(json, "served"));
        assertTrue(jsonLong(json, "serviceMicrosP50") < 1_200,
                "the closed window must not colour the new one");
    }

    @Test
    void testPeakInFlightSurvivesTheRequestsThatCausedIt() {
        MockStats stats = new MockStats();
        stats.enter();
        stats.enter();
        stats.enter();
        stats.exit();
        stats.exit();
        stats.exit();
        String json = stats.toJson();
        assertEquals(0, jsonLong(json, "inFlight"));
        assertEquals(3, jsonLong(json, "peakInFlight"), "the peak is the point — it must not decay");
    }

    private static long jsonLong(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        assertTrue(at >= 0, "no key " + key + " in " + json);
        int colon = json.indexOf(':', at);
        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(colon + 1, end).trim());
    }

}
