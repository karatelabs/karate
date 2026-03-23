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

import io.karatelabs.core.PerfContext;

import java.util.Map;

/**
 * Test utility class for Java interop testing with Gatling.
 * Demonstrates custom performance event capture via PerfContext.
 */
public class TestUtils {

    /**
     * Simulates a custom RPC call and captures a performance event.
     * This demonstrates how Java code can report timing to Gatling via PerfContext.
     *
     * @param args    arguments from Karate feature
     * @param context PerfContext for capturing performance events
     * @return result map with success status and duration
     */
    public static Map<String, Object> myRpc(Map<String, Object> args, PerfContext context) {
        long start = System.currentTimeMillis();

        // Simulate some work (e.g., database call, gRPC, etc.)
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long end = System.currentTimeMillis();

        // Capture custom performance event for Gatling reporting
        context.capturePerfEvent("custom-rpc", start, end);

        return Map.of(
                "success", true,
                "duration", end - start
        );
    }

}
