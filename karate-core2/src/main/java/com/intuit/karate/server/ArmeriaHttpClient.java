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

import com.intuit.karate.StringUtils;
import com.intuit.karate.netty.NettyUtils;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 *
 * @author pthomas3
 */
public class ArmeriaHttpClient implements HttpClient {

    private final RequestContext requestContext;

    public ArmeriaHttpClient(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public Response invoke(HttpRequest request) {
        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
        StringUtils.Pair urlAndPath = NettyUtils.parseUriIntoUrlBaseAndPath(request.getUrl());
        WebClient webClient = WebClient.builder(urlAndPath.left).decorator(new HttpClientLogger()).build();
        RequestHeadersBuilder rhb = RequestHeaders.builder(httpMethod, urlAndPath.right);
        Map<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> rhb.add(k, v));
        }
        final byte[] body = request.getBody() == null ? HttpConstants.ZERO_BYTES : request.getBody();
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
        Map<String, List<String>> responseHeaders = rh.isEmpty() ? null : new LinkedHashMap(rh.size());
        for (AsciiString name : rh.names()) {
            responseHeaders.put(name.toString(), rh.getAll(name));
        }
        String contentType = ahr.contentType() == null ? null : ahr.contentType().nameWithoutParameters();
        ResourceType responseType = ResourceType.fromContentType(contentType);
        byte[] responseBody = ahr.content().isEmpty() ? null : ahr.content().array();
        return new Response(ahr.status().code(), responseHeaders, responseBody, responseType);
    }

}
