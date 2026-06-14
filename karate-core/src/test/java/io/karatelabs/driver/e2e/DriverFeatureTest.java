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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs Gherkin feature files for driver E2E testing.
 * Uses Testcontainers for Docker-based Chrome and reuses existing test infrastructure.
 *
 * <h2>Networking Setup (Critical for GitHub Actions)</h2>
 * <p>
 * This test uses a separate Chrome container (not SharedChromeContainer) because it
 * needs a dedicated container for feature file execution with parallel scenarios.
 * </p>
 * <p>
 * The networking setup is done in a static initializer block that runs BEFORE
 * the {@code @Container} field is initialized. This order is critical:
 * </p>
 * <ol>
 *   <li>Static block calls {@code Testcontainers.exposeHostPorts()} - sets up SOCKS proxy</li>
 *   <li>{@code @Container} field triggers container creation with proxy already available</li>
 *   <li>{@code @BeforeAll} starts the test server on the exposed port</li>
 * </ol>
 *
 * @see ChromeContainer
 * @see SharedChromeContainer
 */
@org.testcontainers.junit.jupiter.Testcontainers
class DriverFeatureTest {

    private static final Logger logger = LoggerFactory.getLogger(DriverFeatureTest.class);

    // Test server port - different from SharedChromeContainer (18080) to avoid conflicts
    // This port is used exclusively by this test class
    private static final int TEST_SERVER_PORT = 18081;

    // Static initialization block runs BEFORE @Container field initialization
    // This order is critical for testcontainers networking to work correctly
    static {
        // Workaround for Docker 29.x compatibility (affects API version negotiation)
        // See: https://github.com/testcontainers/testcontainers-java/issues/11212
        System.setProperty("api.version", "1.44");

        // CRITICAL: Expose the test server port BEFORE container starts
        // This sets up a SOCKS proxy that makes host.testcontainers.internal work
        // The @Container annotation triggers container creation after static init
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
        logger.info("exposed host port {} to containers for Gherkin tests", TEST_SERVER_PORT);
    }

    @Container
    private static final ChromeContainer chrome = new ChromeContainer();

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        // Start test page server
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        logger.info("test page server started on port: {}", testServer.getPort());

        // Set serverUrl for test pages (webSocketUrl not needed - ContainerDriverProvider handles it)
        String serverUrl = chrome.getHostAccessUrl(TEST_SERVER_PORT);
        // crossOriginUrl points at the same TestPageServer via a different hostname so the
        // OOPIF feature can load an iframe from a different "site". Combined with
        // --site-per-process on ChromeContainer this exercises out-of-process iframes.
        String crossOriginUrl = chrome.getCrossOriginHostAccessUrl(TEST_SERVER_PORT);
        System.setProperty("karate.driver.serverUrl", serverUrl);
        System.setProperty("karate.driver.crossOriginUrl", crossOriginUrl);
        logger.info("driver serverUrl: {}", serverUrl);
        logger.info("driver crossOriginUrl: {}", crossOriginUrl);
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
    void testDriverFeatures() throws Exception {
        // ContainerDriverProvider creates tabs in the Chrome container
        // Pool size is auto-detected from parallel(N) - no need to specify it
        ContainerDriverProvider provider = new ContainerDriverProvider(chrome);

        Path reportDir = Path.of("target", "driver-feature-reports");

        // Run all driver feature tests with scenario-level parallelism
        // Cookie tests use @lock=cookies for mutual exclusion (cookies are browser-level shared state)
        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .tags("~@expect-failure")  // exclude features designed to fail (see StepFailureFeatureTest)
                .parallel(2);  // Pool size auto-detected from this

        // Log results
        logger.info("Feature count: {}", result.getFeatureCount());
        logger.info("Scenarios passed: {}", result.getScenarioPassedCount());
        logger.info("Scenarios failed: {}", result.getScenarioFailedCount());

        if (result.isFailed()) {
            result.getErrors().forEach(error ->
                logger.error("Test error: {}", error)
            );
        }

        assertTrue(result.isPassed(), "All driver feature tests should pass");

        assertScreenshotEmbeds(reportDir);
    }

    /**
     * Regression guard: OOPIF auto-attach must survive pooled-driver reuse.
     *
     * <p>{@code Target.setAutoAttach} is armed once at init on the tab's root
     * session. Every {@code switchPage()}/{@code close()} drives a DIFFERENT tab
     * through a fresh flattened CDP session where auto-attach defaults OFF, so
     * unless the driver re-arms it on each session switch, cross-origin iframes
     * silently stop attaching and {@code switchFrame()} fails with
     * "could not find frame".
     *
     * <p>The failure is cross-scenario on a reused driver:
     * {@code PooledDriverProvider.resetDriver()} navigates to {@code about:blank}
     * but does not re-create the CDP session, so the unarmed session carries over.
     * This runs the reproduction with a <b>pool of one</b> ({@code parallel(1)})
     * so scenario 2 deterministically inherits the session scenario 1 left active
     * on a non-root tab — the conditions seen in the wild with payment-provider
     * card-field iframes at higher thread counts. Kept out of the {@code features/}
     * directory (and thus out of {@link #testDriverFeatures()}'s {@code parallel(2)}
     * sweep) because the reproduction needs a single shared driver and a
     * deterministic scenario order.
     */
    @Test
    void testOopifSurvivesPooledDriverReuse() {
        ContainerDriverProvider provider = new ContainerDriverProvider(chrome);
        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features-pool/oopif-pool-reuse.feature")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .outputDir(Path.of("target", "oopif-pool-reports"))
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .parallel(1);  // pool size 1 => scenario 2 reuses scenario 1's driver
        logger.info("oopif pool-reuse scenarios passed: {}, failed: {}",
                result.getScenarioPassedCount(), result.getScenarioFailedCount());
        if (result.isFailed()) {
            result.getErrors().forEach(error -> logger.error("Test error: {}", error));
        }
        assertTrue(result.isPassed(),
                "cross-origin iframe must still attach on a pooled driver reused after a tab switch");
    }

    /**
     * Regression guard:
     * screenshot() from Gherkin must embed the image into the HTML report.
     *
     * <p>We verify the embed pipeline end-to-end: the PNG bytes land on disk
     * in the {@code embeds/} directory AND the per-feature HTML carries the
     * embed metadata in its inlined JSON (the {@code ../embeds/<file>} URL
     * itself is rendered client-side by {@code karate-report.js}).
     */
    private static void assertScreenshotEmbeds(Path reportDir) throws Exception {
        Path embedsDir = reportDir.resolve("embeds");
        assertTrue(Files.isDirectory(embedsDir),
                "embeds/ directory should exist after a screenshot()");
        long pngCount;
        try (Stream<Path> files = Files.list(embedsDir)) {
            pngCount = files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
        }
        // screenshot.feature has 3 scenarios, 2 of which embed (default + explicit true)
        assertTrue(pngCount >= 2,
                "expected >= 2 PNG embeds from screenshot.feature, found " + pngCount);

        Path featureHtml = reportDir.resolve("feature-html")
                .resolve("target.test-classes.io.karatelabs.driver.features.screenshot.html");
        assertTrue(Files.exists(featureHtml), "screenshot feature HTML not found: " + featureHtml);
        String html = Files.readString(featureHtml);
        assertTrue(html.contains("\"mime\": \"image/png\""),
                "feature HTML should carry the embed part with image/png mime type");
        assertTrue(html.matches("(?s).*\"file\":\\s*\"\\d+_screenshot[^\"]*\\.png\".*"),
                "feature HTML should carry the embed part with a screenshot .png file reference");
    }

}
