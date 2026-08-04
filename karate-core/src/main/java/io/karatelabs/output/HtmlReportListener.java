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

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link ResultListener} that generates HTML reports.
 * <p>
 * This listener writes each feature's HTML file as that feature completes, then generates
 * summary pages at suite end. This approach:
 * <ul>
 *   <li>Keeps only small summary data in memory</li>
 *   <li>Makes partial results available as tests complete</li>
 * </ul>
 */
public class HtmlReportListener implements ResultListener {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final String SUBFOLDER = "feature-html";

    private final Path outputDir;
    private final String env;
    // One entry per completed feature, kept until onSuiteEnd builds the summary and
    // timeline pages. Held in the SHALLOW form produced by summaryJson() — see there for
    // why the full tree must not be retained here.
    private final List<Map<String, Object>> featureMaps = new CopyOnWriteArrayList<>();

    private long suiteStartTime;
    private int threadCount;
    private boolean resourcesCopied = false;
    // Ext report-asset specs captured at suite start; threaded into the writer so
    // ext <script>/<link> tags get spliced and ext static dirs copied. Empty when
    // no exts registered assets.
    private Map<String, io.karatelabs.core.ReportAssets> reportAssets = java.util.Collections.emptyMap();
    // Suite captured at start so onSuiteEnd can read getSummaryCards() — exts add those in
    // onShutdown(), which runs after onSuiteStart but before this listener's onSuiteEnd write.
    private Suite suite;

    /**
     * Create a new HTML report listener.
     *
     * @param outputDir the directory to write reports
     * @param env       the karate environment (may be null)
     */
    public HtmlReportListener(Path outputDir, String env) {
        this.outputDir = outputDir;
        this.env = env;
    }

    @Override
    public void onSuiteStart(Suite suite) {
        suiteStartTime = System.currentTimeMillis();
        threadCount = suite.threadCount;
        reportAssets = suite.getReportAssets();
        this.suite = suite;

        // Embed file names use a 001_, 002_, ... sequence; reset per suite so
        // numbers don't bleed across runs in the same JVM (e.g. test suites).
        HtmlReportWriter.resetEmbedCounter();

        // Create directories eagerly
        try {
            Files.createDirectories(outputDir.resolve(SUBFOLDER));
            Files.createDirectories(outputDir.resolve("res"));
        } catch (Exception e) {
            logger.warn("Failed to create report directories: {}", e.getMessage());
        }
    }

    @Override
    public void onFeatureEnd(FeatureResult result) {
        // Sort scenarios for deterministic ordering in reports
        result.sortScenarioResults();

        // Collect feature data using toJson() format, reduced to what the suite-end pages
        // actually read. The per-feature HTML below is written from the FeatureResult
        // itself, so nothing here needs the step detail.
        featureMaps.add(summaryJson(result));

        // Extract the page model and render it, both here, on the feature's own thread.
        //
        // Rendering used to be handed to a single-thread executor with an unbounded queue, on
        // the reasoning that templating and IO should stay off the hot path. Measured, the
        // opposite was true: rendering a feature page cost ~3x the suite's entire wall-clock
        // when summed over all features, so that one thread could never keep up and its queue
        // grew for the whole run — holding a complete page model per queued feature. Peak heap
        // then tracked the number of features, not the number in flight. It was the single
        // largest memory consumer with reports on, and it was noisy run-to-run because what
        // was really being measured was a race between producer and writer.
        //
        // Inline, the cost is shared across every feature thread instead of serialized onto
        // one, and a feature cannot complete until its page is on disk — so the page model is
        // live for one feature per thread rather than for the whole suite.
        try {
            ensureResourcesCopied();
            HtmlReportWriter.renderFeatureHtml(
                    HtmlReportWriter.prepareFeatureData(result, outputDir), outputDir, reportAssets);
        } catch (Exception e) {
            logger.warn("Failed to write feature HTML for {}: {}", result.getDisplayName(), e.getMessage());
        }
    }

    /**
     * A feature's result reduced to what the suite-end pages read.
     *
     * <p>Retaining {@code result.toJson()} whole is what made report generation the largest
     * memory consumer in a long run: the map holds every step, its log text, its embeds and
     * the full result tree of every {@code karate.call()} it made, and it stays reachable
     * until the last feature finishes. Memory then scales with the size of the whole suite.
     * Measured on a 60-calls-per-scenario shape, retention was roughly 1.5 MB per scenario
     * with reports on against 0.43 MB with them off — reporting cost more than execution.
     *
     * <p>Neither consumer needs any of that. {@code buildFeatureSummaryList} and
     * {@code buildTimelineData} read only feature-level identity and status plus a handful
     * of per-scenario fields; {@code stepResults} is never touched at suite end. The
     * per-feature HTML — the one thing that does need step detail — is written eagerly in
     * {@link #onFeatureEnd} from the {@link FeatureResult} itself, before this reduction.
     *
     * <p>Everything except {@code stepResults} is preserved verbatim rather than
     * hand-picked, so a page that starts reading another feature- or scenario-level field
     * keeps working. Only the heavy branch is pruned.
     */
    private static Map<String, Object> summaryJson(FeatureResult result) {
        Map<String, Object> featureMap = result.toJson();
        Object scenarios = featureMap.get("scenarioResults");
        if (!(scenarios instanceof List<?> scenarioList)) {
            return featureMap;
        }
        List<Map<String, Object>> reduced = new ArrayList<>(scenarioList.size());
        for (Object scenario : scenarioList) {
            if (scenario instanceof Map<?, ?> scenarioMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) scenarioMap);
                copy.remove("stepResults");
                reduced.add(copy);
            }
        }
        featureMap.put("scenarioResults", reduced);
        return featureMap;
    }

    @Override
    public void onSuiteEnd(SuiteResult result) {
        try {
            // Ensure resources are copied
            ensureResourcesCopied();

            // Write summary pages using canonical feature maps
            List<Map<String, Object>> summaryCards = suite != null ? suite.getSummaryCards() : java.util.Collections.emptyList();
            HtmlReportWriter.writeSummaryPages(featureMaps, result, outputDir, env, reportAssets, summaryCards);

            // Write timeline page using canonical feature maps
            HtmlReportWriter.writeTimelineHtml(featureMaps, result, outputDir, env, threadCount, reportAssets);

            logger.debug("HTML report written to: {}", outputDir.resolve("karate-summary.html"));

        } catch (Exception e) {
            logger.warn("Failed to write HTML summary: {}", e.getMessage());
        }
    }

    private synchronized void ensureResourcesCopied() {
        if (!resourcesCopied) {
            try {
                HtmlReportWriter.copyStaticResources(outputDir.resolve("res"));
                resourcesCopied = true;
            } catch (Exception e) {
                logger.warn("Failed to copy static resources: {}", e.getMessage());
            }
        }
    }

}
