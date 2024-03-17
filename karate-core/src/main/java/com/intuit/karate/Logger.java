/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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

/**
 * derived from org.slf4j.simple.SimpleLogger
 *
 * @author pthomas3
 */
public class Logger {

    private static final String DEFAULT_PACKAGE = "com.intuit.karate";

    private final org.slf4j.Logger LOGGER;

    // not static, has to be per thread
    private final DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

    private LogAppender appender = LogAppender.NO_OP;

    private boolean appendOnly;

    private boolean logOnly;

    public void setAppender(LogAppender appender) {
        this.appender = appender;
    }

    public LogAppender getAppender() {
        return appender;
    }

    public boolean isTraceEnabled() {
        return LOGGER.isTraceEnabled();
    }

    public void setAppendOnly(boolean appendOnly) {
        this.appendOnly = appendOnly;
    }

    public boolean isAppendOnly() {
        return appendOnly;
    }

    public void setLogOnly(boolean logOnly) {
        this.logOnly = logOnly;
    }

    public boolean isLogOnly() {
        return logOnly;
    }

    public Logger(Class clazz) {
        LOGGER = LoggerFactory.getLogger(clazz);
    }

    public Logger(String name) {
        LOGGER = LoggerFactory.getLogger(name);
    }

    public Logger() {
        this(DEFAULT_PACKAGE);
    }

    public void trace(String format, Object... arguments) {
        if (LOGGER.isTraceEnabled()) {
            if (!appendOnly) {
                LOGGER.trace(format, arguments);
            }
            if (!logOnly) {
                formatAndAppend(format, arguments);
            }
        }
    }

    public void debug(String format, Object... arguments) {
        if (LOGGER.isDebugEnabled()) {
            if (!appendOnly) {
                LOGGER.debug(format, arguments);
            }
            if (!logOnly) {
                formatAndAppend(format, arguments);
            }
        }
    }

    public void info(String format, Object... arguments) {
        if (LOGGER.isInfoEnabled()) {
            if (!appendOnly) {
                LOGGER.info(format, arguments);
            }
            if (!logOnly) {
                formatAndAppend(format, arguments);
            }
        }
    }

    public void warn(String format, Object... arguments) {
        if (LOGGER.isWarnEnabled()) {
            if (!appendOnly) {
                LOGGER.warn(format, arguments);
            }
            if (!logOnly) {
                formatAndAppend(format, arguments);
            }
        }
    }

    public void error(String format, Object... arguments) {
        if (LOGGER.isErrorEnabled()) {
            if (!appendOnly) {
                LOGGER.error(format, arguments);
            }
            if (!logOnly) {
                formatAndAppend(format, arguments);
            }
        }
    }

    private String getFormattedDate() {
        Date now = new Date();
        String dateText;
        dateText = dateFormatter.format(now);
        return dateText;
    }

    private void formatAndAppend(String format, Object... arguments) {
        if (appender == null) {
            return;
        }
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        append(tp.getMessage());
    }

    private void append(String message) {
        StringBuilder buf = new StringBuilder();
        buf.append(getFormattedDate()).append(' ').append(message).append('\n');
        appender.append(buf.toString());
    }

}
