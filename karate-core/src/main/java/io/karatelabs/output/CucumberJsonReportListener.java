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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link ResultListener} that generates Cucumber JSON reports.
 * <p>
 * This listener writes each feature's Cucumber JSON file as that feature completes.
 * Each feature produces a separate JSON file named
 * {@code {packageQualifiedName}.json}. This approach:
 * <ul>
 *   <li>Makes partial results available as tests complete</li>
 *   <li>Compatible with third-party tools (Allure, ReportPortal, etc.)</li>
 * </ul>
 */
public class CucumberJsonReportListener implements ResultListener {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final String SUBFOLDER = "cucumber-json";

    private final Path outputDir;

    /**
     * Create a new Cucumber JSON report listener.
     *
     * @param outputDir the base output directory (subfolder will be created)
     */
    public CucumberJsonReportListener(Path outputDir) {
        this.outputDir = outputDir.resolve(SUBFOLDER);
    }

    @Override
    public void onSuiteStart(Suite suite) {
        // Create output directory eagerly
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to create Cucumber JSON output directory: {}", e.getMessage());
        }
    }

    @Override
    public void onFeatureEnd(FeatureResult result) {
        // Sort scenarios for deterministic ordering
        result.sortScenarioResults();

        // Serialize AND write here, on the feature's own thread. The write used to be handed
        // to a single-thread executor with an unbounded queue; measured over a many-feature
        // suite that thread was idle — its share of the work was a few percent of wall-clock —
        // so the queue could only ever grow, never help. Writing inline also means a feature
        // cannot complete until its report is on disk, which bounds what is held in memory.
        try {
            CucumberJsonWriter.writeSerialized(CucumberJsonWriter.fileNameFor(result),
                    CucumberJsonWriter.serializeFeature(result), outputDir);
        } catch (Exception e) {
            logger.warn("Failed to write Cucumber JSON for {}: {}", result.getDisplayName(), e.getMessage());
        }
    }

}
