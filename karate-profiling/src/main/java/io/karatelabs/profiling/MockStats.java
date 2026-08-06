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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * What {@link LatencyMock} knows about itself: how much it served, how long its own work took,
 * and how many requests it was holding at once.
 *
 * <p><b>This is the instrument's credibility, not its telemetry.</b> A mock exists here to be a
 * reference clock — the client's numbers only mean something while the server is demonstrably
 * not the bottleneck. These counters are how that is established rather than assumed. See
 * docs/PROFILING.md §10.</p>
 *
 * <p><b>Read the service-time percentiles in one direction only.</b> The clock starts at handler
 * entry, so it cannot see the accept queue, the dispatcher thread that owns socket readiness, or
 * the executor handoff. A mock saturated <i>upstream of this clock</i> reports a flat p99 while
 * clients queue. So a rising p99 proves the mock is the bottleneck; a flat p99 proves nothing on
 * its own — pair it with the calibrated knee (§10 B2) and with the client-side queueing residue
 * the digest derives.</p>
 *
 * <p>Everything here is lock-free by construction: adders, a fixed-bucket histogram and an atomic
 * max. There is deliberately no per-request logging and no synchronized histogram — those are the
 * ways instrumentation stops measuring and starts perturbing.</p>
 */
final class MockStats {

    /**
     * Microsecond histogram, HdrHistogram-shaped: linear below 16 µs, then 16 sub-buckets per
     * power of two, giving ~6% resolution everywhere above that. One array increment per request,
     * no allocation, no lock.
     *
     * <p>The first cut used plain powers of two and was <b>useless at the scale this measures</b>:
     * at a 10 ms injected latency the bucket spanning 13 ms is 8 ms wide, so every percentile
     * pinned to the same bucket bound and the derived numbers came out constant — and negative.
     * Resolution has to be finer than the difference being looked for, which here is a millisecond
     * of queueing on top of a ten-millisecond wait.</p>
     */
    private static final int SUB_BUCKETS = 16;
    private static final int BUCKETS = 640;

    private final LongAdder[] histogram = new LongAdder[BUCKETS];
    private final LongAdder[] heldHistogram = new LongAdder[BUCKETS];
    private final LongAdder served = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder serviceNanosTotal = new LongAdder();
    private final LongAdder sleptNanosTotal = new LongAdder();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong peakInFlight = new AtomicLong();
    private final long startedNanos = System.nanoTime();

    MockStats() {
        for (int i = 0; i < BUCKETS; i++) {
            histogram[i] = new LongAdder();
            heldHistogram[i] = new LongAdder();
        }
    }

    /** Call on handler entry; returns the current in-flight count so the caller can pair it. */
    int enter() {
        int current = inFlight.incrementAndGet();
        peakInFlight.getAndAccumulate(current, Math::max);
        return current;
    }

    void exit() {
        inFlight.decrementAndGet();
    }

    int inFlight() {
        return inFlight.get();
    }

    /**
     * Record one request.
     *
     * @param serviceNanos time this handler spent working — <b>excluding the injected latency</b>,
     *                     which is the point: the injected sleep is the simulated network, not
     *                     the server's cost, and mixing them would hide exactly the saturation
     *                     this is here to detect
     * @param sleptNanos   what the injected sleep <i>actually</i> cost. Not the nominal knob
     *                     value: {@code Thread.sleep(10ms)} lands nearer 13 ms once scheduler
     *                     granularity is counted, and a caller subtracting the nominal 10 would
     *                     mistake a fixed 3 ms of sleep overshoot for 3 ms of queueing
     */
    void record(long serviceNanos, long sleptNanos) {
        served.increment();
        serviceNanosTotal.add(serviceNanos);
        sleptNanosTotal.add(sleptNanos);
        histogram[bucket(serviceNanos / 1000)].increment();
        // Entry to response complete, sleep included: what the client was waiting on that this
        // server can account for. Anything the client saw beyond THIS is queueing upstream of
        // the handler, which is the blind spot named above and the reason the pair is reported.
        heldHistogram[bucket((serviceNanos + sleptNanos) / 1000)].increment();
    }

    void recordError() {
        errors.increment();
    }

