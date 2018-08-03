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

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.http.HttpRequest;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;



/**
 *
 * @author dkumar
 */
public class RequestLoggingInterceptor implements HttpRequestInterceptor {

    private final ScriptContext context;
    private final AtomicInteger counter = new AtomicInteger();

    private long startTime;

    public RequestLoggingInterceptor(ScriptContext context) {
        this.context = context;
    }

    public AtomicInteger getCounter() {
        return counter;
    }

    public long getStartTime() {
        return startTime;
    }

    @Override
    public void process(org.apache.hc.core5.http.HttpRequest request, EntityDetails entityDetails, HttpContext httpContext) throws HttpException, IOException {
        HttpRequest actual = new HttpRequest();
        int id = counter.incrementAndGet();
        String uri = (String) httpContext.getAttribute(ApacheHttpAsyncClient.URI_CONTEXT_KEY);
        String method = request.getMethod();
        actual.setUri(uri);
        actual.setMethod(method);
        StringBuilder sb = new StringBuilder();
        sb.append("request:\n").append(id).append(" > ").append(method).append(' ').append(uri).append('\n');
        LoggingUtils.logHeaders(sb, id, '>', request, actual);
        if (request instanceof BasicClassicHttpRequest) {
        BasicClassicHttpRequest entityRequest = (BasicClassicHttpRequest) request;
            HttpEntity entity = entityRequest.getEntity();
            if (LoggingUtils.isPrintable(entityDetails)) {
                LoggingEntityWrapper wrapper = new LoggingEntityWrapper(entity); // todo optimize, preserve if stream
                String buffer = FileUtils.toString(wrapper.getContent());
                if (context.getConfig().isLogPrettyRequest()) {
                    buffer = FileUtils.toPrettyString(buffer);
                }
                sb.append(buffer).append('\n');
                actual.setBody(wrapper.getBytes());
                entityRequest.setEntity(wrapper);
            }
        }
        context.setPrevRequest(actual);
        context.logger.debug(sb.toString());
        startTime = System.currentTimeMillis();
        actual.setStartTime(startTime);
    }

}
