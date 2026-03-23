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

import io.karatelabs.http.HttpRequest;

/**
 * Hook interface for performance testing integration (e.g., Gatling).
 * <p>
 * Implementations receive HTTP request timing events and can report them
 * to external performance testing frameworks.
 * <p>
 * The event lifecycle:
 * <ol>
 *   <li>{@link #getPerfEventName} is called before HTTP request to get the name</li>
 *   <li>HTTP request executes with timing captured</li>
 *   <li>{@link #reportPerfEvent} is called with the completed event</li>
 * </ol>
 * <p>
 * Events are "held" until the next HTTP request or scenario end, so that
 * assertion failures after an HTTP request can be attributed to that request.
 */
public interface PerfHook {

    /**
     * Get the name for a performance event based on the HTTP request.
     * <p>
     * This is called before the request executes. The implementation can
     * use URI patterns or custom logic to determine the name that will
     * appear in performance reports.
     *
     * @param request the HTTP request about to be executed
     * @param runtime the scenario runtime context
     * @return the name for this request in performance reports, or null to skip reporting
     */
    String getPerfEventName(HttpRequest request, ScenarioRuntime runtime);

    /**
     * Report a completed performance event.
     * <p>
     * Called after HTTP request completion (possibly with failure info added later).
     * The event includes timing, status code, and any failure message.
     *
     * @param event the completed performance event
     */
    void reportPerfEvent(PerfEvent event);

    /**
     * Submit a task for execution in the performance framework's execution model.
     * <p>
     * For Gatling, this ensures tasks run within Gatling's scheduler.
     * Default implementation runs synchronously.
     *
     * @param task the task to execute
     */
    default void submit(Runnable task) {
        task.run();
    }

    /**
     * Called after a feature completes execution.
     * <p>
     * Allows the performance framework to perform cleanup or final reporting.
     *
     * @param result the feature execution result
     */
    default void afterFeature(FeatureResult result) {
        // default no-op
    }

    /**
     * Pause execution for the specified duration.
     * <p>
     * For Gatling integration, this uses Gatling's non-blocking pause mechanism
     * rather than Thread.sleep(), which is important for virtual user scaling.
     *
     * @param millis the pause duration in milliseconds
     */
    default void pause(Number millis) {
        try {
            Thread.sleep(millis.longValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
