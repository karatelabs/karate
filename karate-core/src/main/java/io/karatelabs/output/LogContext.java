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

import io.karatelabs.core.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local log collector for scenario execution.
 * All user-script logging (print, karate.log, HTTP) goes here.
 * Also collects embeds (HTML from doc, images, etc.) for reports.
 * The collected log/embeds are written to karate-json reports.
 */
public class LogContext {

    private static final ThreadLocal<LogContext> CURRENT = new ThreadLocal<>();

    // ========== Category Loggers ==========
    // Use these for category-aware logging that cascades to SLF4J

    /** Logger for core framework (Suite, Runner, StepExecutor, reports, config, process utils) */
    public static final Logger RUNTIME_LOGGER = LoggerFactory.getLogger("karate.runtime");

    /** Logger for HTTP request/response logs */
    public static final Logger HTTP_LOGGER = LoggerFactory.getLogger("karate.http");

    /** Logger for mock server logs */
    public static final Logger MOCK_LOGGER = LoggerFactory.getLogger("karate.mock");

    /** Logger for test logs (karate.log, karate.logger, print statements) */
    public static final Logger SCENARIO_LOGGER = LoggerFactory.getLogger("karate.scenario");

    /** Logger for console output (test summary) */
    public static final Logger CONSOLE_LOGGER = LoggerFactory.getLogger("karate.console");

    private static LogLevel threshold = LogLevel.INFO;

    private final StringBuilder buffer = new StringBuilder();
    private List<StepResult.Embed> embeds;

    // ========== Thread-Local Access ==========

    public static LogContext get() {
        LogContext ctx = CURRENT.get();
        if (ctx == null) {
            ctx = new LogContext();
            CURRENT.set(ctx);
        }
        return ctx;
    }

    public static void set(LogContext ctx) {
        CURRENT.set(ctx);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Create a LogWriter that captures to the thread-local LogContext AND cascades to SLF4J.
     * Use this for category-aware logging.
     *
     * @param logger the SLF4J logger (determines the category)
     * @return a LogWriter for fluent logging
     */
    public static LogWriter with(Logger logger) {
        return new LogWriter(logger);
    }

    /**
     * Set the minimum log level for report capture.
     * Logs below this level will be filtered out.
     */
    public static void setLogLevel(LogLevel level) {
        threshold = level;
    }

    /**
     * Get the current log level threshold.
     */
    public static LogLevel getLogLevel() {
        return threshold;
    }

    /**
     * Set the runtime log level for SLF4J/Logback.
     * Uses reflection to avoid compile-time dependency on Logback.
     * Sets the level on the "karate" logger, which affects all subcategories
     * (karate.runtime, karate.http, karate.mock, karate.scenario, karate.console).
     *
     * @param level the log level (trace, debug, info, warn, error)
     * @return true if the level was set successfully, false if Logback is not available
     */
    public static boolean setRuntimeLogLevel(String level) {
        if (level == null || level.isEmpty()) {
            return false;
        }
        try {
            // Get the ILoggerFactory
            Object factory = LoggerFactory.getILoggerFactory();
            if (!factory.getClass().getName().equals("ch.qos.logback.classic.LoggerContext")) {
                RUNTIME_LOGGER.debug("Runtime log level not supported: not using Logback");
                return false;
            }

            // Get the "karate" logger from the context
            // LoggerContext.getLogger(String name) returns ch.qos.logback.classic.Logger
            Object logger = factory.getClass()
                    .getMethod("getLogger", String.class)
                    .invoke(factory, "karate");

            // Get the Level class and parse the level string
            Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
            Object levelValue = levelClass
                    .getMethod("toLevel", String.class)
                    .invoke(null, level.toUpperCase());

            // Set the level on the logger
            logger.getClass()
                    .getMethod("setLevel", levelClass)
                    .invoke(logger, levelValue);

            RUNTIME_LOGGER.debug("Set runtime log level to: {}", level);
            return true;

        } catch (Exception e) {
            RUNTIME_LOGGER.debug("Failed to set runtime log level: {}", e.getMessage());
            return false;
        }
    }

    // ========== Logging ==========

    /**
     * Log a message at the specified level.
     * Message is filtered if level is below threshold.
     */
    public void log(LogLevel level, String message) {
        if (!level.isEnabled(threshold)) {
            return; // Filtered
        }
        buffer.append(message).append('\n');
    }

    /**
     * Log a message at the specified level with format arguments.
     */
    public void log(LogLevel level, String format, Object... args) {
        if (!level.isEnabled(threshold)) {
            return; // Filtered
        }
        String message = format(format, args);
        buffer.append(message).append('\n');
    }

    /**
     * Log a message at INFO level.
     * Used by karate.log() and print statements.
     */
    public void log(Object message) {
        log(LogLevel.INFO, String.valueOf(message));
    }

    /**
     * Log a message at INFO level with format arguments.
     */
    public void log(String format, Object... args) {
        log(LogLevel.INFO, format, args);
    }

    // ========== Embeds ==========

    /**
     * Add an embed (HTML, image, etc.) to be included in step result.
     */
    public void embed(byte[] data, String mimeType, String name) {
        if (embeds == null) {
            embeds = new ArrayList<>();
        }
        embeds.add(new StepResult.Embed(data, mimeType, name));
    }

    /**
     * Add an embed with just data and mime type.
     */
    public void embed(byte[] data, String mimeType) {
        embed(data, mimeType, null);
    }

    /**
     * Collect and clear embeds.
     */
    public List<StepResult.Embed> collectEmbeds() {
        List<StepResult.Embed> result = embeds;
        embeds = null;
        return result;
    }

    // ========== Collect ==========

    /**
     * Get accumulated log and clear buffer (for step/scenario end).
     */
    public String collect() {
        String result = buffer.toString();
        buffer.setLength(0);
        return result;
    }

    /**
     * Get accumulated log without clearing.
     */
    public String peek() {
        return buffer.toString();
    }

    // ========== Format Helper ==========

    /**
     * Simple {} placeholder replacement (no SLF4J dependency).
     */
    static String format(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < format.length()) {
            if (i < format.length() - 1 && format.charAt(i) == '{' && format.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    sb.append(args[argIndex++]);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(format.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    // ========== LogWriter Inner Class ==========

    /**
     * Fluent log writer that captures to LogContext buffer AND cascades to SLF4J.
     * Thread-safe: each call gets the current thread's LogContext.
     */
    public static class LogWriter {

        private final Logger logger;

        LogWriter(Logger logger) {
            this.logger = logger;
        }

        public void trace(String message) {
            log(LogLevel.TRACE, message);
            logger.trace(message);
        }

        public void debug(String message) {
            log(LogLevel.DEBUG, message);
            logger.debug(message);
        }

        public void info(String message) {
            log(LogLevel.INFO, message);
            logger.info(message);
        }

        public void warn(String message) {
            log(LogLevel.WARN, message);
            logger.warn(message);
        }

        public void error(String message) {
            log(LogLevel.ERROR, message);
            logger.error(message);
        }

        private void log(LogLevel level, String message) {
            if (level.isEnabled(threshold)) {
                get().buffer.append(message).append('\n');
            }
        }
    }
}
