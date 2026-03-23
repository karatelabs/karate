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
import io.karatelabs.driver.e2e.support.TestPageServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for Scenario Outline with driver inheritance across multiple call levels.
 * Simulates the v1 00_outline.feature pattern:
 * - Top level: Scenario Outline
 * - Mid level: Orchestration feature that inits driver and calls sub-features
 * - Bottom level: Sub-features that use inherited driver
 *
 * Uses local Chrome browser (visible, not headless) for manual verification.
 * Not intended for CI/CD - run manually to watch the test execution.
 *
 * For CI/CD, the outline-driver.feature is run by DriverFeatureTest using
 * testcontainers and ContainerDriverProvider.
 *
 * To run manually (with visible browser):
 *   mvn test -Dtest=OutlineDriverTest -pl karate-core
 */
@Disabled("Manual test - requires local Chrome browser. CI runs via DriverFeatureTest.")
public class OutlineDriverTest {

    private static final Logger logger = LoggerFactory.getLogger(OutlineDriverTest.class);
    private static final int TEST_PORT = 18082;
    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        testServer = TestPageServer.start(TEST_PORT);
        logger.info("Test page server started on port: {}", testServer.getPort());

        // Set server URL for karate-config (temp config uses karate.serverUrl)
        System.setProperty("karate.serverUrl", "http://localhost:" + TEST_PORT);
        // Override headless to false for visible browser during manual testing
        System.setProperty("karate.driver.headless", "false");
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
        System.clearProperty("karate.serverUrl");
        System.clearProperty("karate.driver.headless");
    }

    @Test
    void testOutlineWithDriverInheritance() {
        // Use temp config which sets up driver for local Chrome (visible)
        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features/outline-driver.feature")
                .configDir("classpath:io/karatelabs/driver/temp/karate-config.js")
                .outputDir(Path.of("target", "outline-driver-reports"))
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .parallel(1);

        logger.info("Feature count: {}", result.getFeatureCount());
        logger.info("Scenarios passed: {}", result.getScenarioPassedCount());
        logger.info("Scenarios failed: {}", result.getScenarioFailedCount());

        if (result.isFailed()) {
            result.getErrors().forEach(error ->
                logger.error("Test error: {}", error)
            );
        }

        assertTrue(result.isPassed(), "Outline driver inheritance test should pass");
    }

}
