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
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;

/**
 *
 * @author pthomas3
 */
public class RequestLoggingInterceptor implements HttpRequestInterceptor {

    private final Logger logger;

    private final AtomicInteger counter;
    
    public RequestLoggingInterceptor(AtomicInteger counter, Logger logger) {
        this.counter = counter;
        this.logger = logger;
    }      

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        int id = counter.incrementAndGet();
        String uri = (String) context.getAttribute(ApacheHttpClient.URI_CONTEXT_KEY);
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(id).append(" > ").append(request.getRequestLine().getMethod()).append(' ').append(uri).append('\n');
        LoggingUtils.logHeaders(sb, id, '>', request);
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) request;
            HttpEntity entity = entityRequest.getEntity();
            if (LoggingUtils.isPrintable(entity)) {
                LoggingEntityWrapper wrapper = new LoggingEntityWrapper(entity);
                String buffer = IOUtils.toString(wrapper.getContent(), "utf-8");
                sb.append(buffer).append('\n');
                entityRequest.setEntity(wrapper);
            }
        }
        logger.debug(sb.toString());
    }

}
