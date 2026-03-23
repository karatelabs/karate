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
package io.karatelabs.output;

import io.karatelabs.common.Json;
import io.karatelabs.common.ResourceType;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Globals;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.StepResult;
import io.karatelabs.core.SuiteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates HTML reports with inlined JSON data and Alpine.js client-side rendering.
 * <p>
 * This implementation uses a single-file HTML architecture where:
 * <ul>
 *   <li>JSON data is inlined in a {@code <script type="application/json">} tag</li>
 *   <li>Alpine.js renders the content client-side</li>
 *   <li>No server-side template rendering is required</li>
 * </ul>
 * <p>
 * Output structure:
 * <pre>
 * target/karate-reports/
 * ├── karate-results.jsonl      (streamed during execution, optional)
 * ├── index.html                (redirects to karate-summary.html)
 * ├── karate-summary.html       (summary page with tag filtering)
 * ├── features/
 * │   └── {feature.path}.html   (per-feature reports, dot-based naming)
 * └── res/
 *     ├── bootstrap.min.css
 *     ├── alpine.min.js
 *     └── karate-report.css
 * </pre>
 */
public final class HtmlReportWriter {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String RESOURCE_ROOT = "io/karatelabs/output/";

    private static final String[] STATIC_RESOURCES = {
            "bootstrap.min.css",
            "bootstrap.bundle.min.js",
            "alpine.min.js",
            "karate-report.css",
            "karate-report.js",
            "karate-logo.svg",
            "favicon.ico"
    };

    private static final String DATA_PLACEHOLDER = "/* KARATE_DATA */";

    private static final java.util.concurrent.atomic.AtomicInteger embedCounter =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private HtmlReportWriter() {
    }

    /**
     * Generate HTML reports from a JSON Lines file.
     * This is the primary entry point when using streaming via {@link JsonLinesReportListener}.
     *
     * @param jsonlPath the path to the JSON Lines file
     * @param outputDir  the directory to write reports
     */
    public static void writeFromJsonLines(Path jsonlPath, Path outputDir) {
        try {
            // Parse JSON Lines file
            JsonLinesData data = parseJsonLines(jsonlPath);

            // Generate reports
            writeReports(data.suiteData, data.features, outputDir);

            logger.debug("HTML report written to: {}", outputDir.resolve("karate-summary.html"));

        } catch (Exception e) {
            logger.warn("Failed to write HTML report from JSON Lines: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("HTML report error details", e);
            }
        }
    }

    /**
     * Generate HTML reports from a SuiteResult.
     * This is the backward-compatible entry point for direct invocation.
     *
     * @param result    the suite result to render
     * @param outputDir the directory to write reports
     * @param env       the karate environment (may be null)
     */
    public static void write(SuiteResult result, Path outputDir, String env) {
        try {
            // Build data structures from SuiteResult
            Map<String, Object> suiteData = buildSuiteData(result, env);
            List<Map<String, Object>> features = buildFeaturesList(result);

            // Generate reports
            writeReports(suiteData, features, outputDir);

            logger.debug("HTML report written to: {}", outputDir.resolve("karate-summary.html"));

        } catch (Exception e) {
            logger.warn("Failed to write HTML report: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("HTML report error details", e);
            }
        }
    }

    /**
     * Write a single feature HTML report.
     * Used by {@link HtmlReportListener} for async feature HTML generation.
     *
     * @param result    the feature result to render
     * @param outputDir the root output directory (features/ subdirectory will be used)
     */
    public static void writeFeatureHtml(FeatureResult result, Path outputDir) throws IOException {
        Path featuresDir = outputDir.resolve(HtmlReportListener.SUBFOLDER);
        Path embedsDir = outputDir.resolve("embeds");
        Files.createDirectories(featuresDir);

        // Write embed files first (sets fileName on each Embed)
        writeEmbedFiles(result, embedsDir);

        Map<String, Object> featureData = buildFeatureData(result);
        String template = loadTemplate("karate-feature.html");
        String html = inlineJson(template, featureData);

        String fileName = getFeatureFileName(featureData) + ".html";
        Files.writeString(featuresDir.resolve(fileName), html);
    }

