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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard:
 * when a Gherkin step fails and the driver has {@code screenshotOnFailure: true}
 * (default), a PNG screenshot must land in the HTML report against the failed
 * step. Also verifies the negative path: {@code screenshotOnFailure: false}
 * suppresses the embed.
 *
 * <p>Sits alongside {@link DriverFeatureTest} but with inverted expectations
 * — the feature under test contains scenarios that fail by design, tagged
 * {@code @expect-failure} so {@link DriverFeatureTest} excludes them.
 */
@org.testcontainers.junit.jupiter.Testcontainers
class StepFailureFeatureTest {

    private static final Logger logger = LoggerFactory.getLogger(StepFailureFeatureTest.class);

    // Distinct from DriverFeatureTest (18081) and SharedChromeContainer (18080)
    private static final int TEST_SERVER_PORT = 18082;

    static {
        System.setProperty("api.version", "1.44");
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
        logger.info("exposed host port {} to containers for step-failure tests", TEST_SERVER_PORT);
    }

    @Container
    private static final ChromeContainer chrome = new ChromeContainer();

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        String serverUrl = chrome.getHostAccessUrl(TEST_SERVER_PORT);
        System.setProperty("karate.driver.serverUrl", serverUrl);
        logger.info("driver serverUrl: {}", serverUrl);
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
        System.clearProperty("karate.driver.serverUrl");
    }

    @Test
    void screenshotOnFailure_embedsForDefaultsAndSuppressesWhenDisabled() throws Exception {
        ContainerDriverProvider provider = new ContainerDriverProvider(chrome);

        Path reportDir = Path.of("target", "step-failure-feature-reports");

        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features/screenshot-on-failure.feature")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(false)
                .driverProvider(provider)
                .tags("@expect-failure")
                .parallel(1);  // sequential — the two scenarios in this feature are independent

        // Both scenarios are designed to fail — the suite must end in the failed state.
        assertTrue(result.isFailed(),
                "expected suite to fail (screenshot-on-failure.feature scenarios fail by design)");
        assertEquals(2, result.getScenarioFailedCount(),
                "expected exactly 2 scenarios to fail");

        // Exactly one screenshot PNG should land in embeds/ — the first scenario
        // (default screenshotOnFailure=true) embeds; the second (overridden to
        // false) does not.
        Path embedsDir = reportDir.resolve("embeds");
        assertTrue(Files.isDirectory(embedsDir),
                "embeds/ directory should exist after the failed scenario");
        long pngCount;
        try (Stream<Path> files = Files.list(embedsDir)) {
            pngCount = files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
        }
        assertEquals(1, pngCount,
                "expected exactly 1 PNG embed — one from the default scenario, "
                        + "none from the screenshotOnFailure=false scenario; got " + pngCount);

        // The per-feature HTML must inline the embed JSON for the surviving screenshot.
        Path featureHtml = reportDir.resolve("feature-html")
                .resolve("target.test-classes.io.karatelabs.driver.features.screenshot-on-failure.html");
        assertTrue(Files.exists(featureHtml),
                "feature HTML not found: " + featureHtml);
        String html = Files.readString(featureHtml);
        assertTrue(html.contains("\"mime\": \"image/png\""),
                "feature HTML should carry the embed part with image/png mime type");
    }
}
