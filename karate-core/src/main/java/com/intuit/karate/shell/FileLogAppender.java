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
package com.intuit.karate.shell;

import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 *
 * @author pthomas3
 */
public class FileLogAppender implements LogAppender {

    private final FileChannel file;
    private final Logger logger;
    private int prevPos;
    
    public FileLogAppender(String in, Logger logger) {
        this(in == null ? null : new File(in), logger);
    }

    public FileLogAppender(File in, Logger logger) {
        this.logger = logger;
        try {
            if (in == null) {
                in = File.createTempFile("karate", "tmp");
            } else {
                in.getParentFile().mkdirs();
            }
            RandomAccessFile raf = new RandomAccessFile(in, "rw");
            file = raf.getChannel();
            prevPos = (int) file.position();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.setLogAppender(this);
    }

    @Override
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
    public void append(String text) {
        try {
            file.write(ByteBuffer.wrap(text.getBytes(FileUtils.UTF8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            file.close();
        } catch (Exception e) {
            logger.warn("log appender close failed: {}", e.getMessage());
        }
    }

}
