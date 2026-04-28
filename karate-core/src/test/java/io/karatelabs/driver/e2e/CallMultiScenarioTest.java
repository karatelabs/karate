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
import io.karatelabs.driver.e2e.support.ChromeContainer;
import io.karatelabs.driver.e2e.support.ContainerDriverProvider;
import io.karatelabs.driver.e2e.support.TestPageServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a feature calls another feature that contains multiple UI scenarios,
 * the second scenario must inherit the driver acquired by the first instead
 * of trying to acquire a fresh one from the pool. Pool size 1 + parallel(1)
 * makes the deadlock deterministic if the inheritance is broken.
 */
@org.testcontainers.junit.jupiter.Testcontainers
class CallMultiScenarioTest {

    private static final Logger logger = LoggerFactory.getLogger(CallMultiScenarioTest.class);

    private static final int TEST_SERVER_PORT = 18083;

    static {
        System.setProperty("api.version", "1.44");
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
        logger.info("exposed host port {} to containers", TEST_SERVER_PORT);
    }

    @Container
    private static final ChromeContainer chrome = new ChromeContainer();

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        String serverUrl = chrome.getHostAccessUrl(TEST_SERVER_PORT);
        System.setProperty("karate.driver.serverUrl", serverUrl);
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
        System.clearProperty("karate.driver.serverUrl");
    }

    // The PooledDriverProvider's internal wait timeout is 30s, so a hang would
    // surface within ~30s. Cap the test at 90s to fail fast in case the bug
    // returns and the wait times out repeatedly.
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void calledFeatureWithMultipleUiScenariosDoesNotDeadlock() {
        ContainerDriverProvider provider = new ContainerDriverProvider(chrome);

        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/call-multi/main.feature")
                .configDir("classpath:io/karatelabs/driver/call-multi/karate-config.js")
                .outputDir(Path.of("target", "call-multi-reports"))
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .parallel(1);  // Pool size = 1: forces deterministic deadlock without the fix

        if (result.isFailed()) {
            result.getErrors().forEach(error -> logger.error("Test error: {}", error));
        }
        assertTrue(result.isPassed(), "called feature with multiple UI scenarios should not deadlock");
    }

}
