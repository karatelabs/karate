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
import io.karatelabs.core.ReportAssets;
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
 * ├── index.html                (redirects to karate-summary.html)
 * ├── karate-summary.html       (summary page with tag filtering)
 * ├── karate-timeline.html      (parallel-execution Gantt)
 * ├── feature-html/
 * │   └── {feature.path}.html   (per-feature reports, dot-based naming)
 * ├── karate-json/karate-events.jsonl  (streamed during execution, opt-in)
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
            "alpine.min.js",
            "karate-report.css",
            "karate-report.js",
            "karate-logo.svg",
            "favicon.ico"
    };

    private static final String DATA_PLACEHOLDER = "/* KARATE_DATA */";
    // Heroicons inline-SVG sprite splice (see _icons.svg). One of three string-replace
    // placeholders below; the placeholder model is the report's only server-side render seam.
    private static final String ICONS_PLACEHOLDER = "<!-- KARATE_ICONS -->";
    // Phase 2 (§3.3): per-ext <script>/<link> asset tags spliced into <head>.
    private static final String EXTS_PLACEHOLDER = "<!-- KARATE_EXTS -->";
    // Phase 2 (§3.2 nav.pages): per-ext <a> tabs spliced into the topbar nav.
    private static final String NAV_PLACEHOLDER = "<!-- KARATE_NAV -->";

    // Lazily-loaded sprite contents. Loaded once per JVM (sprite is small and
    // identical for every template + every run).
    private static volatile String iconsSprite;

    private static final java.util.concurrent.atomic.AtomicInteger embedCounter =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private HtmlReportWriter() {
    }

    /**
     * Reset the embed file sequence. Called at suite start so embed file names
     * (e.g. {@code 001_screenshot.png}) restart from 1 for each run instead of
     * accumulating across runs in the same JVM.
     */
    public static void resetEmbedCounter() {
        embedCounter.set(0);
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
        writeFeatureHtml(result, outputDir, java.util.Collections.emptyMap());
    }

    public static void writeFeatureHtml(FeatureResult result, Path outputDir,
                                        Map<String, ReportAssets> reportAssets) throws IOException {
        Path featuresDir = outputDir.resolve(HtmlReportListener.SUBFOLDER);
        Path embedsDir = outputDir.resolve("embeds");
        Files.createDirectories(featuresDir);

        // Write embed files first (sets fileName on each Embed)
        writeEmbedFiles(result, embedsDir);

        Map<String, Object> featureData = buildFeatureData(result);
        String template = loadTemplate("karate-feature.html");
        // feature pages live under feature-html/, so ext refs need the "../" prefix
        String html = inlineJson(template, featureData, reportAssets, "../");

        String fileName = getFeatureFileName(featureData) + ".html";
        Files.writeString(featuresDir.resolve(fileName), html);
    }

    private static boolean hasEmbeds(FeatureResult result) {
        for (ScenarioResult sr : result.getScenarioResults()) {
            if (sr.isReportDisabled()) continue;
            for (StepResult step : sr.getStepResults()) {
                if (step.getEmbeds() != null && !step.getEmbeds().isEmpty()) {
                    return true;
                }
                if (step.hasCallResults()) {
                    for (FeatureResult fr : step.getCallResults()) {
                        if (hasEmbeds(fr)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void writeEmbedFile(StepResult.Embed embed, Path embedsDir) throws IOException {
        String baseName = embed.getName() != null
                ? embed.getName().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "embed";
        List<StepResult.Part> parts = embed.getParts();
        boolean multi = parts.size() > 1;
        for (StepResult.Part part : parts) {
            // url-only parts (ext-written assets) are already on disk; nothing to copy
            if (part.getData() == null) {
                continue;
            }
            String ext = getExtensionForMimeType(part.getMime());
            String suffix = multi && part.getRole() != null ? "_" + part.getRole() : "";
            String fileName = String.format("%03d_%s%s.%s",
                    embedCounter.incrementAndGet(), baseName, suffix, ext);
            Files.write(embedsDir.resolve(fileName), part.getData());
            part.setFileName(fileName);
        }
    }

    /**
     * Write embed files to the embeds/ directory.
     * Sets the fileName on each Embed for JSON serialization.
     */
    private static void writeEmbedFiles(FeatureResult result, Path embedsDir) throws IOException {
        if (!hasEmbeds(result)) {
            return;  // No embeds to write
        }

        Files.createDirectories(embedsDir);
        for (ScenarioResult sr : result.getScenarioResults()) {
            // @report=false scenarios suppress all step detail in toJson(); skip
            // embed extraction so screenshots / attachments don't leak to disk either.
            if (sr.isReportDisabled()) continue;
            for (StepResult step : sr.getStepResults()) {
                if (step.hasCallResults()) {
                    for (FeatureResult fr : step.getCallResults()) {
                        writeEmbedFiles(fr, embedsDir);
                    }
                }
                if (step.getEmbeds() == null) continue;
                for (StepResult.Embed embed : step.getEmbeds()) {
                    writeEmbedFile(embed, embedsDir);
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
        writeSummaryPages(features, result, outputDir, env, java.util.Collections.emptyMap());
    }

    public static void writeSummaryPages(List<Map<String, Object>> features,
                                         SuiteResult result, Path outputDir, String env,
                                         Map<String, ReportAssets> reportAssets) throws IOException {
        // Copy ext assets once at suite end — feature/summary/timeline pages all
        // reference ext/<name>/... so the files must be on disk before the report is viewed.
        copyExtAssets(reportAssets, outputDir);

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
        summary.put("scenario_skipped", result.getScenarioSkippedCount());
        summary.put("duration_millis", result.getDurationMillis());
        summary.put("status", result.isFailed() ? "failed" : "passed");
        suiteData.put("summary", summary);

        // Build feature summary list for display
        List<Map<String, Object>> featureSummaryList = buildFeatureSummaryList(features);

        // Write summary page
        Map<String, Object> summaryPageData = new LinkedHashMap<>(suiteData);
        summaryPageData.put("features", featureSummaryList);
        String summaryTemplate = loadTemplate("karate-summary.html");
        String summaryHtml = inlineJson(summaryTemplate, summaryPageData, reportAssets, "");
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
     * Inline JSON data into an HTML template, splicing the icons sprite (and, via
     * the overload, the per-ext asset + nav blocks). The templates are static HTML with
     * string-replace placeholders (not a template engine) — `HtmlReportWriter` is the
     * single string-replace point for every template. This no-ext overload is used by the direct
     * {@link #write(SuiteResult, Path, String)} path, which has no registered exts.
     */
    private static String inlineJson(String template, Object data) {
        return inlineJson(template, data, null, "");
    }

    /**
     * Inline JSON + splice the per-ext asset ({@link #EXTS_PLACEHOLDER}) and
     * nav-tab ({@link #NAV_PLACEHOLDER}) fragments assembled from
     * {@code Suite.getReportAssets()}. {@code relPrefix} matches the page's depth —
     * {@code ""} for root pages (summary, timeline), {@code "../"} for feature
     * pages under {@code feature-html/} — mirroring how templates reference
     * {@code res/}. Both ext fragments share the same prefix so their URLs agree.
     */
    private static String inlineJson(String template, Object data,
                                     Map<String, ReportAssets> reportAssets, String relPrefix) {
        String json = Json.of(data).toStringPretty();
        json = json.replace("</", "<\\/"); // prevent </script> in JSON from closing the script tag
        String html = template.replace(DATA_PLACEHOLDER, json);
        html = html.replace(ICONS_PLACEHOLDER, loadIconsSprite());
        html = html.replace(EXTS_PLACEHOLDER, buildExtsHtml(reportAssets, relPrefix));
        html = html.replace(NAV_PLACEHOLDER, buildNavHtml(reportAssets, relPrefix));
        return html;
    }

    /**
     * Assemble the {@code <!-- KARATE_EXTS -->} fragment: one {@code <script defer>}
     * (and optional {@code <link>}) per registered ext, ordered as registered in
     * {@code karate-boot.js}. {@code relPrefix} matches the page's depth — {@code ""}
     * for root pages (summary, timeline), {@code "../"} for feature pages under
     * {@code feature-html/} — mirroring how the templates reference {@code res/}.
     */
    private static String buildExtsHtml(Map<String, ReportAssets> reportAssets, String relPrefix) {
        if (reportAssets == null || reportAssets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ReportAssets assets : reportAssets.values()) {
            String base = relPrefix + "ext/" + assets.name() + "/";
            String cssHref = assets.cssHref();
            if (cssHref != null) {
                sb.append("<link rel=\"stylesheet\" href=\"").append(base).append(cssHref).append("\">\n");
            }
            sb.append("<script src=\"").append(base).append(assets.jsHref()).append("\" defer></script>\n");
        }
        return sb.toString();
    }

    /**
     * Assemble the {@code <!-- KARATE_NAV -->} fragment: one topbar {@code <a>} tab
     * per ext {@code page("nav.pages", title, href)} contribution, in registration
     * order. The tab links to {@code <relPrefix>ext/<name>/<href>} — the same web
     * path {@link ReportAssets#copyTo(Path)} writes the page to. Tabs carry the
     * same utility classes as the built-in Summary/Timeline links so they read as
     * peers. Non-{@code nav.pages} slots are ignored here (no other slot renders a
     * tab); page titles are HTML-escaped since they are ext-author strings.
     */
    private static String buildNavHtml(Map<String, ReportAssets> reportAssets, String relPrefix) {
        if (reportAssets == null || reportAssets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ReportAssets assets : reportAssets.values()) {
            for (ReportAssets.Page page : assets.pages()) {
                if (!"nav.pages".equals(page.slot()) || page.href() == null) {
                    continue;
                }
                String href = relPrefix + "ext/" + assets.name() + "/" + page.href();
                sb.append("<a class=\"text-sm text-slate-600 hover:text-slate-900 dark:text-white/80 dark:hover:text-white\" href=\"")
                        .append(escapeHtml(href)).append("\">").append(escapeHtml(page.title())).append("</a>\n");
            }
        }
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Copy each registered ext's declared assets into {@code outputDir/ext/<name>/}.
     * Best-effort per ext: a copy failure for one ext is logged and the rest proceed.
     */
    private static void copyExtAssets(Map<String, ReportAssets> reportAssets, Path outputDir) {
        if (reportAssets == null || reportAssets.isEmpty()) {
            return;
        }
        for (ReportAssets assets : reportAssets.values()) {
            try {
                assets.copyTo(outputDir.resolve("ext").resolve(assets.name()));
            } catch (Exception e) {
                logger.warn("Failed to copy ext assets for '{}': {}", assets.name(), e.getMessage());
            }
        }
    }

    // ========== Template and Resource Loading ==========

    private static String loadTemplate(String name) throws IOException {
        return loadClasspathResource(RESOURCE_ROOT + name, true);
    }

    /**
     * Read a UTF-8 classpath resource. When {@code required} is true a missing
     * resource raises; otherwise the empty string is returned.
     */
    private static String loadClasspathResource(String path, boolean required) throws IOException {
        try (InputStream is = HtmlReportWriter.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                if (required) {
                    throw new IOException("Resource not found: " + path);
                }
                return "";
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
     * Load the icons sprite once per JVM and cache it. Missing sprite is logged
     * at WARN and the splice yields the empty string — the report still renders,
     * just without icons. See {@code _icons.svg} (Heroicons v2, MIT).
     */
    private static String loadIconsSprite() {
        String cached = iconsSprite;
        if (cached != null) {
            return cached;
        }
        try {
            cached = loadClasspathResource(RESOURCE_ROOT + "_icons.svg", false);
        } catch (IOException e) {
            logger.warn("Failed to load icons sprite: {}", e.getMessage());
            cached = "";
        }
        iconsSprite = cached;
        return cached;
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
        // Timestamp + karate version on every feature page so the hero + footer
        // render the same provenance line as the summary. Falls back to the
        // earliest scenario start time if FeatureResult itself has none.
        long startTime = fr.getStartTime();
        if (startTime == 0) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                long st = sr.getStartTime();
                if (st > 0 && (startTime == 0 || st < startTime)) startTime = st;
            }
        }
        if (startTime > 0) {
            data.put("reportDate", DATE_FORMAT.format(Instant.ofEpochMilli(startTime)));
        }
        data.put("karateVersion", Globals.KARATE_VERSION);

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

        // Tags (plus synthetic @skipped when the scenario was aborted / never ran)
        var tags = sr.getScenario().getTags();
        List<String> tagNames = new ArrayList<>();
        if (tags != null) {
            for (var tag : tags) {
                tagNames.add(tag.toString());
            }
        }
        if (sr.isSkipped() && !tagNames.contains("@skipped")) {
            tagNames.add("@skipped");
        }
        if (!tagNames.isEmpty()) {
            data.put("tags", tagNames);
        }
        data.put("skipped", sr.isSkipped());

        // @report=false: render the scenario row with status + counts but suppress
        // step detail entirely. Failures show only a redacted message so secrets
        // don't leak into the inlined JSON of the published HTML report.
        if (sr.isReportDisabled()) {
            data.put("reportDisabled", true);
            data.put("steps", new ArrayList<Map<String, Object>>());
            if (sr.isFailed()) {
                data.put("error", ScenarioResult.SUPPRESSED_FAILURE_MESSAGE);
            }
            return data;
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
            // Fake step (e.g., for @fail tag, or a lifecycle hook)
            data.put("prefix", "*");
            if (step.isHook()) {
                // Hook step renders as `* beforeScenario` — the hook name *is* the keyword,
                // so leave the keyword slot empty to avoid the double `* *` prefix.
                data.put("keyword", "");
                data.put("text", step.getHookName());
                data.put("hook", step.getHookName());
            } else {
                data.put("keyword", "*");
                data.put("text", step.getLog() != null ? step.getLog() : "");
            }
            data.put("line", 0);
        }
        data.put("status", step.getStatus().name().toLowerCase());
        data.put("durationMillis", step.getDurationNanos() / 1_000_000);

        // Log indicator for UI
        boolean hasLogs = step.getLog() != null && !step.getLog().isEmpty();
        data.put("hasLogs", hasLogs);

        if (hasLogs) {
            data.put("logs", Console.stripAnsi(step.getLog()));
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
            int skipped = 0;
            List<Map<String, Object>> scenarioSummaries = new ArrayList<>();
            if (scenarioResults != null) {
                for (Map<String, Object> s : scenarioResults) {
                    boolean scenarioPassed = Boolean.TRUE.equals(s.get("passed"));
                    boolean scenarioSkipped = Boolean.TRUE.equals(s.get("skipped"));
                    if (scenarioPassed) {
                        passed++;
                    } else {
                        failed++;
                    }
                    if (scenarioSkipped) {
                        skipped++;
                    }
                    Map<String, Object> scenarioSummary = new LinkedHashMap<>();
                    scenarioSummary.put("name", s.get("name"));
                    scenarioSummary.put("refId", s.get("refId"));
                    scenarioSummary.put("passed", scenarioPassed);
                    scenarioSummary.put("skipped", scenarioSkipped);
                    scenarioSummary.put("durationMillis", s.get("durationMillis"));
                    scenarioSummary.put("tags", s.get("tags"));
                    scenarioSummaries.add(scenarioSummary);
                }
            }
            summary.put("scenarioCount", total);
            summary.put("passedCount", passed);
            summary.put("failedCount", failed);
            summary.put("skippedCount", skipped);
            int executed = passed + failed;
            summary.put("passedRate", executed == 0 ? null : (int) Math.round((passed * 100.0) / executed));
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
        writeTimelineHtml(features, result, outputDir, env, threadCount, java.util.Collections.emptyMap());
    }

    public static void writeTimelineHtml(List<Map<String, Object>> features,
                                         SuiteResult result, Path outputDir, String env,
                                         int threadCount, Map<String, ReportAssets> reportAssets) throws IOException {
        Map<String, Object> timelineData = buildTimelineData(features, result, env, threadCount);
        String template = loadTemplate("karate-timeline.html");
        String html = inlineJson(template, timelineData, reportAssets, "");
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

        // Summary (mirrors the shape on summary page) — drives the hero status pill
        // and the Speedup/Wall-clock cards.
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("feature_count", result.getFeatureCount());
        summary.put("feature_passed", result.getFeaturePassedCount());
        summary.put("feature_failed", result.getFeatureFailedCount());
        summary.put("scenario_count", result.getScenarioCount());
        summary.put("scenario_passed", result.getScenarioPassedCount());
        summary.put("scenario_failed", result.getScenarioFailedCount());
        summary.put("scenario_skipped", result.getScenarioSkippedCount());
        summary.put("duration_millis", result.getDurationMillis());
        summary.put("status", result.isFailed() ? "failed" : "passed");
        data.put("summary", summary);

        // Build groups (one per thread) and items (one per scenario)
        Map<String, Integer> threadToGroupId = new LinkedHashMap<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        // Flat list of all scenarios — JS sorts by durationMillis desc for the
        // Top-5 slowest panel (sibling card below the gantt).
        List<Map<String, Object>> allScenarios = new ArrayList<>();
        // Sum of per-scenario wall time across the suite — drives the Speedup
        // card (serial-equivalent / actual). Computed here so we don't have to
        // re-walk the tree in JS.
        long serialMillis = 0;
        int itemId = 0;

        for (Map<String, Object> feature : features) {
            // Extract feature filename for timeline display
            String path = (String) feature.get("relativePath");
            String featureFileName = extractFileName(path);
            String featureHtmlName = getFeatureFileName(feature);

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
                    // Bare thread names tend to be just a digit ("1", "2"); prefix
                    // them so the vis-timeline sidebar reads as a label rather than
                    // a stray index.
                    String label = threadName.matches("\\d+") ? "Thread " + threadName : threadName;
                    group.put("content", label);
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

                // Per-scenario summary for the Top-5 slowest panel + Speedup card.
                long scenarioDuration = endTime - startTime + 1;
                serialMillis += scenarioDuration;
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("featureFileName", featureFileName);
                s.put("featureHtmlName", featureHtmlName);
                s.put("refId", refId);
                s.put("name", scenarioName);
                s.put("durationMillis", scenarioDuration);
                s.put("passed", passed);
                allScenarios.add(s);
            }
        }

        data.put("groups", groups);
        data.put("items", items);
        data.put("scenarios", allScenarios);
        data.put("serialDurationMillis", serialMillis);

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

