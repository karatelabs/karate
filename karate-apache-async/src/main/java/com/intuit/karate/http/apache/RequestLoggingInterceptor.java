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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.impl.nio.MessageState;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;

import com.intuit.karate.ScriptContext;
import com.intuit.karate.http.HttpRequest;



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
        try {
            actual.setUri(request.getUri().toString());
        } catch (URISyntaxException e) {
            actual.setUri(extractUri(request));
        }
        actual.setMethod(request.getMethod());
        
        StringBuilder sb = new StringBuilder();
        sb.append("request:\n").append(id).append(" > ").append(request.toString()).append('\n');
        LoggingUtils.logHeaders(sb, id, '>', request, actual);
        if (entityDetails instanceof AsyncEntityProducer) {
            AsyncEntityProducer entityProducer = (AsyncEntityProducer)entityDetails;
            
            if (LoggingUtils.isPrintable(entityDetails)) {
                entityProducer.produce(new DataStreamChannel() {
    
                    @Override
                    public int write(final ByteBuffer src) throws IOException {
                        actual.setBody(src.array());
                        return src.limit();
                    }
    
                    @Override
                    public void requestOutput() {
                    }
    
                    @Override
                    public void endStream(final List<? extends Header> trailers) throws IOException {
                    }
    
                    @Override
                    public void endStream() throws IOException {
                        endStream(null);
                    }
    
                });
            }
        }

        context.setPrevRequest(actual);
        context.logger.debug(sb.toString());
        startTime = System.currentTimeMillis();
        actual.setStartTime(startTime);
    }

    private String extractUri(org.apache.hc.core5.http.HttpRequest request) {
        final StringBuilder buf = new StringBuilder();
        URIAuthority reqAuth = request.getAuthority();
        if (reqAuth != null) {
            buf.append(request.getScheme() != null ? request.getScheme() : "http").append("://");
            buf.append(reqAuth.getHostName());
            if (reqAuth.getPort() >= 0) {
                buf.append(":").append(reqAuth.getPort());
            }
        }
        String path = request.getPath();
        if (path == null) {
            buf.append("/");
        } else {
            if (buf.length() > 0 && !path.startsWith("/")) {
                buf.append("/");
            }
            buf.append(path);
        }
        return buf.toString();
    }
}
