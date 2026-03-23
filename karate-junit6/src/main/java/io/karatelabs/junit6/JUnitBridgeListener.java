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
import io.karatelabs.output.ResultListener;

import java.util.concurrent.BlockingQueue;

/**
 * Bridges Karate execution events to JUnit's dynamic test tree via a blocking queue.
 * <p>
 * Implements {@link ResultListener} to receive streaming events from Karate execution
 * and converts them to {@link TestEvent} records that are consumed by
 * {@link StreamingTestIterator} on the JUnit thread.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Flat mode</b>: All scenarios appear at the root level</li>
 *   <li><b>Hierarchical mode</b>: Features as containers with scenario children</li>
 * </ul>
 */
public class JUnitBridgeListener implements ResultListener {

    private final BlockingQueue<TestEvent> eventQueue;
    private final boolean hierarchical;

    /**
     * Creates a new bridge listener.
     *
     * @param eventQueue   the queue to publish events to
     * @param hierarchical if true, emit FeatureStart/FeatureEnd events for container nesting
     */
    public JUnitBridgeListener(BlockingQueue<TestEvent> eventQueue, boolean hierarchical) {
        this.eventQueue = eventQueue;
        this.hierarchical = hierarchical;
    }

    @Override
    public void onFeatureStart(Feature feature) {
        if (hierarchical) {
            putEvent(new TestEvent.FeatureStart(feature));
        }
    }

    @Override
    public void onFeatureEnd(FeatureResult result) {
        if (hierarchical) {
            putEvent(new TestEvent.FeatureEnd(result));
        }
    }

    @Override
    public void onScenarioEnd(ScenarioResult result) {
        putEvent(new TestEvent.ScenarioEnd(result));
    }

    @Override
    public void onSuiteEnd(SuiteResult result) {
        putEvent(new TestEvent.SuiteEnd(result));
    }

    private void putEvent(TestEvent event) {
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing test event", e);
        }
    }

}
