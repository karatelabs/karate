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

import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.output.Console;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeatureResult {

    private static final DateTimeFormatter RESULT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd hh:mm:ss a", Locale.US)
            .withZone(ZoneId.systemDefault());

    private final Feature feature;
    private final List<ScenarioResult> scenarioResults = Collections.synchronizedList(new ArrayList<>());
    private int callDepth;
    private int loopIndex = -1;  // -1 means not looped
    private Object callArg;
    private Map<String, Object> resultVariables;
    private long startTime;
    private long endTime;

    public FeatureResult(Feature feature) {
        this.feature = feature;
    }

    /**
     * Create a failed FeatureResult from an exception that occurred during feature execution.
     * This is used when an unexpected error prevents normal scenario execution,
     * such as failures in dynamic expression evaluation for Scenario Outlines.
     *
     * @param feature   the feature that failed
     * @param error     the exception that caused the failure
     * @param startTime the start time of the feature execution
     * @return a FeatureResult marked as failed with a synthetic scenario containing the error
     */
    public static FeatureResult fromException(Feature feature, Throwable error, long startTime) {
        FeatureResult result = new FeatureResult(feature);
        result.setStartTime(startTime);
        result.setEndTime(System.currentTimeMillis());

        // Create a synthetic failed scenario result to capture the error
        // Use the first scenario from the feature if available, otherwise create a minimal one
        Scenario scenario = getFirstScenarioOrCreate(feature);

        ScenarioResult scenarioResult = new ScenarioResult(scenario);
        scenarioResult.setStartTime(startTime);
        scenarioResult.setEndTime(System.currentTimeMillis());
        scenarioResult.setThreadName(Thread.currentThread().getName());

        // Add a synthetic failed step with the error
        String errorMessage = "Feature execution failed: " + error.getMessage();
        scenarioResult.addStepResult(StepResult.fakeFailure(errorMessage, startTime, error));

        result.addScenarioResult(scenarioResult);
        return result;
    }

    /**
     * Get the first scenario from a feature, or create a minimal one for error reporting.
     */
    private static Scenario getFirstScenarioOrCreate(Feature feature) {
        if (feature.getSections().isEmpty()) {
            // Create a minimal scenario via a synthetic FeatureSection
            return Scenario.createError(feature, "Feature execution failed", feature.getLine());
        }
        if (feature.getSections().get(0).isOutline()) {
            return feature.getSections().get(0).getScenarioOutline().toScenario(null, 0, feature.getLine(), null);
        }
        return feature.getSections().get(0).getScenario();
    }

    public Feature getFeature() {
        return feature;
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

    public int getCallDepth() {
        return callDepth;
    }

    public void setCallDepth(int callDepth) {
        this.callDepth = callDepth;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    public Object getCallArg() {
        return callArg;
    }

    public void setCallArg(Object callArg) {
        this.callArg = callArg;
    }

    public Map<String, Object> getResultVariables() {
        return resultVariables;
    }

    public void setResultVariables(Map<String, Object> resultVariables) {
        this.resultVariables = resultVariables;
    }

    public synchronized void addScenarioResult(ScenarioResult sr) {
        scenarioResults.add(sr);
    }

    public List<ScenarioResult> getScenarioResults() {
        return scenarioResults;
    }

    /**
     * Sort scenario results by section index, example index, and line number.
     * This ensures deterministic ordering in reports regardless of parallel execution order.
     */
    public void sortScenarioResults() {
        synchronized (scenarioResults) {
            Collections.sort(scenarioResults);
        }
    }

    public int getScenarioCount() {
        return scenarioResults.size();
    }

    public int getPassedCount() {
        return (int) scenarioResults.stream().filter(ScenarioResult::isPassed).count();
    }

    public int getFailedCount() {
        return (int) scenarioResults.stream().filter(ScenarioResult::isFailed).count();
    }

    public boolean isPassed() {
        return scenarioResults.stream().noneMatch(ScenarioResult::isFailed);
    }

    public boolean isFailed() {
        return scenarioResults.stream().anyMatch(ScenarioResult::isFailed);
    }

    public boolean isEmpty() {
        return scenarioResults.isEmpty();
    }

    public long getDurationMillis() {
        return endTime - startTime;
    }

    public String getDisplayName() {
        return feature.getResource().getRelativePath();
    }

    public String getFailureMessage() {
        return scenarioResults.stream()
                .filter(ScenarioResult::isFailed)
                .findFirst()
                .map(ScenarioResult::getFailureMessage)
                .orElse(null);
    }

    // ========== Canonical Map Format ==========

    /**
     * Convert to JSON format.
     * Used for HTML reports, JSONL streaming, and report aggregation.
     */
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();

        // Core identity
        map.put("name", feature.getName());
        map.put("description", feature.getDescription());

        // Metrics
        map.put("passed", isPassed());
        map.put("failed", isFailed());
        map.put("durationMillis", getDurationMillis());
        map.put("passedCount", getPassedCount());
        map.put("failedCount", getFailedCount());

        // Path fields
        map.put("packageQualifiedName", feature.getResource().getPackageQualifiedName());
        map.put("relativePath", feature.getResource().getRelativePath());
        map.put("resultDate", RESULT_DATE_FORMAT.format(Instant.ofEpochMilli(endTime)));
        map.put("prefixedPath", feature.getResource().getPrefixedPath());

        // Timing
        map.put("startTime", startTime);
        map.put("endTime", endTime);

        // Call hierarchy (for called features)
        map.put("loopIndex", loopIndex);
        map.put("callDepth", callDepth);
        if (callArg != null) {
            map.put("callArg", callArg);
        }

        // Feature location
        map.put("line", feature.getLine());
        map.put("id", feature.getResource().getRelativePath().replace('/', '_').replace('.', '_'));

        // Tags
        List<Tag> tags = feature.getTags();
        if (tags != null && !tags.isEmpty()) {
            List<Map<String, Object>> tagList = new ArrayList<>();
            for (Tag tag : tags) {
                Map<String, Object> tagMap = new LinkedHashMap<>();
                tagMap.put("name", tag.toString());
                tagMap.put("line", feature.getLine());
                tagList.add(tagMap);
            }
            map.put("tags", tagList);
        }

        // Scenario results
        List<Map<String, Object>> scenarioResultsList = new ArrayList<>();
        for (ScenarioResult sr : scenarioResults) {
            scenarioResultsList.add(sr.toJson());
        }
        map.put("scenarioResults", scenarioResultsList);

        return map;
    }

    // ========== Console Output ==========

    /**
     * Print a summary of this feature's results to the console.
     */
    public void printSummary() {
        String path = getDisplayName();
        int passed = getPassedCount();
        int failed = getFailedCount();
        int total = getScenarioCount();
        double secs = getDurationMillis() / 1000.0;

        String status = failed > 0
                ? Console.red(failed + " failed")
                : Console.green("passed");

        String featureLine = failed > 0
                ? Console.red(path)
                : Console.green(path);

        Console.println(Console.grey("-".repeat(57)));
        Console.println("feature: " + featureLine);
        Console.println(String.format("scenarios: %2d | passed: %2d | %s | time: %.4f",
                total, passed, status, secs));
        Console.println(Console.grey("=".repeat(57)));
    }

}
