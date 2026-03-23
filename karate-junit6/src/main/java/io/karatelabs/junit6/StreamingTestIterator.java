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
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A blocking iterator that yields {@link DynamicNode} instances as Karate scenarios complete.
 * <p>
 * This iterator bridges the gap between Karate's streaming execution model and JUnit's
 * {@code @TestFactory} pattern. The iterator blocks on {@link #hasNext()} until test events
 * arrive from the Karate execution thread, enabling true streaming test discovery.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Flat mode</b>: Each scenario becomes a top-level {@link DynamicTest}</li>
 *   <li><b>Hierarchical mode</b>: Features become {@link DynamicContainer}s containing scenario tests</li>
 * </ul>
 */
public class StreamingTestIterator implements Iterator<DynamicNode> {

    private static final long DEFAULT_TIMEOUT_MINUTES = 30;

    private final BlockingQueue<TestEvent> eventQueue;
    private final boolean hierarchical;
    private final long timeoutMinutes;

    private DynamicNode bufferedNode;
    private boolean finished;

    // Hierarchical mode state
    private Feature currentFeature;
    private final List<DynamicTest> currentFeatureTests = new ArrayList<>();

    /**
     * Creates a new streaming iterator with default timeout.
     *
     * @param eventQueue   the queue to consume events from
     * @param hierarchical if true, group scenarios under feature containers
     */
    public StreamingTestIterator(BlockingQueue<TestEvent> eventQueue, boolean hierarchical) {
        this(eventQueue, hierarchical, DEFAULT_TIMEOUT_MINUTES);
    }

    /**
     * Creates a new streaming iterator with custom timeout.
     *
     * @param eventQueue     the queue to consume events from
     * @param hierarchical   if true, group scenarios under feature containers
     * @param timeoutMinutes maximum time to wait for next event
     */
    public StreamingTestIterator(BlockingQueue<TestEvent> eventQueue, boolean hierarchical, long timeoutMinutes) {
        this.eventQueue = eventQueue;
        this.hierarchical = hierarchical;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    public boolean hasNext() {
        if (finished) {
            return false;
        }
        if (bufferedNode != null) {
            return true;
        }
        try {
            bufferedNode = pollForNextNode();
            return bufferedNode != null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public DynamicNode next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more test events");
        }
        DynamicNode node = bufferedNode;
        bufferedNode = null;
        return node;
    }

    /**
     * Returns this iterator as a {@link Stream} for use with {@code @TestFactory}.
     *
     * @return a sequential stream of dynamic nodes
     */
    public Stream<DynamicNode> stream() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED),
                false
        );
    }

    private DynamicNode pollForNextNode() throws InterruptedException {
        while (!finished) {
            TestEvent event = eventQueue.poll(timeoutMinutes, TimeUnit.MINUTES);
            if (event == null) {
                throw new RuntimeException("Timeout waiting for Karate test events after " + timeoutMinutes + " minutes");
            }

            DynamicNode node = processEvent(event);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private DynamicNode processEvent(TestEvent event) {
        return switch (event) {
            case TestEvent.FeatureStart(Feature feature) -> {
                currentFeature = feature;
                currentFeatureTests.clear();
                yield null; // No node yet, wait for scenarios
            }

            case TestEvent.ScenarioEnd(ScenarioResult result) -> {
                DynamicTest test = createDynamicTest(result);
                if (hierarchical) {
                    currentFeatureTests.add(test);
                    yield null; // Wait for FeatureEnd to emit container
                }
                yield test; // Flat mode: emit immediately
            }

            case TestEvent.FeatureEnd(FeatureResult result) -> {
                if (!hierarchical) {
                    yield null;
                }
                DynamicContainer container = createFeatureContainer(result);
                currentFeature = null;
                currentFeatureTests.clear();
                yield container;
            }

            case TestEvent.SuiteEnd ignored -> {
                finished = true;
                yield null;
            }
        };
    }

    private DynamicTest createDynamicTest(ScenarioResult result) {
        Scenario scenario = result.getScenario();
        String displayName = scenario.getRefIdAndName();
        URI testSourceUri = scenario.getUriToLineNumber();

        return DynamicTest.dynamicTest(displayName, testSourceUri, () -> {
            if (result.isFailed()) {
                Throwable error = result.getError();
                if (error != null) {
                    // Preserve original stack trace
                    throw error;
                }
                // Fallback to assertion failure with descriptive message
                String message = result.getFailureMessageForDisplay();
                Assertions.fail(message != null ? message : "Scenario failed");
            }
            // Passed - test body does nothing
        });
    }

    private DynamicContainer createFeatureContainer(FeatureResult result) {
        Feature feature = result.getFeature();
        String displayName = feature.getNameForReport();
        URI featureUri = feature.getResource().getUri();

        // Use the collected tests from ScenarioEnd events
        List<DynamicTest> tests = new ArrayList<>(currentFeatureTests);

        return DynamicContainer.dynamicContainer(displayName, featureUri, tests.stream());
    }

}
