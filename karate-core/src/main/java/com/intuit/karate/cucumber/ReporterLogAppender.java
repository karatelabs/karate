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
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ReporterLogAppender extends AppenderBase<ILoggingEvent> {

    private final Logger logger;
    private final PatternLayoutEncoder encoder;
    private final String threadName;
    private final FileChannel file;
    private int prevPos;

    public ReporterLogAppender(String tempFilePath) {
        try {
            if (tempFilePath == null) {
                File temp = File.createTempFile("karate", "tmp");
                tempFilePath = temp.getPath();
            }
            RandomAccessFile raf = new RandomAccessFile(tempFilePath, "rw");
            file = raf.getChannel();
            prevPos = (int) file.position();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.threadName = Thread.currentThread().getName();
        LoggerContext ctx = null;
		if (!(LoggerFactory.getILoggerFactory()
				.getLogger("com.intuit.karate") instanceof ch.qos.logback.classic.Logger)) {
			ctx = new LoggerContext();
		} else {
			ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
		}
		this.logger = ctx.getLogger("com.intuit.karate");
		setName("karate-reporter");
		setContext(ctx);
        encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level - %msg%n");
        encoder.setContext(context);
        encoder.start();
        start();
        logger.addAppender(this);
    }

    public String collect() {
        try {
            int pos = (int) file.position();
            ByteBuffer buf = ByteBuffer.allocate(pos - prevPos);
            file.read(buf, prevPos);
            prevPos = pos;
            buf.flip();
            return FileUtils.toString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!threadName.equals(event.getThreadName())) {
            return;
        }
        try {
            byte[] bytes = encoder.encode(event);
            file.write(ByteBuffer.wrap(bytes));
        } catch (Exception e) {
            System.err.println("possible logback version conflict: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
