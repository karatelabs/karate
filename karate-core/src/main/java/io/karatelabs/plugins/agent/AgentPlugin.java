/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.plugins.agent;

import io.karatelabs.common.Json;
import io.karatelabs.core.Globals;
import io.karatelabs.core.Plugin;
import io.karatelabs.core.RunEvent;
import io.karatelabs.core.RunEventType;
import io.karatelabs.core.Suite;
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
 * Baked-in karate-agent client plugin — registered from {@code karate-boot.js} via
 * {@code boot.plugin('agent')}. POSTs run events as JSONL to a remote receiver
 * (typically the karate-agent dashboard, but the wire is open).
 *
 * <p>Per K44 ships in karate-core itself (OSS, free, opt-in only). Per K30 the
 * K30 "no surprise network egress" guarantee holds: no HttpClient is constructed,
 * no startup log line fires, no listener registration happens unless
 * {@code boot.plugin('agent')} is invoked AND a {@code .url} is set.</p>
 *
 * <p>Configuration in karate-boot.js:</p>
 * <pre>
 * const agent = boot.plugin('agent');
 * agent.url = boot.sysenv('AGENT_URL') || 'http://localhost:4444';
 * agent.mode = boot.env === 'ci' ? 'batch' : 'final';
 * agent.token = boot.sysenv('AGENT_TOKEN');
 * agent.params = { dev: boot.env !== 'ci' };
 * </pre>
 *
 * <p>Behaviour is best-effort: POST failures log a WARN and are dropped on the floor.
 * The on-disk JSONL stream ({@code karate-json/karate-events.jsonl}, opt-in via
 * {@code Runner.Builder#outputJsonLines(boolean)}) remains the source of truth —
 * operators can reconcile a missed dashboard by re-ingesting the on-disk file.</p>
 *
 * <p>Endpoints called (paths relative to {@code .url}):</p>
 * <ul>
 *   <li>{@code POST /api/runs/{runId}/events}   — batched events, body is JSONL</li>
 *   <li>{@code POST /api/runs/{runId}/complete} — final flush on SUITE_EXIT</li>
 * </ul>
 *
 * <p>Wire envelope (one per line):</p>
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
public class AgentPlugin implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final int SCHEMA_VERSION = 1;
    public static final String SCHEMA_DIALECT = "karate-v2";

    public static final int DEFAULT_BATCH_SIZE = 50;
    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    public enum Mode {
        /** Accumulate and POST every {@link #DEFAULT_BATCH_SIZE} events; flush on SUITE_EXIT. */
        BATCH,
        /** Accumulate everything; POST once on SUITE_EXIT. */
        FINAL
    }

    private String url;
    private String token;
    private Mode mode = Mode.BATCH;
    private Map<String, Object> params;
    private String project;

    private String env;
    private String runId;
    private HttpClient httpClient;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private boolean activated;

    private final List<String> buffer = new ArrayList<>();
    private final Object bufferLock = new Object();

    public String getUrl() {
        return url;
    }

    /**
     * Setter for {@code agent.url = '...'}. Strips trailing slash. Empty/null leaves
     * the plugin inert — no listener registration, no activation, no startup log line.
     */
    public void setUrl(String url) {
        if (url == null || url.isBlank()) {
            this.url = null;
            return;
        }
        this.url = stripTrailingSlash(url);
    }

    public String getMode() {
        return mode == null ? null : mode.name().toLowerCase();
    }

    /** Setter for {@code agent.mode = 'batch'} or {@code 'final'}. Fails fast on bad value. */
    public void setMode(String mode) {
        if (mode == null || mode.isBlank()) {
            this.mode = Mode.BATCH;
            return;
        }
        try {
            this.mode = Mode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "agent.mode: expected 'batch' or 'final', got: " + mode);
        }
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Setter for {@code agent.params = { dev: true, ... }}. The map is attached
     * verbatim to {@code SUITE_ENTER.data.params}. V0 schema {@code {"dev": bool}}
     * marks the run as developer-loop so the aggregator can hide it from the default
     * Runs rail. Forward-compatible: receivers persist unknown keys untouched.
     */
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getProject() {
        return project;
    }

    /**
     * Setter for {@code agent.project = '<slug>'}. The slug is attached to
     * {@code SUITE_ENTER.data.project}; the receiver auto-creates the project
     * (or resolves an existing slug / alias) and binds the run to it. Null /
     * blank leaves the run unbound — it still surfaces in the god-view "All
     * Runs" list but doesn't roll up under any project.
     */
    public void setProject(String project) {
        this.project = (project == null || project.isBlank()) ? null : project.trim();
    }

    /** Test-only override; production always uses {@link #DEFAULT_BATCH_SIZE}. */
    void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public void onBoot(Suite suite) {
        if (suite != null) {
            this.env = suite.env;
        }
    }

    @Override
    public boolean onEvent(RunEvent event) {
        if (url == null) {
            // Not configured — silently inert per K30.
            return true;
        }
        if (!activated) {
            activate();
        }

        RunEventType type = event.getType();
        // Match JsonLinesEventWriter's filtering: step/HTTP events are too granular.
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
            logger.warn("AgentPlugin: failed to enqueue event: {}", e.getMessage());
        }

        return true;
    }

    @Override
    public Map<String, Object> getManifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", Globals.KARATE_VERSION);
        if (url != null) m.put("url", url);
        if (mode != null) m.put("mode", mode.name().toLowerCase());
        return m;
    }

    /**
     * Serialize one event into a JSONL envelope. Package-visible for unit-testing the
     * wire shape; not part of the public plugin API.
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
            if (project != null) {
                data.put("project", project);
            }
        }
        envelope.put("data", data);
        return Json.stringifyStrict(envelope);
    }

    /** Mirrors {@link io.karatelabs.core.JsonLinesEventWriter}'s thread id rule so wire shapes agree. */
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

    private void activate() {
        this.runId = UUID.randomUUID().toString();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        // K30: single startup line announcing the destination.
        logger.info("karate: posting run events to {} (mode={}, runId={})",
                url, mode.name().toLowerCase(), runId);
        activated = true;
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
                logger.warn("AgentPlugin: POST {} returned {}: {}",
                        endpoint, res.statusCode(), truncate(res.body(), 200));
            }
        } catch (Exception e) {
            // Best-effort: the on-disk JSONL is the spool.
            logger.warn("AgentPlugin: POST {} failed: {}", endpoint, e.getMessage());
        }
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** @return the random per-run id used in POST URLs (null until {@link #activate()} fires). */
    public String getRunId() {
        return runId;
    }

    /** Test-only: snapshot the current buffer size without flushing. */
    int bufferSizeForTest() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

    /** Test-only: whether {@link #activate()} has fired (first event seen with url set). */
    boolean isActivatedForTest() {
        return activated;
    }
}
