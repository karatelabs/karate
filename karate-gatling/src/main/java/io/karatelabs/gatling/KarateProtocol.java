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
package io.karatelabs.gatling;

import io.gatling.core.protocol.Protocol;
import io.karatelabs.http.HttpUtils;
import io.karatelabs.http.HttpRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Gatling protocol for Karate feature execution.
 * Holds configuration for URI patterns, pauses, and request naming.
 */
public class KarateProtocol implements Protocol {

    /**
     * Session key for Karate variables passed between features.
     */
    public static final String KARATE_KEY = "__karate";

    /**
     * Session key for Gatling variables accessible in features.
     */
    public static final String GATLING_KEY = "__gatling";

    private final Map<String, KarateUriPattern> uriPatterns;
    private BiFunction<HttpRequest, Map<String, Object>, String> nameResolver;

    KarateProtocol(Map<String, KarateUriPattern> uriPatterns) {
        this.uriPatterns = Collections.unmodifiableMap(new HashMap<>(uriPatterns));
    }

    /**
     * Get all configured URI patterns.
     */
    public Map<String, KarateUriPattern> getUriPatterns() {
        return uriPatterns;
    }

    /**
     * Get the custom name resolver, if configured.
     */
    public BiFunction<HttpRequest, Map<String, Object>, String> getNameResolver() {
        return nameResolver;
    }

    /**
     * Set a custom name resolver for HTTP requests.
     * The function receives the HttpRequest and scenario variables,
     * and should return a request name for Gatling reporting.
     *
     * @param resolver the name resolver function
     */
    void setNameResolver(BiFunction<HttpRequest, Map<String, Object>, String> resolver) {
        this.nameResolver = resolver;
    }

    /**
     * Resolve the request name using the custom resolver or default pattern matching.
     *
     * @param request the HTTP request
     * @param variables the scenario variables
     * @return the request name for reporting
     */
    public String resolveRequestName(HttpRequest request, Map<String, Object> variables) {
        // Try custom resolver first
        if (nameResolver != null) {
            String customName = nameResolver.apply(request, variables);
            if (customName != null) {
                return customName;
            }
        }
        // Fall back to URI pattern matching
        return defaultNameResolver(request);
    }

    /**
     * Default name resolver using URI pattern matching.
     * Returns the matching pattern or the raw path if no pattern matches.
     */
    String defaultNameResolver(HttpRequest request) {
        String path = request.getPath();
        if (path == null) {
            path = "/";
        }
        return resolveName(path);
    }

    /**
     * Resolve a path to a matching URI pattern name.
     *
     * @param path the request path
     * @return the matching pattern or null if no match
     */
    public String resolveName(String path) {
        if (path == null) {
            return null;
        }
        // Try to match against configured patterns
        for (String pattern : uriPatterns.keySet()) {
            if (pathMatches(pattern, path)) {
                return pattern;
            }
        }
        // No pattern match - return null
        return null;
    }

    /**
     * Check if a path matches a URI pattern.
     * Patterns can contain path parameters like "/users/{id}".
     */
    public boolean pathMatches(String pattern, String path) {
        Map<String, String> params = HttpUtils.parseUriPattern(pattern, path);
        return params != null;
    }

    /**
     * Get the pause duration for a specific request and method.
     *
     * @param requestName the request name (usually the matched pattern)
     * @param method the HTTP method
     * @return pause duration in milliseconds, or 0 if not configured
     */
    public int pauseFor(String requestName, String method) {
        KarateUriPattern pattern = uriPatterns.get(requestName);
        if (pattern != null) {
            return pattern.getPauseFor(method);
        }
        return 0;
    }

}
