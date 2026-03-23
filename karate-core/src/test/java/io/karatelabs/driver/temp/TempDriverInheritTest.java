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
package io.karatelabs.driver.temp;

import io.karatelabs.common.ResourceType;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.http.HttpServer;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temporary test to debug driver inheritance with real local Chrome browser.
 * This test is disabled by default (not for CI/CD).
 *
 * To run manually:
 *   mvn test -Dtest=TempDriverInheritTest -pl karate-core
 */
@Disabled("Manual test - requires local Chrome browser")
public class TempDriverInheritTest {

    private static final Logger logger = LoggerFactory.getLogger(TempDriverInheritTest.class);
    private static final int TEST_PORT = 18899;
    private static HttpServer server;

    @BeforeAll
    static void startServer() {
        // Simple HTML server for testing
        server = HttpServer.start(TEST_PORT, request -> {
            String path = request.getPath();
            HttpResponse response = new HttpResponse();

            if ("/".equals(path) || "/index.html".equals(path)) {
                response.setBody("<html><head><title>Main Page</title></head><body><h1>Main</h1></body></html>".getBytes(),
                    ResourceType.HTML);
            } else if ("/wait.html".equals(path)) {
                response.setBody("<html><head><title>Wait Page</title></head><body><h1>Wait</h1></body></html>".getBytes(),
                    ResourceType.HTML);
            } else {
                response.setStatus(404);
                response.setBody("Not Found");
            }
            return response;
        });

        // Set server URL as system property for karate-config
        System.setProperty("karate.serverUrl", "http://localhost:" + TEST_PORT);
        logger.info("Test server started on port {}", TEST_PORT);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stopAsync();
        }
        System.clearProperty("karate.serverUrl");
    }

    @Test
    void testDriverInheritance() {
        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/temp")
                .configDir("classpath:io/karatelabs/driver/temp/karate-config.js")
                .outputDir(Path.of("target", "temp-driver-reports"))
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

        assertTrue(result.isPassed(), "All driver tests should pass");
    }
}
