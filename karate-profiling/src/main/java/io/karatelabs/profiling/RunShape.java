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

    /**
     * Rejects shapes that would run cleanly and measure nothing. Every case here produces a
     * green exit and a well-formed digest, which is the failure mode this harness is least able
     * to survive: {@code --threads 0} starts no workers and reports a successful run of zero
     * iterations; {@code --iterations 0} the same; a negative iteration count reads as "no
     * bound" to the worker loop and runs until the timeout kills it; {@code --duration 0s} closes
     * the window before the first iteration is claimed. A typo in any of these is indistinguishable
     * from a real result once it reaches `compare`.
     */
    public RunShape {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0, got " + threads);
        }
        if (duration == null && iterations <= 0) {
            throw new IllegalArgumentException(
                    "an iteration-bounded run needs iterations > 0, got " + iterations);
        }
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("duration must be > 0, got " + format(duration));
        }
        if (warmup == null || warmup.isNegative()) {
            throw new IllegalArgumentException("warmup must be >= 0, got " + warmup);
        }
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be > 0, got " + format(timeout));
        }
    }

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

    /**
     * Accepts "250ms", "30s", "10m", "1h", or a bare number of seconds. The {@code ms} case is
     * checked before the single-character units, because "10ms" ends in 's' and would otherwise
     * be read as seconds — and then fail parsing "10m" as a number, which is a confusing way to
     * learn that milliseconds are not supported.
     */
    public static Duration parseDuration(String s) {
        String v = s.trim().toLowerCase();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("empty duration");
        }
        if (v.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2)));
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
            default -> throw new IllegalArgumentException("unknown duration unit: " + s + " (expected ms, s, m or h)");
        };
    }

    /**
     * Human-readable and <b>lossless</b>. It used to truncate with {@code toSeconds()}, which was
     * fine for display and wrong everywhere else it was used: {@code --warmup 1500ms} reached the
     * child as {@code warmup=1s} while the JFR recording was delayed by the full 1500 ms, and
     * {@code --warmup 500ms} reached it as {@code 0s} — no warmup at all, against a digest row
     * still claiming the warmup was excluded. Anything sub-second now keeps its milliseconds, and
     * {@link #parseDuration} reads them back.
     */
    public static String format(Duration d) {
        long millis = d.toMillis();
        if (millis % 1000 != 0) {
            return millis + "ms";
        }
        long seconds = millis / 1000;
        if (seconds % 3600 == 0 && seconds > 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0 && seconds > 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

}
