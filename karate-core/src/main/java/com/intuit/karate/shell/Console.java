/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class Console extends Thread {

    private final boolean useLineFeed;
    private final InputStream is;
    private final BufferedReader reader;
    private final Logger logger;
    private final LogAppender appender;
    private final StringBuilder buffer;
    private final Consumer<String> listener;
    
    public String getBuffer() {
        return buffer.toString();
    }

    public Console(String name, boolean useLineFeed, InputStream is, Logger logger, LogAppender appender, Consumer<String> listener) {
        super(name);
        this.useLineFeed = useLineFeed;
        this.is = is;
        this.buffer = new StringBuilder();
        reader = new BufferedReader(new InputStreamReader(is));
        this.logger = logger;
        this.appender = appender;
        this.listener = listener;
    }

    @Override
    public void run() {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                appender.append(line);
                buffer.append(line);
                logger.debug("{}", line);
                if (useLineFeed) {
                    buffer.append('\n');
                }
                if (listener != null) {
                    listener.accept(line);
                }
            }
        } catch (Exception e) {
            logger.error("console reader error: {}", e.getMessage());
        }
    }

}
