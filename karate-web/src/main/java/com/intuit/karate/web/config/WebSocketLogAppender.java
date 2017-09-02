/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.web.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.classic.Logger;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Script;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private final PatternLayoutEncoder encoder;
    private StringBuilder sb;
    private LogAppenderTarget target;
    private final Logger logger;
    private final String sessionId;

    public WebSocketLogAppender(String sessionId) {
        // deliberately NOT the com.intuit form else will pick up all those
        // this is supposed to isolate user-session s
        this.sessionId = sessionId;
        logger = (Logger) LoggerFactory.getLogger(sessionId);
        sb = new StringBuilder();
        setName("karate-web");
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(lc);
        encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level - %msg%n");
        encoder.setContext(context);
        encoder.start();
        start();
        logger.addAppender(this);
        logger.setLevel(Level.DEBUG);
    }

    public String getSessionId() {
        return sessionId;
    }        

    public void setTarget(LogAppenderTarget target) {
        this.target = target;
    }        

    public String getBuffer() {
        return sb.toString();
    }

    public void clearBuffer() {
        sb = new StringBuilder();
    }

    @Override
    protected void append(ILoggingEvent event) {
        byte[] bytes = encoder.encode(event);
        String line = FileUtils.toString(bytes);
        sb.append(line);
        if (target != null) {
            target.append(line);
        }
    }

    public org.slf4j.Logger getLogger() {
        return (org.slf4j.Logger) logger;
    }

}
