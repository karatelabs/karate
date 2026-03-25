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
package io.karatelabs.driver.e2e;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.driver.Driver;
import io.karatelabs.driver.PooledDriverProvider;
import io.karatelabs.driver.e2e.support.TestPageServer;
import io.karatelabs.driver.w3c.W3cDriver;
import io.karatelabs.driver.w3c.W3cDriverOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs frame.feature independently against W3C WebDriver.
 *
 * <p>Frame tests are isolated because frame switching modifies browser state globally
 * and is sensitive to parallelism. Running frames in a dedicated container with
 * a single thread eliminates cross-scenario interference.</p>
 *
 * <p>Disabled by default. Enable with {@code -Dkarate.w3c.test=true}.</p>
 */
@org.testcontainers.junit.jupiter.Testcontainers
class W3cFrameFeatureTest {

    private static final Logger logger = LoggerFactory.getLogger(W3cFrameFeatureTest.class);

    // Use a different port from W3cDriverFeatureTest to avoid conflicts when run in parallel
    private static final int TEST_SERVER_PORT = 18083;

    static {
        System.setProperty("api.version", "1.44");
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
    }

    @Container
    private static final BrowserWebDriverContainer<?> chrome =
            new BrowserWebDriverContainer<>("selenium/standalone-chrome:latest")
                    .withExposedPorts(4444);

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        logger.info("W3C frame test server started on port: {}", testServer.getPort());
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
    }

    @Test
    void testW3cFrameFeatures() {
        String webDriverUrl = "http://" + chrome.getHost() + ":" + chrome.getMappedPort(4444);
        String serverUrl = "http://host.testcontainers.internal:" + TEST_SERVER_PORT;

        logger.info("W3C Frame test WebDriver URL: {}", webDriverUrl);

        PooledDriverProvider provider = new PooledDriverProvider() {
            @Override
            protected Driver createDriver(Map<String, Object> config) {
                W3cDriverOptions opts = W3cDriverOptions.fromMap(config);
                return W3cDriver.connect(webDriverUrl, opts);
            }
        };

        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features/frame.feature")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .systemProperty("karate.driver.serverUrl", serverUrl)
                .systemProperty("karate.driver.type", "chromedriver")
                .systemProperty("karate.driver.webDriverUrl", webDriverUrl)
                .outputDir(Path.of("target", "w3c-frame-reports"))
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .parallel(1);  // Single thread — frame switching is global browser state

        logger.info("W3C Frame scenarios passed: {}, failed: {}",
                result.getScenarioPassedCount(), result.getScenarioFailedCount());

        if (result.isFailed()) {
            result.getErrors().forEach(error ->
                logger.error("W3C Frame test error: {}", error)
            );
        }

        assertTrue(result.isPassed(), "All W3C frame feature tests should pass");
    }

}
