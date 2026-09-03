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
package io.karatelabs.gatling;

import io.gatling.javaapi.core.ProtocolBuilder;
import io.karatelabs.core.Runner;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.output.LogLevel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

/**
 * Builder for creating KarateProtocol instances.
 * Provides a fluent API for configuring URI patterns and request naming.
 *
 * <p>Example usage:
 * <pre>
 * KarateProtocolBuilder protocol = karateProtocol(
 *     uri("/cats/{id}").nil(),
 *     uri("/cats").pauseFor(method("get", 10), method("post", 20))
 * ).nameResolver((req, vars) -&gt; req.getPath());
 * </pre>
 */
public final class KarateProtocolBuilder implements ProtocolBuilder {

    private final Map<String, KarateUriPattern> uriPatterns = new HashMap<>();
    private BiFunction<HttpRequest, Map<String, Object>, String> nameResolver;
    private KarateLogReplay logReplay = KarateLogReplay.OFF;
    private LogLevel logReplayLevel = LogReplayer.DEFAULT_LEVEL;
    private int logReplayLimit = LogReplayer.DEFAULT_LIMIT;
    private PooledHttpClientFactory pool;

    /**
     * Mutable {@link Runner.Builder} for configuring underlying Karate execution.
     * Mirrors the v1 {@code protocol.runner} field — set {@code karateEnv},
     * {@code configDir}, {@code systemProperty}, etc. directly on it.
     * <p>
     * Example: {@code protocol.runner.karateEnv("perf").configDir("src/test/resources");}
     * <p>
     * Protocol-scoped callSingle and callOnce/setupOnce caches are also pre-wired
     * onto this builder. They live for the lifetime of the simulation (one protocol
     * = one setUp), so an expensive {@code karate.callSingle(...)} or a feature-level
     * {@code callonce} runs once per simulation rather than once per virtual user.
     */
    public final Runner.Builder runner = Runner.builder()
            .callSingleCache(new ConcurrentHashMap<>(), new ReentrantLock())
            .callOnceCacheStore(new ConcurrentHashMap<>(), new ConcurrentHashMap<>())
            .setupOnceCacheStore(new ConcurrentHashMap<>());

    /**
     * Create a new protocol builder with the given URI patterns.
     */
    public KarateProtocolBuilder(KarateUriPattern... patterns) {
        for (KarateUriPattern pattern : patterns) {
            uriPatterns.put(pattern.getPattern(), pattern);
        }
    }

    /**
     * Set a custom name resolver for HTTP requests.
     * The resolver receives the HttpRequest and scenario variables,
     * returning a custom name for Gatling reporting.
     *
     * @param resolver the name resolver function
     * @return this builder for chaining
     */
    public KarateProtocolBuilder nameResolver(BiFunction<HttpRequest, Map<String, Object>, String> resolver) {
        this.nameResolver = resolver;
        return this;
    }

    /**
     * Replay Karate's per-step output — HTTP request / response blocks and {@code print}
     * statements — when a feature fails, at {@link #logReplayLevel(String) a level of your
     * choosing}. A load run is normally configured at a high Logback level so this output never
     * reaches the log; Karate captures it regardless, so it can be held and released only for the
     * runs that actually failed.
     *
     * <p>{@link KarateLogReplay#ALL} also replays the features that already passed for this virtual
     * user — the context that led up to the failure — bounded by {@link #logReplayLimit(int)}.
     *
     * @param mode {@code OFF} (default), {@code FAILED}, or {@code ALL}
     * @return this builder for chaining
     */
    public KarateProtocolBuilder logReplay(KarateLogReplay mode) {
        this.logReplay = mode == null ? KarateLogReplay.OFF : mode;
        return this;
    }

    /** {@link #logReplay(KarateLogReplay)} by name: {@code "off"}, {@code "failed"}, {@code "all"}. */
    public KarateProtocolBuilder logReplay(String mode) {
        return logReplay(KarateLogReplay.fromString(mode));
    }

