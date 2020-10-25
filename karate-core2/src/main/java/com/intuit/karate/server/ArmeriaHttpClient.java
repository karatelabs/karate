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

import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.HttpLogModifier;
import com.intuit.karate.netty.NettyUtils;
import com.intuit.karate.runtime.Config;
import com.intuit.karate.runtime.ScenarioEngine;
import com.intuit.karate.runtime.Variable;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import java.util.Iterator;
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

    private final Logger logger;
    private final ScenarioEngine engine;
    private final RequestContext requestContext;
    private final HttpClientLogger httpClientLogger = new HttpClientLogger(this);

    private int requestCount;
    private Config config;
    private HttpLogModifier logModifier;

    public ArmeriaHttpClient(ScenarioEngine engine, RequestContext requestContext) {
        logger = engine == null ? new Logger(getClass()) : engine.logger;
        if (engine != null) {
            setConfig(engine.getConfig());
        }
        this.engine = engine;
        this.requestContext = requestContext;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
        logModifier = config.getLogModifier();
    }

    private static void logHeaders(int num, String prefix, StringBuilder sb,
            HttpLogModifier modifier, Map<String, List<String>> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((k, v) -> {
            sb.append(num).append(prefix).append(k).append(": ");
            int count = v.size();
            if (count == 1) {
                if (modifier == null) {
                    sb.append(v.get(0));
                } else {
                    sb.append(modifier.header(k, v.get(0)));
                }
            } else {
                if (modifier == null) {
                    sb.append(v);
                } else {
                    sb.append('[');
                    Iterator<String> i = v.iterator();
                    while (i.hasNext()) {
                        sb.append(modifier.header(k, i.next()));
                        if (i.hasNext()) {
                            sb.append(", ");
                        }
                    }
                    sb.append(']');
                }
            }
            sb.append('\n');
        });
    }

    public void logBody(StringBuilder sb, Object body) {
        if (body == null || body instanceof byte[]) {
            return;
        }
        Variable v = new Variable(body);
        if (config != null && config.isLogPrettyRequest()) {
            sb.append(v.getAsPrettyString());
        } else {
            sb.append(v.getAsString());
        }
    }

    public void logRequest(HttpRequest request) {
        String uri = request.getUrl();
        HttpLogModifier requestModifier = logModifier == null ? null : logModifier.enableForUri(uri) ? logModifier : null;
        String maskedUri = requestModifier == null ? uri : requestModifier.uri(uri);
        StringBuilder sb = new StringBuilder();
        sb.append("request:\n").append(requestCount).append(" > ")
                .append(request.getMethod()).append(' ').append(maskedUri).append('\n');
        logHeaders(requestCount, " > ", sb, requestModifier, request.getHeaders());
        logBody(sb, request.getBody());
        logger.debug("{}", sb);
    }

    public void logResponse(Response response) {
        HttpRequest request = response.getHttpRequest();
        long startTime = request.getStartTimeMillis();
        long elapsedTime = request.getEndTimeMillis() - startTime;
        StringBuilder sb = new StringBuilder();
        String uri = request.getUrl(); // TODO optimize
        HttpLogModifier responseModifier = logModifier == null ? null : logModifier.enableForUri(uri) ? logModifier : null;
        sb.append("response time in milliseconds: ").append(elapsedTime).append('\n');
        sb.append(requestCount).append(" < ").append(response.getStatus()).append('\n');
        logHeaders(requestCount, " < ", sb, responseModifier, response.getHeaders());
        logBody(sb, response.getBodyConverted());
        logger.debug("{}", sb);
    }

    @Override
    public Response invoke(HttpRequest request) {
        requestCount++;
        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
        StringUtils.Pair urlAndPath = NettyUtils.parseUriIntoUrlBaseAndPath(request.getUrl());
        httpClientLogger.setRequest(request);
        WebClient webClient = WebClient.builder(urlAndPath.left).decorator(httpClientLogger).build();
        RequestHeadersBuilder rhb = RequestHeaders.builder(httpMethod, urlAndPath.right);
        Map<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> rhb.add(k, v));
        }
        final byte[] body = request.getBody() == null ? HttpConstants.ZERO_BYTES : request.getBody();
        AggregatedHttpResponse ahr;
        Callable<AggregatedHttpResponse> callable = () -> webClient.execute(rhb.build(), body).aggregate().join();
        ServiceRequestContext src = requestContext == null ? null : requestContext.root();
        String perfEventName = null; // acts as a flag to report perf if not null
        if (engine.runtime.featureRuntime.isPerfMode()) {
            perfEventName = engine.runtime.featureRuntime.getPerfRuntime().getPerfEventName(request, engine);
        }
        long startTime = System.currentTimeMillis();
        request.setStartTimeMillis(startTime); // this will be re-adjusted by HttpClientLogger
        try {
            if (src == null) {
                ahr = callable.call();
            } else {
                Future<AggregatedHttpResponse> future = src.blockingTaskExecutor().submit(callable);
                ahr = future.get();
            }
        } catch (Exception e) {
            // edge case when request building failed maybe because of malformed url
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            String message = "http call failed after " + responseTime + " milliseconds for URL: " + request.getUrl();
            if (perfEventName != null) {
                PerfEvent pe = new PerfEvent(startTime, endTime, perfEventName, 0);
                engine.capturePerfEvent(pe);
                // failure flag and message should be set by ScenarioEngine.logLastPerfEvent()
            }
            logger.error(e.getMessage() + ", " + message);
            throw new KarateException(message, e);
        }
        ResponseHeaders rh = ahr.headers();
        Map<String, List<String>> responseHeaders = new LinkedHashMap(rh.size());
        for (AsciiString name : rh.names()) {
            if (!HttpHeaderNames.STATUS.equals(name)) {
                responseHeaders.put(name.toString(), rh.getAll(name));
            }
        }
        byte[] responseBody = ahr.content().isEmpty() ? null : ahr.content().array();
        Response response = new Response(ahr.status().code(), responseHeaders, responseBody);
        response.setHttpRequest(request);
        logResponse(response);
        return response;
    }

}
