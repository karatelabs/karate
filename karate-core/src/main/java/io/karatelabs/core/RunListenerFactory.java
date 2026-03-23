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
 * Factory for creating per-thread RunListener instances.
 * Use this when listeners need thread-local state (e.g., debuggers with per-thread stack frames).
 *
 * <p>The {@link #create()} method is called once per execution thread, before any features
 * run on that thread. Each thread gets its own listener instance.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * Suite.of("features/")
 *     .listenerFactory(() -> new MyPerThreadListener())
 *     .parallel(10)
 *     .run();
 * </pre>
 *
 * <p>Use case: The debug adapter creates one DebugThread per execution thread,
 * each with its own stack frames and state.</p>
 */
@FunctionalInterface
public interface RunListenerFactory {

    /**
     * Create a listener instance for the current thread.
     * Called once per execution thread, before any features run on that thread.
     *
     * @return a new RunListener instance for this thread
     */
    RunListener create();

}
