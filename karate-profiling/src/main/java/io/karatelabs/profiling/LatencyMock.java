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
package io.karatelabs.profiling;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A mock whose own cost is known — the instrument the Gatling parity question needs, as scoped in
 * docs/PROFILING.md §10.
 *
 * <p>It serves the same three endpoints as {@code mock/profiling-mock.feature}, so both existing
 * simulations run against it unchanged, and it differs from that mock in the two ways that
 * matter:</p>
 *
 * <ul>
 *   <li><b>A latency knob.</b> {@code --latency 10ms} makes it answer like a real API instead of
 *       like localhost. Whether Karate's per-execution overhead disappears into network time is
 *       the whole question, and it cannot be asked of a server that answers in a millisecond.</li>
 *   <li><b>It measures itself</b> ({@link MockStats}). The feature mock saturates before either
 *       client does, which is why every existing throughput comparison proves nothing: two
 *       saturated clients queued behind one overloaded server report identical numbers.</li>
 * </ul>
 *
 * <p><b>The feature mock is not replaced by this.</b> The two are different kinds of thing: the
 * feature mock is a <i>subject</i> — {@code --record mock} profiles gherkin matching and JS
 * evaluation — and this is an <i>instrument</i>. Conflating them is how the throughput ceiling
 * got stuck.</p>
 *
 * <p><b>On being deliberately boring:</b> the JDK's own HTTP server on virtual threads, no
 * dependencies, no JSON library, no logging on the request path. The request body is spliced
 * rather than parsed, which is narrow — it handles a JSON object and nothing else — and narrow is
 * the point. A reference clock must not share a parser, a client or an allocator with the thing it
 * is measuring, or a regression over there quietly moves the reference over here.</p>
 *
 * <p><b>What it cannot tell you:</b> see the one-directional caveat on {@link MockStats}. This
 * server knowing it is healthy is not the same as it being out of the way, which is what §10's
 * calibration step exists to establish.</p>
 */
public final class LatencyMock {

    /** The parent blocks on this line to learn the port; keep the format stable. */
    static final String READY_PREFIX = "PROFILING-MOCK-URL ";

    /** Prefix of the shutdown stats line; the parent greps for it. Keep stable. */
    static final String STATS_PREFIX = "PROFILING-MOCK-STATS ";

    /**
     * Slots in the created-cat ring. Bounded storage on purpose: the instrument's own heap must
     * not be a variable over a long ramp. A ring rather than an evicting map because ids are
     * monotonic and every GET immediately follows its POST — with room for this many in flight,
     * a slot is never overwritten before it is read, and if it ever were, the miss is a visible
     * 404 rather than a silent drift.
     */
    private static final int RING_SLOTS = 1 << 16;

    private static final byte[] PING = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_FOUND = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BAD_REQUEST = "{\"error\":\"expected a json object body\"}".getBytes(StandardCharsets.UTF_8);

    private final MockStats stats = new MockStats();
    private final AtomicLong nextId = new AtomicLong();
    private final long[] ringIds = new long[RING_SLOTS];
    private final byte[][] ringBodies = new byte[RING_SLOTS][];
    private final long latencyNanos;
    private final int backlog;

    private LatencyMock(Duration latency, int backlog) {
        this.latencyNanos = latency.toNanos();
        this.backlog = backlog;
    }