    static int bucket(long micros) {
        if (micros < SUB_BUCKETS) {
            return (int) Math.max(micros, 0);
        }
        int exponent = 63 - Long.numberOfLeadingZeros(micros);
        int shift = exponent - 4;                                  // puts (micros >> shift) in [16, 31]
        int sub = (int) ((micros >> shift) & (SUB_BUCKETS - 1));   // implicit leading 1 stripped
        return Math.min((shift << 4) + sub + SUB_BUCKETS, BUCKETS - 1);
    }

    /** Inverse of {@link #bucket}: the upper bound in µs of everything that lands in this index. */
    static long bucketUpperMicros(int index) {
        if (index < SUB_BUCKETS) {
            return index;
        }
        int shift = (index - SUB_BUCKETS) >> 4;
        int sub = (index - SUB_BUCKETS) & (SUB_BUCKETS - 1);
        return ((long) (SUB_BUCKETS + sub) << shift) + (1L << shift) - 1;
    }

    /**
     * Zero everything, so the next window is measured on its own. The calibration ramp and the
     * matrix both need per-point numbers, and a cumulative histogram cannot give them — the first
     * ramp point would still be dominating the percentiles at the last one.
     *
     * <p>Called between points, while the server is idle. It is deliberately not atomic with
     * respect to in-flight requests: making it so would put a lock on the request path, which is
     * a far worse trade than a handful of requests landing on either side of a reset that happens
     * when nothing is in flight.</p>
     */
    void reset() {
        for (int i = 0; i < BUCKETS; i++) {
            histogram[i].reset();
            heldHistogram[i].reset();
        }
        served.reset();
        errors.reset();
        serviceNanosTotal.reset();
        sleptNanosTotal.reset();
        peakInFlight.set(inFlight.get());
    }

    /**
     * Upper bound in microseconds of the bucket holding the given percentile, or 0 if nothing
     * served.
     *
     * <p>The denominator is summed from <b>this histogram</b>, not from {@link #served}. Those two
     * disagree by design for the length of a race: {@code record} increments {@code served} first
     * and the buckets last, so a {@code /stats} read landing between them sees a total larger than
     * the mass it is searching. The old code took {@code served} as the denominator and, when the
     * target overshot, fell off the end of the loop and reported the last bucket's bound — decades,
     * presented as a percentile. Summing the array cannot overshoot it.</p>
     */
    private long percentileMicros(LongAdder[] buckets, double percentile) {
        long total = 0;
        for (LongAdder bucket : buckets) {
            total += bucket.sum();
        }
        if (total == 0) {
            return 0;
        }
        long target = (long) Math.ceil(total * percentile);
        long cumulative = 0;
        for (int i = 0; i < BUCKETS; i++) {
            cumulative += buckets[i].sum();
            if (cumulative >= target) {
                return bucketUpperMicros(i);
            }
        }
        return bucketUpperMicros(BUCKETS - 1);
    }

    /**
     * A JSON object, hand-rolled. No JSON library on purpose — a reference clock must not share a
     * parser with the thing it is measuring, or a regression in that parser moves the reference.
     */
    String toJson() {
        long total = served.sum();
        double elapsedSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        return "{\"served\":" + total
                + ",\"errors\":" + errors.sum()
                + ",\"inFlight\":" + inFlight.get()
                + ",\"peakInFlight\":" + peakInFlight.get()
                + ",\"serviceMicrosMean\":" + (total == 0 ? 0 : serviceNanosTotal.sum() / total / 1000)
                + ",\"serviceMicrosP50\":" + percentileMicros(histogram, 0.50)
                + ",\"serviceMicrosP95\":" + percentileMicros(histogram, 0.95)
                + ",\"serviceMicrosP99\":" + percentileMicros(histogram, 0.99)
                // What the client was waiting on that this server can account for — service plus
                // the sleep it actually took. The client's own latency minus this is the part
                // nobody's clock owns, which is the number the calibration reads.
                + ",\"heldMicrosP50\":" + percentileMicros(heldHistogram, 0.50)
                + ",\"heldMicrosP99\":" + percentileMicros(heldHistogram, 0.99)
                // Measured, not nominal: sleep overshoot is real and constant, and subtracting
                // the knob value instead of this would book it as queueing.
                + ",\"sleepMicrosMean\":" + (total == 0 ? 0 : sleptNanosTotal.sum() / total / 1000)
                + ",\"elapsedSeconds\":" + String.format("%.3f", elapsedSeconds)
                + "}";
    }

    /** The one-line shutdown summary. Keep the prefix stable — the parent greps for it. */
    String summary() {
        return LatencyMock.STATS_PREFIX + toJson();
    }

}
