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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code format()} is not only for display — it is how a duration reaches the child JVM, so
 * losing precision here silently changes the run.
 *
 * <p>It used to truncate with {@code toSeconds()}. A review found the consequence: {@code --warmup
 * 1500ms} produced a JFR recording delayed by the full 1500 ms while the child was told
 * {@code warmup=1s} and warmed for 1000 — so 500 ms of warmup landed inside the "measured"
 * recording. Worse, {@code --warmup 500ms} reached the child as {@code 0s}, meaning **no warmup at
 * all**, while a warning printed the truncated value back at the operator and the digest's warmup
 * row claimed exclusion.
 *
 * <p>The round-trip is the property that matters: whatever {@code format()} writes,
 * {@code parseDuration()} must read back unchanged.
 */
class RunShapeFormatTest {

    private static void assertRoundTrips(Duration original) {
        String text = RunShape.format(original);
        assertEquals(original, RunShape.parseDuration(text),
                "format() -> parseDuration() lost precision via \"" + text + "\"");
    }

    @Test
    void testSubSecondDurationsKeepTheirMilliseconds() {
        assertEquals("500ms", RunShape.format(Duration.ofMillis(500)));
        assertEquals("1500ms", RunShape.format(Duration.ofMillis(1500)));
        assertEquals("250ms", RunShape.format(Duration.ofMillis(250)));
    }

    @Test
    void testWholeUnitsStillReadNaturally() {
        assertEquals("0s", RunShape.format(Duration.ZERO));
        assertEquals("5s", RunShape.format(Duration.ofSeconds(5)));
        assertEquals("10m", RunShape.format(Duration.ofMinutes(10)));
        assertEquals("1h", RunShape.format(Duration.ofHours(1)));
        assertEquals("7h", RunShape.format(Duration.ofHours(7)));
    }

    @Test
    void testEveryDurationSurvivesTheRoundTripToTheChild() {
        assertRoundTrips(Duration.ZERO);
        assertRoundTrips(Duration.ofMillis(1));
        assertRoundTrips(Duration.ofMillis(250));
        assertRoundTrips(Duration.ofMillis(500));
        assertRoundTrips(Duration.ofMillis(1500));
        assertRoundTrips(Duration.ofMillis(90_500));
        assertRoundTrips(Duration.ofSeconds(1));
        assertRoundTrips(Duration.ofSeconds(45));
        assertRoundTrips(Duration.ofMinutes(20));
        assertRoundTrips(Duration.ofHours(8));
    }

    /**
     * The exact case from the review: a 1500 ms warmup must not reach the child as 1s while the
     * recording is delayed by 1500 ms.
     */
    @Test
    void testTheWarmupTheChildIsToldMatchesTheOneTheRecordingSkips() {
        Duration warmup = Duration.ofMillis(1500);
        RunShape shape = RunShape.defaults().withWarmup(warmup);
        Duration asTheChildSeesIt = RunShape.parseDuration(RunShape.format(shape.warmup()));
        assertEquals(warmup.toMillis(), asTheChildSeesIt.toMillis(),
                "the child's warmup and the JFR delay are derived from the same value and must agree");
    }
}
