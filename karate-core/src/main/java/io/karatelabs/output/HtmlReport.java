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
import io.karatelabs.core.Suite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent API for HTML report aggregation across multiple test runs.
 * <p>
 * This class allows merging JSON Lines files from different test runs to create
 * a combined HTML report. This is useful for aggregating results from:
 * <ul>
 *   <li>Parallel test shards</li>
 *   <li>Retry runs</li>
 *   <li>Different environments</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * HtmlReport.aggregate()
 *     .json("target/run1/karate-results.jsonl")
 *     .json("target/run2/karate-results.jsonl")
 *     .outputDir("target/combined-report")
 *     .generate();
 * </pre>
 */
public final class HtmlReport {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private HtmlReport() {
    }

    /**
     * Start building an aggregate report.
     *
     * @return a new aggregate builder
     */
    public static AggregateBuilder aggregate() {
        return new AggregateBuilder();
    }

    /**
     * Builder for aggregating multiple JSON Lines files into a single HTML report.
     */
    public static class AggregateBuilder {
        private final List<Path> jsonlFiles = new ArrayList<>();
        private Path outputDir;

        /**
         * Add a JSON Lines file to aggregate.
         *
         * @param path path to the JSON Lines file
         * @return this builder
         */
        public AggregateBuilder json(String path) {
            jsonlFiles.add(Path.of(path));
            return this;
        }

        /**
         * Add a JSON Lines file to aggregate.
         *
         * @param path path to the JSON Lines file
         * @return this builder
         */
        public AggregateBuilder json(Path path) {
            jsonlFiles.add(path);
            return this;
        }

        /**
         * Set the output directory for the aggregated report.
         *
         * @param dir output directory path
         * @return this builder
         */
        public AggregateBuilder outputDir(String dir) {
            this.outputDir = Path.of(dir);
            return this;
        }

        /**
         * Set the output directory for the aggregated report.
         *
         * @param dir output directory path
         * @return this builder
         */
        public AggregateBuilder outputDir(Path dir) {
            this.outputDir = dir;
            return this;
        }

        /**
         * Generate the aggregated HTML report.
         *
         * @throws IllegalStateException if no JSON Lines files or output directory specified
         */
        public void generate() {
            if (jsonlFiles.isEmpty()) {
                throw new IllegalStateException("No JSON Lines files specified for aggregation");
            }
            if (outputDir == null) {
                throw new IllegalStateException("Output directory not specified");
            }

            try {
                // Parse and merge all JSON Lines files (new event format)
                List<Map<String, Object>> allFeatures = new ArrayList<>();
                Map<String, Object> suiteData = new LinkedHashMap<>();
                int totalFeaturesPassed = 0;
                int totalFeaturesFailed = 0;
                int totalScenariosPassed = 0;
                int totalScenariosFailed = 0;
                long totalDuration = 0;

                for (Path jsonlFile : jsonlFiles) {
                    if (!Files.exists(jsonlFile)) {
                        logger.warn("JSON Lines file not found, skipping: {}", jsonlFile);
                        continue;
                    }

                    List<String> lines = Files.readAllLines(jsonlFile, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        if (line.trim().isEmpty()) continue;

                        @SuppressWarnings("unchecked")
                        Map<String, Object> envelope = (Map<String, Object>) Json.of(line).value();
                        String eventType = (String) envelope.get("type");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) envelope.get("data");

                        if ("SUITE_ENTER".equals(eventType)) {
                            // Take metadata from the first suite header
                            if (suiteData.isEmpty() && data != null) {
                                suiteData.put("env", data.get("env"));
                                suiteData.put("threads", data.get("threads"));
                                suiteData.put("karateVersion", data.get("version"));
                                suiteData.put("ts", envelope.get("ts"));
                            }
                        } else if ("FEATURE_EXIT".equals(eventType) && data != null) {
                            // Feature data from toJson()
                            allFeatures.add(data);
                        } else if ("SUITE_EXIT".equals(eventType) && data != null) {
                            // Summary from SuiteResult.toJson()
                            @SuppressWarnings("unchecked")
                            Map<String, Object> summary = (Map<String, Object>) data.get("summary");
                            if (summary != null) {
                                totalFeaturesPassed += ((Number) summary.getOrDefault("featuresPassed", 0)).intValue();
                                totalFeaturesFailed += ((Number) summary.getOrDefault("featuresFailed", 0)).intValue();
                                totalScenariosPassed += ((Number) summary.getOrDefault("scenariosPassed", 0)).intValue();
                                totalScenariosFailed += ((Number) summary.getOrDefault("scenariosFailed", 0)).intValue();
                                totalDuration += ((Number) summary.getOrDefault("durationMillis", 0)).longValue();
                            }
                        }
                    }
                }

                // Build aggregated summary
                int totalScenarios = 0;
                for (Map<String, Object> feature : allFeatures) {
                    @SuppressWarnings("unchecked")
                    List<?> scenarioResults = (List<?>) feature.get("scenarioResults");
                    totalScenarios += scenarioResults != null ? scenarioResults.size() : 0;
                }

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("feature_count", allFeatures.size());
                summary.put("feature_passed", totalFeaturesPassed);
                summary.put("feature_failed", totalFeaturesFailed);
                summary.put("scenario_count", totalScenarios);
                summary.put("scenario_passed", totalScenariosPassed);
                summary.put("scenario_failed", totalScenariosFailed);
                summary.put("duration_millis", totalDuration);
                summary.put("status", totalFeaturesFailed > 0 ? "failed" : "passed");

                suiteData.put("summary", summary);

                // Write merged JSON Lines to karate-json subfolder
                Path karateJsonDir = outputDir.resolve(Suite.KARATE_JSON_SUBFOLDER);
                Files.createDirectories(karateJsonDir);
                Path mergedJsonl = karateJsonDir.resolve("karate-events.jsonl");
                writeMergedJsonLines(mergedJsonl, suiteData, allFeatures, summary);

                // Generate HTML from merged data
                HtmlReportWriter.writeFromJsonLines(mergedJsonl, outputDir);

                logger.info("Aggregated report generated at: {}", outputDir);

            } catch (IOException e) {
                throw new RuntimeException("Failed to generate aggregated report: " + e.getMessage(), e);
            }
        }