    /**
     * Write embed files to the embeds/ directory.
     * Sets the fileName on each Embed for JSON serialization.
     */
    private static void writeEmbedFiles(FeatureResult result, Path embedsDir) throws IOException {
        boolean hasEmbeds = false;
        for (ScenarioResult sr : result.getScenarioResults()) {
            for (StepResult step : sr.getStepResults()) {
                if (step.getEmbeds() != null && !step.getEmbeds().isEmpty()) {
                    hasEmbeds = true;
                    break;
                }
            }
            if (hasEmbeds) break;
        }
        if (!hasEmbeds) {
            return;  // No embeds to write
        }

        Files.createDirectories(embedsDir);
        for (ScenarioResult sr : result.getScenarioResults()) {
            for (StepResult step : sr.getStepResults()) {
                if (step.getEmbeds() == null) continue;
                for (StepResult.Embed embed : step.getEmbeds()) {
                    String ext = getExtensionForMimeType(embed.getMimeType());
                    String baseName = embed.getName() != null
                            ? embed.getName().replaceAll("[^a-zA-Z0-9_-]", "_")
                            : "embed";
                    String fileName = String.format("%03d_%s.%s",
                            embedCounter.incrementAndGet(), baseName, ext);
                    Path filePath = embedsDir.resolve(fileName);
                    Files.write(filePath, embed.getData());
                    embed.setFileName(fileName);
                }
            }
        }
    }

    /**
     * Get file extension for a MIME type using ResourceType.
     */
    private static String getExtensionForMimeType(String mimeType) {
        ResourceType rt = ResourceType.fromContentType(mimeType);
        if (rt != null && rt.getExtension() != null) {
            return rt.getExtension();
        }
        // Fallback for unknown types
        return "bin";
    }

    /**
     * Write summary pages (summary, index) from feature maps.
     * Used by {@link HtmlReportListener} at suite end.
     *
     * @param features  the collected feature maps (from FeatureResult.toJson())
     * @param result    the suite result
     * @param outputDir the root output directory
     * @param env       the karate environment (may be null)
     */
    public static void writeSummaryPages(List<Map<String, Object>> features,
                                         SuiteResult result, Path outputDir, String env) throws IOException {
        // Build suite data from SuiteResult
        Map<String, Object> suiteData = new LinkedHashMap<>();
        suiteData.put("env", env);
        suiteData.put("reportDate", DATE_FORMAT.format(Instant.ofEpochMilli(result.getStartTime())));
        suiteData.put("karateVersion", Globals.KARATE_VERSION);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("feature_count", result.getFeatureCount());
        summary.put("feature_passed", result.getFeaturePassedCount());
        summary.put("feature_failed", result.getFeatureFailedCount());
        summary.put("scenario_count", result.getScenarioCount());
        summary.put("scenario_passed", result.getScenarioPassedCount());
        summary.put("scenario_failed", result.getScenarioFailedCount());
        summary.put("duration_millis", result.getDurationMillis());
        summary.put("status", result.isFailed() ? "failed" : "passed");
        suiteData.put("summary", summary);

        // Build feature summary list for display
        List<Map<String, Object>> featureSummaryList = buildFeatureSummaryList(features);

        // Write summary page
        Map<String, Object> summaryPageData = new LinkedHashMap<>(suiteData);
        summaryPageData.put("features", featureSummaryList);
        String summaryTemplate = loadTemplate("karate-summary.html");
        String summaryHtml = inlineJson(summaryTemplate, summaryPageData);
        Files.writeString(outputDir.resolve("karate-summary.html"), summaryHtml);

        // Write index redirect
        writeIndexRedirect(outputDir);
    }

    /**
     * Core report generation - same for both JSON Lines and SuiteResult paths.
     */
    private static void writeReports(Map<String, Object> suiteData,
                                     List<Map<String, Object>> features,
                                     Path outputDir) throws IOException {
        // Create directories
        Path featuresDir = outputDir.resolve(HtmlReportListener.SUBFOLDER);
        Path resDir = outputDir.resolve("res");
        Files.createDirectories(featuresDir);
        Files.createDirectories(resDir);

        // Copy static resources
        copyStaticResources(resDir);

        // Generate summary page
        writeSummaryHtml(suiteData, features, outputDir);

        // Generate feature pages
        for (Map<String, Object> feature : features) {
            writeFeatureHtml(feature, featuresDir);
        }

        // Generate index redirect
        writeIndexRedirect(outputDir);
    }

