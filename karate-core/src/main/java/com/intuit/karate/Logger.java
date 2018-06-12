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

    private static final int LOG_LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    private static final int LOG_LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    private static final int LOG_LEVEL_INFO = LocationAwareLogger.INFO_INT;
    private static final int LOG_LEVEL_WARN = LocationAwareLogger.WARN_INT;
    private static final int LOG_LEVEL_ERROR = LocationAwareLogger.ERROR_INT;

    private static final long START_TIME = System.currentTimeMillis();

    private final org.slf4j.Logger LOGGER;

    private final int currentLogLevel;
    // not static, has to be per thread
    private final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    private LogAppender logAppender;

    public void setLogAppender(LogAppender logAppender) {
        this.logAppender = logAppender;
    }

    public Logger() {
        LOGGER = LoggerFactory.getLogger("com.intuit.karate");
        if (LOGGER.isTraceEnabled()) {
            currentLogLevel = LOG_LEVEL_TRACE;
        } else if (LOGGER.isDebugEnabled()) {
            currentLogLevel = LOG_LEVEL_DEBUG;
        } else if (LOGGER.isInfoEnabled()) {
            currentLogLevel = LOG_LEVEL_INFO;
        } else if (LOGGER.isWarnEnabled()) {
            currentLogLevel = LOG_LEVEL_WARN;
        } else if (LOGGER.isErrorEnabled()) {
            currentLogLevel = LOG_LEVEL_ERROR;
        } else {
            currentLogLevel = LOG_LEVEL_DEBUG;
        }
    }

    public boolean isTraceEnabled() {
        return LOGGER.isTraceEnabled();
    }

    public void trace(String format, Object... arguments) {
        LOGGER.trace(format, arguments);
        formatAndAppend(LOG_LEVEL_TRACE, format, arguments);
    }

    public boolean isDebugEnabled() {
        return LOGGER.isDebugEnabled();
    }

    public void debug(String format, Object... arguments) {
        LOGGER.debug(format, arguments);
        formatAndAppend(LOG_LEVEL_DEBUG, format, arguments);
    }

    public boolean isInfoEnabled() {
        return LOGGER.isInfoEnabled();
    }

    public void info(String format, Object... arguments) {
        LOGGER.info(format, arguments);
        formatAndAppend(LOG_LEVEL_INFO, format, arguments);
    }

    public void warn(String format, Object... arguments) {
        LOGGER.warn(format, arguments);
        formatAndAppend(LOG_LEVEL_WARN, format, arguments);
    }

    public void error(String format, Object... arguments) {
        LOGGER.error(format, arguments);
        formatAndAppend(LOG_LEVEL_ERROR, format, arguments);
    }

    private boolean isLevelEnabled(int logLevel) {
        return (logLevel >= currentLogLevel);
    }

    private String getFormattedDate() {
        Date now = new Date();
        String dateText;
        dateText = dateFormatter.format(now);
        return dateText;
    }

    private String renderLevel(int level) {
        switch (level) {
            case LOG_LEVEL_TRACE:
                return "TRACE";
            case LOG_LEVEL_DEBUG:
                return ("DEBUG");
            case LOG_LEVEL_INFO:
                return "INFO";
            case LOG_LEVEL_WARN:
                return "WARN";
            case LOG_LEVEL_ERROR:
                return "ERROR";
        }
        throw new IllegalStateException("Unrecognized level [" + level + "]");
    }

    private void formatAndAppend(int level, String format, Object... arguments) {
        if (!isLevelEnabled(level)) {
            return;
        }
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        append(level, tp.getMessage());
    }

    private void append(int level, String message) {
        if (logAppender == null || !isLevelEnabled(level)) {
            return;
        }
        StringBuilder buf = new StringBuilder(32);
        buf.append(getFormattedDate());
        buf.append(' ').append('[');
        String levelStr = renderLevel(level);
        buf.append(levelStr);
        buf.append(']').append(' ');
        buf.append(message);
        logAppender.append(buf.toString());
    }

}
