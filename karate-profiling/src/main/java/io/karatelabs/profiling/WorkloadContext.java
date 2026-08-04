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

/**
 * What a {@link Workload} is told about the run it is part of. Deliberately thin: a
 * workload should not be able to observe the measurement apparatus, only the shape of
 * the work it has been asked to do.
 *
 * @param threads    concurrency it will be driven at — or, for a workload that
 *                   {@link Workload#drivesOwnConcurrency() drives itself}, the concurrency it
 *                   should apply internally
 * @param iterations total iterations requested, or -1 for a duration-bounded run. Only
 *                   meaningful to a self-driving workload, which decides what an "iteration"
 *                   means for it (a scenario, say, rather than a feature execution)
 * @param mockUrl    base URL of the sibling mock JVM, or null when {@code needsMock()} is false
 */
public record WorkloadContext(int threads, long iterations, String mockUrl) {

    /** Fails loudly rather than letting a workload silently build requests against "null/path". */
    public String requireMockUrl() {
        if (mockUrl == null) {
            throw new IllegalStateException("no mock url — workload must override needsMock() to return true");
        }
        return mockUrl;
    }

}
