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
package io.karatelabs.core;

import io.karatelabs.gherkin.Scenario;

/**
 * A failure replayed from a run-once cache ({@code callonce}, {@code karate.callonce()},
 * {@code karate.setupOnce()}, {@code karate.callSingle()}): the call failed once and is not
 * run again, so later callers fail fast with the same reason but have nothing of their own
 * to show. The called feature's steps, HTTP traffic and nested calls live only under the
 * scenario that ran it. This names that scenario in the message, and as {@link #getOrigin()}
 * for the HTML report to link to, so the reader of any failing scenario is one click from
 * the one holding the trace. See {@link OnceCache}.
 */
public class CachedFailureException extends RuntimeException {

    private final Scenario origin;
    private final boolean originInThisRun;

    CachedFailureException(Throwable cause, Scenario origin, boolean originInThisRun) {
        super(message(cause.getMessage(), origin), cause);
        this.origin = origin;
        this.originInThisRun = originInThisRun;
    }

    /**
     * The top-level scenario whose report holds the failed call's trace, when it is in this
     * run's report: null when the trace went nowhere (the call ran inside an {@code @setup}
     * scenario, whose result is not reported) or into another Suite's report (a host-shared
     * cache, as karate-gatling injects).
     */
    public Scenario getOrigin() {
        return originInThisRun ? origin : null;
    }

    private static String message(String reason, Scenario origin) {
        // the pointer goes first: the reason may span lines (a match failure, a nested replay)
        return origin == null ? "(cached failure) " + reason
                : "(cached failure, it ran in " + describe(origin) + ") " + reason;
    }

    /** {@code path/to/some.feature[1.2:7] scenario name}: the feature, the scenario's refId and its name. */
    public static String describe(Scenario scenario) {
        String where = scenario.getFeature().getResource().getRelativePath() + scenario.getRefId();
        String name = scenario.getName();
        return name == null || name.isEmpty() ? where : where + " " + name;
    }

}
