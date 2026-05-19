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

import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpClientFactory;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * In-memory HTTP client that bypasses the network for fast tests.
 * Accepts a handler function to produce responses for requests.
 * <p>
 * Records every {@link #apply(KarateConfig)} call so tests can assert
 * what was pushed to the client (e.g. that an inherited proxy reached a
 * called feature's client). See {@link #getAppliedConfigs()} /
 * {@link #getLatestConfig()}.
 */
public class InMemoryHttpClient implements HttpClient {

    private Function<HttpRequest, HttpResponse> handler;

    private final List<KarateConfig> appliedConfigs = new ArrayList<>();

    /**
     * Create with default handler that returns 200 OK with empty body.
     */
    public InMemoryHttpClient() {
        this.handler = req -> {
            HttpResponse resp = new HttpResponse();
            resp.setStatus(200);
            return resp;
        };
    }

    /**
     * Create with custom handler.
     */
    public InMemoryHttpClient(Function<HttpRequest, HttpResponse> handler) {
        this.handler = handler;
    }

    @Override
    public HttpResponse invoke(HttpRequest request) {
        return handler.apply(request);
    }

    @Override
    public void apply(KarateConfig config) {
        // Snapshot so later mutations on the caller don't change what we recorded.
        appliedConfigs.add(config == null ? null : config.copy());
    }

    @Override
    public void abort() {
        // No-op for in-memory client
    }

    @Override
    public void close() {
        // No-op for in-memory client
    }

    /**
     * All {@link #apply(KarateConfig)} invocations as snapshots, in order.
     */
    public List<KarateConfig> getAppliedConfigs() {
        return appliedConfigs;
    }

    /**
     * Latest applied config snapshot, or {@code null} if {@code apply}
     * was never called.
     */
    public KarateConfig getLatestConfig() {
        return appliedConfigs.isEmpty() ? null : appliedConfigs.get(appliedConfigs.size() - 1);
    }

    /**
     * Factory that yields a fresh {@link InMemoryHttpClient} per scenario,
     * tracking every instance created. Mirrors the production
     * {@link io.karatelabs.http.DefaultHttpClientFactory} contract (one
     * client per scenario) so tests can inspect each scenario's client
     * independently — including the client created for a called feature.
     */
    public static class Factory implements HttpClientFactory {

        private final List<InMemoryHttpClient> clients = new ArrayList<>();
        private final Function<HttpRequest, HttpResponse> handler;

        public Factory() {
            this(null);
        }

        public Factory(Function<HttpRequest, HttpResponse> handler) {
            this.handler = handler;
        }

        @Override
        public HttpClient create() {
            InMemoryHttpClient client = handler == null
                    ? new InMemoryHttpClient()
                    : new InMemoryHttpClient(handler);
            clients.add(client);
            return client;
        }

        /**
         * All clients created by this factory, in creation order.
         */
        public List<InMemoryHttpClient> getClients() {
            return clients;
        }
    }

    /**
     * Builder for creating responses easily.
     */
    public static HttpResponse response() {
        HttpResponse resp = new HttpResponse();
        resp.setStatus(200);
        return resp;
    }

    /**
     * Create a response with JSON body.
     */
    public static HttpResponse json(String jsonBody) {
        return HttpResponse.json(jsonBody);
    }

    /**
     * Create a response with status and JSON body.
     */
    public static HttpResponse json(int status, String jsonBody) {
        return HttpResponse.json(status, jsonBody);
    }

    /**
     * Create a response with just a status code.
     */
    public static HttpResponse status(int statusCode) {
        HttpResponse resp = new HttpResponse();
        resp.setStatus(statusCode);
        return resp;
    }

}
