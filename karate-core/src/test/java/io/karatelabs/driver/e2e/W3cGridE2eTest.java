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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-grid regression for the W3C driver — exercises a hub + node-chromium
 * topology rather than the simpler standalone server used by
 * {@link W3cDriverFeatureTest}.
 *
 * <p>Selenium's standalone mode bundles router + node in-process and can be
 * lenient about filters like {@code CheckContentTypeHeader}, while a real grid
 * (Selenium Grid, SauceLabs, BrowserStack, Selenium Server in hub mode)
 * applies them strictly on every routed request. This split is what allowed
 * the bug fixed in #2883 (missing {@code charset=utf-8} on POST /session) to
 * slip past the existing standalone-container test.</p>
 *
 * <p>This test runs the smallest meaningful navigation flow against a real
 * grid. It is intentionally narrow: its job is to catch wire-format
 * regressions that only surface against a real grid, not to re-run the full
 * W3C feature suite.</p>
 *
 * <p>Runs only in the {@code w3c} Maven profile
 * ({@code mvn verify -Pw3c -pl karate-core}).</p>
 */
class W3cGridE2eTest {

    private static final Logger logger = LoggerFactory.getLogger(W3cGridE2eTest.class);

    private static final int TEST_SERVER_PORT = 18083;

    private static final Network network = Network.newNetwork();

    private static final GenericContainer<?> hub = new GenericContainer<>("selenium/hub:latest")
            .withNetwork(network)
            .withNetworkAliases("selenium-hub")
            .withExposedPorts(4442, 4443, 4444)
            // /status returns 200 even before a node is registered (ready: false);
            // wait for the hub HTTP port to accept connections, then assert grid
            // readiness explicitly after the node is up.
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    private static final GenericContainer<?> node = new GenericContainer<>("selenium/node-chromium:latest")
            .withNetwork(network)
            .withNetworkAliases("selenium-node")
            .withEnv("SE_EVENT_BUS_HOST", "selenium-hub")
            .withEnv("SE_EVENT_BUS_PUBLISH_PORT", "4442")
            .withEnv("SE_EVENT_BUS_SUBSCRIBE_PORT", "4443")
            .withEnv("SE_NODE_HOST", "selenium-node")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));

    private static TestPageServer testServer;

    @BeforeAll
    static void setup() {
        Testcontainers.exposeHostPorts(TEST_SERVER_PORT);
        hub.start();
        node.start();
        waitForGridReady(Duration.ofMinutes(2));
        testServer = TestPageServer.start(TEST_SERVER_PORT);
        logger.info("test page server started on port: {}", testServer.getPort());
        logger.info("hub: {}:{}", hub.getHost(), hub.getMappedPort(4444));
    }

    /** Poll the hub's /status endpoint until it reports {@code "ready": true}. */
    private static void waitForGridReady(Duration timeout) {
        String url = "http://" + hub.getHost() + ":" + hub.getMappedPort(4444) + "/status";
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> r = client.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200 && r.body().replaceAll("\\s", "").contains("\"ready\":true")) {
                    logger.info("grid is ready");
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
        throw new IllegalStateException("grid never became ready at " + url, last);
    }

    @AfterAll
    static void cleanup() {
        if (testServer != null) {
            testServer.stopAsync();
            testServer = null;
        }
        node.stop();
        hub.stop();
        network.close();
        System.clearProperty("karate.driver.serverUrl");
        System.clearProperty("karate.driver.type");
        System.clearProperty("karate.driver.webDriverUrl");
    }

    @Test
    void testNavigationAgainstSeleniumGrid() {
        String webDriverUrl = "http://" + hub.getHost() + ":" + hub.getMappedPort(4444);
        String serverUrl = "http://host.testcontainers.internal:" + TEST_SERVER_PORT;
        logger.info("grid webDriverUrl: {}", webDriverUrl);
        logger.info("test server URL (from container): {}", serverUrl);

        System.setProperty("karate.driver.serverUrl", serverUrl);
        System.setProperty("karate.driver.type", "chromedriver");
        System.setProperty("karate.driver.webDriverUrl", webDriverUrl);

        PooledDriverProvider provider = new PooledDriverProvider() {
            @Override
            protected Driver createDriver(Map<String, Object> config) {
                W3cDriverOptions opts = W3cDriverOptions.fromMap(config);
                return W3cDriver.connect(webDriverUrl, opts);
            }
        };

        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/features/navigation.feature")
                .configDir("classpath:io/karatelabs/driver/features/karate-config.js")
                .outputDir(Path.of("target", "w3c-grid-reports"))
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .parallel(1);

        logger.info("grid scenarios passed: {}, failed: {}",
                result.getScenarioPassedCount(), result.getScenarioFailedCount());

        if (result.isFailed()) {
            result.getErrors().forEach(e -> logger.error("grid test error: {}", e));
        }

        assertTrue(result.isPassed(), "Navigation against Selenium Grid (hub + node-chromium) should pass");
    }

}
