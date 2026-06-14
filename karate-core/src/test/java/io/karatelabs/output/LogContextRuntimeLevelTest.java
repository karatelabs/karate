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
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link LogContext#setRuntimeLogLevel} fanning the console level out to every
 * {@code karate.*} category, so {@code configure logging = { console: ... }} actually
 * silences HTTP — even though the bundled logback.xml pins {@code karate.http} to an
 * explicit level (issue #2917). Also covers the {@code off}/{@code none} aliases and
 * that report capture is independent of the console level.
 */
class LogContextRuntimeLevelTest {

    private Logger karateParent;
    private Logger httpLogger;
    private Logger runtimeLogger;
    private Level origParent;
    private Level origHttp;
    private Level origRuntime;

    private OutputStreamAppender<ILoggingEvent> appender;
    private ByteArrayOutputStream consoleOut;

    @BeforeEach
    void setUp() {
        karateParent = (Logger) LoggerFactory.getLogger("karate");
        httpLogger = (Logger) LoggerFactory.getLogger("karate.http");
        runtimeLogger = (Logger) LoggerFactory.getLogger("karate.runtime");
        origParent = karateParent.getLevel();
        origHttp = httpLogger.getLevel();
        origRuntime = runtimeLogger.getLevel();

        consoleOut = new ByteArrayOutputStream();
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%level %msg%n");
        encoder.start();
        appender = new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.setOutputStream(consoleOut);
        appender.start();
        httpLogger.addAppender(appender);
        httpLogger.setAdditive(false);
        LogContext.clear();
    }

    @AfterEach
    void tearDown() {
        httpLogger.detachAppender(appender);
        appender.stop();
        karateParent.setLevel(origParent);
        httpLogger.setLevel(origHttp);
        runtimeLogger.setLevel(origRuntime);
        LogContext.clear();
    }

    // The crux of #2917: karate.http is pinned to an explicit level (DEBUG in the
    // shipped logback.xml), which in Logback overrides the "karate" parent. The
    // console knob must override the child too, not just the parent.
    @Test
    void consoleLevelOverridesPinnedChildLogger() {
        httpLogger.setLevel(Level.DEBUG); // simulate logback.xml's karate.http=DEBUG
        LogContext.setRuntimeLogLevel("warn");
        assertEquals(Level.WARN, httpLogger.getLevel());
        assertEquals(Level.WARN, karateParent.getLevel());
        assertEquals(Level.WARN, runtimeLogger.getLevel());
    }

    @Test
    void offAndNoneSilenceTheConsole() {
        httpLogger.setLevel(Level.DEBUG);
        LogContext.setRuntimeLogLevel("off");
        assertEquals(Level.OFF, httpLogger.getLevel());
        LogContext.setRuntimeLogLevel("none"); // alias for off
        assertEquals(Level.OFF, httpLogger.getLevel());
    }

    // With console silenced, the HTTP exchange must NOT hit the console but MUST still
    // land in the report buffer — the two paths are independent.
    @Test
    void offSilencesHttpConsoleButReportBufferKeepsFullContent() {
        httpLogger.setLevel(Level.DEBUG);
        LogContext.setRuntimeLogLevel("off");
        HttpLogger logger = new HttpLogger(httpLogger);
        HttpRequest request = sampleRequest();
        logger.logRequest(request);
        logger.logResponse(sampleResponse(request));
        String console = consoleOut.toString();
        assertTrue(console.isEmpty(), "console should be silent, got: " + console);
        String report = strip(LogContext.get().peek());
        assertTrue(report.contains("POST http://example.com/api/todos"), report);
        assertTrue(report.contains("buy milk"), report);
        assertTrue(report.contains("\"id\": 42"), report);
    }

    // A mid-test console change must not leak: snapshot before, restore after, and the
    // pinned child level is back where logback.xml left it.
    @Test
    void snapshotRestoresPinnedChildLevel() {
        httpLogger.setLevel(Level.DEBUG);
        LogContext.Snapshot snap = LogContext.snapshot();
        LogContext.setRuntimeLogLevel("off");
        assertEquals(Level.OFF, httpLogger.getLevel());
        snap.restore();
        assertEquals(Level.DEBUG, httpLogger.getLevel());
    }

    private HttpRequest sampleRequest() {
        HttpRequest request = new HttpRequest();
        request.setMethod("POST");
        request.setUrl("http://example.com/api/todos");
        request.setHeaders(Collections.singletonMap("Content-Type", Collections.singletonList("application/json")));
        request.setBody("{\"title\":\"buy milk\"}".getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private HttpResponse sampleResponse(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.setRequest(request);
        response.setStatus(201);
        response.setHeaders(Collections.singletonMap("Content-Type", Collections.singletonList("application/json")));
        response.setBody("{\"id\":42,\"title\":\"buy milk\"}".getBytes(StandardCharsets.UTF_8), ResourceType.JSON);
        response.setResponseTime(17);
        return response;
    }

    private String strip(String s) {
        return s.replaceAll("\\[[0-9;]*m", "");
    }
}
