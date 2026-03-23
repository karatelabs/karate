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
package io.karatelabs.core;

import io.karatelabs.gherkin.Step;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-level events (HTTP_ENTER, HTTP_EXIT).
 * <p>
 * Enables commercial extensions to capture HTTP request/response data for reports
 * like OpenAPI coverage, request logging, or mock injection.
 * <p>
 * Usage with pattern matching:
 * <pre>
 * listener = event -> switch (event) {
 *     case HttpRunEvent e when e.type() == HTTP_EXIT -> {
 *         Step step = e.getCurrentStep();
 *         HttpRequest req = e.request();
 *         HttpResponse res = e.response();
 *         // Process HTTP data
 *         yield true;
 *     }
 *     default -> true;
 * };
 * </pre>
 * <p>
 * Returning {@code false} from HTTP_ENTER listener skips the actual HTTP request.
 * This enables commercial mocking/stubbing tools to intercept requests.
 */
public record HttpRunEvent(
        RunEventType type,
        HttpRequest request,
        HttpResponse response,  // null for ENTER
        ScenarioRuntime scenarioRuntime,
        long timeStamp
) implements RunEvent {

    /**
     * Creates an HTTP_ENTER event fired before the HTTP request is made.
     * Return false from listener to skip the request.
     */
    public static HttpRunEvent enter(HttpRequest request, ScenarioRuntime sr) {
        return new HttpRunEvent(RunEventType.HTTP_ENTER, request, null, sr, System.currentTimeMillis());
    }

    /**
     * Creates an HTTP_EXIT event fired after the HTTP response is received.
     * Always fires, even for skipped requests (response.isSkipped() == true).
     */
    public static HttpRunEvent exit(HttpRequest request, HttpResponse response, ScenarioRuntime sr) {
        return new HttpRunEvent(RunEventType.HTTP_EXIT, request, response, sr, System.currentTimeMillis());
    }

    @Override
    public RunEventType getType() {
        return type;
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (request != null) {
            map.put("method", request.getMethod());
            map.put("url", request.getUrlAndPath());
        }
        if (type == RunEventType.HTTP_EXIT && response != null) {
            map.put("status", response.getStatus());
            map.put("responseTime", response.getResponseTime());
        }
        return map;
    }

    /**
     * Convenience method to get the currently executing step.
     * Use this to correlate HTTP requests to the step that triggered them.
     */
    public Step getCurrentStep() {
        return scenarioRuntime != null ? scenarioRuntime.getCurrentStep() : null;
    }

}
