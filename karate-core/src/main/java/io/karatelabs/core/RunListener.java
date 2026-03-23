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
 * Unified listener interface for runtime events.
 * Replaces the separate RuntimeHook and ResultListener interfaces with a single, consistent API.
 *
 * <p>Usage example:</p>
 * <pre>
 * Suite.of("features/")
 *     .listener(event -> {
 *         switch (event.getType()) {
 *             case SCENARIO_ENTER -> {
 *                 System.out.println("Starting: " + event.getScenarioRuntime().getScenario().getName());
 *             }
 *             case STEP_EXIT -> {
 *                 StepResult sr = event.getStepResult();
 *                 if (sr != null && sr.isFailed()) {
 *                     System.out.println("Step failed: " + event.getStep().getText());
 *                 }
 *             }
 *         }
 *         return true;
 *     })
 *     .run();
 * </pre>
 *
 * <p>Thread safety: Events are fired on different threads during parallel execution.
 * Implementations must be thread-safe - avoid shared mutable state or use proper synchronization.</p>
 */
@FunctionalInterface
public interface RunListener {

    /**
     * Called for all runtime events.
     *
     * @param event the event containing type, runtime objects, and results
     * @return true to continue execution, false to skip (only meaningful for *_ENTER events)
     */
    boolean onEvent(RunEvent event);

}
