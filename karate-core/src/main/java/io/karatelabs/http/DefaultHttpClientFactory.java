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
package io.karatelabs.http;

import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link HttpClientFactory} that creates a new
 * {@link ApacheHttpClient} instance for each call.
 * <p>
 * This is the standard behavior for functional tests where each test scenario
 * should have an isolated HTTP client with its own configuration and state.
 * <p>
 * For performance testing with connection pooling, use a custom factory
 * implementation (e.g., PooledHttpClientFactory in karate-gatling).
 *
 * @see HttpClientFactory
 * @see ApacheHttpClient
 */
public class DefaultHttpClientFactory implements HttpClientFactory {

    @Override
    public HttpClient create() {
        return new ApacheHttpClient();
    }

    /**
     * Closes it. This factory hands out one client per scenario and nothing else can reach it, so
     * the scenario's end is the last moment anyone can release its sockets deterministically.
     *
     * <p>Every ScenarioRuntime builds its own client — including one per {@code karate.call()} —
     * and none of them used to be closed.
     */
    @Override
    public void release(HttpClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            // Teardown: a release failure must not fail a scenario that already passed.
            LoggerFactory.getLogger(DefaultHttpClientFactory.class)
                    .warn("error closing http client: {}", e.getMessage());
        }
    }

}
