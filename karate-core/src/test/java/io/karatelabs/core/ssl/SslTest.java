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
package io.karatelabs.core.ssl;

import io.karatelabs.core.MockServer;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SSL/TLS tests for Karate HTTP client.
 * Tests both trustStore and keyStore (mTLS) configurations.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Self-signed certificate generation not supported on Windows")
class SslTest {

    static MockServer mockServer;

    @BeforeAll
    static void beforeAll() {
        // Start mock server with SSL using self-signed certificate
        mockServer = MockServer.featureString(
                "Feature: SSL Mock\n" +
                "Scenario: pathMatches('/test')\n" +
                "* def response = { success: true }\n"
        ).ssl(true).start();
    }

    @Test
    void testSslTrustAll() {
        int port = mockServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-trust-all.feature")
                .systemProperty("ssl.port", port + "")
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslTrustStore() {
        int port = mockServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-truststore.feature")
                .systemProperty("ssl.port", port + "")
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslKeyStore() {
        int port = mockServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-keystore.feature")
                .systemProperty("ssl.port", port + "")
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @AfterAll
    static void afterAll() {
        if (mockServer != null) {
            mockServer.stopAndWait();
        }
    }

}
