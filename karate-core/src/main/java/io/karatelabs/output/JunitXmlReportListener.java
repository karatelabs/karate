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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ResultListener} that generates JUnit XML reports asynchronously.
 * <p>
 * This listener writes per-feature JUnit XML files as features complete using
 * a single-thread executor. Each feature produces a separate XML file named
 * {@code {packageQualifiedName}.xml}. This approach:
 * <ul>
 *   <li>Does not block test execution during report generation</li>
 *   <li>Makes partial results available as tests complete</li>
 *   <li>Compatible with CI systems (Jenkins, GitHub Actions, etc.)</li>
 * </ul>
 */
public class JunitXmlReportListener implements ResultListener {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final String SUBFOLDER = "junit-xml";

    private final Path outputDir;
    private final ExecutorService executor;

    /**
     * Create a new JUnit XML report listener.
     *
     * @param outputDir the base output directory (subfolder will be created)
     */
    public JunitXmlReportListener(Path outputDir) {
        this.outputDir = outputDir.resolve(SUBFOLDER);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "karate-junit-xml");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onSuiteStart(Suite suite) {
        // Create output directory eagerly
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to create JUnit XML output directory: {}", e.getMessage());
        }
    }

    @Override
    public void onFeatureEnd(FeatureResult result) {
        // Sort scenarios for deterministic ordering
        result.sortScenarioResults();

        // Queue JUnit XML generation (async)
        executor.submit(() -> {
            try {
                JunitXmlWriter.writeFeature(result, outputDir);
            } catch (Exception e) {
                logger.warn("Failed to write JUnit XML for {}: {}", result.getDisplayName(), e.getMessage());
            }
        });
    }

    @Override
    public void onSuiteEnd(SuiteResult result) {
        // Wait for all JUnit XML writes to complete
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("JUnit XML executor did not complete in time");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

}
