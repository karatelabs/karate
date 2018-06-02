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
package com.intuit.karate.ui;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.intuit.karate.FileUtils;
import javafx.scene.control.TextArea;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class TextAreaLogAppender extends AppenderBase<ILoggingEvent> {

    private final TextArea textArea;
    private final Logger logger;
    private final PatternLayoutEncoder encoder;

    public TextAreaLogAppender(TextArea textArea) {
        this.textArea = textArea;
        LoggerContext ctx = null;
		if (!(LoggerFactory.getILoggerFactory()
				.getLogger("com.intuit.karate") instanceof ch.qos.logback.classic.Logger)) {
			ctx = new LoggerContext();
		} else {
			ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        }
        this.logger = ctx.getLogger("com.intuit.karate");
        setName("karate-ui");
        setContext(ctx);
        encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level - %msg%n");
        encoder.setContext(context);
        encoder.start();
        start();
        logger.addAppender(this);
        logger.setLevel(Level.DEBUG);
    }

    @Override
    protected void append(ILoggingEvent event) {
        byte[] bytes = encoder.encode(event);
        String line = FileUtils.toString(bytes);
        textArea.appendText(line);
    }

}
