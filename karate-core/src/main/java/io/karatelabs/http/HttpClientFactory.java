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

}
