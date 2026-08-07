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

/**
 * Factory for creating HttpClient instances.
 * <p>
 * This interface allows external modules (such as karate-gatling) to provide
 * custom HttpClient implementations, for example with a shared connection pool
 * for performance testing scenarios.
 * <p>
 * The default implementation {@link DefaultHttpClientFactory} creates a new
 * {@link ApacheHttpClient} instance for each call, which is the standard
 * behavior for functional tests where isolation is preferred.
 *
 * @see DefaultHttpClientFactory
 * @see HttpClient
 */
public interface HttpClientFactory {

    /**
     * Creates a new HttpClient instance.
     *
     * @return a new HttpClient instance
     */
    HttpClient create();

    /**
     * Called when the scenario that owns {@code client} has finished with it.
     *
     * <p><b>The default does nothing</b>, and that is the conservative choice rather than the
     * obvious one. A factory exists so a caller can supply its own policy — this interface's own
     * javadoc offers "a shared connection pool for performance testing scenarios" — and a factory
     * handing out one shared instance is the common shape, including the {@code () -> client}
     * lambda form. Closing that at the end of one scenario would break every other scenario using
     * it, so a custom factory keeps today's behaviour unless it opts in.
     *
     * <p>{@link DefaultHttpClientFactory} overrides this to close, because it mints one client per
     * scenario that nothing else can reach — and until this existed, not one of them was ever
     * closed. Each abandoned its connection manager and pooled sockets to the collector, released
     * at a time no test controlled; a file descriptor is not heap, so no heap-based check could
     * see them accumulate.
     *
     * @param client a client previously returned by {@link #create()}
     */
    default void release(HttpClient client) {
        // Nothing: an unknown factory's clients may be shared. See DefaultHttpClientFactory.
    }

}
