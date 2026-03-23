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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ResultListener} that generates HTML reports asynchronously.
 * <p>
 * This listener writes feature HTML files as features complete using a single-thread
 * executor, then generates summary pages at suite end. This approach:
 * <ul>
 *   <li>Keeps only small summary data in memory</li>
 *   <li>Does not block test execution during report generation</li>
 *   <li>Makes partial results available as tests complete</li>
 * </ul>
 */
public class HtmlReportListener implements ResultListener {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final String SUBFOLDER = "feature-html";

    private final Path outputDir;
    private final String env;
    private final ExecutorService executor;
    // Use JSON format from FeatureResult.toJson()
    private final List<Map<String, Object>> featureMaps = new CopyOnWriteArrayList<>();

    private long suiteStartTime;
    private int threadCount;
    private boolean resourcesCopied = false;

    /**
     * Create a new HTML report listener.
     *
     * @param outputDir the directory to write reports
     * @param env       the karate environment (may be null)
     */
    public HtmlReportListener(Path outputDir, String env) {
        this.outputDir = outputDir;
        this.env = env;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "karate-html-report");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onSuiteStart(Suite suite) {
        suiteStartTime = System.currentTimeMillis();
        threadCount = suite.threadCount;

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

        // Collect feature data using toJson() format
        featureMaps.add(result.toJson());

        // Queue feature HTML generation (async)
        executor.submit(() -> {
            try {
                ensureResourcesCopied();
                HtmlReportWriter.writeFeatureHtml(result, outputDir);
            } catch (Exception e) {
                logger.warn("Failed to write feature HTML for {}: {}", result.getDisplayName(), e.getMessage());
            }
        });
    }

    @Override
    public void onSuiteEnd(SuiteResult result) {
        try {
            // Ensure resources are copied
            ensureResourcesCopied();

            // Write summary pages using canonical feature maps
            HtmlReportWriter.writeSummaryPages(featureMaps, result, outputDir, env);

            // Write timeline page using canonical feature maps
            HtmlReportWriter.writeTimelineHtml(featureMaps, result, outputDir, env, threadCount);

            logger.debug("HTML report written to: {}", outputDir.resolve("karate-summary.html"));

        } catch (Exception e) {
            logger.warn("Failed to write HTML summary: {}", e.getMessage());
        } finally {
            // Wait for all feature HTML writes to complete
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("HTML report executor did not complete in time");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
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
