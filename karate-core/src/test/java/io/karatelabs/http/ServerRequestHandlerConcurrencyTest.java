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

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Disabled — kept as a documented reproducer for the concurrent JS-mock race
 * tracked in {@code docs/TODOS.md} (the {@code context.synchronized(name, fn)}
 * follow-up). Enable locally to confirm the failure mode and to verify a fix.
 * <p>
 * Setup mirrors {@code karate-todo}'s demo: a {@link ServerRequestHandler}
 * backed by a singleton {@link SessionStore} (every {@code create}/{@code get}
 * returns the same {@link Session} object) is hit concurrently with POSTs
 * against the {@code classpath:demo/api/todos.js} JS handler.
 * <p>
 * Two failure modes surface:
 * <ul>
 *   <li><b>Silent item loss</b> — POSTs return 201 but later the list contains
 *   fewer entries than were posted. {@link io.karatelabs.js.JsArrayPrototype#push}
 *   does {@code lengthOf(target)} → {@code specSet(target, len, value)} →
 *   {@code setLength(target, len+1)} as three separate steps; concurrent calls
 *   on the same backing list see a stale {@code len} and the {@code specSet}
 *   stage routes through {@code setByIndex}'s {@code list.set(i, value)}
 *   branch, overwriting a sibling thread's just-appended entry.</li>
 *   <li><b>500 ({@code IndexOutOfBoundsException} / {@code ConcurrentModificationException})</b>
 *   — {@code setLength} calls {@code JsArray$ArrayLength.applySet}, which
 *   reads {@code oldLen = arr.list.size()} and (if {@code newLen < oldLen})
 *   takes the truncate branch via {@code arr.list.subList(newLen, oldLen).clear()}.
 *   Concurrent extensions inflate {@code list.size()} between the read of
 *   {@code oldLen} and the {@code subList} call; on a {@link
 *   java.util.concurrent.CopyOnWriteArrayList} the snapshot view trips
 *   {@code checkForComodification}, on a plain {@code ArrayList} it can
 *   surface as IOOB during internal resize.</li>
 * </ul>
 * <p>
 * The race is in the engine's non-atomic Array.prototype op sequence — not in
 * user data structures — so swapping {@code HashMap} → {@code ConcurrentHashMap}
 * and {@code ArrayList} → {@code CopyOnWriteArrayList} doesn't fix it.
 * Workaround today: wrap the request handler in a {@link
 * java.util.concurrent.locks.ReentrantLock} (see karate-todo's {@code App.handler()}).
 */
@Disabled("documented reproducer — see docs/TODOS.md context.synchronized item")
class ServerRequestHandlerConcurrencyTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        Session singleton = new Session(
                "singleton",
                new HashMap<>(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                -1
        );
        ServerConfig config = new ServerConfig()
                .sessionStore(new SessionStore() {
                    @Override
                    public Session create(int expirySeconds) {
                        return singleton;
                    }

                    @Override
                    public Session get(String id) {
                        return singleton;
                    }

                    @Override
                    public void save(Session session) {
                    }

                    @Override
                    public void delete(String id) {
                    }
                })
                .apiPrefix("/api/")
                .staticPrefix("/pub/")
                .csrfEnabled(false);
        ServerRequestHandler handler = new ServerRequestHandler(
                config, new RootResourceResolver("classpath:demo"));
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void parallelPostsAgainstSingletonSession() throws Exception {
        int threads = 8;
        int requestsPerThread = 32;
        int total = threads * requestsPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger nonCreated = new AtomicInteger();
        AtomicReference<String> firstFailureBody = new AtomicReference<>();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int threadIndex = t;
                futures.add(pool.submit(() -> {
                    for (int r = 0; r < requestsPerThread; r++) {
                        String body = "{\"title\":\"t" + threadIndex + "-" + r + "\",\"complete\":false}";
                        HttpResponse response = harness.request()
                                .path("/api/todos")
                                .body(body)
                                .header("Content-Type", "application/json")
                                .post();
                        if (response.getStatus() != 201) {
                            nonCreated.incrementAndGet();
                            firstFailureBody.compareAndSet(null,
                                    "status=" + response.getStatus()
                                            + " body=" + response.getBodyString());
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        // Every POST should have returned 201
        assertEquals(0, nonCreated.get(),
                "POSTs failed (non-201). first failure: " + firstFailureBody.get());

        // After all POSTs, GET the list — every created todo should still be there
        HttpResponse list = harness.get("/api/todos");
        assertEquals(200, list.getStatus(), "list GET failed");
        String body = list.getBodyString();
        // Crude count of entries in the list — count occurrences of "title"
        int found = 0;
        int idx = 0;
        while ((idx = body.indexOf("\"title\"", idx)) != -1) {
            found++;
            idx++;
        }
        assertEquals(total, found,
                "expected " + total + " todos in session, got " + found
                        + " (silent loss under concurrent push)");
        assertNotEquals(0, found, "no todos at all (server completely broken)");
    }

}
