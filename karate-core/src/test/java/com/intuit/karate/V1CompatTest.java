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
package com.intuit.karate;

import com.intuit.karate.core.MockServer;
import io.karatelabs.output.Console;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for V1 backward compatibility shim classes.
 * <p>
 * These tests verify that code written against the Karate v1 API
 * (com.intuit.karate.*) works correctly when running on v2.
 */
@SuppressWarnings("removal")
class V1CompatTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
    }

    // ========== Results Tests ==========

    @Test
    void testResultsShimMethods() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Test

            Scenario: Pass
            * def a = 1
            * match a == 1

            Scenario: Another pass
            * def b = 2
            * match b == 2
            """);

        // Use V1 API
        Results results = Runner.path(feature.toString())
                .workingDir(tempDir.toString())
                .reportDir(tempDir.resolve("reports").toString())
                .parallel(1);

        // Verify V1 methods work
        assertEquals(0, results.getFailCount());
        assertEquals(2, results.getPassCount());
        assertEquals(2, results.getScenarioCount());
        assertEquals(1, results.getFeatureCount());
        assertEquals("", results.getErrorMessages());
        assertEquals("target/karate-reports", results.getReportDir());

        // Verify can access underlying v2 SuiteResult
        assertNotNull(results.toSuiteResult());
        assertTrue(results.toSuiteResult().isPassed());
    }

    @Test
    void testResultsWithFailure() throws Exception {
        Path feature = tempDir.resolve("fail.feature");
        Files.writeString(feature, """
            Feature: Test

            Scenario: Fail
            * def a = 1
            * match a == 2
            """);

        Results results = Runner.path(feature.toString())
                .workingDir(tempDir.toString())
                .reportDir(tempDir.resolve("reports").toString())
                .parallel(1);

        assertEquals(1, results.getFailCount());
        assertEquals(0, results.getPassCount());
        assertFalse(results.getErrorMessages().isEmpty());
    }

    // ========== Runner Tests ==========

    @Test
    void testRunnerPathVarargs() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Test
            Scenario: Pass
            * def a = 1
            """);

        Results results = Runner.path(feature.toString())
                .workingDir(tempDir.toString())
                .outputHtmlReport(false)
                .parallel(1);

        assertEquals(0, results.getFailCount());
    }

    @Test
    void testRunnerPathList() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Test
            Scenario: Pass
            * def a = 1
            """);

        Results results = Runner.path(Arrays.asList(feature.toString()))
                .workingDir(tempDir.toString())
                .outputHtmlReport(false)
                .parallel(1);

        assertEquals(0, results.getFailCount());
    }

    @Test
    void testRunnerTagsList() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Test

            @smoke
            Scenario: Smoke test
            * def a = 1

            @slow
            Scenario: Slow test
            * def b = 2
            """);

        // V1 had tags(List<String>) variant
        Results results = Runner.path(feature.toString())
                .workingDir(tempDir.toString())
                .tags(Arrays.asList("@smoke"))
                .outputHtmlReport(false)
                .parallel(1);

        assertEquals(1, results.getScenarioCount());
        assertEquals(0, results.getFailCount());
    }

    @Test
    void testRunnerV1BuilderMethods() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Test
            Scenario: Pass
            * def a = 1
            """);

        // Test v1-named methods: reportDir, backupReportDir, clientFactory
        Results results = Runner.path(feature.toString())
                .workingDir(tempDir.toString())
                .reportDir(tempDir.resolve("reports").toString())  // v1 name
                .backupReportDir(false)                            // v1 name
                .outputHtmlReport(false)
                .parallel(1);

        assertEquals(0, results.getFailCount());
    }

    @Test
    void testRunFeatureV1Signature() throws Exception {
        Path feature = tempDir.resolve("run-feature.feature");
        Files.writeString(feature, """
            Feature: Test
            Scenario: Use arg
            * def result = input + 1
            """);

        // V1 runFeature returned Map<String, Object>
        Map<String, Object> result = Runner.runFeature(
                "file:" + feature.toString(),
                Map.of("input", 5)
        );

        assertEquals(6, result.get("result"));
    }

    // ========== MockServer Tests ==========

    @Test
    void testMockServerV1Build() {
        MockServer server = MockServer.featureString("""
            Feature: Mock
            Scenario: pathMatches('/hello')
            * def response = { message: 'hello' }
            """)
                .http(0)   // v1: http(port) instead of port().ssl(false)
                .build();  // v1: build() instead of start()

        try {
            assertTrue(server.getPort() > 0);
            assertFalse(server.isSsl());
            assertTrue(server.getUrl().startsWith("http://"));
        } finally {
            server.stop();  // v1: stop() instead of stopAndWait()
        }
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Self-signed certificate generation not supported on Windows")
    void testMockServerV1Https() {
        MockServer server = MockServer.featureString("""
            Feature: Mock
            Scenario: pathMatches('/hello')
            * def response = { message: 'hello' }
            """)
                .https(0)  // v1: https(port) instead of ssl(true).port()
                .build();

        try {
            assertTrue(server.getPort() > 0);
            assertTrue(server.isSsl());
            assertTrue(server.getUrl().startsWith("https://"));
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void testMockServerV1ArgSingleKeyValue() {
        MockServer server = MockServer.featureString("""
            Feature: Mock
            Scenario: pathMatches('/test')
            * def response = { value: myArg }
            """)
                .arg("myArg", "hello")  // v1: arg(key, value) instead of arg(Map.of())
                .http(0)
                .build();

        try {
            assertEquals("hello", server.getVariable("myArg"));
        } finally {
            server.stop();
        }
    }

    @Test
    void testMockServerV1ArgMultiple() {
        MockServer server = MockServer.featureString("""
            Feature: Mock
            Scenario: pathMatches('/test')
            * def response = { a: argA, b: argB }
            """)
                .arg("argA", "valueA")
                .arg("argB", "valueB")
                .http(0)
                .build();

        try {
            assertEquals("valueA", server.getVariable("argA"));
            assertEquals("valueB", server.getVariable("argB"));
        } finally {
            server.stop();
        }
    }

    @Test
    void testMockServerToV2() {
        MockServer v1Server = MockServer.featureString("""
            Feature: Mock
            Scenario: pathMatches('/hello')
            * def response = { message: 'hello' }
            """)
                .http(0)
                .build();

        try {
            // Can access underlying v2 MockServer
            io.karatelabs.core.MockServer v2Server = v1Server.toV2MockServer();
            assertNotNull(v2Server);
            assertEquals(v1Server.getPort(), v2Server.getPort());
        } finally {
            v1Server.stop();
        }
    }

    @Test
    void testRunnerToV2Builder() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: Test
            Scenario: Pass
            * def a = 1
            """);

        // Can access underlying v2 Builder
        Runner.Builder v1Builder = Runner.path(feature.toString());
        io.karatelabs.core.Runner.Builder v2Builder = v1Builder.toV2Builder();
        assertNotNull(v2Builder);
    }

}
