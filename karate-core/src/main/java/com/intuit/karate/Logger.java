/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

/**
 * derived from org.slf4j.simple.SimpleLogger
 *
 * @author pthomas3
 */
public class Logger {

    public static final int LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    public static final int LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    public static final int LEVEL_INFO = LocationAwareLogger.INFO_INT;
    public static final int LEVEL_WARN = LocationAwareLogger.WARN_INT;
    public static final int LEVEL_ERROR = LocationAwareLogger.ERROR_INT;

    private final org.slf4j.Logger LOGGER;

    private final int currentLogLevel;
    // not static, has to be per thread
    private final DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

    private LogAppender logAppender;

    public void setLogAppender(LogAppender logAppender) {
        this.logAppender = logAppender;
    }

    public Logger() {
        this(LEVEL_DEBUG);
    }

    public Logger(int logLevel) {
        LOGGER = LoggerFactory.getLogger("com.intuit.karate");
        this.currentLogLevel = logLevel;
    }

    public void trace(String format, Object... arguments) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(format, arguments);
        }
        if (isLogEnabled(LEVEL_TRACE)) {
            formatAndAppend(LEVEL_TRACE, format, arguments);
        }
    }

    public void debug(String format, Object... arguments) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(format, arguments);
        }
        if (isLogEnabled(LEVEL_DEBUG)) {
            formatAndAppend(LEVEL_DEBUG, format, arguments);
        }
    }

    public void info(String format, Object... arguments) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(format, arguments);
        }
        if (isLogEnabled(LEVEL_INFO)) {
            formatAndAppend(LEVEL_INFO, format, arguments);
        }
    }

    public void warn(String format, Object... arguments) {
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(format, arguments);
        }
        if (isLogEnabled(LEVEL_WARN)) {
            formatAndAppend(LEVEL_WARN, format, arguments);
        }
    }

    public void error(String format, Object... arguments) {
        LOGGER.error(format, arguments);
        formatAndAppend(LEVEL_ERROR, format, arguments);
    }

    private String getFormattedDate() {
        Date now = new Date();
        String dateText;
        dateText = dateFormatter.format(now);
        return dateText;
    }

    private boolean isLogEnabled(int level) {
        return currentLogLevel <= level;
    }

    private void formatAndAppend(int level, String format, Object... arguments) {
        if (!isLogEnabled(level)) {
            return;
        }
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        append(level, tp.getMessage());
    }

    private void append(int level, String message) {
        if (logAppender == null || !isLogEnabled(level)) {
            return;
        }
        StringBuilder buf = new StringBuilder();
        buf.append(getFormattedDate());
        buf.append(' ');
        buf.append(message);
        buf.append('\n');
        logAppender.append(buf.toString());
    }

}
