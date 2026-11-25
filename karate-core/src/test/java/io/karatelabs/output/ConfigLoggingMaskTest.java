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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A mask set with {@code karate.configure('logging', ...)} in karate-base.js, karate-config.js or
 * karate-config-&lt;env&gt;.js reaches every HTTP exchange of the run, on the console and in the reports:
 * a top-level scenario, a called feature, callonce, callSingle, an outline row, parallel threads, and
 * a {@code configure logging} issued mid-scenario. The default credential headers are masked on the
 * console as in the reports, with no config at all too.
 */
class ConfigLoggingMaskTest {

    static final String AUTH = "eyJhbGciOiJIUzI1NiJ9.auth-secret-91f2";
    static final String API_KEY = "apikey-secret-3e8d";
    static final String OTHER = "other-secret-6b1c";
    static final String MASK_CONFIG = """
            karate.configure('logging', {
              console: 'trace',
              mask: { defaults: true, headers: ['X-API-KEY'], replacement: '***' }
            });
            """;
    static final Pattern BASE64_DATA = Pattern.compile("\"data\":\\s*\"([A-Za-z0-9+/=]+)\"");

    static ServerTestHarness harness;

    @TempDir
    Path tempDir;

    Map<String, Level> origLevels = new LinkedHashMap<>();
    Logger httpLogger;
    OutputStreamAppender<ILoggingEvent> appender;
    ByteArrayOutputStream consoleOut;

    @BeforeAll
    static void start() {
        harness = new ServerTestHarness("classpath:io/karatelabs/report");
        harness.start();
        harness.setHandler(ctx -> {
            var response = ctx.response();
            response.setHeader("x-api-key", API_KEY);
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
            appender.stop();
        }
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        origLevels.forEach((name, level) -> ctx.getLogger(name).setLevel(level));
        LogContext.clear();
    }

    @Test
    void maskInKarateConfig() throws Exception {
        run(Map.of("karate-config.js", "function fn() {\n" + MASK_CONFIG + "return {}; }"), null, 1);
    }

    @Test
    void maskInKarateConfigParallel() throws Exception {
        run(Map.of("karate-config.js", "function fn() {\n" + MASK_CONFIG + "return {}; }"), null, 3);
    }

    @Test
    void maskInKarateBase() throws Exception {
        run(Map.of("karate-base.js", "function fn() {\n" + MASK_CONFIG + "return {}; }",
                "karate-config.js", "function fn() { return {}; }"), null, 2);
    }

    @Test
    void maskInEnvConfig() throws Exception {
        run(Map.of("karate-config.js", "function fn() { return {}; }",
                "karate-config-dev.js", "function fn() {\n" + MASK_CONFIG + "return {}; }"), "dev", 2);
    }

    @Test
    void maskThenCallSingleInKarateConfig() throws Exception {
        run(Map.of("karate-config.js", "function fn() {\n" + MASK_CONFIG
                + "var s = karate.callSingle('called.feature'); return {}; }"), null, 2);
    }

    @Test
    void noLoggingConfigMasksTheDefaultsOnTheConsole() throws Exception {
        String console = run(Map.of("karate-config.js",
                "function fn() { karate.configure('logging', { console: 'trace' }); return {}; }"), null, 1, false);
        assertTrue(console.contains(API_KEY), "the run sent X-API-KEY unmasked, so the console was at TRACE: " + console);
    }

    private String run(Map<String, String> configFiles, String env, int threads) throws Exception {
        return run(configFiles, env, threads, true);
    }

    private String run(Map<String, String> configFiles, String env, int threads, boolean apiKeyMasked) throws Exception {
        for (var e : configFiles.entrySet()) {
            Files.writeString(tempDir.resolve(e.getKey()), e.getValue());
        }
        Files.writeString(tempDir.resolve("called.feature"), """
                @ignore
                Feature: called
                Scenario: send
                * url 'http://127.0.0.1:' + karate.properties['port']
                * path 'called'
                * header Authorization = 'Bearer ' + karate.properties['auth']
                * header X-API-KEY = karate.properties['apiKey']
                * method get
                * status 200
                """);
        Files.writeString(tempDir.resolve("main.feature"), """
                Feature: main
                Background:
                * def once = callonce read('called.feature')
                * url 'http://127.0.0.1:' + karate.properties['port']

                Scenario: top level and a call
                * call read('called.feature')
                * path 'top'
                * header Authorization = 'Bearer ' + karate.properties['auth']
                * header x-api-key = karate.properties['apiKey']
                * method get
                * status 200

                Scenario Outline: row <n>
                * path 'row', '<n>'
                * header X-Api-Key = karate.properties['apiKey']
                * method get
                * status 200
                Examples:
                | n |
                | 1 |
                | 2 |

                Scenario: callSingle
                * def s = karate.callSingle('called.feature')

                Scenario: configure mid-scenario
                * configure logging = { mask: { headers: ['X-API-KEY', 'X-Other'] } }
                * path 'mid'
                * header X-API-KEY = karate.properties['apiKey']
                * header X-Other = karate.properties['other']
                * method get
                * status 200
                """);
        attachConsole();
        Path reports = tempDir.resolve("reports");
        SuiteResult result = Runner.path(tempDir.resolve("main.feature").toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .karateEnv(env)
                .outputDir(reports)
                .outputJsonLines(true)
                .outputHtmlReport(true)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .systemProperty("port", String.valueOf(harness.getPort()))
                .systemProperty("auth", AUTH)
                .systemProperty("apiKey", API_KEY)
                .systemProperty("other", OTHER)
                .parallel(threads);
        assertTrue(result.isPassed(), result.getErrors().toString());
        String console = Console.stripAnsi(consoleOut.toString(StandardCharsets.UTF_8));
        if (apiKeyMasked) {
            assertTrue(console.contains("X-API-KEY: ***"), "console at TRACE shows the request headers: " + console);
            assertTrue(console.contains("x-api-key: ***"), "the response header, matched case-insensitively: " + console);
            assertFalse(console.contains(API_KEY), "X-API-KEY leaked to the console: " + console);
        }
        assertFalse(console.contains(AUTH), "Authorization leaked to the console: " + console);
        assertTrue(console.contains("Authorization: ***"), console);
        assertFalse(console.contains(OTHER), "a mid-scenario mask did not reach the console: " + console);
        String all = readReports(reports);
        assertTrue(all.contains("Authorization: ***"), "the reports carry the masked exchange");
        for (String secret : apiKeyMasked ? new String[]{AUTH, API_KEY, OTHER} : new String[]{AUTH, OTHER}) {
            assertFalse(all.contains(secret), secret + " is in a report file");
        }
        return console;
    }

    private void attachConsole() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (String name : new String[]{"karate", "karate.runtime", "karate.http", "karate.mock",
                "karate.server", "karate.scenario", "karate.console", "io.karatelabs"}) {
            origLevels.put(name, ctx.getLogger(name).getLevel());
        }
        httpLogger = ctx.getLogger("karate.http");
        consoleOut = new ByteArrayOutputStream();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%msg%n");
        encoder.start();
        appender = new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.setOutputStream(consoleOut);
        appender.start();
        httpLogger.addAppender(appender);
        httpLogger.setAdditive(false);
    }

    private static String readReports(Path reports) throws Exception {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> walk = Files.walk(reports)) {
            for (Path f : walk.filter(Files::isRegularFile).toList()) {
                String text = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                all.append(text).append('\n');
                Matcher m = BASE64_DATA.matcher(text);
                while (m.find()) {
                    all.append(new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        return all.toString();
    }
}
