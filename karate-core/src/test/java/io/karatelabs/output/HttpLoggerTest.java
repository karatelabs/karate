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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpLoggerTest {

    private Logger logbackLogger;
    private OutputStreamAppender<ILoggingEvent> appender;
    private ByteArrayOutputStream consoleOut;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        logbackLogger = (Logger) LoggerFactory.getLogger("karate.test.httplogger");
        originalLevel = logbackLogger.getLevel();
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
        logbackLogger.addAppender(appender);
        logbackLogger.setAdditive(false);
        LogContext.clear();
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        appender.stop();
        logbackLogger.setLevel(originalLevel);
        LogContext.clear();
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
        return s.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    // The default `pretty: true` reformats JSON bodies — `"id": 42` (with a space)
    // not `"id":42`. Tests assert against the spaced form.
    @Test
    void reportBufferAlwaysHasFullContentAtInfoLevel() {
        logbackLogger.setLevel(Level.INFO);
        HttpLogger httpLogger = new HttpLogger(logbackLogger);
        HttpRequest request = sampleRequest();
        httpLogger.logRequest(request);
        httpLogger.logResponse(sampleResponse(request));
        String reportBuffer = strip(LogContext.get().peek());
        // Report has request AND response bodies
        assertTrue(reportBuffer.contains("POST http://example.com/api/todos"), reportBuffer);
        assertTrue(reportBuffer.contains("Content-Type: application/json"), reportBuffer);
        assertTrue(reportBuffer.contains("buy milk"), reportBuffer);
        assertTrue(reportBuffer.contains("201 POST"), reportBuffer);
        assertTrue(reportBuffer.contains("\"id\": 42"), reportBuffer);
        // Console (SLF4J) at INFO shows one-liner only — no headers, no body
        String console = strip(consoleOut.toString());
        assertTrue(console.contains("1 > POST http://example.com/api/todos"), console);
        assertTrue(console.contains("1 < 201 POST http://example.com/api/todos"), console);
        assertTrue(console.contains("(17 ms)"), console);
        assertFalse(console.contains("Content-Type: application/json"), console);
        assertFalse(console.contains("buy milk"), console);
    }

    @Test
    void reportBufferAlwaysHasFullContentAtDebugLevel() {
        logbackLogger.setLevel(Level.DEBUG);
        HttpLogger httpLogger = new HttpLogger(logbackLogger);
        HttpRequest request = sampleRequest();
        httpLogger.logRequest(request);
        httpLogger.logResponse(sampleResponse(request));
        String reportBuffer = strip(LogContext.get().peek());
        assertTrue(reportBuffer.contains("buy milk"), reportBuffer);
        assertTrue(reportBuffer.contains("\"id\": 42"), reportBuffer);
        // Console (SLF4J) at DEBUG shows headers but not bodies
        String console = strip(consoleOut.toString());
        assertTrue(console.contains("Content-Type: application/json"), console);
        assertFalse(console.contains("buy milk"), console);
        assertFalse(console.contains("\"id\": 42"), console);
    }

    @Test
    void reportBufferAlwaysHasFullContentAtTraceLevel() {
        logbackLogger.setLevel(Level.TRACE);
        HttpLogger httpLogger = new HttpLogger(logbackLogger);
        HttpRequest request = sampleRequest();
        httpLogger.logRequest(request);
        httpLogger.logResponse(sampleResponse(request));
        String reportBuffer = strip(LogContext.get().peek());
        assertTrue(reportBuffer.contains("buy milk"), reportBuffer);
        assertTrue(reportBuffer.contains("\"id\": 42"), reportBuffer);
        // Console (SLF4J) at TRACE shows headers and bodies
        String console = strip(consoleOut.toString());
        assertTrue(console.contains("Content-Type: application/json"), console);
        assertTrue(console.contains("buy milk"), console);
        assertTrue(console.contains("\"id\": 42"), console);
    }
}
