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

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.ResponseHeadersBuilder;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class HttpHandler implements HttpService {

    private static final byte[] ZERO_BYTES = new byte[0];

    private final RequestHandler handler;
    private final Config config;

    public HttpHandler(Config config) {
        this.config = config;
        handler = new RequestHandler(config);
    }

    @Override
    public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
        return HttpResponse.from(req.aggregate().thenApply(ahr -> {
            Request request = toRequest(ctx, ahr);
            Response response = handler.handle(request);
            return toResponse(response);
        }));
    }

    private Request toRequest(ServiceRequestContext ctx, AggregatedHttpRequest req) {
        String uri = req.path();
        if (config.isStripContextPathFromRequest() && config.getHostContextPath() != null) {
            if (uri.startsWith(config.getHostContextPath())) {
                uri = uri.substring(config.getHostContextPath().length());
            }
        }
        Request request = new Request();
        request.setRequestContext(ctx);
        request.setMethod(req.method().name());
        QueryStringDecoder qsd = new QueryStringDecoder(uri);
        request.setPath(qsd.path());
        request.setParams(qsd.parameters());
        RequestHeaders rh = req.headers();
        if (rh != null) {
            Set<AsciiString> names = rh.names();
            Map<String, List<String>> headers = new HashMap(names.size());
            request.setHeaders(headers);
            for (AsciiString name : names) {
                headers.put(name.toString(), rh.getAll(name));
            }
        }
        if (!req.content().isEmpty()) {
            byte[] bytes = req.content().array();
            request.setBody(bytes);
        }
        return request;
    }

    private HttpResponse toResponse(Response response) {
        byte[] body = response.getBody();
        if (body == null) {
            body = ZERO_BYTES;
        }
        ResponseHeadersBuilder rhb = ResponseHeaders.builder(response.getStatus());
        Map<String, List<String>> headers = response.getHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> rhb.add(k, v));
        }
        return HttpResponse.of(rhb.build(), HttpData.wrap(body));
    }

}
