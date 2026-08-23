/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.core.mock;

import io.karatelabs.common.ResourceType;
import io.karatelabs.core.InMemoryHttpClient;
import io.karatelabs.core.MockServer;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code configure strictResponseParsing} end to end, through the real HTTP client: the declared
 * Content-Type drives the {@code response} conversion — no first-character sniffing — while the
 * default lane keeps the V1-compatible sniff (the #2977/#2990 behavior pinned in
 * {@code HttpUtilsTest} stays the default). Also proves the config crosses a feature-call
 * boundary (the {@code copyFrom} inheritance path), which is where a forgotten field silently
 * reverts to the default.
 */
class StrictResponseParsingTest {

    private static MockServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = MockServer.featureString("""
            Feature: strict-response-parsing mock

            # wholly-valid JSON deliberately DECLARED text/plain (the sniffable shape)
            Scenario: pathMatches('/plain-json')
              * def response = '{"total":81.99,"outcome":"auto_approved"}'
              * def responseHeaders = { 'Content-Type': 'text/plain' }

            Scenario: pathMatches('/json')
              * def response = { total: 81.99 }
            """)
                .start();
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stopAsync();
    }

    @Test
    void lenientSniffStaysTheDefault() {
        ScenarioRuntime sr = run(new ApacheHttpClient(), """
            * url 'http://localhost:%d'
            * path 'plain-json'
            * method get
            * match responseType == 'json'
            * match response.total == 81.99
            """.formatted(port));
        assertPassed(sr);
    }

    @Test
    void strictHonorsTheDeclaredContentType() {
        ScenarioRuntime sr = run(new ApacheHttpClient(), """
            * configure strictResponseParsing = true
            * match karate.config.strictResponseParsing == true
            * url 'http://localhost:%d'
            * path 'plain-json'
            * method get
            * match responseType == 'string'
            * match response == '{"total":81.99,"outcome":"auto_approved"}'
            """.formatted(port));
        assertPassed(sr);
    }

    @Test
    void strictStillParsesDeclaredJson() {
        ScenarioRuntime sr = run(new ApacheHttpClient(), """
            * configure strictResponseParsing = true
            * url 'http://localhost:%d'
            * path 'json'
            * method get
            * match responseType == 'json'
            * match response.total == 81.99
            """.formatted(port));
        assertPassed(sr);
    }

    @Test
    void strictCoversTheKarateHttpJsLaneForAnyClient() {
        // a CUSTOM HttpClient (here the in-memory test double) is not obliged to stamp the flag,
        // and karate.http() never passes through the step executor — the shared builder's
        // config-supplier stamp is what keeps this lane consistent with the method-step lane
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            HttpResponse response = new HttpResponse();
            response.setStatus(200);
            response.setBody("{\"total\":81.99}", ResourceType.TEXT);
            return response;
        });
        ScenarioRuntime sr = run(client, """
            * configure strictResponseParsing = true
            * def res = karate.http('http://in-memory').get()
            * match res.body == '{"total":81.99}'
            """);
        assertPassed(sr);
    }

    @Test
    void strictSurvivesAFeatureCallBoundary(@TempDir Path tempDir) throws Exception {
        // the copyFrom inheritance path: the CALLER configures, the CALLEE's response obeys
        Path called = tempDir.resolve("called.feature");
        Files.writeString(called, """
            Feature: called
            Scenario:
            * url 'http://localhost:%d'
            * path 'plain-json'
            * method get
            * match responseType == 'string'
            """.formatted(port));
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
            Feature: caller
            Scenario:
            * configure strictResponseParsing = true
            * call read('called.feature')
            """);
        SuiteResult result = Runner.builder()
                .path(caller.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);
        assertTrue(result.isPassed(), () -> "callee should inherit strict parsing: " + result.getErrors());
    }
}
