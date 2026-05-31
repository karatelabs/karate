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

import io.karatelabs.gherkin.Step;

import io.karatelabs.output.Console;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StepResult {

    public enum Status {
        PASSED, FAILED, SKIPPED
    }

    private final Step step;
    private final Status status;
    private final long startTime;
    private final long durationNanos;
    private final Throwable error;
    private String log;
    private List<Embed> embeds;
    private List<FeatureResult> callResults;  // For call steps - called feature results (V1 style)
    private String hookName;  // "beforeScenario" / "afterScenario" when this represents a lifecycle hook

    private StepResult(Step step, Status status, long startTime, long durationNanos, Throwable error) {
        this.step = step;
        this.status = status;
        this.startTime = startTime;
        this.durationNanos = durationNanos;
        this.error = error;
    }

    public static StepResult passed(Step step, long startTime, long durationNanos) {
        return new StepResult(step, Status.PASSED, startTime, durationNanos, null);
    }

    public static StepResult failed(Step step, long startTime, long durationNanos, Throwable error) {
        return new StepResult(step, Status.FAILED, startTime, durationNanos, error);
    }

    public static StepResult skipped(Step step, long startTime) {
        return new StepResult(step, Status.SKIPPED, startTime, 0, null);
    }

    /**
     * Create a fake success step result (for @fail tag handling).
     */
    public static StepResult fakeSuccess(String message, long startTime) {
        StepResult sr = new StepResult(null, Status.PASSED, startTime, 0, null);
        sr.log = message;
        return sr;
    }

    /**
     * Create a fake failure step result (for @fail tag handling).
     */
    public static StepResult fakeFailure(String message, long startTime, Throwable error) {
        StepResult sr = new StepResult(null, Status.FAILED, startTime, 0, error);
        sr.log = message;
        return sr;
    }

    /**
     * Create a synthetic step result representing a beforeScenario / afterScenario hook.
     * The hook name becomes the step text; nested karate.call() results and logs are set
     * by the caller via the existing setters.
     */
    public static StepResult hook(String hookName, Status status, long startTime,
                                  long durationNanos, Throwable error) {
        StepResult sr = new StepResult(null, status, startTime, durationNanos, error);
        sr.hookName = hookName;
        return sr;
    }

    public Step getStep() {
        return step;
    }

    public Status getStatus() {
        return status;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public double getDurationMillis() {
        return durationNanos / 1_000_000.0;
    }

    public Throwable getError() {
        return error;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public void appendLog(String message) {
        if (log == null || log.isEmpty()) {
            log = message + "\n";
        } else {
            log = log + message + "\n";
        }
    }

    public List<Embed> getEmbeds() {
        return embeds;
    }

    public void addEmbed(Embed embed) {
        if (embeds == null) {
            embeds = new ArrayList<>();
        }
        embeds.add(embed);
    }

    public List<FeatureResult> getCallResults() {
        return callResults;
    }

    public void setCallResults(List<FeatureResult> callResults) {
        this.callResults = callResults;
    }

    public boolean hasCallResults() {
        return callResults != null && !callResults.isEmpty();
    }

    public String getHookName() {
        return hookName;
    }

    public boolean isHook() {
        return hookName != null;
    }

    public boolean isPassed() {
        return status == Status.PASSED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public String getErrorMessage() {
        return error != null ? error.getMessage() : null;
    }

    /**
     * Convert to JSON format (V1 nested structure).
     * Uses nested `step` and `result` objects for V1 compatibility.
     */
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();

        // Step object
        if (step != null) {
            map.put("step", step.toJson());
        } else {
            // Fake step (e.g., for @fail tag, or a lifecycle hook)
            Map<String, Object> fakeStep = new LinkedHashMap<>();
            fakeStep.put("index", -1);
            fakeStep.put("line", 0);
            fakeStep.put("prefix", "*");
            if (hookName != null) {
                fakeStep.put("text", hookName);
                fakeStep.put("hook", hookName);
            } else {
                fakeStep.put("text", log != null ? log : "");
            }
            map.put("step", fakeStep);
        }

        // Result object
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("status", status.name().toLowerCase());
        resultMap.put("millis", durationNanos / 1_000_000);
        resultMap.put("nanos", durationNanos);
        resultMap.put("startTime", startTime);
        long endTime = startTime + (durationNanos / 1_000_000);
        resultMap.put("endTime", endTime);
        if (error != null) {
            resultMap.put("errorMessage", error.getMessage());
        }
        map.put("result", resultMap);

        // Top-level optional fields
        if (log != null && !log.isEmpty()) {
            map.put("stepLog", Console.stripAnsi(log));
        }
        if (embeds != null && !embeds.isEmpty()) {
            List<Map<String, Object>> embedList = new ArrayList<>();
            for (Embed embed : embeds) {
                embedList.add(embed.toMap());
            }
            map.put("embeds", embedList);
        }
        if (callResults != null && !callResults.isEmpty()) {
            List<Map<String, Object>> callResultsList = new ArrayList<>();
            for (FeatureResult fr : callResults) {
                callResultsList.add(fr.toJson());
            }
            map.put("callResults", callResultsList);
        }

        return map;
    }

    /**
     * A named report embed, generalised to one-or-more {@link Part}s plus optional
     * {@code meta} (see EXT.md § Embeds). A legacy single-asset embed (screenshot,
     * {@code doc} HTML, {@code karate.embed}) is one {@code "primary"} part carrying
     * inline bytes; a multi-asset embed (e.g. image-comparison: baseline / current /
     * diff) carries several parts, typically url-based.
     *
     * <p>Wire shape is uniform: {@code {name, parts:[{role, mime, data|url|file}], meta}}.
     * There is no legacy flat ({@code mime_type}) form — v2 has no released embed
     * consumers, so this is a clean break. The legacy constructor + accessors remain
     * only as ergonomic helpers for core code that deals in single-asset embeds.</p>
     */
    public static class Embed {

        private final String name;
        private final List<Part> parts;
        private final Map<String, Object> meta;

        /** Legacy single inline-bytes embed — wrapped as one {@code "primary"} part. */
        public Embed(byte[] data, String mimeType, String name) {
            this.name = name;
            this.parts = new ArrayList<>();
            this.parts.add(new Part("primary", mimeType, data));
            this.meta = null;
        }

        /** Multi-asset embed (e.g. image-comparison). {@code parts} must be non-empty. */
        public Embed(String name, List<Part> parts, Map<String, Object> meta) {
            this.name = name;
            this.parts = parts != null ? parts : new ArrayList<>();
            this.meta = meta;
        }

        private Part primary() {
            return parts.isEmpty() ? null : parts.get(0);
        }

        // ----- legacy accessors: delegate to the primary part (single-asset embeds) -----

        public byte[] getData() {
            Part p = primary();
            return p == null ? null : p.data;
        }

        public String getMimeType() {
            Part p = primary();
            return p == null ? null : p.mime;
        }

        public String getName() {
            return name;
        }

        /** Set by HtmlReportWriter after writing the primary part's bytes to disk. */
        public void setFileName(String fileName) {
            Part p = primary();
            if (p != null) {
                p.fileName = fileName;
            }
        }

        public String getFileName() {
            Part p = primary();
            return p == null ? null : p.fileName;
        }

        // ----- multi-part accessors -----

        public List<Part> getParts() {
            return parts;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (name != null) {
                map.put("name", name);
            }
            List<Map<String, Object>> partList = new ArrayList<>();
            for (Part p : parts) {
                partList.add(p.toMap());
            }
            map.put("parts", partList);
            if (meta != null && !meta.isEmpty()) {
                map.put("meta", meta);
            }
            return map;
        }
    }

    /**
     * One asset within an {@link Embed}: either inline {@code data} (core writes the
     * file and sets {@code fileName}) or a report-relative {@code url} (the ext wrote
     * the asset itself, e.g. under {@code ext/image/assets/}).
     */
    public static class Part {
        private final String role;       // "primary" | "baseline" | "current" | "diff" | ...
        private final String mime;
        private final byte[] data;       // inline bytes (legacy / core-written)
        private final String url;        // report-relative URL (ext-written asset)
        private String fileName;         // set by HtmlReportWriter when inline bytes are written to disk

        /** Inline-bytes part — core writes the file and sets {@link #fileName}. */
        public Part(String role, String mime, byte[] data) {
            this.role = role;
            this.mime = mime;
            this.data = data;
            this.url = null;
        }

        /** URL part — the ext has already written the asset to disk. */
        public Part(String role, String mime, String url) {
            this.role = role;
            this.mime = mime;
            this.url = url;
            this.data = null;
        }

        public String getRole() {
            return role;
        }

        public String getMime() {
            return mime;
        }

        public byte[] getData() {
            return data;
        }

        public String getUrl() {
            return url;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", role);
            map.put("mime", mime);
            if (fileName != null) {
                map.put("file", fileName);
            } else if (url != null) {
                map.put("url", url);
            } else if (data != null) {
                map.put("data", java.util.Base64.getEncoder().encodeToString(data));
            }
            return map;
        }
    }

}
