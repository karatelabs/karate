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
package com.intuit.karate.http.apache;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ResponseLoggingInterceptor implements HttpResponseInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ResponseLoggingInterceptor.class);

    private final AtomicInteger counter;

    public ResponseLoggingInterceptor(AtomicInteger counter) {
        this.counter = counter;
    }    

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        int id = counter.get();
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(id).append(" < ").append(response.getStatusLine().getStatusCode()).append('\n');
        LoggingUtils.logHeaders(sb, id, '<', response);
        HttpEntity entity = response.getEntity();
        if (LoggingUtils.isPrintable(entity)) {
            LoggingEntityWrapper wrapper = new LoggingEntityWrapper(entity);
            String buffer = IOUtils.toString(wrapper.getContent(), "utf-8");
            sb.append(buffer).append('\n');
            response.setEntity(wrapper);
        }
        logger.debug(sb.toString());
    }

}
