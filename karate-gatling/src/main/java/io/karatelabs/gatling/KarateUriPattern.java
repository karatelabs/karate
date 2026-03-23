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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a URI pattern with optional method-specific pause configurations.
 * URI patterns can include path parameters like "/users/{id}".
 *
 * <p>Example usage:
 * <pre>
 * KarateUriPattern pattern = KarateUriPattern.uri("/cats/{id}")
 *     .pauseFor(method("get", 10), method("post", 20))
 *     .build();
 * </pre>
 */
public final class KarateUriPattern {

    private final String pattern;
    private final Map<String, Integer> methodPauses;

    private KarateUriPattern(String pattern, Map<String, Integer> methodPauses) {
        this.pattern = pattern;
        this.methodPauses = Collections.unmodifiableMap(methodPauses);
    }

    /**
     * Get the URI pattern string.
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Get the pause duration for a specific HTTP method.
     *
     * @param method the HTTP method (GET, POST, etc.)
     * @return the pause duration in milliseconds, or 0 if not specified
     */
    public int getPauseFor(String method) {
        return methodPauses.getOrDefault(method.toUpperCase(), 0);
    }

    /**
     * Get all method pause configurations.
     */
    public Map<String, Integer> getMethodPauses() {
        return methodPauses;
    }

    /**
     * Create a builder for a URI pattern.
     *
     * @param pattern the URI pattern (e.g., "/users/{id}")
     * @return a new builder
     */
    public static Builder uri(String pattern) {
        return new Builder(pattern);
    }

    /**
     * Builder for KarateUriPattern.
     */
    public static final class Builder {

        private final String pattern;
        private final Map<String, Integer> methodPauses = new HashMap<>();

        Builder(String pattern) {
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("pattern cannot be null or blank");
            }
            this.pattern = pattern;
        }

        /**
         * Configure method-specific pauses for this URI pattern.
         *
         * @param pauses the method pause configurations
         * @return this builder
         */
        public Builder pauseFor(MethodPause... pauses) {
            for (MethodPause pause : pauses) {
                methodPauses.put(pause.method(), pause.pauseMillis());
            }
            return this;
        }

        /**
         * Configure no pauses for this URI pattern.
         * This is syntactic sugar for patterns that don't need pauses.
         *
         * @return the built KarateUriPattern
         */
        public KarateUriPattern nil() {
            return build();
        }

        /**
         * Build the KarateUriPattern.
         *
         * @return the built KarateUriPattern
         */
        public KarateUriPattern build() {
            return new KarateUriPattern(pattern, methodPauses);
        }

    }

}
