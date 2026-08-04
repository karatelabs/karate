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

import java.time.Duration;

/**
 * How long and how wide to run.
 *
 * <p>A workload is bounded either by {@code iterations} or by {@code duration}, never
 * both — iteration-bounded suits a reproduction (same work every time, so two runs are
 * comparable), duration-bounded suits a soak (the heap-after-GC floor over a fixed
 * window is the measurement).
 *
 * @param threads    concurrency; each is a virtual thread
 * @param iterations total iterations across all threads, or -1 when duration-bounded
 * @param duration   measured window, or null when iteration-bounded
 * @param warmup     excluded from the measured window; the JFR recording is delayed past it
 * @param timeout    parent-side wall-clock cap, after which the child is dumped and killed
 */
public record RunShape(int threads, long iterations, Duration duration, Duration warmup, Duration timeout) {

    public static RunShape defaults() {
        return new RunShape(16, 5000, null, Duration.ofSeconds(5), null);
    }

    public boolean isDurationBounded() {
        return duration != null;
    }

    public RunShape withThreads(Integer value) {
        return value == null ? this : new RunShape(value, iterations, duration, warmup, timeout);
    }

    /** Setting an iteration bound clears any duration bound — they are mutually exclusive. */
    public RunShape withIterations(Long value) {
        return value == null ? this : new RunShape(threads, value, null, warmup, timeout);
    }

    /** Setting a duration bound clears any iteration bound. */
    public RunShape withDuration(Duration value) {
        return value == null ? this : new RunShape(threads, -1, value, warmup, timeout);
    }

    public RunShape withWarmup(Duration value) {
        return value == null ? this : new RunShape(threads, iterations, duration, value, timeout);
    }

    public RunShape withTimeout(Duration value) {
        return value == null ? this : new RunShape(threads, iterations, duration, warmup, value);
    }

    /**
     * Effective wall-clock cap. An explicit {@code --timeout} always wins. Otherwise a
     * duration-bounded run gets its window plus generous slack, and an iteration-bounded
     * run — whose runtime is unknown by construction — gets a flat hour.
     *
     * <p>This is never merely belt-and-braces: Karate runs scenarios on a virtual-thread
     * executor inside try-with-resources whose {@code close()} waits indefinitely, so an
     * {@code OutOfMemoryError} swallowed in a worker can leave the child alive-but-dead.
     * Without a cap the operator waits forever on a digest that is only written on exit.
     */
    public Duration effectiveTimeout() {
        if (timeout != null) {
            return timeout;
        }
        if (duration != null) {
            return duration.plus(warmup).plusMinutes(5);
        }
        return Duration.ofHours(1);
    }

    /** Accepts "30s", "10m", "1h", or a bare number of seconds. */
    public static Duration parseDuration(String s) {
        String v = s.trim().toLowerCase();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("empty duration");
        }
        char unit = v.charAt(v.length() - 1);
        if (Character.isDigit(unit)) {
            return Duration.ofSeconds(Long.parseLong(v));
        }
        long amount = Long.parseLong(v.substring(0, v.length() - 1));
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            default -> throw new IllegalArgumentException("unknown duration unit: " + s + " (expected s, m or h)");
        };
    }

    public static String format(Duration d) {
        long seconds = d.toSeconds();
        if (seconds % 3600 == 0 && seconds > 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0 && seconds > 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

}
