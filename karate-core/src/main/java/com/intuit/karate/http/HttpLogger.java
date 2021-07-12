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

import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.core.Config;
import com.intuit.karate.core.Variable;
import com.intuit.karate.graal.JsValue;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HttpLogger {

    private int requestCount;
    private final Logger logger;

    public HttpLogger(Logger logger) {
        this.logger = logger;
    }

    private static void logHeaders(int num, String prefix, StringBuilder sb,
            HttpLogModifier modifier, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        sb.append('\n');
        headers.forEach((k, v) -> {
            for (String value : v) {
                sb.append(num).append(prefix).append(k).append(": ");
                if (modifier == null) {
                    sb.append(value);
                } else {
                    sb.append(modifier.header(k, value));
                }
                sb.append('\n');
            }
        });
    }

    private static void logBody(Config config, HttpLogModifier logModifier,
            StringBuilder sb, String uri, byte[] body, boolean request) {
        if (body == null) {
            return;
        }
        String text;
        if (config != null && needsPrettyLogging(config, request)) {
            Object converted = JsValue.fromBytes(body, false, null);
            Variable v = new Variable(converted);
            text = v.getAsPrettyString();
        } else {
            text = FileUtils.toString(body);
        }
        if (logModifier != null) {
            text = request ? logModifier.request(uri, text) : logModifier.response(uri, text);
        }
        sb.append(text);
    }

    private static boolean needsPrettyLogging(Config config, boolean request) {
        return logPrettyRequest(config, request) || logPrettyResponse(config, request);
    }

    private static boolean logPrettyResponse(Config config, boolean request) {
        return !request && config.isLogPrettyResponse();
    }

    private static boolean logPrettyRequest(Config config, boolean request) {
        return request && config.isLogPrettyRequest();
    }

    private static HttpLogModifier logModifier(Config config, String uri) {
        HttpLogModifier logModifier = config.getLogModifier();
        return logModifier == null ? null : logModifier.enableForUri(uri) ? logModifier : null;
    }

    public static String getStatusFailureMessage(int expected, Config config, HttpRequest request, Response response) {
        String url = request.getUrl();
        HttpLogModifier logModifier = logModifier(config, url);
        String maskedUrl = logModifier == null ? url : logModifier.uri(url);
        String rawResponse = response.getBodyAsString();
        if (rawResponse != null && logModifier != null) {
            rawResponse = logModifier.response(url, rawResponse);
        }
        long responseTime = request.getEndTimeMillis() - request.getStartTimeMillis();
        return "status code was: " + response.getStatus() + ", expected: " + expected
                + ", response time in milliseconds: " + responseTime + ", url: " + maskedUrl
                + ", response: \n" + rawResponse;
    }

    public void logRequest(Config config, HttpRequest request) {
        requestCount++;
        String uri = request.getUrl();
        HttpLogModifier requestModifier = logModifier(config, uri);
        String maskedUri = requestModifier == null ? uri : requestModifier.uri(uri);
        StringBuilder sb = new StringBuilder();
        sb.append("request:\n").append(requestCount).append(" > ")
                .append(request.getMethod()).append(' ').append(maskedUri);
        logHeaders(requestCount, " > ", sb, requestModifier, request.getHeaders());
        ResourceType rt = ResourceType.fromContentType(request.getContentType());
        if (rt == null || rt.isBinary()) {
            // don't log body
        } else {
            byte[] body = rt == ResourceType.MULTIPART ? request.getBodyForDisplay().getBytes() : request.getBody();
            logBody(config, requestModifier, sb, uri, body, true);
        }
        sb.append('\n');
        logger.debug("{}", sb);
    }

    public void logResponse(Config config, HttpRequest request, Response response) {
        long startTime = request.getStartTimeMillis();
        long elapsedTime = request.getEndTimeMillis() - startTime;
        StringBuilder sb = new StringBuilder();
        String uri = request.getUrl();
        HttpLogModifier responseModifier = logModifier(config, uri);
        sb.append("response time in milliseconds: ").append(elapsedTime).append('\n');
        sb.append(requestCount).append(" < ").append(response.getStatus());
        logHeaders(requestCount, " < ", sb, responseModifier, response.getHeaders());
        ResourceType rt = response.getResourceType();
        if (rt == null || rt.isBinary()) {
            // don't log body
        } else {
            logBody(config, responseModifier, sb, uri, response.getBody(), false);
        }
        logger.debug("{}", sb);
    }

}
