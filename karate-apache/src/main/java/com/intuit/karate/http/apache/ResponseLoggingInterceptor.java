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
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 *
 * @author pthomas3
 */
public class ResponseLoggingInterceptor implements HttpResponseInterceptor {

    private final ScenarioContext context;
    private final HttpLogModifier logModifier;
    private final RequestLoggingInterceptor requestInterceptor;

    public ResponseLoggingInterceptor(RequestLoggingInterceptor requestInterceptor, ScenarioContext context) {
        this.requestInterceptor = requestInterceptor;
        this.context = context;
        logModifier = context.getConfig().getLogModifier();
    }

    @Override
    public void process(HttpResponse response, HttpContext httpContext) throws HttpException, IOException {
        HttpRequest actual = context.getPrevRequest();
        actual.stopTimer();                
        boolean showLog = !context.isReportDisabled() && context.getConfig().isShowLog();
        if (!showLog) {
            return;
        }
        int id = requestInterceptor.getCounter().get();
        StringBuilder sb = new StringBuilder();        
        sb.append("response time in milliseconds: ").append(actual.getResponseTimeFormatted()).append('\n');
        sb.append(id).append(" < ").append(response.getStatusLine().getStatusCode()).append('\n');
        HttpLogModifier responseModifier = logModifier == null ? null : logModifier.enableForUri(actual.getUri()) ? logModifier : null;
        LoggingUtils.logHeaders(responseModifier, sb, id, '<', response);
        HttpEntity entity = response.getEntity();
        if (LoggingUtils.isPrintable(entity)) {
            LoggingEntityWrapper wrapper = new LoggingEntityWrapper(entity);
            String buffer = FileUtils.toString(wrapper.getContent());
            if (context.getConfig().isLogPrettyResponse()) {
                buffer = FileUtils.toPrettyString(buffer);
            }
            if (responseModifier != null) {
                buffer = responseModifier.response(actual.getUri(), buffer);
            }
            sb.append(buffer).append('\n');
            response.setEntity(wrapper);
        }
        context.logger.debug(sb.toString());
    }

}
