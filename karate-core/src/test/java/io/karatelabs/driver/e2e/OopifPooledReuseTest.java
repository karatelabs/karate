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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for OOPIF auto-attach surviving pooled-driver reuse.
 *
 * <p>{@code Target.setAutoAttach} is armed once at init on the tab's root
 * session. Every {@code switchPage()} / {@code close()} drives a DIFFERENT tab
 * through a fresh flattened CDP session where auto-attach defaults OFF — so
 * unless the driver re-arms it on each session switch, cross-origin iframes
 * silently stop attaching and {@code switchFrame()} fails with
 * "could not find frame".
 *
 * <p>The failure only shows up across scenarios on a reused driver:
 * {@code PooledDriverProvider.resetDriver()} navigates to {@code about:blank}
 * but does not re-create the CDP session, so the unarmed session carries over to
 * the next scenario. This test pins that path with a <b>pool of one</b>
 * ({@code parallel(1)}) so scenario 2 deterministically inherits the session
 * scenario 1 left active on a non-root tab — the conditions seen in the wild
 * with payment-provider card-field iframes at higher thread counts.
 *
 * <p><b>Why its own container, not a method on {@link DriverFeatureTest}:</b>
 * this reproduction deliberately leaves extra browser tabs open (a popup the
 * driver is left sitting on). {@code CdpDriver.quit()} on a container-attached
 * driver only closes the WebSocket — it does not close browser tabs — so those
 * tabs survive suite shutdown. Sharing {@code DriverFeatureTest}'s static Chrome
 * container would leak a {@code serverUrl/oopif} tab into its run, where
 * {@code oopif.feature}'s {@code switchPage(serverUrl + '/oopif')} could match
 * the stray tab instead of its own — an order-dependent failure. A dedicated
 * container keeps the leak contained and torn down with this class.
 *
 * @see DriverFeatureTest
 */
@org.testcontainers.junit.jupiter.Testcontainers
class OopifPooledReuseTest {

    private static final Logger logger = LoggerFactory.getLogger(OopifPooledReuseTest.class);

    // Distinct port from DriverFeatureTest (18081) and SharedChromeContainer (18080)
    private static final int TEST_SERVER_PORT = 18082;

    static {
        System.setProperty("api.version", "1.44");
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
    }

    @Container
    private static final ChromeContainer chrome = new ChromeContainer();

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        String serverUrl = chrome.getHostAccessUrl(TEST_SERVER_PORT);
        String crossOriginUrl = chrome.getCrossOriginHostAccessUrl(TEST_SERVER_PORT);
        System.setProperty("karate.driver.serverUrl", serverUrl);
        System.setProperty("karate.driver.crossOriginUrl", crossOriginUrl);
        logger.info("driver serverUrl: {}, crossOriginUrl: {}", serverUrl, crossOriginUrl);
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
        System.clearProperty("karate.driver.serverUrl");
        System.clearProperty("karate.driver.crossOriginUrl");
    }

    @Test
    void testOopifSurvivesPooledDriverReuse() {
        // Pool size auto-detects from parallel(1) => exactly one shared driver, so
        // scenario 2 is guaranteed to reuse the driver scenario 1 poisoned.
        ContainerDriverProvider provider = new ContainerDriverProvider(chrome);
        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features-pool/oopif-pool-reuse.feature")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .outputDir(Path.of("target", "oopif-pool-reports"))
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .parallel(1);
        logger.info("oopif pool-reuse scenarios passed: {}, failed: {}",
                result.getScenarioPassedCount(), result.getScenarioFailedCount());
        if (result.isFailed()) {
            result.getErrors().forEach(error -> logger.error("test error: {}", error));
        }
        assertTrue(result.isPassed(),
                "cross-origin iframe must still attach on a pooled driver reused after a tab switch");
    }

}