    /**
     * The level the replayed output is logged at, defaulting to {@code error} so it survives the
     * quiet Logback config a load run usually has. Accepts {@code trace}, {@code debug},
     * {@code info}, {@code warn}, {@code error}.
     *
     * @return this builder for chaining
     */
    public KarateProtocolBuilder logReplayLevel(String level) {
        try {
            this.logReplayLevel = LogLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid log replay level: '" + level
                    + "', expected one of: trace, debug, info, warn, error");
        }
        return this;
    }

    /**
     * How many already-passed feature logs to retain per virtual user in
     * {@link KarateLogReplay#ALL} mode, defaulting to {@value LogReplayer#DEFAULT_LIMIT}. This text
     * is held in memory for every virtual user until it is replayed or dropped, so raise it with
     * the run's concurrency in mind. Once a replay happens the retained logs are cleared, which is
     * what keeps the window to the current iteration in the common case — Gatling gives an action
     * no iteration boundary to hook, so the guarantee is "the last N Karate calls for this user".
     *
     * @return this builder for chaining
     */
    public KarateProtocolBuilder logReplayLimit(int limit) {
        if (limit < 1) {
            // zero would retain nothing AND report nothing dropped, quietly turning ALL into
            // FAILED — which is a mode you can just ask for
            throw new IllegalArgumentException("logReplayLimit must be at least 1, was: " + limit
                    + " (use logReplay(FAILED) to replay only the failing feature)");
        }
        this.logReplayLimit = limit;
        return this;
    }

    /**
     * Share one connection pool across the whole simulation, instead of opening a connection per
     * iteration.
     *
     * <p>Karate builds an HTTP client per scenario execution, so under Gatling it reconnects every
     * iteration where plain Gatling reuses a connection per virtual user. On a two-host bench that
     * was 4000 distinct client ports against 8, and collapsing it was worth about 24% of Karate's
     * per-iteration overhead against a plaintext endpoint on the same network — more against a
     * public TLS endpoint, where the avoided handshake costs two round trips and asymmetric crypto.
     *
     * <p><b>Read {@link PooledHttpClientFactory} before turning this on.</b> A pooled client cannot
     * honour {@code configure ssl} set inside a scenario and ignores it silently, and NTLM does not
     * work at all with pooling because it authenticates the connection rather than the request.
     * Configure SSL once, for the simulation. Those trades are why this is opt-in and why pooling
     * is not karate-core's behaviour.
     *
     * @return this builder for chaining
     */
    public KarateProtocolBuilder pooledConnections() {
        return pooledConnections(PooledHttpClientFactory.DEFAULT_MAX_CONNECTIONS,
                PooledHttpClientFactory.DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * As {@link #pooledConnections()}, with an explicit ceiling.
     *
     * <p>Size it above the peak virtual-user count. The pool opens connections on demand, so a high
     * ceiling costs nothing, while one below the concurrency becomes a bottleneck that reads as the
     * server being slow rather than as a client limit.
     *
     * @param maxTotal total pooled connections across all routes
     * @param perRoute connections per target host — the binding number for a single-endpoint test
     * @return this builder for chaining
     */
    public KarateProtocolBuilder pooledConnections(int maxTotal, int perRoute) {
        if (maxTotal < 1 || perRoute < 1) {
            throw new IllegalArgumentException("pool sizes must be at least 1, got maxTotal="
                    + maxTotal + " perRoute=" + perRoute);
        }
        // Calling this twice used to overwrite the field and abandon the first pool: only the last
        // one reaches the protocol, so only the last one is ever registered for close, and the
        // other's connection manager survives the simulation holding whatever it had opened.
        // Closing here is safe because nothing has leased from it yet — the protocol has not been
        // built, so no scenario has a client.
        if (pool != null) {
            pool.close();
        }
        pool = new PooledHttpClientFactory(maxTotal, perRoute);
        runner.httpClientFactory(pool);
        return this;
    }

    /**
     * Build the KarateProtocol.
     */
    public KarateProtocol build() {
        // Replay reads Karate's per-step log, which this lane otherwise does not collect at all
        // (no HTML report, nothing else reads it). Asking for replay is asking for the capture
        // it replays — without this, replay would silently produce nothing, so it wins over a
        // protocol.runner.captureStepLogs(false). With replay OFF nothing is touched here, so
        // captureStepLogs stays the user's to set.
        if (logReplay != KarateLogReplay.OFF) {
            runner.captureStepLogs(true);
            // A called feature's output lives only on its own StepResults, reachable through the
            // caller's call results — which this lane otherwise releases at scenario end. Replay
            // renders after the feature returns, so the tree has to survive that long, and no
            // longer: KarateExecutor.execute() releases it right after the replay has rendered.
            runner.retainCallResults(true);
        }
        KarateProtocol protocol = new KarateProtocol(uriPatterns, runner);
        if (nameResolver != null) {
            protocol.setNameResolver(nameResolver);
        }
        protocol.setLogReplay(logReplay);
        protocol.setLogReplayLevel(logReplayLevel);
        protocol.setLogReplayLimit(logReplayLimit);
        // Handed over so Gatling's actor-system termination can close it — see
        // KarateProtocol.getCloseAtSimulationEnd.
        protocol.setCloseAtSimulationEnd(pool);
        return protocol;
    }

    @Override
    public io.gatling.core.protocol.Protocol protocol() {
        return build();
    }

}