    public static void main(String[] args) throws Exception {
        Duration latency = Duration.ZERO;
        int port = 0;
        // Explicit, and generously sized. The default delegates to a small system value, which is
        // itself a silent ceiling — and connection establishment is precisely the axis on which
        // the two clients differ, since Karate builds an HTTP client per execution.
        int backlog = 1024;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--latency" -> latency = RunShape.parseDuration(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--backlog" -> backlog = Integer.parseInt(args[++i]);
                default -> {
                }
            }
        }
        new LatencyMock(latency, backlog).run(port);
    }

    private void run(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), backlog);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();

        System.out.println(READY_PREFIX + "http://127.0.0.1:" + server.getAddress().getPort());
        System.out.flush();

        // Block until the parent's pipe closes, exactly as MockJvm does: read() returns -1 on EOF,
        // and also returns if the parent writes a byte to ask for shutdown.
        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (Exception ignored) {
            // treat any stdin failure as "parent is gone"
        }

        // The summary goes out BEFORE the server stops: this is the artifact the run is read
        // from, and a stats line lost to a shutdown race is a run that has to be repeated.
        System.out.println(stats.summary());
        System.out.flush();
        server.stop(0);
        executor.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Served outside the accounting entirely: no latency, and not counted. The readout is not
        // part of the load, and an instrument that included its own readout in its numbers would
        // be reporting a small lie — one that grows with however often the harness polls.
        if (path.startsWith("/stats")) {
            try {
                // /stats/reset returns the window just closed and starts a new one — how a ramp
                // point or a measured window gets numbers of its own instead of ones still
                // dominated by whatever ran before it. It doubles as the warmup trim.
                byte[] body = stats.toJson().getBytes(StandardCharsets.UTF_8);
                if ("/stats/reset".equals(path)) {
                    stats.reset();
                }
                respond(exchange, 200, body);
            } finally {
                exchange.close();
            }
            return;
        }

        stats.enter();
        long start = System.nanoTime();
        long sleptNanos = 0;
        try {
            String method = exchange.getRequestMethod();
            int status;
            byte[] body;
            if ("/ping".equals(path)) {
                status = 200;
                body = PING;
            } else if ("/cats".equals(path) && "POST".equalsIgnoreCase(method)) {
                byte[] created = create(exchange.getRequestBody());
                status = created == null ? 400 : 201;
                body = created == null ? BAD_REQUEST : created;
            } else if (path.startsWith("/cats/")) {
                byte[] found = find(path.substring("/cats/".length()));
                status = found == null ? 404 : 200;
                body = found == null ? NOT_FOUND : found;
            } else {
                status = 404;
                body = NOT_FOUND;
            }

            // The simulated network sits between the work and the response, and is excluded from
            // the service time recorded below — the injected wait is not the server's cost.
            sleptNanos = sleep();
            respond(exchange, status, body);
            if (status >= 400) {
                stats.recordError();
            }
        } finally {
            stats.record(System.nanoTime() - start - sleptNanos, sleptNanos);
            stats.exit();
            exchange.close();
        }
    }

    private long sleep() {
        if (latencyNanos <= 0) {
            return 0;
        }
        long before = System.nanoTime();
        try {
            // Parks the virtual thread rather than pinning its carrier, which is what lets this
            // hold hundreds of waiting requests on a handful of platform threads. Without that,
            // the mock's own thread count would become the concurrency limit under measurement.
            Thread.sleep(Duration.ofNanos(latencyNanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return System.nanoTime() - before;
    }

    /**
     * Echoes the posted object back with an {@code id} spliced in — what the feature mock does with
     * {@code def cat = request} / {@code set cat.id = id}, minus the JSON parser. Returns null for
     * anything that is not a JSON object, which the caller reports as a 400: a mock that quietly
     * accepted a malformed body would let a broken client look like a passing run.
     */
    private byte[] create(InputStream requestBody) throws IOException {
        String text = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8).trim();
        if (text.length() < 2 || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            return null;
        }
        long id = nextId.incrementAndGet();
        String separator = text.length() == 2 ? "" : ",";
        byte[] body = (text.substring(0, text.length() - 1) + separator + "\"id\":" + id + "}")
                .getBytes(StandardCharsets.UTF_8);
        int slot = (int) (id & (RING_SLOTS - 1));
        // Body before id: a reader that sees the id must see the body it belongs to. The pair is
        // not atomic, and does not need to be — a torn read yields a 404, never a wrong cat.
        ringBodies[slot] = body;
        ringIds[slot] = id;
        return body;
    }

    private byte[] find(String rawId) {
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            return null;
        }
        int slot = (int) (id & (RING_SLOTS - 1));
        return ringIds[slot] == id ? ringBodies[slot] : null;
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

}
