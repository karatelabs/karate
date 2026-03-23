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

import io.karatelabs.output.Console;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuiteResult {

    private final List<FeatureResult> featureResults = Collections.synchronizedList(new ArrayList<>());
    private long startTime;
    private long endTime;
    private Path reportDir;
    private boolean htmlReportEnabled;

    public SuiteResult() {
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setReportDir(Path reportDir) {
        this.reportDir = reportDir;
    }

    public Path getReportDir() {
        return reportDir;
    }

    public void setHtmlReportEnabled(boolean htmlReportEnabled) {
        this.htmlReportEnabled = htmlReportEnabled;
    }

    public boolean isHtmlReportEnabled() {
        return htmlReportEnabled;
    }

    public synchronized void addFeatureResult(FeatureResult fr) {
        featureResults.add(fr);
    }

    public List<FeatureResult> getFeatureResults() {
        return featureResults;
    }

    // ========== Aggregation ==========

    public int getFeatureCount() {
        return featureResults.size();
    }

    public int getFeaturePassedCount() {
        return (int) featureResults.stream().filter(FeatureResult::isPassed).count();
    }

    public int getFeatureFailedCount() {
        return (int) featureResults.stream().filter(FeatureResult::isFailed).count();
    }

    public int getScenarioCount() {
        return featureResults.stream().mapToInt(FeatureResult::getScenarioCount).sum();
    }

    public int getScenarioPassedCount() {
        return featureResults.stream().mapToInt(FeatureResult::getPassedCount).sum();
    }

    public int getScenarioFailedCount() {
        return featureResults.stream().mapToInt(FeatureResult::getFailedCount).sum();
    }

    public boolean isPassed() {
        return featureResults.stream().noneMatch(FeatureResult::isFailed);
    }

    public boolean isFailed() {
        return featureResults.stream().anyMatch(FeatureResult::isFailed);
    }

    public long getDurationMillis() {
        return endTime - startTime;
    }

    // ========== Serialization ==========

    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();

        // Features
        List<Map<String, Object>> features = new ArrayList<>();
        for (FeatureResult fr : featureResults) {
            features.add(fr.toJson());
        }
        map.put("features", features);

        // Summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("featureCount", getFeatureCount());
        summary.put("featuresPassed", getFeaturePassedCount());
        summary.put("featuresFailed", getFeatureFailedCount());
        summary.put("scenarioCount", getScenarioCount());
        summary.put("scenariosPassed", getScenarioPassedCount());
        summary.put("scenariosFailed", getScenarioFailedCount());
        summary.put("durationMillis", getDurationMillis());
        summary.put("passed", !isFailed());
        map.put("summary", summary);

        return map;
    }

    public String toJsonString() {
        return io.karatelabs.common.Json.stringifyStrict(toJson());
    }

    public String toJsonPretty() {
        return io.karatelabs.common.Json.of(toJson()).toStringPretty();
    }

    // ========== Console Output ==========

    /**
     * Print a summary of the test results to the console.
     */
    public void printSummary() {
        printSummary(null, 1);
    }

    /**
     * Print a summary of the test results to the console.
     *
     * @param env         the karate environment (may be null)
     * @param threadCount number of threads used
     */
    public void printSummary(String env, int threadCount) {
        // Determine URL for final line length
        String reportUrl = null;
        if (htmlReportEnabled && reportDir != null) {
            Path reportFile = reportDir.resolve("karate-summary.html");
            reportUrl = reportFile.toUri().toString();
        }
        int finalLineLength = Math.max(60, reportUrl != null ? reportUrl.length() : 0);

        // List failed features first (before the summary block)
        List<FeatureResult> failedFeatures = getFailedFeatures();
        if (!failedFeatures.isEmpty()) {
            Console.println();
            Console.println(Console.fail("failed features:"));
            for (FeatureResult fr : failedFeatures) {
                String path;
                if (fr.getFeature().getResource() != null && fr.getFeature().getResource().getPath() != null) {
                    path = fr.getFeature().getResource().getPath().toString();
                } else {
                    path = fr.getFeature().getName();
                }
                Console.println("  " + Console.red(path));

                // Show failed scenarios with line numbers for easy navigation
                for (ScenarioResult sr : fr.getScenarioResults()) {
                    if (sr.isFailed()) {
                        String scenarioName = sr.getScenario().getName();
                        if (scenarioName == null || scenarioName.isEmpty()) {
                            scenarioName = "line " + sr.getScenario().getLine();
                        }
                        Console.println("    - " + scenarioName);
                        // Show feature:line for failed step (enables IDE click-to-navigate)
                        String stepLocation = sr.getFailedStepLocation();
                        if (stepLocation != null) {
                            Console.println("      " + stepLocation);
                        }
                        // Show error message (truncate only very long messages)
                        if (sr.getFailureMessage() != null) {
                            String msg = sr.getFailureMessage();
                            if (msg.length() > 200) {
                                msg = msg.substring(0, 197) + "...";
                            }
                            Console.println("      " + Console.yellow(msg));
                        }
                    }
                }
            }
        }

        // Summary block
        Console.println();
        Console.println(Console.cyan(Console.line()));

        // Timing info
        double elapsedSecs = getDurationMillis() / 1000.0;
        double threadTimeSecs = getThreadTimeMillis() / 1000.0;
        double efficiency = threadCount > 0 && elapsedSecs > 0
                ? threadTimeSecs / (elapsedSecs * threadCount)
                : 1.0;

        Console.println(String.format("elapsed: %6.2fs | threads: %3d | efficiency: %.2f",
                elapsedSecs, threadCount, efficiency));

        // Feature stats
        int featureTotal = getFeatureCount();
        int featurePassed = getFeaturePassedCount();
        int featureFailed = getFeatureFailedCount();
        String featureStatus = featureFailed > 0
                ? Console.fail(featureFailed + " failed")
                : Console.pass("all passed");

        Console.println(String.format("features: %4d | passed: %4d | %s",
                featureTotal, featurePassed, featureStatus));

        // Scenario stats
        int scenarioTotal = getScenarioCount();
        int scenarioPassed = getScenarioPassedCount();
        int scenarioFailed = getScenarioFailedCount();
        String scenarioStatus = scenarioFailed > 0
                ? Console.fail(scenarioFailed + " failed")
                : Console.pass("all passed");

        Console.println(String.format("scenarios: %3d | passed: %4d | %s",
                scenarioTotal, scenarioPassed, scenarioStatus));

        // Footer with version, env, and HTML report (URL last for easy clicking)
        Console.println(Console.cyan("-".repeat(60)));
        StringBuilder footer = new StringBuilder("Karate " + Globals.KARATE_VERSION);
        if (env != null && !env.isEmpty()) {
            footer.append(" | env: ").append(Console.cyan(env));
        }
        if (reportUrl != null) {
            footer.append(" | HTML report:");
            Console.println(footer.toString());
            Console.println(reportUrl);
        } else {
            Console.println(footer.toString());
        }

        // Final line - colored based on result, matches URL length
        String endLine = isFailed() ? Console.fail("=".repeat(finalLineLength)) : Console.pass("=".repeat(finalLineLength));
        Console.println(endLine);
    }

    /**
     * Get sum of all scenario execution times (thread time).
     */
    public long getThreadTimeMillis() {
        return featureResults.stream()
                .mapToLong(FeatureResult::getDurationMillis)
                .sum();
    }

    /**
     * Get list of failed features.
     */
    public List<FeatureResult> getFailedFeatures() {
        List<FeatureResult> failed = new ArrayList<>();
        for (FeatureResult fr : featureResults) {
            if (fr.isFailed()) {
                failed.add(fr);
            }
        }
        return failed;
    }

    /**
     * Get all error messages from failed scenarios.
     */
    public List<String> getErrors() {
        List<String> errors = new ArrayList<>();
        for (FeatureResult fr : featureResults) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed() && sr.getFailureMessage() != null) {
                    errors.add(sr.getFailureMessage());
                }
            }
        }
        return errors;
    }

}
