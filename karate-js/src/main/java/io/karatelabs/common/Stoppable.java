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
package io.karatelabs.common;

import java.util.concurrent.ExecutorService;

/**
 * Anything long-lived that an embedding application may need to enumerate and
 * drain — an HTTP server, a websocket client, a thread pool, a browser process.
 *
 * <p>This is <b>the</b> stop contract for the Karate ecosystem. A component that
 * holds threads, sockets or child processes past the call that created it should
 * implement this interface and {@link KarateLifecycle#register(Stoppable) register}
 * itself once it is genuinely running, then
 * {@link KarateLifecycle#unregister(Stoppable) unregister} when it has stopped —
 * whether that stop came from the owner, from the registry, or from the component
 * itself (a socket closed by the peer, a process that exited).
 *
 * <p>Implementation rules, all of which {@link KarateLifecycle#shutdownAll} relies on:
 * <ul>
 *   <li>{@link #stop()} is <b>graceful</b> — it releases resources the polite way
 *       (close frames, in-flight requests drained) rather than killing threads.</li>
 *   <li>{@link #stop()} is <b>blocking</b> — it returns only once the component has
 *       actually stopped, so a caller can sequence shutdown deterministically.</li>
 *   <li>{@link #stop()} is <b>idempotent</b> — calling it twice, or on something that
 *       already stopped by itself, is a no-op and never throws for that reason.</li>
 * </ul>
 *
 * <p>Daemon threads (see {@link ThreadUtils}) remain the backstop, not the plan: if a
 * stop is skipped for any reason the JVM still terminates, but callbacks may be dropped
 * and peers may see abrupt disconnects. Registering here is what turns that backstop
 * into an orderly shutdown.
 *
 * <p>Extends {@link AutoCloseable} so an instance can be used in try-with-resources;
 * {@link #close()} simply delegates to {@link #stop()} and, unlike the parent
 * declaration, throws no checked exception.
 */
public interface Stoppable extends AutoCloseable {

    /**
     * Human-readable identity for logs and for listing what is running, ideally
     * carrying the one detail that distinguishes this instance from its siblings —
     * e.g. {@code "http-server:8080"}, {@code "ws-client:ws://localhost:8080/echo"}.
     */
    String lifecycleName();

    /**
     * Short category shared by all instances of a kind, lower-case and hyphenated —
     * e.g. {@code "http-server"}, {@code "ws-client"}, {@code "executor"}. Meant for
     * grouping and filtering, never parsed for meaning.
     */
    String lifecycleKind();

    /**
     * Stop this component: graceful, blocking, idempotent.
     */
    void stop();

    @Override
    default void close() {
        stop();
    }

    /**
     * The executor this component owns, when it owns one — purely for inspection
     * (queue depth, active count) by tooling. Returns null when there is nothing
     * meaningful to expose, which is the common case; never call
     * {@code shutdown()} on what this hands back, that is {@link #stop()}'s job.
     */
    default ExecutorService lifecycleExecutor() {
        return null;
    }

}
