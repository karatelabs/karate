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
package io.karatelabs.output;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import io.karatelabs.common.ResourceType;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.ServerTestHarness;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Without any {@code configure logging.mask}, the values of Authorization, Cookie, Set-Cookie and
 * Proxy-Authorization are masked in every report format and left as sent on the console;
 * {@code mask: false} turns the default off, and a user's mask adds to it unless {@code defaults: false}.
 */
class DefaultReportMaskTest {

    static final String AUTH = "auth-secret-5d4c3b";
    static final String COOKIE = "cookie-secret-a1b2c3";
    static final String PROXY = "proxy-secret-e5f6a7";
    static final String SET_COOKIE = "setcookie-secret-0f9e8d";
    static final String API_KEY = "apikey-secret-7c6b5a";
    static final Pattern BASE64_DATA = Pattern.compile("\"data\":\\s*\"([A-Za-z0-9+/=]+)\"");

    static ServerTestHarness harness;

    @TempDir
    Path tempDir;

    Logger httpLogger;
    Level origHttp;
    OutputStreamAppender<ILoggingEvent> appender;

    @BeforeAll
    static void start() {
        harness = new ServerTestHarness("classpath:io/karatelabs/report");
        harness.start();
        harness.setHandler(ctx -> {
            var response = ctx.response();
            response.setHeader("Set-Cookie", "session=" + SET_COOKIE);
            response.setBody("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), ResourceType.JSON);
            return response;
        });
    }

    @AfterAll
    static void stop() {
        harness.stop();
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            httpLogger.detachAppender(appender);
            httpLogger.setAdditive(true);
            httpLogger.setLevel(origHttp);
            appender.stop();
        }
        LogContext.clear();
    }

    @Test
    void defaultMasksCredentialHeadersInEveryReportFormat() throws Exception {
        List<String> reports = runAndReadReports("");
        int carrying = 0;
        for (String text : reports) {
            for (String secret : List.of(AUTH, COOKIE, PROXY, SET_COOKIE)) {
                assertFalse(text.contains(secret), secret + " is in a report file");
            }
            if (text.contains("Authorization: ***") && text.contains("Proxy-Authorization: ***")
                    && text.contains("Cookie: ***") && text.contains("Set-Cookie: ***")) {
                carrying++;
            }
        }
        assertTrue(carrying >= 4, "the events, feature page, cucumber json and junit xml carry the masked exchange: " + carrying);
    }

    @Test
    void maskFalseLeavesReportsRaw() throws Exception {
        String all = String.join("\n", runAndReadReports("* configure logging = { mask: false }"));
        for (String secret : List.of(AUTH, COOKIE, PROXY, SET_COOKIE)) {
            assertTrue(all.contains(secret), secret + " should be in the reports");
        }
    }

    @Test
    void userMaskAddsToTheDefaults() throws Exception {
        String all = String.join("\n", runAndReadReports("* configure logging = { mask: { headers: ['X-Api-Key'] } }"));
        for (String secret : List.of(AUTH, COOKIE, PROXY, SET_COOKIE, API_KEY)) {
            assertFalse(all.contains(secret), secret + " is in a report file");
        }
        assertTrue(all.contains("X-Api-Key: ***"), "the user's header is masked");
    }

    @Test
    void defaultsFalseKeepsOnlyTheUsersMask() throws Exception {
        String all = String.join("\n", runAndReadReports(
                "* configure logging = { mask: { defaults: false, headers: ['X-Api-Key'] } }"));
        assertFalse(all.contains(API_KEY), "the user's header is masked");
        assertTrue(all.contains(AUTH), "the default is off");
    }

    @Test
    void consoleIsUnmaskedByDefault() {
        String console = logExchange(null, Level.DEBUG);
        assertTrue(console.contains("Authorization: Bearer " + AUTH), console);
        assertTrue(console.contains("Set-Cookie: session=" + SET_COOKIE), console);
        String report = Console.stripAnsi(LogContext.get().peek());
        assertTrue(report.contains("Authorization: ***"), report);
        assertTrue(report.contains("Set-Cookie: ***"), report);
        assertFalse(report.contains(AUTH), report);
    }

    @Test
    void consoleMasksOnlyTheUsersHeaders() {
        String console = logExchange(Map.of("headers", List.of("X-Api-Key")), Level.TRACE);
        assertTrue(console.contains("Authorization: Bearer " + AUTH), console);
        assertTrue(console.contains("X-Api-Key: ***"), console);
        assertTrue(console.contains("buy milk"), "TRACE keeps the body after the console's header lines: " + console);
        String report = Console.stripAnsi(LogContext.get().peek());
        assertTrue(report.contains("Authorization: ***") && report.contains("X-Api-Key: ***"), report);
    }

    private List<String> runAndReadReports(String configureStep) throws Exception {
        Path feature = tempDir.resolve("auth.feature");
        Files.writeString(feature, """
                Feature: auth
                Scenario: send credentials
                %s
                * url 'http://127.0.0.1:%d'
                * path 'me'
                * header Authorization = 'Bearer ' + karate.properties['auth']
                * header Cookie = 'sid=' + karate.properties['cookie']
                * header Proxy-Authorization = 'Basic ' + karate.properties['proxy']
                * header X-Api-Key = karate.properties['apiKey']
                * method get
                * status 200
                """.formatted(configureStep, harness.getPort()));
        Path reports = tempDir.resolve("reports");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputJsonLines(true)
                .outputHtmlReport(true)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .systemProperty("auth", AUTH)
                .systemProperty("cookie", COOKIE)
                .systemProperty("proxy", PROXY)
                .systemProperty("apiKey", API_KEY)
                .parallel(1);
        assertTrue(result.isPassed(), result.getErrors().toString());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(reports)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        List<String> texts = new ArrayList<>();
        for (Path f : files) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
            Matcher m = BASE64_DATA.matcher(text);
            StringBuilder decoded = new StringBuilder();
            while (m.find()) {
                decoded.append(new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8)).append('\n');
            }
            texts.add(text + decoded);
        }
        return texts;
    }

    private String logExchange(Map<String, Object> userMask, Level level) {
        httpLogger = (Logger) LoggerFactory.getLogger("karate.http");
        origHttp = httpLogger.getLevel();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%msg%n");
        encoder.start();
        appender = new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.setOutputStream(out);
        appender.start();
        httpLogger.addAppender(appender);
        httpLogger.setAdditive(false);
        httpLogger.setLevel(level);
        LogContext.clear();
        if (userMask != null) {
            LogContext.get().setMask(LogMask.fromMap(userMask));
        }
        HttpLogger logger = new HttpLogger(httpLogger);
        HttpRequest request = new HttpRequest();
        request.setMethod("GET");
        request.setUrl("http://example.com/me");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of("Bearer " + AUTH));
        headers.put("X-Api-Key", List.of(API_KEY));
        headers.put("Content-Type", List.of("application/json"));
        request.setHeaders(headers);
        request.setBody("{\"title\":\"buy milk\"}".getBytes(StandardCharsets.UTF_8));
        logger.logRequest(request);
        HttpResponse response = new HttpResponse();
        response.setRequest(request);
        response.setStatus(200);
        response.setHeader("Set-Cookie", "session=" + SET_COOKIE);
        logger.logResponse(response);
        return Console.stripAnsi(out.toString(StandardCharsets.UTF_8));
    }
}
