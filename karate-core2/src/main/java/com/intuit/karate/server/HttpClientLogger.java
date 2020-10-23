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
package com.intuit.karate.server;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.DecoratingHttpClientFunction;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import io.netty.util.AsciiString;

/**
 *
 * @author pthomas3
 */
public class HttpClientLogger implements DecoratingHttpClientFunction {

    private final ArmeriaHttpClient client;
    private com.intuit.karate.server.HttpRequest request;

    public HttpClientLogger(ArmeriaHttpClient client) {
        this.client = client;
    }

    public void setRequest(com.intuit.karate.server.HttpRequest request) {
        this.request = request;
    }

    @Override
    public HttpResponse execute(HttpClient delegate, ClientRequestContext ctx, HttpRequest req) throws Exception {
        ctx.log().whenAvailable(RequestLogProperty.REQUEST_HEADERS).thenAccept(log -> {
            request.setStartTimeMillis(log.requestStartTimeMillis());
            RequestHeaders rh = log.requestHeaders();
            for (AsciiString name : rh.names()) {
                if (name.charAt(0) != ':') {
                    request.putHeader(name.toString(), rh.getAll(name));
                }
            }
            client.logRequest(request);
        });
        ctx.log().whenAvailable(RequestLogProperty.RESPONSE_START_TIME).thenAccept(log -> request.setEndTimeMillis(log.responseStartTimeMillis()));
        return delegate.execute(ctx, req);
    }

}
