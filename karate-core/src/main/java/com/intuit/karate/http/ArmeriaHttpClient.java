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
package com.intuit.karate.http;

import com.intuit.karate.Constants;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Config;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.DecoratingHttpClientFunction;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import com.linecorp.armeria.server.ServiceRequestContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 *
 * @author pthomas3
 */
public class ArmeriaHttpClient implements HttpClient, DecoratingHttpClientFunction {

    private final Logger logger;
    private final HttpLogger httpLogger;

    private Config config;
    private HttpRequest request;
    private RequestContext requestContext;     

    public void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public ArmeriaHttpClient(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
        httpLogger = new HttpLogger(logger);
    }

    @Override
    public Response invoke(HttpRequest request) {
        this.request = request;
        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
        StringUtils.Pair urlAndPath = HttpUtils.parseUriIntoUrlBaseAndPath(request.getUrl());
        WebClient webClient = WebClient.builder(urlAndPath.left).decorator(this).build();
        RequestHeadersBuilder rhb = RequestHeaders.builder(httpMethod, urlAndPath.right);
        Map<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> rhb.add(k, v));
        }
        final byte[] body = request.getBody() == null ? Constants.ZERO_BYTES : request.getBody();
        AggregatedHttpResponse ahr;
        Callable<AggregatedHttpResponse> callable = () -> webClient.execute(rhb.build(), body).aggregate().join();
        ServiceRequestContext src = requestContext == null ? null : requestContext.root();
        try {
            if (src == null) {
                ahr = callable.call();
            } else {
                Future<AggregatedHttpResponse> future = src.blockingTaskExecutor().submit(callable);
                ahr = future.get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ResponseHeaders rh = ahr.headers();
        Map<String, List<String>> responseHeaders = new LinkedHashMap(rh.size());
        for (CharSequence name : rh.names()) {
            if (!HttpHeaderNames.STATUS.equals(name)) {
                responseHeaders.put(name.toString(), rh.getAll(name));
            }
        }
        byte[] responseBody = ahr.content().isEmpty() ? Constants.ZERO_BYTES : ahr.content().array();
        Response response = new Response(ahr.status().code(), responseHeaders, responseBody);
        httpLogger.logResponse(config, request, response);
        return response;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public HttpResponse execute(com.linecorp.armeria.client.HttpClient delegate, ClientRequestContext ctx,
            com.linecorp.armeria.common.HttpRequest req) throws Exception {
        ctx.log().whenAvailable(RequestLogProperty.REQUEST_HEADERS).thenAccept(log -> {
            request.setStartTimeMillis(log.requestStartTimeMillis());
            RequestHeaders rh = log.requestHeaders();
            for (CharSequence name : rh.names()) {
                if (name.charAt(0) != ':') {
                    request.putHeader(name.toString(), rh.getAll(name));
                }
            }
            httpLogger.logRequest(config, request);
        });
        ctx.log().whenAvailable(RequestLogProperty.RESPONSE_START_TIME).thenAccept(log -> request.setEndTimeMillis(log.responseStartTimeMillis()));
        return delegate.execute(ctx, req);
    }

}
