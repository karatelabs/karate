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
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.StepResult;
import io.karatelabs.core.SuiteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;

public class ImageComparisonReportListener implements ResultListener {
    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private final ExecutorService executor;
    private final ImageComparisonReport report;

    /**
     * Create a new HTML report listener.
     *
     * @param outputDir the directory to write reports
     */
    public ImageComparisonReportListener(Path outputDir) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "karate-image-comparison-report");
            t.setDaemon(true);
            return t;
        });
        this.report = new ImageComparisonReport(outputDir + File.separator + "image-comparison.pdf");
    }

    public boolean hasResults() {
        return report.hasResults();
    }

    @Override
    public void onScenarioEnd(ScenarioResult result) {
        executor.submit(() -> {
            try {
                writeImageComparisonResult(result);
            } catch (Exception e) {
                logger.warn("Failed to write scenario image comparisons for {}: {}", result.getScenario().getName(), e.getMessage());
            }
        });
    }

    private void writeImageComparisonResult(ScenarioResult result) {
        List<StepResult> results = requireNonNullElse(result.getStepResults(), emptyList());
        for (StepResult stepResult : results) {
            List<ImageComparisonResult> imageComparisonResults = requireNonNullElse(
                    stepResult.getImageComparisonResults(), emptyList());
            for (ImageComparisonResult imageComparisonResult : imageComparisonResults) {
                report.writeResult(imageComparisonResult);
            }

            if (stepResult.hasCallResults()) {
                for (FeatureResult featureResult : stepResult.getCallResults()) {
                    for (ScenarioResult sr: featureResult.getScenarioResults()) {
                        writeImageComparisonResult(sr);
                    }
                }
            }
        }
    }

    @Override
    public void onSuiteEnd(SuiteResult result) {
        // Wait for all image comparison writes to complete
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("Image comparison report executor did not complete in time");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } finally {
            try {
                report.close();
            } catch (Exception e) {
                logger.warn("Failed to write image comparison report: {}", e.getMessage());
            }
        }
    }
}
