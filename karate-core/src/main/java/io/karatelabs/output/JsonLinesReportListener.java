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
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Globals;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link ResultListener} that streams test results to a JSON Lines (.jsonl) file.
 * <p>
 * This listener writes feature results as they complete during test execution, enabling:
 * <ul>
 *   <li>Memory efficiency - results written to disk immediately, not held in memory</li>
 *   <li>Streaming - can tail the file during execution for live progress</li>
 *   <li>Easy aggregation - multiple JSON Lines files can be concatenated</li>
 * </ul>
 * <p>
 * JSON Lines format:
 * <pre>
 * {"t":"suite","time":"2025-12-16T10:30:00Z","threads":5,"env":"dev","version":"..."}
 * {"t":"feature","path":"features/users.feature","name":"User Management","scenarios":[...],"passed":true,"ms":1234}
 * {"t":"suite_end","featuresPassed":10,"featuresFailed":2,"scenariosPassed":42,"scenariosFailed":3,"ms":12345}
 * </pre>
 * <p>
 * At suite end, this listener generates HTML reports from the accumulated JSON Lines data.
 */
public class JsonLinesReportListener implements ResultListener {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final Path outputDir;
    private final Path jsonlPath;
    private final String env;
    private BufferedWriter writer;
    private long suiteStartTime;
    private int threadCount;

    /**
     * Create a new JSON Lines report listener.
     *
     * @param outputDir the directory to write reports
     * @param env       the karate environment (may be null)
     */
    public JsonLinesReportListener(Path outputDir, String env) {
        this.outputDir = outputDir;
        this.jsonlPath = outputDir.resolve("karate-results.jsonl");
        this.env = env;
    }

    @Override
    public void onSuiteStart(Suite suite) {
        try {
            // Create output directory
            Files.createDirectories(outputDir);

            // Open writer for JSON Lines file
            writer = Files.newBufferedWriter(jsonlPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            // Capture suite info
            suiteStartTime = System.currentTimeMillis();
            threadCount = suite.threadCount;

            // Write suite header line
            Map<String, Object> suiteHeader = new LinkedHashMap<>();
            suiteHeader.put("t", "suite");
            suiteHeader.put("time", ISO_FORMAT.format(Instant.ofEpochMilli(suiteStartTime).atOffset(ZoneOffset.UTC)));
            suiteHeader.put("threads", threadCount);
            if (env != null && !env.isEmpty()) {
                suiteHeader.put("env", env);
            }
            suiteHeader.put("version", Globals.KARATE_VERSION);

            writeLine(Json.stringifyStrict(suiteHeader));

            logger.debug("JSON Lines report started: {}", jsonlPath);

        } catch (IOException e) {
            logger.warn("Failed to start JSON Lines report: {}", e.getMessage());
        }
    }

    @Override
    public void onFeatureEnd(FeatureResult result) {
        if (writer == null) {
            return;
        }

        try {
            // Use toJson() format with JSON Lines type prefix
            Map<String, Object> featureLine = new LinkedHashMap<>();
            featureLine.put("t", "feature");
            featureLine.putAll(result.toJson());
            writeLine(Json.stringifyStrict(featureLine));
        } catch (Exception e) {
            logger.warn("Failed to write feature to JSON Lines: {}", e.getMessage());
        }
    }

    @Override
    public void onSuiteEnd(SuiteResult result) {
        if (writer == null) {
            return;
        }

        try {
            // Write suite_end line
            Map<String, Object> suiteEnd = new LinkedHashMap<>();
            suiteEnd.put("t", "suite_end");
            suiteEnd.put("featuresPassed", result.getFeaturePassedCount());
            suiteEnd.put("featuresFailed", result.getFeatureFailedCount());
            suiteEnd.put("scenariosPassed", result.getScenarioPassedCount());
            suiteEnd.put("scenariosFailed", result.getScenarioFailedCount());
            suiteEnd.put("ms", result.getDurationMillis());

            writeLine(Json.stringifyStrict(suiteEnd));

            // Close writer
            writer.close();
            writer = null;

            logger.info("JSON Lines report written to: {}", jsonlPath);

            // Note: We do NOT regenerate HTML from JSON Lines here.
            // HtmlReportListener handles HTML generation (with embeds).
            // JSON Lines is for data exchange/aggregation via HtmlReport.aggregate().

        } catch (IOException e) {
            logger.warn("Failed to complete JSON Lines report: {}", e.getMessage());
        }
    }

    /**
     * Thread-safe write to JSON Lines file.
     */
    private synchronized void writeLine(String json) throws IOException {
        if (writer != null) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }
    }

    /**
     * Get the path to the JSON Lines file.
     */
    public Path getJsonlPath() {
        return jsonlPath;
    }

}
