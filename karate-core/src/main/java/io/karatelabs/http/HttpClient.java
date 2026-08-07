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

import io.karatelabs.core.KarateConfig;

import java.io.Closeable;

public interface HttpClient extends Closeable {

    HttpResponse invoke(HttpRequest request);

    /**
     * Apply a typed configuration snapshot. {@link KarateConfig} is the single
     * source of truth — implementations read every relevant setting via its
     * typed getters and (re)build their internal client state.
     *
     * <p><b>Must be idempotent: an implementation may not tear down or rebuild anything when the
     * settings it cares about are unchanged.</b> This is a requirement rather than an
     * optimisation, because it is what lets callers stop deciding. Config reaches a client from
     * four places — a {@code configure <key>} step, a shared-scope call returning, a callee
     * inheriting from its caller, and a cached {@code callonce} replaying — and the last three
     * copy a whole config across a scenario boundary with no key to test. Any caller-side
     * shortcut therefore has to be conservative on those paths, which means rebuilding a live
     * client and its connection pool for a config that did not change. Deciding here, against
     * the settings actually in use, is the only place the question can be answered exactly.
     *
     * <p>Callers should call this whenever config may have changed and let the implementation
     * sort it out. They must not try to predict the answer.
     */
    void apply(KarateConfig config);

    void abort();

}
