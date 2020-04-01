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
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.http.HttpLogModifier;
import com.intuit.karate.http.HttpRequest;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 *
 * @author pthomas3
 */
public class RequestLoggingInterceptor implements HttpRequestInterceptor {

    private final ScenarioContext context;
    private final HttpLogModifier logModifier;
    private final AtomicInteger counter = new AtomicInteger();

    public RequestLoggingInterceptor(ScenarioContext context) {
        this.context = context;
        logModifier = context.getConfig().getLogModifier();
    }

    public AtomicInteger getCounter() {
        return counter;
    }

    @Override
    public void process(org.apache.http.HttpRequest request, HttpContext httpContext) throws HttpException, IOException {
        HttpRequest actual = new HttpRequest();
        int id = counter.incrementAndGet();
        String uri = (String) httpContext.getAttribute(ApacheHttpClient.URI_CONTEXT_KEY);
        String method = request.getRequestLine().getMethod();
        actual.setUri(uri);
        actual.setMethod(method);
        context.setPrevRequest(actual);
        HttpLogModifier requestModifier = logModifier == null ? null : logModifier.enableForUri(uri) ? logModifier : null;
        String maskedUri = requestModifier == null ? uri : requestModifier.uri(uri);
        boolean showLog = !context.isReportDisabled() && context.getConfig().isShowLog();
        if (showLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("request:\n").append(id).append(" > ").append(method).append(' ').append(maskedUri).append('\n');
            LoggingUtils.logHeaders(requestModifier, sb, id, '>', request, actual);
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) request;
                HttpEntity entity = entityRequest.getEntity();
                if (LoggingUtils.isPrintable(entity)) {
                    LoggingEntityWrapper wrapper = new LoggingEntityWrapper(entity); // todo optimize, preserve if stream
                    String buffer = FileUtils.toString(wrapper.getContent());
                    if (context.getConfig().isLogPrettyRequest()) {
                        buffer = FileUtils.toPrettyString(buffer);
                    }
                    if (requestModifier != null) {
                        buffer = requestModifier.request(uri, buffer);
                    }
                    sb.append(buffer).append('\n');
                    actual.setBody(wrapper.getBytes());
                    entityRequest.setEntity(wrapper);
                }
            }
            context.logger.debug(sb.toString());
        }
        // make sure this does not include the toString / logging time
        actual.startTimer();
    }

}
