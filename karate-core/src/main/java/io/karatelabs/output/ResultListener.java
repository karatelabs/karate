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
package io.karatelabs.output;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.gherkin.Feature;

/**
 * Interface for receiving test execution results as they stream in.
 * <p>
 * Unlike {@link RuntimeHook}, ResultListener is purely observational and cannot
 * abort execution. Use this for reporting, telemetry, and external integrations.
 * <p>
 * The scenario is the smallest unit of granularity - step-level events are not
 * streamed as they add overhead without significant reporting value.
 * <p>
 * Example usage:
 * <pre>
 * Runner.path("features/")
 *     .resultListener(new HtmlReportListener())
 *     .resultListener(new TelemetryListener())
 *     .parallel(10);
 * </pre>
 */
public interface ResultListener {

    /**
     * Called when the suite starts execution.
     *
     * @param suite the suite about to run
     */
    default void onSuiteStart(Suite suite) {
    }

    /**
     * Called when the suite completes execution.
     *
     * @param result the final suite result
     */
    default void onSuiteEnd(SuiteResult result) {
    }

    /**
     * Called when a feature starts execution.
     *
     * @param feature the feature about to run
     */
    default void onFeatureStart(Feature feature) {
    }

    /**
     * Called when a feature completes execution.
     *
     * @param result the feature result
     */
    default void onFeatureEnd(FeatureResult result) {
    }

    /**
     * Called when a scenario starts execution.
     *
     * @param scenario the scenario about to run
     */
    default void onScenarioStart(io.karatelabs.gherkin.Scenario scenario) {
    }

    /**
     * Called when a scenario completes execution.
     * This is the finest granularity - step events are not streamed.
     *
     * @param result the scenario result
     */
    default void onScenarioEnd(ScenarioResult result) {
    }

}
