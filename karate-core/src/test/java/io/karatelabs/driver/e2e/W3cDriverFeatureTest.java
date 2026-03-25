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
import io.karatelabs.driver.PooledDriverProvider;
import io.karatelabs.driver.e2e.support.TestPageServer;
import io.karatelabs.driver.w3c.W3cDriver;
import io.karatelabs.driver.w3c.W3cDriverOptions;
import io.karatelabs.driver.Driver;
import io.karatelabs.core.ScenarioRuntime;
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
 * Runs the same Gherkin feature files as DriverFeatureTest but using W3C WebDriver
 * protocol via a Selenium standalone Chrome container.
 *
 * <p>CDP-only features (dialog callbacks, mouse coordinates) are excluded via
 * {@code ~@cdp} tag filter.</p>
 *
 * <p>This test is disabled by default. Enable with {@code -Dkarate.w3c.test=true}
 * to run as part of the cicd profile. This allows incremental stabilization of
 * W3C WebDriver support without blocking the main build.</p>
 */
@org.testcontainers.junit.jupiter.Testcontainers
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "karate.w3c.test", matches = "true")
class W3cDriverFeatureTest {

    private static final Logger logger = LoggerFactory.getLogger(W3cDriverFeatureTest.class);

    private static final int TEST_SERVER_PORT = 18082;

    static {
        System.setProperty("api.version", "1.44");
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
        logger.info("exposed host port {} to containers for W3C tests", TEST_SERVER_PORT);
    }

    @Container
    private static final BrowserWebDriverContainer<?> chrome =
            new BrowserWebDriverContainer<>("selenium/standalone-chrome:latest")
                    .withExposedPorts(4444);

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        logger.info("test page server started on port: {}", testServer.getPort());
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
        System.clearProperty("karate.driver.serverUrl");
        System.clearProperty("karate.driver.type");
        System.clearProperty("karate.driver.webDriverUrl");
    }

    @Test
    void testW3cDriverFeatures() {
        String webDriverUrl = "http://" + chrome.getHost() + ":" + chrome.getMappedPort(4444);
        String serverUrl = "http://host.testcontainers.internal:" + TEST_SERVER_PORT;

        logger.info("W3C WebDriver URL: {}", webDriverUrl);
        logger.info("Test server URL (from container): {}", serverUrl);

        System.setProperty("karate.driver.serverUrl", serverUrl);
        System.setProperty("karate.driver.type", "chromedriver");
        System.setProperty("karate.driver.webDriverUrl", webDriverUrl);

        // Custom driver provider that creates W3cDriver instances
        PooledDriverProvider provider = new PooledDriverProvider() {
            @Override
            protected Driver createDriver(Map<String, Object> config) {
                // Force W3C driver connecting to the Selenium container
                W3cDriverOptions opts = W3cDriverOptions.fromMap(config);
                return W3cDriver.connect(webDriverUrl, opts);
            }
        };

        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .outputDir(Path.of("target", "w3c-driver-feature-reports"))
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .tags("~@cdp")
                .driverProvider(provider)
                .parallel(1);  // Single thread for W3C (shared session)

        logger.info("W3C Feature count: {}", result.getFeatureCount());
        logger.info("W3C Scenarios passed: {}", result.getScenarioPassedCount());
        logger.info("W3C Scenarios failed: {}", result.getScenarioFailedCount());

        if (result.isFailed()) {
            result.getErrors().forEach(error ->
                logger.error("W3C Test error: {}", error)
            );
        }

        assertTrue(result.isPassed(), "All W3C driver feature tests should pass");
    }

}
