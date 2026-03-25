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
import io.karatelabs.driver.cdp.CdpDriver;
import io.karatelabs.driver.cdp.CdpDriverOptions;
import io.karatelabs.driver.w3c.W3cBrowserType;
import io.karatelabs.driver.w3c.W3cDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Driver provider that maintains a bounded pool of drivers.
 * <p>
 * This provider works correctly with virtual threads where each task gets a new
 * thread ID. Drivers are pooled and reused across scenarios regardless of which
 * thread executes them.
 * <p>
 * The pool size automatically matches the Suite's parallelism (threadCount),
 * ensuring exactly the right number of drivers are created.
 *
 * <pre>
 * // Usage - pool size auto-detected from Runner.parallel(N)
 * Runner.path("features/")
 *     .driverProvider(new PooledDriverProvider())
 *     .parallel(4);  // Pool will have 4 drivers
 *
 * // Or with Testcontainers - override createDriver()
 * Runner.path("features/")
 *     .driverProvider(new PooledDriverProvider() {
 *         protected Driver createDriver(Map&lt;String, Object&gt; config) {
 *             return CdpDriver.connect(container.getCdpUrl(), options);
 *         }
 *     })
 *     .parallel(4);
 * </pre>
 */
public class PooledDriverProvider implements DriverProvider {

    private static final Logger logger = LoggerFactory.getLogger(PooledDriverProvider.class);

    private volatile int poolSize = -1;  // -1 means auto-detect from Suite
    private volatile ArrayBlockingQueue<Driver> availableDrivers;
    private final ConcurrentHashMap<ScenarioRuntime, Driver> assignedDrivers = new ConcurrentHashMap<>();
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final Object initLock = new Object();
    private volatile boolean shutdown = false;

    /**
     * Create a pooled driver provider with auto-detected pool size.
     * Pool size will match the Suite's parallelism (threadCount).
     */
    public PooledDriverProvider() {
        // Pool size auto-detected on first acquire()
    }

