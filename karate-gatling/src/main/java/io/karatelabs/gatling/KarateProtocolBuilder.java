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
            this.logReplayLevel = LogLevel.valueOf(level.trim().toUpperCase());
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
        this.logReplayLimit = limit;
        return this;
    }

    /**
     * Build the KarateProtocol.
     */
    public KarateProtocol build() {
        KarateProtocol protocol = new KarateProtocol(uriPatterns, runner);
        if (nameResolver != null) {
            protocol.setNameResolver(nameResolver);
        }
        protocol.setLogReplay(logReplay);
        protocol.setLogReplayLevel(logReplayLevel);
        protocol.setLogReplayLimit(logReplayLimit);
        return protocol;
    }

    @Override
    public io.gatling.core.protocol.Protocol protocol() {
        return build();
    }

}
