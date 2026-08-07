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
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

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

    /** Prefix of the shutdown stats line; the parent greps for it. Keep stable. */
    static final String STATS_PREFIX = "PROFILING-MOCK-STATS ";

    /** Prefix of the startup line naming the settings that can fake a knee. Keep stable. */
    static final String CONFIG_PREFIX = "PROFILING-MOCK-CONFIG ";

    private static final String IDLE_CONNECTIONS_PROPERTY = "sun.net.httpserver.maxIdleConnections";

    /**
     * Slots in the created-cat ring. Bounded storage on purpose: the instrument's own heap must
     * not be a variable over a long ramp. A ring rather than an evicting map because ids are
     * monotonic and every GET immediately follows its POST — with room for this many in flight,
     * a slot is never overwritten before it is read, and if it ever were, the miss is a visible
     * 404 rather than a silent drift.
     */
    private static final int RING_SLOTS = 1 << 16;

    /** Nanos from handler entry to just before the response write, for this request. */
    static final String ELAPSED_HEADER = "X-Mock-Elapsed-Nanos";

    private static final byte[] PING = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_FOUND = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BAD_REQUEST = "{\"error\":\"expected a json object body\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESET_BUSY = "{\"error\":\"requests in flight\"}".getBytes(StandardCharsets.UTF_8);

    /**
     * One created cat. A record, so id and body are published together by a single reference
     * write — the first cut kept them in two parallel arrays written back to back with plain
     * stores, which has no happens-before edge at all: a reader could observe the matching id
     * against a stale body. That would not have surfaced as an error, because every cat in the
     * simulations is named "Billie" and the plain variant only checks the name — it would have
     * served the wrong record and passed.
     */
    private record Cat(long id, byte[] body) {
    }

    private final MockStats stats = new MockStats();
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicReferenceArray<Cat> ring = new AtomicReferenceArray<>(RING_SLOTS);
    private final long latencyNanos;
    private final int backlog;
    private final boolean tls;

    private LatencyMock(Duration latency, int backlog, boolean tls) {
        this.latencyNanos = latency.toNanos();
        this.backlog = backlog;
        this.tls = tls;
    }

    public static void main(String[] args) throws Exception {
        Duration latency = Duration.ZERO;
        int port = 0;
        // Loopback by default, because a forked sibling is the common case and a mock that
        // listens on every interface without being asked to is a surprise. `--bind 0.0.0.0` is
        // what a two-host run needs: docs/PROFILING.md §10 names co-location as the one confound
        // it cannot argue away, and separate hosts is the phase that removes it.
        String bind = "127.0.0.1";
        // Run until killed rather than until stdin closes — see run().
        boolean standalone = false;
        // Explicit, and generously sized. The default delegates to a small system value, which is
        // itself a silent ceiling — and connection establishment is precisely the axis on which
        // the two clients differ, since Karate builds an HTTP client per execution.
        int backlog = 1024;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--latency" -> latency = RunShape.parseDuration(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--bind" -> bind = args[++i];
                case "--backlog" -> backlog = Integer.parseInt(args[++i]);
                default -> {
                }
            }
        }
        // Scanned separately: the loop above stops at args.length - 1 because every other flag
        // takes a value, so a bare trailing flag would be silently dropped.
        boolean tls = false;
        for (String arg : args) {
            if (arg.equals("--standalone")) {
                standalone = true;
            }
            if (arg.equals("--tls")) {
                tls = true;
            }
        }
        new LatencyMock(latency, backlog, tls).run(bind, port, standalone);
    }

    /** Keystore bundled with this module, so a TLS tier needs no setup on the bench. */
    private static final String KEYSTORE = "/ssl/mock-keystore.p12";
    private static final char[] KEYSTORE_PASSWORD = "karate-mock".toCharArray();

    /**
     * The server side of the TLS tier: one self-signed certificate, bundled.
     *
     * <p>Self-signed is the right call rather than a shortcut. The measurement is what a handshake
     * costs in time — key exchange, and the round trips it takes — and that is unchanged by who
     * signed the certificate. A real chain would add validation work to the <em>client</em>, and
     * both arms would have to do exactly the same amount of it or the tier would be measuring
     * trust configuration instead of the handshake. Both arms are pointed at this one cert and
     * both trust it, which is symmetric and is what makes the tier readable.
     *
     * <p>It is emphatically not a secret: it is a test certificate that ships in the repository,
     * the mock binds to a private address, and the security group is what keeps it unreachable.
     */
    private static SSLContext tlsContext() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = LatencyMock.class.getResourceAsStream(KEYSTORE)) {
            if (in == null) {
                throw new IllegalStateException("no " + KEYSTORE + " on the classpath —"
                        + " the TLS tier needs it bundled, see src/main/resources");
            }
            store.load(in, KEYSTORE_PASSWORD);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, KEYSTORE_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }

    private void run(String bind, int port, boolean standalone) throws Exception {
        // The JDK server parks idle keep-alive connections and CLOSES them once more than
        // maxIdleConnections are parked — default 200. Above that many concurrent users it starts
        // churning connections, which looks exactly like a capacity knee and is a tunable default.
        // Set before the server class initialises, and echoed at startup so a run can be checked
        // against what it actually ran with.
        if (System.getProperty(IDLE_CONNECTIONS_PROPERTY) == null) {
            System.setProperty(IDLE_CONNECTIONS_PROPERTY, "8192");
        }
        HttpServer server;
        if (tls) {
            HttpsServer https = HttpsServer.create(new InetSocketAddress(bind, port), backlog);
            https.setHttpsConfigurator(new HttpsConfigurator(tlsContext()));
            server = https;
        } else {
            server = HttpServer.create(new InetSocketAddress(bind, port), backlog);
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();

        // Same handshake line as MockJvm, and deliberately its constant rather than a copy:
        // the parent greps for one prefix, so two definitions of it can only ever drift apart.
        System.out.println(MockJvm.READY_PREFIX + (tls ? "https://" : "http://")
                + bind + ":" + server.getAddress().getPort());
        // The two settings that can put a knee where there is no capacity limit. The kernel one
        // cannot be set from here — listen() silently clamps the requested backlog to
        // kern.ipc.somaxconn, which is 128 on macOS — so it is printed for the operator to check
        // rather than asserted. A backlog "generously sized" in Java is not generously sized.
        System.out.println(CONFIG_PREFIX + configJson());
        System.out.flush();

        if (standalone) {
            // A mock that outlives the run has no parent pipe to watch, and watching stdin anyway
            // is not a harmless default — a backgrounded process reads EOF immediately and shuts
            // down before its first request, printing a full and entirely empty stats line on the
            // way out. That is what this flag exists to prevent, and it was found by doing it.
            // SIGTERM is the shutdown signal here, so the summary moves into a hook.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(stats.summary());
                System.out.flush();
            }, "mock-summary"));
            Thread.currentThread().join();
            return;
        }

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

    /**
     * The settings that can put a knee where there is no capacity limit. Served as well as printed,
     * because a mock on another host has no stdout the harness can read — and a run whose digest
     * cannot say what latency was injected is a run that cannot be placed in a tier afterwards.
     *
     * <p>The kernel backlog cannot be set from here: {@code listen()} silently clamps the requested
     * value to the OS maximum, so it is named for the operator to check rather than asserted. A
     * backlog "generously sized" in Java is not generously sized. The knob differs by platform,
     * which is why the command to check it is chosen rather than hard-coded — every published
     * number in docs/PROFILING.md came off macOS, and the EC2 phase will not.
     */
    private String configJson() {
        boolean linux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        return "{\"backlogRequested\":" + backlog
                + ",\"maxIdleConnections\":" + System.getProperty(IDLE_CONNECTIONS_PROPERTY)
                + ",\"latencyMillis\":" + (latencyNanos / 1_000_000)
                // In the config line because the digest carries it: a TLS cell and a plaintext
                // cell are otherwise indistinguishable once the run is over, and comparing one
                // against the other is the whole point of the tier.
                + ",\"tls\":" + tls
                + ",\"os\":\"" + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "\""
                + ",\"cpus\":" + Runtime.getRuntime().availableProcessors()
                + ",\"checkSomaxconn\":\"" + (linux
                        ? "sysctl net.core.somaxconn" : "sysctl kern.ipc.somaxconn") + "\"}";
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Served outside the accounting entirely: no latency, and not counted. The readout is not
        // part of the load, and an instrument that included its own readout in its numbers would
        // be reporting a small lie — one that grows with however often the harness polls.
        // Exact match, not a prefix: "/stats" once matched by startsWith, which quietly made
        // /statsAnything a success endpoint. A mock that answers 200 to a URL nobody defined is a
        // mock that can absorb a client's routing bug without anyone noticing.
        // Same exemption, same reason: a harness reading the mock's settings is not load.
        if ("/config".equals(path)) {
            try {
                respond(exchange, 200, configJson().getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
            return;
        }
        boolean reset = "/stats/reset".equals(path);
        if (reset || "/stats".equals(path)) {
            try {
                // /stats/reset returns the window just closed and starts a new one — how a ramp
                // point or a measured window gets numbers of its own instead of ones still
                // dominated by whatever ran before it. It doubles as the warmup trim.
                byte[] body = stats.toJson().getBytes(StandardCharsets.UTF_8);
                if (reset) {
                    // Refused while anything is in flight, rather than silently splitting a
                    // request's count from its bucket across two windows. The caller is between
                    // ramp points and has nothing running; if it does, that is the bug.
                    if (stats.inFlight() > 0) {
                        respond(exchange, 409, RESET_BUSY);
                        return;
                    }
                    stats.reset();
                }
                respond(exchange, 200, body);
            } finally {
                exchange.close();
            }
            return;
        }

        stats.enter();
        // Outside the /stats branch above on purpose: a readout the harness makes is not load, and
        // its port would inflate the connection count this exists to report.
        stats.observePeer(exchange.getRemoteAddress() == null ? 0 : exchange.getRemoteAddress().getPort());
        long start = System.nanoTime();
        long sleptNanos = 0;
        try {
            String method = exchange.getRequestMethod();
            int status;
            byte[] body;
            if ("/ping".equals(path) && "GET".equalsIgnoreCase(method)) {
                status = 200;
                body = PING;
            } else if ("/cats".equals(path) && "POST".equalsIgnoreCase(method)) {
                byte[] created = create(exchange.getRequestBody());
                status = created == null ? 400 : 201;
                body = created == null ? BAD_REQUEST : created;
            } else if (path.startsWith("/cats/") && "GET".equalsIgnoreCase(method)) {
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
            // What this server can account for, on THIS request, handed to the client so it can
            // subtract like for like. Aggregate subtraction (client p50 minus server p50) compares
            // two different requests and has no per-request meaning; this does. It stops just short
            // of the response write, so the client's remainder legitimately contains the write, the
            // kernel queue and the network — which is the part being looked for.
            exchange.getResponseHeaders().set(ELAPSED_HEADER,
                    Long.toString(System.nanoTime() - start));
            respond(exchange, status, body);
            if (status >= 400) {
                stats.recordError();
            }
        } catch (IOException | RuntimeException e) {
            // An aborted exchange still reached the handler and still cost the server, so it stays
            // in the served count — but it must not pass as a clean request. During a port
            // exhaustion episode these are exactly the requests that matter.
            stats.recordError();
            throw e;
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
        // Blank interior, not just "{}": a body of "{ }" is length 4 and would otherwise splice to
        // {@code { ,"id":1}}, which is malformed JSON served with a 201.
        String separator = text.substring(1, text.length() - 1).isBlank() ? "" : ",";
        byte[] body = (text.substring(0, text.length() - 1) + separator + "\"id\":" + id + "}")
                .getBytes(StandardCharsets.UTF_8);
        ring.set((int) (id & (RING_SLOTS - 1)), new Cat(id, body));
        return body;
    }

    private byte[] find(String rawId) {
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            return null;
        }
        Cat cat = ring.get((int) (id & (RING_SLOTS - 1)));
        // An id mismatch means the slot was recycled before this read — a visible 404, which is
        // the only failure mode worth having here.
        return cat != null && cat.id() == id ? cat.body() : null;
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

}
