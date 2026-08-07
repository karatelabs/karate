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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Run shapes that would measure nothing, cleanly.
 *
 * <p>Every case here used to be accepted, and each produces a green exit and a well-formed
 * digest — which is the failure this harness is least equipped to survive, because `compare`
 * cannot tell such a run from a real one. A typo is the realistic source: `--threads 0` from a
 * shell variable that did not expand, `--duration 0s` from arithmetic on an empty string.
 */
class RunShapeTest {

    @Test
    void testZeroThreadsIsRejectedRatherThanRunningNoWorkSuccessfully() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withThreads(0));
    }

    @Test
    void testNegativeThreadsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withThreads(-4));
    }

    /**
     * A negative iteration count is the dangerous one: the worker loop reads {@code iterations
     * >= 0} as "there is a bound", so a negative value means <em>no</em> bound and the run
     * continues until the parent's timeout kills it — an hour later, by default.
     */
    @Test
    void testANegativeIterationBoundIsRejectedBecauseItMeansUnbounded() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withIterations(-1L));
    }

    @Test
    void testZeroIterationsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withIterations(0L));
    }

    @Test
    void testAZeroOrNegativeDurationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withDuration(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withDuration(Duration.ofSeconds(-30)));
    }

    @Test
    void testANegativeWarmupIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withWarmup(Duration.ofSeconds(-1)));
    }

    @Test
    void testAZeroTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RunShape.defaults().withTimeout(Duration.ZERO));
    }

    /** The bounds are mutually exclusive, and setting one must clear the other. */
    @Test
    void testSettingOneBoundClearsTheOther() {
        RunShape byDuration = RunShape.defaults().withDuration(Duration.ofMinutes(5));
        assertEquals(-1, byDuration.iterations(),
                "a duration-bounded shape carries -1 iterations, which validation must allow");

        RunShape byIterations = byDuration.withIterations(1000L);
        assertEquals(null, byIterations.duration());
        assertEquals(1000, byIterations.iterations());
    }

    /** Zero warmup is legitimate — the self-driving workloads use it. */
    @Test
    void testZeroWarmupIsAllowed() {
        assertEquals(Duration.ZERO, RunShape.defaults().withWarmup(Duration.ZERO).warmup());
    }
}
