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
package io.karatelabs.profiling.gatling;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CoreDsl;

import java.time.Duration;

/**
 * How a simulation learns the shape of the run it is part of.
 *
 * <p>Gatling instantiates a simulation by class name and has nowhere to pass arguments, so
 * the workload publishes the shape as system properties first and the simulation reads it
 * here. The indirection is Gatling's, not ours — this class exists so exactly one place
 * knows the property names.
 *
 * <p>The defaults are deliberately tiny rather than realistic: a simulation that finds no
 * properties was not launched by the harness, and a one-user single-rep run is the least
 * misleading thing to do about it.
 */
final class SimShape {

    private SimShape() {
    }

    static int users() {
        return Integer.getInteger(GatlingWorkload.USERS_PROPERTY, 1);
    }

    static int reps() {
        return Integer.getInteger(GatlingWorkload.REPS_PROPERTY, 1);
    }

    /**
     * Wrap one virtual user's work in whichever loop this run asked for — a repetition count, or
     * a window when the run is duration-bounded.
     *
     * <p><b>Every simulation goes through here rather than writing its own loop.</b> There are six
     * of them and they exist to be compared in pairs; a soak that worked for some and silently
     * ran one pass for the others would produce a table where the difference between two arms is
     * the loop they happened to use. One place decides, so the arms cannot disagree.
     */
    static ChainBuilder loop(ChainBuilder body) {
        long seconds = Long.getLong(GatlingWorkload.DURATION_SECONDS_PROPERTY, 0L);
        return seconds > 0
                ? CoreDsl.during(Duration.ofSeconds(seconds)).on(body)
                : CoreDsl.repeat(reps()).on(body);
    }

    /**
     * The shared connection pool, when this run asked for one — a single instance for the whole
     * simulation, which is the entire point: a per-virtual-user pool would pool nothing.
     *
     * <p>Null unless {@code -Dkarate.profiling.pooled=true}. The A/B this exists for is whether
     * pooling collapses Karate's connection-per-iteration shape (4000 distinct client ports in a
     * measured run, against plain Gatling's 8); the mock reports {@code distinctPeerPorts}, so the
     * answer is read straight off the digest.
     */
    static final io.karatelabs.profiling.PooledHttpClientFactory POOL =
            Boolean.getBoolean("karate.profiling.pooled")
                    // Sized above the virtual-user count so the pool is never the bottleneck —
                    // otherwise the A/B measures the pool rather than Karate.
                    ? new io.karatelabs.profiling.PooledHttpClientFactory(
                            Math.max(64, users() * 4), Math.max(64, users() * 4))
                    : null;

    /** The sibling mock JVM's base URL. Fails loudly rather than building requests against "null". */
    static String mockUrl() {
        String url = System.getProperty(GatlingWorkload.MOCK_URL_PROPERTY);
        if (url == null) {
            throw new IllegalStateException("no mock url — the workload must override needsMock()");
        }
        return url;
    }

}
