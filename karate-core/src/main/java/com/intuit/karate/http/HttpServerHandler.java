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
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.ResponseHeadersBuilder;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class HttpServerHandler implements HttpService {

    private final ServerHandler handler;

    public HttpServerHandler(ServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
        return HttpResponse.from(req.aggregate().thenApply(ahr -> {
            Request request = toRequest(ctx, ahr);
            Response response = handler.handle(request);
            return toResponse(ctx, response);
        }));
    }

    private Request toRequest(ServiceRequestContext ctx, AggregatedHttpRequest req) {
        Request request = new Request();
        request.setRequestContext(ctx);
        request.setUrl(req.path());
        request.setUrlBase(req.scheme() + "://" + req.authority());
        request.setMethod(req.method().name());
        RequestHeaders rh = req.headers();
        if (rh != null) {
            Set<AsciiString> names = rh.names();
            Map<String, List<String>> headers = new HashMap(names.size());
            request.setHeaders(headers);
            for (AsciiString name : names) {
                if (name.charAt(0) == ':') {
                    continue;
                }
                headers.put(name.toString(), rh.getAll(name));
            }
        }
        if (!req.content().isEmpty()) {
            byte[] bytes = req.content().array();
            request.setBody(bytes);
        }
        return request;
    }

    private HttpResponse toResponse(ServiceRequestContext ctx, Response response) {
        byte[] body = response.getBody();
        if (body == null) {
            body = Constants.ZERO_BYTES;
        }
        ResponseHeadersBuilder rhb = ResponseHeaders.builder(response.getStatus());
        Map<String, List<String>> headers = response.getHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> rhb.add(k, v));
        }
        HttpResponse hr = HttpResponse.of(rhb.build(), HttpData.wrap(body));
        if (response.getDelay() > 0) {
            return HttpResponse.delayed(hr, Duration.ofMillis(response.getDelay()), ctx.eventLoop());
        } else {
            return hr;
        }
    }

}
