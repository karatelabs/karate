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
package com.intuit.karate.http.jersey;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.LoggingFilterOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

/**
 *
 * @author pthomas3
 */
public class LoggingInterceptor implements ClientRequestFilter, ClientResponseFilter {

    private final ScenarioContext context;

    public LoggingInterceptor(ScenarioContext context) {
        this.context = context;
    }

    private final AtomicInteger counter = new AtomicInteger();

    private static boolean isPrintable(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return HttpUtils.isPrintable(mediaType.toString());
    }

    private static void logHeaders(StringBuilder sb, int id, char prefix, MultivaluedMap<String, String> headers, HttpRequest actual) {
        Set<String> keys = new TreeSet(headers.keySet());
        for (String key : keys) {
            List<String> entries = headers.get(key);
            sb.append(id).append(' ').append(prefix).append(' ')
                    .append(key).append(": ").append(entries.size() == 1 ? entries.get(0) : entries).append('\n');
            if (actual != null) {
                actual.putHeader(key, entries);
            }
        }
    }

    @Override
    public void filter(ClientRequestContext request) throws IOException {
        if (request.hasEntity() && isPrintable(request.getMediaType())) {
            LoggingFilterOutputStream out = new LoggingFilterOutputStream(request.getEntityStream());
            request.setEntityStream(out);
            request.setProperty(LoggingFilterOutputStream.KEY, out);
        }
        HttpRequest actual = new HttpRequest();
        context.setPrevRequest(actual);
        actual.startTimer();
    }

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) throws IOException {        
        HttpRequest actual = context.getPrevRequest();
        actual.stopTimer();
        int id = counter.incrementAndGet();
        String method = request.getMethod();
        String uri = request.getUri().toASCIIString();
        actual.setMethod(method);
        actual.setUri(uri);
        StringBuilder sb = new StringBuilder();
        sb.append("request\n").append(id).append(" > ").append(method).append(' ').append(uri).append('\n');
        logHeaders(sb, id, '>', request.getStringHeaders(), actual);
        LoggingFilterOutputStream out = (LoggingFilterOutputStream) request.getProperty(LoggingFilterOutputStream.KEY);
        if (out != null) {
            byte[] bytes = out.getBytes().toByteArray();
            actual.setBody(bytes);            
            String buffer = FileUtils.toString(bytes);
            if (context.getConfig().isLogPrettyRequest()) {
                buffer = FileUtils.toPrettyString(buffer);
            }
            sb.append(buffer).append('\n');
        }        
        context.logger.debug(sb.toString()); // log request
        // response
        sb = new StringBuilder();
        sb.append("response time in milliseconds: ").append(actual.getResponseTimeFormatted()).append('\n');
        sb.append(id).append(" < ").append(response.getStatus()).append('\n');
        logHeaders(sb, id, '<', response.getHeaders(), null);
        if (response.hasEntity() && isPrintable(response.getMediaType())) {
            InputStream is = response.getEntityStream();
            if (!is.markSupported()) {
                is = new BufferedInputStream(is);
            }
            is.mark(Integer.MAX_VALUE);
            String buffer = FileUtils.toString(is);
            if (context.getConfig().isLogPrettyResponse()) {
                buffer = FileUtils.toPrettyString(buffer);
            }            
            sb.append(buffer).append('\n');
            is.reset();
            response.setEntityStream(is); // in case it was swapped
        }
        context.logger.debug(sb.toString());
    }

}
