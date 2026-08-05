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

    /** The sibling mock JVM's base URL. Fails loudly rather than building requests against "null". */
    static String mockUrl() {
        String url = System.getProperty(GatlingWorkload.MOCK_URL_PROPERTY);
        if (url == null) {
            throw new IllegalStateException("no mock url — the workload must override needsMock()");
        }
        return url;
    }

}