    // ========== HTML Generation ==========

    private static void writeSummaryHtml(Map<String, Object> suiteData,
                                         List<Map<String, Object>> features,
                                         Path outputDir) throws IOException {
        Map<String, Object> pageData = new LinkedHashMap<>(suiteData);
        pageData.put("features", buildFeatureSummaryList(features));

        String template = loadTemplate("karate-summary.html");
        String html = inlineJson(template, pageData);
        Files.writeString(outputDir.resolve("karate-summary.html"), html);
    }

    private static void writeFeatureHtml(Map<String, Object> feature, Path featuresDir) throws IOException {
        String template = loadTemplate("karate-feature.html");
        String html = inlineJson(template, feature);

        String fileName = getFeatureFileName(feature) + ".html";
        Files.writeString(featuresDir.resolve(fileName), html);
    }

    private static void writeIndexRedirect(Path outputDir) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta http-equiv="refresh" content="0; url=karate-summary.html">
                </head>
                <body>
                    <a href="karate-summary.html">Redirecting to Karate Summary Report...</a>
                </body>
                </html>
                """;
        Files.writeString(outputDir.resolve("index.html"), html);
    }

    /**
     * Inline JSON data into an HTML template.
     * Replaces the placeholder with the JSON string.
     */
    private static String inlineJson(String template, Object data) {
        String json = Json.of(data).toStringPretty();
        json = json.replace("</", "<\\/"); // prevent </script> in JSON from closing the script tag
        return template.replace(DATA_PLACEHOLDER, json);
    }

    // ========== Template and Resource Loading ==========

    private static String loadTemplate(String name) throws IOException {
        String path = RESOURCE_ROOT + name;
        try (InputStream is = HtmlReportWriter.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Template not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }
    }

    /**
     * Copy static resources (CSS, JS, images) to the res directory.
     * Made public for use by {@link HtmlReportListener}.
     *
     * @param resDir the res directory to copy resources to
     */
    public static void copyStaticResources(Path resDir) throws IOException {
        for (String resourceName : STATIC_RESOURCES) {
            String resourcePath = RESOURCE_ROOT + "res/" + resourceName;
            try (InputStream is = HtmlReportWriter.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    Files.copy(is, resDir.resolve(resourceName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    logger.debug("Static resource not found: {}", resourcePath);
                }
            }
        }
    }

    // ========== JSON Lines Parsing ==========

    private static JsonLinesData parseJsonLines(Path jsonlPath) throws IOException {
        JsonLinesData data = new JsonLinesData();
        List<String> lines = Files.readAllLines(jsonlPath, StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
            String eventType = (String) envelope.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) envelope.get("data");

            if ("SUITE_ENTER".equals(eventType) && eventData != null) {
                data.suiteData = buildSuiteDataFromEvent(envelope, eventData);
            } else if ("FEATURE_EXIT".equals(eventType) && eventData != null) {
                data.features.add(eventData);
            } else if ("SUITE_EXIT".equals(eventType) && eventData != null) {
                // Get summary from SUITE_EXIT data
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = (Map<String, Object>) eventData.get("summary");
                if (summary != null) {
                    data.suiteData.put("summary", summary);
                }
            }
        }

        // If summary wasn't set, build it from features
        if (!data.suiteData.containsKey("summary")) {
            Map<String, Object> summary = buildSummaryFromFeatures(data.features);
            data.suiteData.put("summary", summary);
        }

        return data;
    }

    private static Map<String, Object> buildSuiteDataFromEvent(Map<String, Object> envelope, Map<String, Object> eventData) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("env", eventData.get("env"));
        data.put("threads", eventData.get("threads"));
        data.put("karateVersion", eventData.get("version"));
        // Convert timestamp to date string
        Object ts = envelope.get("ts");
        if (ts instanceof Number) {
            data.put("reportDate", DATE_FORMAT.format(Instant.ofEpochMilli(((Number) ts).longValue())));
        }
        return data;
    }

    private static Map<String, Object> buildSummaryFromFeatures(List<Map<String, Object>> features) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int featurePassed = 0;
        int featureFailed = 0;
        int scenarioPassed = 0;
        int scenarioFailed = 0;
        long durationMillis = 0;

        for (Map<String, Object> feature : features) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) feature.get("result");
            if (result != null) {
                String status = (String) result.get("status");
                if ("passed".equals(status)) {
                    featurePassed++;
                } else {
                    featureFailed++;
                }
                scenarioPassed += ((Number) result.getOrDefault("passed_count", 0)).intValue();
                scenarioFailed += ((Number) result.getOrDefault("failed_count", 0)).intValue();
                durationMillis += ((Number) result.getOrDefault("duration_millis", 0)).longValue();
            }
        }

        summary.put("feature_count", features.size());
        summary.put("feature_passed", featurePassed);
        summary.put("feature_failed", featureFailed);
        summary.put("scenario_count", scenarioPassed + scenarioFailed);
        summary.put("scenario_passed", scenarioPassed);
        summary.put("scenario_failed", scenarioFailed);
        summary.put("duration_millis", durationMillis);
        summary.put("status", featureFailed > 0 ? "failed" : "passed");

        return summary;
    }

    // ========== Data Building from SuiteResult ==========

    private static Map<String, Object> buildSuiteData(SuiteResult result, String env) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("env", env);
        data.put("reportDate", DATE_FORMAT.format(Instant.ofEpochMilli(result.getStartTime())));
        data.put("karateVersion", Globals.KARATE_VERSION);

        // Summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("feature_count", result.getFeatureCount());
        summary.put("feature_passed", result.getFeaturePassedCount());
        summary.put("feature_failed", result.getFeatureFailedCount());
        summary.put("scenario_count", result.getScenarioCount());
        summary.put("scenario_passed", result.getScenarioPassedCount());
        summary.put("scenario_failed", result.getScenarioFailedCount());
        summary.put("duration_millis", result.getDurationMillis());
        summary.put("status", result.isFailed() ? "failed" : "passed");
        data.put("summary", summary);

        return data;
    }

    private static List<Map<String, Object>> buildFeaturesList(SuiteResult result) {
        List<Map<String, Object>> features = new ArrayList<>();
        for (FeatureResult fr : result.getFeatureResults()) {
            features.add(buildFeatureData(fr));
        }
        return features;
    }

    private static Map<String, Object> buildFeatureData(FeatureResult fr) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", fr.getDisplayName());
        data.put("name", fr.getFeature().getName());
        data.put("relativePath", fr.getFeature().getResource().getRelativePath());
        data.put("passed", fr.isPassed());
        data.put("durationMillis", fr.getDurationMillis());

        // Scenarios
        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (ScenarioResult sr : fr.getScenarioResults()) {
            scenarios.add(buildScenarioData(sr));
        }
        data.put("scenarios", scenarios);

        return data;
    }

    private static Map<String, Object> buildScenarioData(ScenarioResult sr) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", sr.getScenario().getName());
        data.put("line", sr.getScenario().getLine());
        data.put("passed", sr.isPassed());
        data.put("durationMillis", sr.getDurationMillis());
        data.put("startTime", sr.getStartTime());
        data.put("endTime", sr.getEndTime());

        // RefId and outline info for v1-style UI
        data.put("refId", sr.getScenario().getRefId());
        data.put("sectionIndex", sr.getScenario().getSection().getIndex() + 1);
        data.put("exampleIndex", sr.getScenario().getExampleIndex());
        data.put("isOutlineExample", sr.getScenario().isOutlineExample());

        if (sr.getThreadName() != null) {
            data.put("thread", sr.getThreadName());
        }

        // Tags
        var tags = sr.getScenario().getTags();
        if (tags != null && !tags.isEmpty()) {
            List<String> tagNames = new ArrayList<>();
            for (var tag : tags) {
                tagNames.add(tag.toString());
            }
            data.put("tags", tagNames);
        }

        // Steps
        List<Map<String, Object>> steps = new ArrayList<>();
        for (StepResult step : sr.getStepResults()) {
            steps.add(buildStepData(step));
        }
        data.put("steps", steps);

        // Error
        if (sr.isFailed() && sr.getFailureMessage() != null) {
            data.put("error", sr.getFailureMessage());
        }

        return data;
    }

    private static Map<String, Object> buildStepData(StepResult step) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (step.getStep() != null) {
            data.put("prefix", step.getStep().getPrefix());
            data.put("keyword", step.getStep().getKeyword());
            data.put("text", step.getStep().getText());
            data.put("line", step.getStep().getLine());
            // Include comments if present
            List<String> comments = step.getStep().getComments();
            if (comments != null && !comments.isEmpty()) {
                data.put("comments", comments);
            }
        } else {
            // Fake step (e.g., for @fail tag)
            data.put("prefix", "*");
            data.put("keyword", "*");
            data.put("text", step.getLog() != null ? step.getLog() : "");
            data.put("line", 0);
        }
        data.put("status", step.getStatus().name().toLowerCase());
        data.put("durationMillis", step.getDurationNanos() / 1_000_000);

        // Log indicator for UI
        boolean hasLogs = step.getLog() != null && !step.getLog().isEmpty();
        data.put("hasLogs", hasLogs);

        if (hasLogs) {
            data.put("logs", step.getLog());
        }

        if (step.getError() != null) {
            data.put("error", step.getError().getMessage());
        }

        // Embeds (images, HTML, etc.)
        boolean hasEmbeds = step.getEmbeds() != null && !step.getEmbeds().isEmpty();
        data.put("hasEmbeds", hasEmbeds);

        if (hasEmbeds) {
            List<Map<String, Object>> embedList = new ArrayList<>();
            for (StepResult.Embed embed : step.getEmbeds()) {
                embedList.add(embed.toMap());
            }
            data.put("embeds", embedList);
        }

        // Call results (from call steps) - V1 uses FeatureResult
        boolean hasCallResults = step.hasCallResults();
        data.put("hasCallResults", hasCallResults);

        if (hasCallResults) {
            List<Map<String, Object>> callList = new ArrayList<>();
            for (FeatureResult fr : step.getCallResults()) {
                callList.add(buildFeatureData(fr));
            }
            data.put("callResults", callList);
        }

        return data;
    }

    // ========== Data Building for Summary Page ==========

    private static List<Map<String, Object>> buildFeatureSummaryList(List<Map<String, Object>> features) {
        List<Map<String, Object>> summaryList = new ArrayList<>();
        for (Map<String, Object> feature : features) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name", feature.get("name"));
            summary.put("relativePath", feature.get("relativePath"));
            summary.put("fileName", getFeatureFileName(feature));

            summary.put("passed", Boolean.TRUE.equals(feature.get("passed")));
            summary.put("failed", Boolean.TRUE.equals(feature.get("failed")));
            Object duration = feature.get("durationMillis");
            summary.put("durationMillis", duration instanceof Number ? ((Number) duration).longValue() : 0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scenarioResults = (List<Map<String, Object>>) feature.get("scenarioResults");
            int total = scenarioResults != null ? scenarioResults.size() : 0;
            int passed = 0;
            int failed = 0;
            List<Map<String, Object>> scenarioSummaries = new ArrayList<>();
            if (scenarioResults != null) {
                for (Map<String, Object> s : scenarioResults) {
                    boolean scenarioPassed = Boolean.TRUE.equals(s.get("passed"));
                    if (scenarioPassed) {
                        passed++;
                    } else {
                        failed++;
                    }
                    Map<String, Object> scenarioSummary = new LinkedHashMap<>();
                    scenarioSummary.put("name", s.get("name"));
                    scenarioSummary.put("refId", s.get("refId"));
                    scenarioSummary.put("passed", scenarioPassed);
                    scenarioSummary.put("durationMillis", s.get("durationMillis"));
                    scenarioSummary.put("tags", s.get("tags"));
                    scenarioSummaries.add(scenarioSummary);
                }
            }
            summary.put("scenarioCount", total);
            summary.put("passedCount", passed);
            summary.put("failedCount", failed);
            summary.put("scenarios", scenarioSummaries);

            summaryList.add(summary);
        }
        return summaryList;
    }

    // ========== Utility ==========

    /**
     * Convert a feature path to a file name using dot-based flattening.
     * Example: "users/list.feature" → "users.list"
     */
    private static String getFeatureFileName(Map<String, Object> feature) {
        String path = (String) feature.get("relativePath");
        if (path == null || path.isEmpty()) {
            path = (String) feature.get("name");
        }
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        // Remove .feature extension and replace path separators with dots
        return path.replace(".feature", "")
                .replace("/", ".")
                .replace("\\", ".")
                .replaceAll("[^a-zA-Z0-9_.-]", "_")
                .toLowerCase();
    }

    /**
     * Internal data holder for JSON Lines parsing.
     */
    private static class JsonLinesData {
        Map<String, Object> suiteData = new LinkedHashMap<>();
        List<Map<String, Object>> features = new ArrayList<>();
    }

    // ========== Timeline Generation ==========

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /**
     * Write the timeline HTML page showing scenario execution across threads.
     * Used by {@link HtmlReportListener} at suite end.
     *
     * @param features    the collected feature maps (from FeatureResult.toMap())
     * @param result      the suite result
     * @param outputDir   the root output directory
     * @param env         the karate environment (may be null)
     * @param threadCount the number of threads used for parallel execution
     */
    public static void writeTimelineHtml(List<Map<String, Object>> features,
                                         SuiteResult result, Path outputDir, String env,
                                         int threadCount) throws IOException {
        Map<String, Object> timelineData = buildTimelineData(features, result, env, threadCount);
        String template = loadTemplate("karate-timeline.html");
        String html = inlineJson(template, timelineData);
        Files.writeString(outputDir.resolve("karate-timeline.html"), html);
    }

    /**
     * Build the timeline data structure for vis.js Timeline.
     * Uses canonical feature/scenario Maps from toMap() methods.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildTimelineData(List<Map<String, Object>> features,
                                                          SuiteResult result, String env,
                                                          int threadCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("env", env);
        data.put("reportDate", DATE_FORMAT.format(Instant.ofEpochMilli(result.getStartTime())));
        data.put("threads", threadCount);
        data.put("karateVersion", Globals.KARATE_VERSION);

        // Build groups (one per thread) and items (one per scenario)
        Map<String, Integer> threadToGroupId = new LinkedHashMap<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int itemId = 0;

        for (Map<String, Object> feature : features) {
            // Extract feature filename for timeline display
            String path = (String) feature.get("relativePath");
            String featureFileName = extractFileName(path);

            List<Map<String, Object>> scenarios = (List<Map<String, Object>>) feature.get("scenarioResults");
            if (scenarios == null) continue;

            for (Map<String, Object> scenario : scenarios) {
                String threadName = (String) scenario.get("executorName");
                if (threadName == null || threadName.isEmpty()) {
                    threadName = "main";
                }

                // Get or create group for this thread
                Integer groupId = threadToGroupId.get(threadName);
                if (groupId == null) {
                    groupId = threadToGroupId.size() + 1;
                    threadToGroupId.put(threadName, groupId);

                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("id", groupId);
                    group.put("content", threadName);
                    groups.add(group);
                }

                // Create item for this scenario
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", ++itemId);
                item.put("group", groupId);

                String refId = (String) scenario.get("refId");
                String content = featureFileName + refId;
                item.put("content", content);

                long startTime = ((Number) scenario.get("startTime")).longValue();
                long endTime = ((Number) scenario.get("endTime")).longValue() - 1; // -1 to avoid overlap in vis.js
                if (endTime < startTime) {
                    endTime = startTime; // Handle zero-duration scenarios
                }
                item.put("start", startTime);
                item.put("end", endTime);

                // Build tooltip title
                String startTimeStr = TIME_FORMAT.format(Instant.ofEpochMilli(startTime));
                long durationMs = endTime - startTime + 1; // +1 because we subtracted 1 from endTime above
                String title = content + " " + startTimeStr + " (" + durationMs + " ms)";
                String scenarioName = (String) scenario.get("name");
                if (scenarioName != null && !scenarioName.isEmpty()) {
                    title = title + " " + scenarioName;
                }
                item.put("title", title);

                // CSS class for pass/fail styling
                boolean passed = Boolean.TRUE.equals(scenario.get("passed"));
                item.put("className", passed ? "passed" : "failed");

                items.add(item);
            }
        }

        data.put("groups", groups);
        data.put("items", items);

        return data;
    }

    /**
     * Extract filename without extension from a path.
     * Example: "target/test-classes/features/users.feature" → "users"
     */
    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        // Remove directory path
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            path = path.substring(lastSlash + 1);
        }
        lastSlash = path.lastIndexOf('\\');
        if (lastSlash >= 0) {
            path = path.substring(lastSlash + 1);
        }
        // Remove .feature extension
        if (path.endsWith(".feature")) {
            path = path.substring(0, path.length() - 8);
        }
        return path;
    }

}

