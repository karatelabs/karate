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
package io.karatelabs.gatling;

import io.karatelabs.core.PerfEvent;
import scala.Option;

/**
 * Java interface to Gatling's StatsEngine for reporting performance metrics.
 * This allows pure Java code to report metrics without direct Scala dependencies.
 */
public interface GatlingStatsReporter {

    /**
     * Report a performance event to Gatling's StatsEngine.
     *
     * @param scenario      the scenario name
     * @param groups        the current groups
     * @param requestName   the request name
     * @param startTime     request start time (epoch ms)
     * @param endTime       request end time (epoch ms)
     * @param ok            true if request succeeded
     * @param statusCode    HTTP status code
     * @param errorMessage  error message (null if ok)
     */
    void logResponse(
            String scenario,
            scala.collection.immutable.List<String> groups,
            String requestName,
            long startTime,
            long endTime,
            boolean ok,
            int statusCode,
            String errorMessage
    );

    /**
     * Convenience method to report a PerfEvent.
     */
    default void reportPerfEvent(String scenario, scala.collection.immutable.List<String> groups, PerfEvent event) {
        logResponse(
                scenario,
                groups,
                event.getName(),
                event.getStartTime(),
                event.getEndTime(),
                !event.isFailed(),
                event.getStatusCode(),
                event.getMessage()
        );
    }
}
