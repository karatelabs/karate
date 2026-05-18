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
package io.karatelabs.core;

import io.karatelabs.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link RunListener} that POSTs run events as JSON Lines to a remote HTTP receiver.
 * <p>
 * Designed for the karate-agent dashboard, but the wire protocol is open — any compatible
 * HTTP receiver can implement it. The schema mirrors {@link JsonLinesEventWriter}'s envelope
 * with an explicit {@code schema} field for forward compatibility.
 * <p>
 * Activation is purely env-var driven. When {@code KARATE_AGENT_URL} is unset,
 * {@link #tryCreate(String)} returns {@code null} and zero network code paths execute.
 * <p>
 * Environment variables:
 * <ul>
 *   <li>{@code KARATE_AGENT_URL} — destination base URL (required to activate)</li>
 *   <li>{@code KARATE_AGENT_TOKEN} — optional bearer token for receivers that require auth</li>
 *   <li>{@code KARATE_AGENT_MODE} — {@code batch} (default) or {@code final}; stream mode is reserved</li>
 *   <li>{@code KARATE_AGENT_PARAMS} — optional JSON object attached verbatim to
 *       {@code SUITE_ENTER.data.params}. V0 schema {@code {"dev": bool}} marks the run as
 *       developer-loop so the aggregator can hide it from the default Runs rail.
 *       Forward-compatible: receivers persist unknown keys untouched. Malformed JSON or a
 *       non-object value logs WARN and is dropped (params absent from the envelope).</li>
 * </ul>
 * <p>
 * Modes:
 * <ul>
 *   <li><b>batch</b> — accumulate events, POST every {@link #DEFAULT_BATCH_SIZE} events plus a
 *       final flush on SUITE_EXIT. Reasonable default for most runs.</li>
 *   <li><b>final</b> — accumulate the whole run, POST once on SUITE_EXIT. Tightest network use,
 *       no real-time visibility.</li>
 * </ul>
 * <p>
 * Behaviour is <b>best-effort</b>: POST failures log a WARN and are dropped on the floor.
 * The build is never failed by a transport error. The on-disk JSONL stream
 * ({@code karate-json/karate-events.jsonl}, opt-in via {@link Runner.Builder#outputJsonLines(boolean)})
 * remains the source of truth — operators can reconcile a missed dashboard by re-ingesting
 * the on-disk file via a bulk-ingest endpoint.
 * <p>
 * Endpoints called (paths relative to {@code KARATE_AGENT_URL}):
 * <ul>
 *   <li>{@code POST /api/runs/{runId}/events}   — batched events, body is JSONL</li>
 *   <li>{@code POST /api/runs/{runId}/complete} — final flush on SUITE_EXIT</li>
 * </ul>
 * <p>
 * Wire envelope (one per line):
 * <pre>
 * {
 *   "schema": { "version": 1, "dialect": "karate-v2" },
 *   "type": "SCENARIO_EXIT",
 *   "timeStamp": 1716000000000,
 *   "threadId": "thread-3",
 *   "data": { ... type-specific ... }
 * }
 * </pre>
 */
public class HttpPostListener implements RunListener {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final int SCHEMA_VERSION = 1;
    public static final String SCHEMA_DIALECT = "karate-v2";

    public static final String ENV_URL = "KARATE_AGENT_URL";
    public static final String ENV_TOKEN = "KARATE_AGENT_TOKEN";
    public static final String ENV_MODE = "KARATE_AGENT_MODE";
    public static final String ENV_PARAMS = "KARATE_AGENT_PARAMS";

    public static final int DEFAULT_BATCH_SIZE = 50;
    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    public enum Mode {
        /** Accumulate and POST every {@link #DEFAULT_BATCH_SIZE} events; flush on SUITE_EXIT. */
        BATCH,
        /** Accumulate everything; POST once on SUITE_EXIT. */
        FINAL
    }

    private final String url;
    private final String token;
    private final Mode mode;
    private final int batchSize;
    private final String runId;
    private final String env;
    private final Map<String, Object> params;
    private final HttpClient httpClient;

    private final List<String> buffer = new ArrayList<>();
    private final Object bufferLock = new Object();

    /**
     * Construct a listener if {@code KARATE_AGENT_URL} is set in the environment,
     * otherwise return {@code null}. Callers MUST honour the null return to preserve the
     * zero-network guarantee: when the env var is unset, no HttpClient is created,
     * no startup line is logged, and no listener is registered.
     *
     * @param env the karate environment (may be null); included in the SUITE_ENTER payload
     * @return a configured listener, or {@code null} if the env var is unset/empty
     */
    public static HttpPostListener tryCreate(String env) {
        String envUrl = System.getenv(ENV_URL);
        if (envUrl == null || envUrl.isEmpty()) {
            return null;
        }
        Map<String, Object> params = parseParams(System.getenv(ENV_PARAMS));
        return new HttpPostListener(envUrl, env, DEFAULT_BATCH_SIZE, params);
    }

    HttpPostListener(String url, String env, int batchSize) {
        this(url, env, batchSize, null);
    }

    HttpPostListener(String url, String env, int batchSize, Map<String, Object> params) {
        this.url = stripTrailingSlash(url);
        this.token = System.getenv(ENV_TOKEN);
        this.mode = parseMode(System.getenv(ENV_MODE));
        this.batchSize = batchSize;
        this.runId = UUID.randomUUID().toString();
        this.env = env;
        this.params = params;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        // K30: single startup line announcing the destination
        logger.info("karate: posting run events to {} (mode={}, runId={}). disable with {}=",
                this.url, mode.name().toLowerCase(), runId, ENV_URL);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static Mode parseMode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Mode.BATCH;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("invalid {}={}, defaulting to batch", ENV_MODE, raw);
            return Mode.BATCH;
        }
    }

    /**
     * Parse the {@code KARATE_AGENT_PARAMS} env-var value as a JSON object. Returns the
     * parsed map verbatim — forward-compat with unknown keys. Returns {@code null} (with a
     * single WARN log line) when the value is null/empty, not a valid JSON object, or fails
     * to parse. The wire envelope omits the {@code params} field in that case; the listener
     * still activates.
     */
    static Map<String, Object> parseParams(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            Object parsed = Json.parseStrict(raw);
            if (!(parsed instanceof Map)) {
                logger.warn("invalid {}: expected JSON object, got {}", ENV_PARAMS,
                        parsed == null ? "null" : parsed.getClass().getSimpleName());
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        } catch (Exception e) {
            logger.warn("invalid {}: {}", ENV_PARAMS, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean onEvent(RunEvent event) {
        RunEventType type = event.getType();
        // Match JsonLinesEventWriter's filtering: step/HTTP events are too granular
        if (type == RunEventType.STEP_ENTER || type == RunEventType.STEP_EXIT
                || type == RunEventType.HTTP_ENTER || type == RunEventType.HTTP_EXIT) {
            return true;
        }

        try {
            String line = serialize(event);
            boolean shouldFlush;
            synchronized (bufferLock) {
                buffer.add(line);
                shouldFlush = (mode == Mode.BATCH && buffer.size() >= batchSize)
                        || type == RunEventType.SUITE_EXIT;
            }
            if (shouldFlush) {
                flush(type == RunEventType.SUITE_EXIT);
            }
        } catch (Exception e) {
            logger.warn("HttpPostListener: failed to enqueue event: {}", e.getMessage());
        }

        // Best-effort listener: never block the run
        return true;
    }

    /**
     * Serialize one event into a JSONL envelope. Public for unit-testing the wire shape;
     * not part of the listener API.
     */
    String serialize(RunEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("version", SCHEMA_VERSION);
        schema.put("dialect", SCHEMA_DIALECT);
        envelope.put("schema", schema);
        envelope.put("type", event.getType().name());
        envelope.put("timeStamp", event.getTimeStamp());
        envelope.put("threadId", getThreadId(event));

        Map<String, Object> data = event.toJson();
        if (event.getType() == RunEventType.SUITE_ENTER) {
            data.put("runId", runId);
            data.put("karateVersion", Globals.KARATE_VERSION);
            if (env != null && !env.isEmpty()) {
                data.put("env", env);
            }
            if (params != null) {
                data.put("params", params);
            }
        }
        envelope.put("data", data);
        return Json.stringifyStrict(envelope);
    }

    /** Mirrors {@link JsonLinesEventWriter#getThreadId(RunEvent)} so wire shapes agree. */
    private String getThreadId(RunEvent event) {
        RunEventType type = event.getType();
        if (type == RunEventType.SUITE_ENTER
                || type == RunEventType.SUITE_EXIT
                || type == RunEventType.PROGRESS) {
            return null;
        }
        Thread thread = Thread.currentThread();
        String name = thread.getName();
        if (name == null || name.isEmpty() || "main".equals(name)) {
            return "thread-" + thread.threadId();
        }
        return name;
    }

    private void flush(boolean isFinal) {
        List<String> toSend;
        synchronized (bufferLock) {
            if (buffer.isEmpty()) {
                return;
            }
            toSend = new ArrayList<>(buffer);
            buffer.clear();
        }
        String body = String.join("\n", toSend);
        String endpoint = isFinal
                ? url + "/api/runs/" + runId + "/complete"
                : url + "/api/runs/" + runId + "/events";
        post(endpoint, body);
    }

    private void post(String endpoint, String body) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (token != null && !token.isEmpty()) {
                req.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> res = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                logger.warn("HttpPostListener: POST {} returned {}: {}",
                        endpoint, res.statusCode(), truncate(res.body(), 200));
            }
        } catch (Exception e) {
            // Best-effort per AGENT_KARATE Open Q 1 resolution: the on-disk JSONL is the spool.
            logger.warn("HttpPostListener: POST {} failed: {}", endpoint, e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** @return the random per-run id used in POST URLs. */
    public String getRunId() {
        return runId;
    }

    /** @return the destination base URL (without trailing slash). */
    public String getUrl() {
        return url;
    }

    /** @return the active mode (batch / final). */
    public Mode getMode() {
        return mode;
    }

    /** Test-only: snapshot the current buffer size without flushing. */
    int bufferSizeForTest() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

}
