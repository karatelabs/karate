/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.output;

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.ResourceType;
import io.karatelabs.common.StringUtils;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;

import java.util.List;
import java.util.Map;

public class HttpLogger {

    private static final LogContext.LogWriter log = LogContext.with(LogContext.HTTP_LOGGER);

    private int requestCount;

    public static void logHeaders(StringBuilder sb, int num, String prefix, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((k, v) -> {
            for (String value : v) {
                sb.append(Console.DIM).append(num).append(prefix).append(Console.RESET);
                sb.append(Console.CYAN).append(k).append(Console.RESET);
                sb.append(": ");
                sb.append(value);
                sb.append('\n');
            }
        });
    }

    public void logBody(StringBuilder sb, byte[] body, ResourceType rt) {
        if (body == null) {
            return;
        }
        String text = FileUtils.toString(body);
        if (rt == ResourceType.JSON && StringUtils.looksLikeJson(text)) {
            sb.append(AnsiJson.colorize(text));
        } else {
            sb.append(text);
        }
        sb.append('\n');
    }

    public static String getStatusFailureMessage(int expected, HttpRequest request, HttpResponse response) {
        String url = request.getUrlAndPath();
        String rawResponse = response.getBodyString();
        long responseTime = response.getResponseTime();
        return "status code was: " + response.getStatus() + ", expected: " + expected
                + ", response time in milliseconds: " + responseTime + ", url: " + url
                + ", response: \n" + rawResponse;
    }

    public void logRequest(HttpRequest request) {
        requestCount++;
        StringBuilder sb = new StringBuilder();
        String uri = request.getUrlAndPath();
        sb.append(Console.BOLD).append("request:").append(Console.RESET).append('\n');
        sb.append(Console.DIM).append(requestCount).append(" > ").append(Console.RESET);
        sb.append(Console.CYAN).append(Console.BOLD).append(request.getMethod()).append(Console.RESET);
        sb.append(' ').append(uri).append('\n');
        logHeaders(sb, requestCount, " > ", request.getHeaders());
        ResourceType rt = ResourceType.fromContentType(request.getContentType());
        if (rt == null || rt.isBinary()) {
            // don't log body
        } else {
            byte[] body;
            if (rt == ResourceType.MULTIPART) {
                body = request.getBodyDisplay() == null ? null : request.getBodyDisplay().getBytes();
            } else {
                body = request.getBody();
            }
            logBody(sb, body, rt);
        }
        log.info(sb.toString());
    }

    public void logResponse(HttpResponse response) {
        HttpRequest request = response.getRequest();
        StringBuilder sb = new StringBuilder();
        sb.append(Console.DIM).append("response time in milliseconds: ")
                .append(response.getResponseTime()).append(Console.RESET).append('\n');
        sb.append(Console.DIM).append(requestCount).append(" < ").append(Console.RESET);
        sb.append(colorStatus(response.getStatus())).append(' ');
        sb.append(Console.CYAN).append(request.getMethod()).append(Console.RESET);
        sb.append(' ').append(request.getPath()).append('\n');
        logHeaders(sb, requestCount, " < ", response.getHeaders());
        ResourceType rt = response.getResourceType();
        if (rt == null || rt.isBinary()) {
            // don't log body
        } else {
            logBody(sb, response.getBodyBytes(), rt);
        }
        log.info(sb.toString());
    }

    private static String colorStatus(int status) {
        String statusStr = String.valueOf(status);
        if (status >= 200 && status < 300) {
            return Console.GREEN + statusStr + Console.RESET;
        } else if (status >= 300 && status < 400) {
            return Console.YELLOW + statusStr + Console.RESET;
        } else if (status >= 400) {
            return Console.RED + Console.BOLD + statusStr + Console.RESET;
        }
        return statusStr;
    }

}
