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
package com.intuit.karate.cucumber;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.intuit.karate.FileUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ReporterLogAppender extends AppenderBase<ILoggingEvent> {
    
    private final Logger logger;
    private final PatternLayoutEncoder encoder;
    private final String threadName;
    private StringBuilder sb;
    
    public ReporterLogAppender() {
        sb = new StringBuilder();
        this.threadName = Thread.currentThread().getName();
        logger = (Logger) LoggerFactory.getLogger("com.intuit");
        setName("karate-reporter");
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(lc);
        encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level - %msg%n");
        encoder.setContext(context);
        encoder.start();
        start();
        logger.addAppender(this);     
    }
    
    public String collect() {
        String temp = sb.toString();
        sb = new StringBuilder();
        return temp;
    }
    
    @Override
    protected void append(ILoggingEvent event) {
        if (!threadName.equals(event.getThreadName())) {
            return;
        }
        try {
            byte[] bytes = encoder.encode(event);
            String line = FileUtils.toString(bytes);
            sb.append(line);
        } catch (Exception e) {
            System.err.println("possible logback version conflict: " + e.getMessage());
            e.printStackTrace();
        }
    }    
    
}
