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
package io.karatelabs.driver.e2e.support;

import io.karatelabs.driver.cdp.CdpDriver;
import io.karatelabs.driver.cdp.CdpDriverOptions;
import io.karatelabs.driver.PageLoadStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base class for driver E2E tests.
 * Uses SharedChromeContainer for efficient test execution - single container for all tests.
 *
 * <p>This class provides the common setup for E2E tests that need a Chrome browser.
 * It uses a shared container to avoid the overhead of starting a new container for
 * each test class.</p>
 *
 * <h2>Networking (GitHub Actions Compatibility)</h2>
 * <p>
 * Test pages are accessed via {@link #testUrl(String)} which returns URLs using
 * "host.testcontainers.internal". This works on all platforms (macOS, Windows, Linux)
 * because SharedChromeContainer properly sets up the testcontainers networking
 * before starting the Chrome container.
 * </p>
 *
 * @see SharedChromeContainer
 * @see ChromeContainer
 */
public abstract class DriverTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(DriverTestBase.class);

    protected static SharedChromeContainer shared;
    protected static CdpDriver driver;

    @BeforeAll
    static void setupDriver() {
        // Get or create shared container (singleton)
        shared = SharedChromeContainer.getInstance();

        // Create driver connected to shared container
        driver = shared.getChrome().createDriver(
                CdpDriverOptions.builder()
                        .timeout(30000)
                        .pageLoadStrategy(PageLoadStrategy.DOMCONTENT_AND_FRAMES)
                        .build()
        );
        logger.info("CDP driver connected to shared Chrome container");
    }

    @AfterAll
    static void cleanupDriver() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
        // Note: shared container is NOT stopped here - it persists for all test classes
    }

    /**
     * Get URL for a test page accessible from inside the Docker container.
     * Use this for driver.setUrl() or driver navigation calls.
     *
     * <p>Returns a URL using "host.testcontainers.internal" which is routed through
     * testcontainers' SOCKS proxy to reach the test server on the host machine.
     * This works on all platforms including GitHub Actions.</p>
     *
     * @param path the path portion of the URL (e.g., "/input", "/iframe")
     * @return full URL accessible from inside the container
     */
    protected String testUrl(String path) {
        return shared.getHostAccessUrl() + path;
    }

    /**
     * Get URL for a test page accessible from the host.
     * Use this for debugging or local access.
     */
    protected String localUrl(String path) {
        return shared.getTestServer().getBaseUrl() + path;
    }

    /**
     * Save a debug screenshot to the specified path.
     */
    protected void saveScreenshot(String filename) {
        try {
            byte[] screenshot = driver.screenshot();
            Path path = Path.of("target", "screenshots", filename);
            Files.createDirectories(path.getParent());
            Files.write(path, screenshot);
            logger.info("screenshot saved: {}", path.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("failed to save screenshot: {}", e.getMessage());
        }
    }

    /**
     * Print debug information about the current page state.
     */
    protected void debugSnapshot() {
        logger.info("=== Debug Snapshot ===");
        logger.info("URL: {}", driver.getUrl());
        logger.info("Title: {}", driver.getTitle());
        saveScreenshot("debug-" + System.currentTimeMillis() + ".png");
    }

}
