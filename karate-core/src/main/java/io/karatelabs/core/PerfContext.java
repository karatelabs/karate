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

/**
 * Interface for capturing custom performance events from Java code.
 * <p>
 * This allows users to report timing for non-HTTP operations (database calls,
 * gRPC, custom protocols) to Gatling's statistics engine.
 * <p>
 * The {@code karate} object passed to Java code implements this interface,
 * so custom Java helpers can capture performance events:
 * <pre>
 * // In feature file:
 * * def result = Java.type('mock.MockUtils').myRpc({ sleep: 100 }, karate)
 *
 * // In MockUtils.java:
 * public static Map myRpc(Map args, PerfContext context) {
 *     long start = System.currentTimeMillis();
 *     // ... custom logic ...
 *     long end = System.currentTimeMillis();
 *     context.capturePerfEvent("myRpc", start, end);
 *     return Map.of("success", true);
 * }
 * </pre>
 */
public interface PerfContext {

    /**
     * Capture a custom performance event.
     * <p>
     * When running under Gatling, this event will be reported to the
     * Gatling statistics engine with OK status (code 200).
     * When not in performance mode, this is a no-op.
     *
     * @param name      the event name for Gatling reports
     * @param startTime the start time in epoch milliseconds
     * @param endTime   the end time in epoch milliseconds
     */
    void capturePerfEvent(String name, long startTime, long endTime);

}
