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

import io.gatling.javaapi.core.ProtocolBuilder;
import io.karatelabs.http.HttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Builder for creating KarateProtocol instances.
 * Provides a fluent API for configuring URI patterns and request naming.
 *
 * <p>Example usage:
 * <pre>
 * KarateProtocolBuilder protocol = karateProtocol(
 *     uri("/cats/{id}").nil(),
 *     uri("/cats").pauseFor(method("get", 10), method("post", 20))
 * ).nameResolver((req, vars) -&gt; req.getPath());
 * </pre>
 */
public final class KarateProtocolBuilder implements ProtocolBuilder {

    private final Map<String, KarateUriPattern> uriPatterns = new HashMap<>();
    private BiFunction<HttpRequest, Map<String, Object>, String> nameResolver;

    /**
     * Create a new protocol builder with the given URI patterns.
     */
    public KarateProtocolBuilder(KarateUriPattern... patterns) {
        for (KarateUriPattern pattern : patterns) {
            uriPatterns.put(pattern.getPattern(), pattern);
        }
    }

    /**
     * Set a custom name resolver for HTTP requests.
     * The resolver receives the HttpRequest and scenario variables,
     * returning a custom name for Gatling reporting.
     *
     * @param resolver the name resolver function
     * @return this builder for chaining
     */
    public KarateProtocolBuilder nameResolver(BiFunction<HttpRequest, Map<String, Object>, String> resolver) {
        this.nameResolver = resolver;
        return this;
    }

    /**
     * Build the KarateProtocol.
     */
    public KarateProtocol build() {
        KarateProtocol protocol = new KarateProtocol(uriPatterns);
        if (nameResolver != null) {
            protocol.setNameResolver(nameResolver);
        }
        return protocol;
    }

    @Override
    public io.gatling.core.protocol.Protocol protocol() {
        return build();
    }

}
