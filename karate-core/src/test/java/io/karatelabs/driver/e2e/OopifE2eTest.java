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

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.ResourceType;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SslUtils;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.driver.e2e.support.ChromeContainer;
import io.karatelabs.driver.e2e.support.ContainerDriverProvider;
import io.karatelabs.driver.e2e.support.TestPageServer;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test for out-of-process iframes (OOPIF).
 *
 * <p>Chrome isolates cross-origin iframes into separate processes when the parent
 * and child frames have different sites (scheme + eTLD+1). This test verifies that
 * the CDP driver can switch into and interact with such frames.</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><b>HTTP server</b> (port {@value HTTP_PORT}): serves the parent page with an iframe element</li>
 *   <li><b>HTTPS server</b> (port {@value HTTPS_PORT}): serves the iframe content with a self-signed cert</li>
 * </ul>
 *
 * <p>To reliably trigger OOPIF, the two servers use <b>different hostnames</b>:
 * the parent page is served via {@code host.testcontainers.internal} (testcontainers'
 * SOCKS proxy) while the iframe is served via {@code host.docker.internal} (Docker's
 * built-in host mapping). Different hostnames mean different eTLD+1 values, which
 * combined with different schemes guarantees Chrome places the iframe in a separate
 * process — exactly like a real third-party iframe (e.g. PayPal).</p>
 *
 * <p>The Chrome container is started with {@code --ignore-certificate-errors} (for the
 * self-signed cert) and {@code --site-per-process} (to enforce process-per-site
 * isolation in headless mode).</p>
 *
 * <p>This test uses its own Chrome container (not SharedChromeContainer) because it
 * requires custom Chrome flags and a dedicated HTTPS server.</p>
 */
@org.testcontainers.junit.jupiter.Testcontainers
class OopifE2eTest {

    private static final Logger logger = LoggerFactory.getLogger(OopifE2eTest.class);

    private static final int HTTP_PORT = 18082;
    private static final int HTTPS_PORT = 18083;

    static {
        System.setProperty("api.version", "1.44");
        Testcontainers.exposeHostPorts(HTTP_PORT, HTTPS_PORT);
        logger.info("exposed host ports {} (HTTP) and {} (HTTPS) to containers", HTTP_PORT, HTTPS_PORT);
    }

    @Container
    private static final ChromeContainer chrome = createContainer();

    private static TestPageServer httpServer;
    private static HttpServer httpsServer;

    private static ChromeContainer createContainer() {
        ChromeContainer c = new ChromeContainer();
        // --ignore-certificate-errors: accept self-signed cert on the HTTPS server
        // --site-per-process: force process-per-site isolation in headless mode
        c.withCommand("--remote-allow-origins=* --ignore-certificate-errors --site-per-process");
        return c;
    }

    @BeforeAll
    static void setup() {
        httpServer = TestPageServer.start(HTTP_PORT);
        logger.info("HTTP test server started on port: {}", httpServer.getPort());

        httpsServer = startHttpsServer(HTTPS_PORT);
        logger.info("HTTPS test server started on port: {}", HTTPS_PORT);

        String serverUrl = chrome.getHostAccessUrl(HTTP_PORT);
        // Use host.docker.internal (different hostname from host.testcontainers.internal)
        // so Chrome sees a different eTLD+1 and isolates the iframe into a separate process.
        // ChromeContainer already maps host.docker.internal via --add-host=host-gateway.
        String httpsServerUrl = "https://host.docker.internal:" + HTTPS_PORT;

        System.setProperty("karate.driver.serverUrl", serverUrl);
        System.setProperty("karate.driver.httpsServerUrl", httpsServerUrl);
        logger.info("serverUrl: {}", serverUrl);
        logger.info("httpsServerUrl: {}", httpsServerUrl);
    }

    @AfterAll
    static void cleanup() {
        if (httpServer != null) {
            httpServer.stopAsync();
            httpServer = null;
        }
        if (httpsServer != null) {
            httpsServer.stopAsync();
            httpsServer = null;
        }
        System.clearProperty("karate.driver.serverUrl");
        System.clearProperty("karate.driver.httpsServerUrl");
    }

    @Test
    void testOopifFeatures() {
        ContainerDriverProvider provider = new ContainerDriverProvider(chrome);

        Path reportDir = Path.of("target", "oopif-reports");

        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/oopif")
                .configDir("classpath:io/karatelabs/driver/oopif/karate-config.js")
                .outputDir(reportDir)
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .driverProvider(provider)
                .parallel(1);

        logger.info("Feature count: {}", result.getFeatureCount());
        logger.info("Scenarios passed: {}", result.getScenarioPassedCount());
        logger.info("Scenarios failed: {}", result.getScenarioFailedCount());

        if (result.isFailed()) {
            result.getErrors().forEach(error ->
                logger.error("Test error: {}", error)
            );
        }

        assertTrue(result.isPassed(), "All OOPIF feature tests should pass");
    }

    private static HttpServer startHttpsServer(int port) {
        SslContext sslContext = SslUtils.generateNettySslContext();
        byte[] frameHtml = loadClasspathResource("io/karatelabs/driver/pages/oopif-frame.html");
        return HttpServer.start(port, sslContext, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody(frameHtml, ResourceType.HTML);
            return response;
        });
    }

    private static byte[] loadClasspathResource(String path) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("classpath resource not found: " + path);
        }
        return FileUtils.toBytes(is);
    }

}