        private void writeMergedJsonLines(Path path, Map<String, Object> suiteData,
                                        List<Map<String, Object>> features,
                                        Map<String, Object> summary) throws IOException {
            StringBuilder sb = new StringBuilder();
            long ts = System.currentTimeMillis();

            // SUITE_ENTER event
            Map<String, Object> enterEnvelope = new LinkedHashMap<>();
            enterEnvelope.put("type", "SUITE_ENTER");
            enterEnvelope.put("ts", suiteData.get("ts") != null ? suiteData.get("ts") : ts);
            enterEnvelope.put("threadId", null);
            Map<String, Object> enterData = new LinkedHashMap<>();
            enterData.put("schemaVersion", "1");
            enterData.put("version", suiteData.get("karateVersion"));
            if (suiteData.get("env") != null) {
                enterData.put("env", suiteData.get("env"));
            }
            enterData.put("threads", suiteData.get("threads"));
            enterEnvelope.put("data", enterData);
            sb.append(Json.stringifyStrict(enterEnvelope)).append("\n");

            // FEATURE_EXIT events
            for (Map<String, Object> feature : features) {
                Map<String, Object> featureEnvelope = new LinkedHashMap<>();
                featureEnvelope.put("type", "FEATURE_EXIT");
                featureEnvelope.put("ts", ts);
                featureEnvelope.put("threadId", "aggregated");
                featureEnvelope.put("data", feature);
                sb.append(Json.stringifyStrict(featureEnvelope)).append("\n");
            }

            // SUITE_EXIT event
            Map<String, Object> exitEnvelope = new LinkedHashMap<>();
            exitEnvelope.put("type", "SUITE_EXIT");
            exitEnvelope.put("ts", ts);
            exitEnvelope.put("threadId", null);
            Map<String, Object> exitData = new LinkedHashMap<>();
            exitData.put("features", features);
            exitData.put("summary", summary);
            exitEnvelope.put("data", exitData);
            sb.append(Json.stringifyStrict(exitEnvelope)).append("\n");

            Files.writeString(path, sb.toString());
        }
    }

}
