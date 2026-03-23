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
package io.karatelabs.junit6;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.gherkin.Feature;

/**
 * Sealed hierarchy of test events for streaming dynamic test generation.
 * <p>
 * Uses Java 17+ sealed interfaces and records for type-safe, immutable event handling.
 * Events flow from Karate execution thread to JUnit thread via a blocking queue.
 */
public sealed interface TestEvent permits
        TestEvent.FeatureStart,
        TestEvent.FeatureEnd,
        TestEvent.ScenarioEnd,
        TestEvent.SuiteEnd {

    /**
     * Fired when a feature starts execution.
     * Used in hierarchical mode to create DynamicContainer boundaries.
     */
    record FeatureStart(Feature feature) implements TestEvent {
    }

    /**
     * Fired when a feature completes execution.
     * In hierarchical mode, triggers creation of a DynamicContainer with all scenario results.
     */
    record FeatureEnd(FeatureResult result) implements TestEvent {
    }

    /**
     * Fired when a scenario completes execution.
     * In flat mode, immediately creates a DynamicTest.
     * In hierarchical mode, collected until FeatureEnd.
     */
    record ScenarioEnd(ScenarioResult result) implements TestEvent {
    }

    /**
     * Fired when the suite completes execution.
     * Signals the end of the event stream (poison pill pattern).
     */
    record SuiteEnd(SuiteResult result) implements TestEvent {
    }

}
