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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared concurrency helpers usable from karate-core and from any code that
 * embeds karate-core as a library.
 *
 * <p>Daemon thread factories are the cleanup contract every long-lived
 * subsystem in karate-core (HttpServer, WsClient, ProcessHandle, ...) relies
 * on: owners call {@code close()} / {@code shutdownNow()} explicitly, and if
 * those are skipped for any reason (test crash, programmer error, embedder
 * shutdown sequence) the daemon flag guarantees the JVM still terminates.
 *
 * <p>Two shapes are offered to cover both common cases:
 * <ul>
 *   <li>{@link #daemonFactory(String)} — numbered, for pools (cached / fixed
 *       thread pools, Netty event loop groups). Thread names are
 *       {@code prefix + N} (1-based).</li>
 *   <li>{@link #daemonFactory(String, boolean)} — pass {@code numbered=false}
 *       for a fixed-name factory suitable for
 *       {@code Executors.newSingleThreadExecutor} /
 *       {@code newSingleThreadScheduledExecutor}, where the thread name
 *       should match across restarts and grep cleanly.</li>
 * </ul>
 */
public final class ThreadUtils {

    private ThreadUtils() {
    }

    /**
     * Numbered daemon thread factory. Each spawned thread is named
     * {@code prefix + N} (N starts at 1). Right for pools where multiple
     * threads can coexist.
     */
    public static ThreadFactory daemonFactory(String prefix) {
        return daemonFactory(prefix, true);
    }

    /**
     * Daemon thread factory.
     *
     * @param name     thread name (or name prefix when {@code numbered} is true)
     * @param numbered when true, threads are named {@code name + N} (1-based);
     *                 when false, every spawned thread is named exactly {@code name}
     *                 — suitable for single-thread executors where a stable name
     *                 helps logs / thread dumps / grep
     */
    public static ThreadFactory daemonFactory(String name, boolean numbered) {
        if (!numbered) {
            return r -> {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            };
        }
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
