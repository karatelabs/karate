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
package io.karatelabs.driver;

import io.karatelabs.core.ScenarioRuntime;

import java.util.Map;

/**
 * Provider for browser driver instances.
 * <p>
 * Implementations can manage driver lifecycle in different ways:
 * <ul>
 *   <li>Per-scenario: create new driver for each scenario (default behavior)</li>
 *   <li>Per-thread: reuse driver within same thread (ThreadLocal pattern)</li>
 *   <li>Pooled: maintain a pool of drivers, borrow/return</li>
 * </ul>
 * <p>
 * Example usage with Runner:
 * <pre>
 * Runner.path("features/")
 *     .driverProvider(new CdpPooledProvider(browserWsUrl))
 *     .parallel(4);
 * </pre>
 */
public interface DriverProvider {

    /**
     * Acquire a driver instance for a scenario.
     * <p>
     * Called when a scenario first accesses the driver (lazy initialization).
     * The provider may create a new driver, return one from a pool, or
     * return a thread-local cached instance.
     *
     * @param runtime the scenario runtime requesting the driver
     * @param config  driver configuration from {@code configure driver = {...}}
     * @return a driver instance
     */
    Driver acquire(ScenarioRuntime runtime, Map<String, Object> config);

    /**
     * Release a driver instance after scenario completion.
     * <p>
     * Called when a scenario ends. The provider may close the driver,
     * return it to a pool, or keep it cached for the thread.
     *
     * @param runtime the scenario runtime that used the driver
     * @param driver  the driver to release
     */
    void release(ScenarioRuntime runtime, Driver driver);

    /**
     * Shutdown the provider and close all managed drivers.
     * <p>
     * Called when the Suite completes. All drivers should be closed.
     */
    void shutdown();

}