    /**
     * Create a pooled driver provider with explicit pool size.
     * Use this when you need to override the auto-detected size.
     *
     * @param poolSize maximum number of drivers to create
     */
    public PooledDriverProvider(int poolSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("Pool size must be at least 1");
        }
        this.poolSize = poolSize;
        this.availableDrivers = new ArrayBlockingQueue<>(poolSize);
    }

    @Override
    public Driver acquire(ScenarioRuntime runtime, Map<String, Object> config) {
        if (shutdown) {
            throw new IllegalStateException("Provider has been shut down");
        }

        // Initialize pool lazily with auto-detected size
        ensurePoolInitialized(runtime);

        // Check if this scenario already has a driver assigned (shouldn't happen normally)
        Driver existing = assignedDrivers.get(runtime);
        if (existing != null && !existing.isTerminated()) {
            logger.debug("Returning existing driver for scenario: {}", runtime.getScenario().getName());
            return existing;
        }

        // Try to get a driver from the pool
        Driver driver = availableDrivers.poll();

        if (driver != null) {
            // Got one from pool - check if it's still valid
            if (driver.isTerminated()) {
                logger.debug("Discarding terminated driver from pool");
                createdCount.decrementAndGet();
                driver = null;
            } else {
                resetDriver(driver);
                logger.debug("Reusing pooled driver for scenario: {}", runtime.getScenario().getName());
            }
        }

        if (driver == null) {
            // Need to create a new driver or wait for one
            synchronized (initLock) {
                // Double-check pool (another thread may have returned one)
                driver = availableDrivers.poll();
                if (driver != null && !driver.isTerminated()) {
                    resetDriver(driver);
                    logger.debug("Reusing pooled driver (after lock) for scenario: {}", runtime.getScenario().getName());
                } else {
                    if (driver != null && driver.isTerminated()) {
                        createdCount.decrementAndGet();
                    }
                    // Check if we can create more
                    if (createdCount.get() < poolSize) {
                        driver = createDriver(config);
                        int count = createdCount.incrementAndGet();
                        logger.info("Created driver {}/{} for scenario: {}",
                                count, poolSize, runtime.getScenario().getName());
                    } else {
                        // Pool exhausted - wait for one to be returned
                        driver = waitForDriver(config, runtime);
                    }
                }
            }
        }

        assignedDrivers.put(runtime, driver);
        return driver;
    }

    private void ensurePoolInitialized(ScenarioRuntime runtime) {
        if (availableDrivers != null) {
            return;
        }
        synchronized (initLock) {
            if (availableDrivers != null) {
                return;
            }
            // Auto-detect pool size from Suite's threadCount
            if (poolSize < 1 && runtime.getFeatureRuntime() != null
                    && runtime.getFeatureRuntime().getSuite() != null) {
                poolSize = runtime.getFeatureRuntime().getSuite().threadCount;
                logger.info("Auto-detected pool size from Suite: {}", poolSize);
            }
            if (poolSize < 1) {
                poolSize = 1;  // Fallback
                logger.warn("Could not detect pool size, defaulting to 1");
            }
            availableDrivers = new ArrayBlockingQueue<>(poolSize);
        }
    }

    private Driver waitForDriver(Map<String, Object> config, ScenarioRuntime runtime) {
        String scenarioName = runtime.getScenario().getName();
        logger.warn("Pool exhausted for scenario '{}', waiting for available driver... [{}]",
                scenarioName, getStats());
        try {
            Driver driver = availableDrivers.poll(30, TimeUnit.SECONDS);
            if (driver == null) {
                throw new RuntimeException("Timeout waiting for available driver for scenario '"
                        + scenarioName + "' [" + getStats() + "]");
            }
            if (driver.isTerminated()) {
                // Create replacement
                createdCount.decrementAndGet();
                driver = createDriver(config);
                createdCount.incrementAndGet();
                logger.info("Replaced terminated driver for scenario: {}", scenarioName);
            } else {
                resetDriver(driver);
                logger.info("Acquired pooled driver after wait for scenario: {}", scenarioName);
            }
            return driver;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for driver", e);
        }
    }

    @Override
    public void release(ScenarioRuntime runtime, Driver driver) {
        assignedDrivers.remove(runtime);

        if (shutdown) {
            // During shutdown, just close the driver
            closeDriverQuietly(driver);
            return;
        }

        if (driver.isTerminated()) {
            logger.debug("Not returning terminated driver to pool");
            createdCount.decrementAndGet();
            return;
        }

        // Return to pool
        boolean offered = availableDrivers.offer(driver);
        if (!offered) {
            // Pool full (shouldn't happen with correct sizing)
            logger.warn("Pool full, closing excess driver");
            closeDriverQuietly(driver);
            createdCount.decrementAndGet();
        } else {
            logger.debug("Returned driver to pool for scenario: {}", runtime.getScenario().getName());
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
        int count = createdCount.get();
        logger.debug("Shutting down PooledDriverProvider, closing {} drivers", count);

        // Close all assigned drivers
        for (Driver driver : assignedDrivers.values()) {
            closeDriverQuietly(driver);
        }
        assignedDrivers.clear();

        // Close all pooled drivers
        if (availableDrivers != null) {
            Driver driver;
            while ((driver = availableDrivers.poll()) != null) {
                closeDriverQuietly(driver);
            }
        }
    }

    /**
     * Reset driver state between scenarios.
     * Override to customize reset behavior.
     */
    protected void resetDriver(Driver driver) {
        try {
            // Dismiss any open dialog first — dialogs block all CDP Runtime.evaluate calls
            Dialog dialog = driver.getDialog();
            if (dialog != null) {
                logger.debug("Dismissing stale dialog before reset");
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    logger.debug("Dialog dismiss during reset (may already be handled): {}", e.getMessage());
                }
            }
            driver.setUrl("about:blank");
            driver.clearCookies();
        } catch (Exception e) {
            logger.warn("Error resetting driver state: {}", e.getMessage());
        }
    }

    /**
     * Create a new driver from config.
     * Subclasses should override for custom driver creation (e.g., Testcontainers).
     */
    protected Driver createDriver(Map<String, Object> config) {
        String type = (String) config.getOrDefault("type", "chrome");
        if (W3cBrowserType.isW3cType(type)) {
            return W3cDriver.start(config);
        }
        // CDP (default)
        CdpDriverOptions options = CdpDriverOptions.fromMap(config);
        String wsUrl = options.getWebSocketUrl();
        if (wsUrl != null && !wsUrl.isEmpty()) {
            return CdpDriver.connect(wsUrl, options);
        } else {
            return CdpDriver.start(options);
        }
    }

    private void closeDriverQuietly(Driver driver) {
        try {
            if (!driver.isTerminated()) {
                driver.quit();
            }
        } catch (Exception e) {
            logger.warn("Error closing driver: {}", e.getMessage());
        }
    }

    /**
     * Get current pool size (may be -1 if not yet initialized).
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Get current pool statistics for monitoring.
     */
    public String getStats() {
        return String.format("PooledDriverProvider[size=%d, created=%d, available=%d, assigned=%d]",
                poolSize, createdCount.get(),
                availableDrivers != null ? availableDrivers.size() : 0,
                assignedDrivers.size());
    }

}
