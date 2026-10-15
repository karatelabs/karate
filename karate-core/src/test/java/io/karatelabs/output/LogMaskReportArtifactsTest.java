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
package io.karatelabs.output;

import io.karatelabs.common.Json;
import io.karatelabs.common.ResourceType;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.http.ServerTestHarness;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code configure logging.mask} masks HTTP traffic where it is captured, so every report artifact that
 * carries it — HTML page data, the JSONL stream, Cucumber JSON (where the log travels base64) and JUnit XML —
 * holds the masked text.
 */
class LogMaskReportArtifactsTest {

    static final String HEADER_SECRET = "hdr-secret-9f8e7d";
    static final String BODY_SECRET = "body-secret-1a2b3c";
    static final Pattern BASE64_DATA = Pattern.compile("\"data\":\\s*\"([A-Za-z0-9+/=]+)\"");

    static ServerTestHarness harness;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void start() {
        harness = new ServerTestHarness("classpath:io/karatelabs/report");
        harness.start();
        harness.setHandler(ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.parseLenient(ctx.request().getBodyString());
            var response = ctx.response();
            response.setBody(Json.toBytes(Map.of("token", body.get("token"), "ok", true)), ResourceType.JSON);
            return response;
        });
    }

    @AfterAll
    static void stop() {
        harness.stop();
    }

    @Test
    void maskedHeaderAndJsonPathReachNoReportFile() throws Exception {
        Path feature = tempDir.resolve("login.feature");
        Files.writeString(feature, """
                Feature: login
                Background:
                * configure logging = { mask: { headers: ['Authorization'], jsonPaths: ['$..token'] } }
                Scenario: send a credential
                * url 'http://127.0.0.1:%d'
                * path 'login'
                * header Authorization = 'Bearer ' + karate.properties['hdr']
                * def payload = { token: '#(karate.properties["body"])', user: 'u' }
                * request payload
                * method post
                * status 200
                """.formatted(harness.getPort()));
        Path reports = tempDir.resolve("reports");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputJsonLines(true)
                .outputHtmlReport(true)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .systemProperty("hdr", HEADER_SECRET)
                .systemProperty("body", BODY_SECRET)
                .parallel(1);
        assertTrue(result.isPassed());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(reports)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        int carrying = 0;
        for (Path f : files) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
            Matcher m = BASE64_DATA.matcher(text);
            StringBuilder decoded = new StringBuilder();
            while (m.find()) {
                decoded.append(new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8)).append('\n');
            }
            String all = text + decoded;
            assertFalse(all.contains(HEADER_SECRET), "the header value is in " + reports.relativize(f));
            assertFalse(all.contains(BODY_SECRET), "the body value is in " + reports.relativize(f));
            String unescaped = all.replace("\\\"", "\"").replace("&quot;", "\"");
            if (unescaped.contains("Authorization: ***") && unescaped.contains("\"token\": \"***\"")) {
                carrying++;
            }
        }
        assertTrue(carrying >= 4, "the events, feature page, cucumber json and junit xml carry the masked exchange: " + carrying);
    }
}
