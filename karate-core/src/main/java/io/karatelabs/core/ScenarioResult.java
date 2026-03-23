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

import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Step;
import io.karatelabs.gherkin.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScenarioResult implements Comparable<ScenarioResult> {

    public static final String EXPECT_TEST_TO_FAIL_BECAUSE_OF_FAIL_TAG = "Expect test to fail because of @fail tag";

    private final Scenario scenario;
    private final List<StepResult> stepResults = new ArrayList<>();
    private long startTime;
    private long endTime;
    private String threadName;
    private boolean failTagApplied;

    public ScenarioResult(Scenario scenario) {
        this.scenario = scenario;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public List<StepResult> getStepResults() {
        return stepResults;
    }

    public void addStepResult(StepResult sr) {
        stepResults.add(sr);
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getThreadName() {
        return threadName;
    }

    public boolean isPassed() {
        if (failTagApplied) {
            // When @fail tag is applied, check if the last (fake) step passed
            if (!stepResults.isEmpty()) {
                return stepResults.getLast().isPassed();
            }
        }
        return stepResults.stream().noneMatch(StepResult::isFailed);
    }

    public boolean isFailed() {
        if (failTagApplied) {
            // When @fail tag is applied, check if the last (fake) step failed
            if (!stepResults.isEmpty()) {
                return stepResults.getLast().isFailed();
            }
        }
        return stepResults.stream().anyMatch(StepResult::isFailed);
    }

    public long getDurationMillis() {
        return endTime - startTime;
    }

    public long getDurationNanos() {
        return stepResults.stream()
                .mapToLong(StepResult::getDurationNanos)
                .sum();
    }

    public String getFailureMessage() {
        return stepResults.stream()
                .filter(StepResult::isFailed)
                .findFirst()
                .map(StepResult::getErrorMessage)
                .orElse(null);
    }

    /**
     * Get the failed step location for IDE navigation.
     * Format: "path/to/feature.feature:LINE" (standard IDE clickable format)
     * Uses absolute path when available for better IDE compatibility.
     *
     * @return location string "path:line", or null if no failure
     */
    public String getFailedStepLocation() {
        StepResult failedStep = stepResults.stream()
                .filter(StepResult::isFailed)
                .findFirst()
                .orElse(null);
        if (failedStep == null) {
            return null;
        }
        Step step = failedStep.getStep();
        if (step == null) {
            return null;
        }
        // Prefer absolute path for IDE click-to-navigate; fall back to relative
        var resource = scenario.getFeature().getResource();
        String featurePath = resource.getPath() != null
                ? resource.getPath().toString()
                : resource.getRelativePath();
        // Gherkin line numbers are 1-indexed (human-readable)
        return featurePath + ":" + step.getLine();
    }

    /**
     * Get the failure message with feature file path and line number for display.
     * Format: "path/to/feature.feature:LINE step text"
     *
     * @return formatted failure message, or null if no failure
     */
    public String getFailureMessageForDisplay() {
        StepResult failedStep = stepResults.stream()
                .filter(StepResult::isFailed)
                .findFirst()
                .orElse(null);
        if (failedStep == null) {
            return null;
        }
        Step step = failedStep.getStep();
        if (step == null) {
            return null;
        }
        String location = getFailedStepLocation();
        return location != null ? location + " " + step.getText() : step.getText();
    }

    public Throwable getError() {
        return stepResults.stream()
                .filter(StepResult::isFailed)
                .findFirst()
                .map(StepResult::getError)
                .orElse(null);
    }

    public int getPassedCount() {
        return (int) stepResults.stream().filter(StepResult::isPassed).count();
    }

    public int getFailedCount() {
        return (int) stepResults.stream().filter(StepResult::isFailed).count();
    }

    public int getSkippedCount() {
        return (int) stepResults.stream().filter(StepResult::isSkipped).count();
    }

    /**
     * Apply @fail tag logic: invert the pass/fail result.
     * If the scenario failed (as expected with @fail), mark it as passed.
     * If the scenario passed (unexpectedly with @fail), mark it as failed.
     */
    public void applyFailTag() {
        boolean originallyFailed = stepResults.stream().anyMatch(StepResult::isFailed);
        if (originallyFailed) {
            // Expected: test was supposed to fail and it did - mark as success
            // Add a fake passing step to indicate the @fail expectation was met
            stepResults.add(StepResult.fakeSuccess(EXPECT_TEST_TO_FAIL_BECAUSE_OF_FAIL_TAG, System.currentTimeMillis()));
        } else {
            // Unexpected: test was supposed to fail but passed - mark as failure
            stepResults.add(StepResult.fakeFailure(EXPECT_TEST_TO_FAIL_BECAUSE_OF_FAIL_TAG, System.currentTimeMillis(),
                    new RuntimeException(EXPECT_TEST_TO_FAIL_BECAUSE_OF_FAIL_TAG)));
        }
        failTagApplied = true;
    }

    public boolean isFailTagApplied() {
        return failTagApplied;
    }

    /**
     * Convert to JSON format.
     * Used for HTML reports, JSONL streaming, and report aggregation.
     */
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();

        // Core identity
        map.put("name", scenario.getName());
        map.put("description", scenario.getDescription());
        map.put("line", scenario.getLine());

        // Status
        map.put("passed", isPassed());
        map.put("failed", isFailed());
        map.put("durationMillis", getDurationMillis());
        if (isFailed()) {
            map.put("error", getFailureMessage());
        }

        // RefId and outline info
        map.put("refId", scenario.getRefId());
        map.put("sectionIndex", scenario.getSection().getIndex());
        map.put("exampleIndex", scenario.getExampleIndex());
        map.put("isOutlineExample", scenario.isOutlineExample());
        Map<String, Object> exampleData = scenario.getExampleData();
        if (exampleData != null) {
            map.put("exampleData", exampleData);
        }

        // Execution info
        map.put("executorName", threadName);
        map.put("startTime", startTime);
        map.put("endTime", endTime);

        // Tags (effective = merged feature + scenario)
        List<Tag> effectiveTags = scenario.getTagsEffective();
        if (effectiveTags != null && !effectiveTags.isEmpty()) {
            List<String> tagNames = new ArrayList<>();
            for (Tag tag : effectiveTags) {
                tagNames.add(tag.toString());
            }
            map.put("tags", tagNames);
        }

        // Step results
        List<Map<String, Object>> stepResultsList = new ArrayList<>();
        for (StepResult sr : stepResults) {
            stepResultsList.add(sr.toJson());
        }
        map.put("stepResults", stepResultsList);

        return map;
    }

    @Override
    public int compareTo(ScenarioResult other) {
        if (other == null) {
            return 1;
        }
        // Compare by section index first
        int sectionCmp = Integer.compare(
                this.scenario.getSection().getIndex(),
                other.scenario.getSection().getIndex()
        );
        if (sectionCmp != 0) {
            return sectionCmp;
        }
        // Then by example index (-1 means not an outline example)
        int exampleCmp = Integer.compare(
                this.scenario.getExampleIndex(),
                other.scenario.getExampleIndex()
        );
        if (exampleCmp != 0) {
            return exampleCmp;
        }
        // Finally by line number
        return Integer.compare(
                this.scenario.getLine(),
                other.scenario.getLine()
        );
    }

}
