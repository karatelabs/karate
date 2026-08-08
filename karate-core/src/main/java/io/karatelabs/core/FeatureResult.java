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
import java.util.Optional;

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

    public int getSkippedCount() {
        return (int) scenarioResults.stream().filter(ScenarioResult::isSkipped).count();
    }

    /**
     * Pass rate as an integer percentage 0–100, or null when no scenarios executed.
     * Denominator is {@code passedCount + failedCount} — mirrors the HTML report's
     * per-feature calc so JSONL consumers and the HTML view agree.
     */
    public Integer getPassedRate() {
        int passed = getPassedCount();
        int executed = passed + getFailedCount();
        return executed == 0 ? null : (int) Math.round((passed * 100.0) / executed);
    }

    public boolean isPassed() {
        return scenarioResults.stream().noneMatch(ScenarioResult::isFailed);
    }

    /**
     * Drop the nested {@code karate.call()} result trees hanging off this feature's steps.
     *
     * <p>Every call attaches the callee's entire {@link FeatureResult} to the calling
     * {@link StepResult}, and a {@link SuiteResult} holds every feature for the whole run —
     * so without this the live set grows with total scenarios times calls per scenario, and
     * a long suite exhausts the heap on retention alone rather than on anything it is
     * actually doing.
     *
     * <p>Called once a feature has completed and every listener has consumed it, so the
     * HTML report — which extracts its page model synchronously — keeps full nested step
     * detail. What is lost is the ability to walk into a called feature's steps from a
     * {@code SuiteResult} <em>after</em> the run; {@code Runner.Builder.retainCallResults(true)}
     * keeps that for callers who need it.
     *
     * <p>Only the nesting is dropped. Each step's own status, timing, log and embeds are
     * untouched, so summaries, failure messages and the report itself are unaffected.
     */
    public void releaseCallResults() {
        for (ScenarioResult scenarioResult : scenarioResults) {
            for (StepResult stepResult : scenarioResult.getStepResults()) {
                stepResult.setCallResults(null);
            }
        }
    }

    /**
     * Drop each step's captured log text and embedded assets, once every consumer has had them.
     *
     * <p><b>This is the bulk of what a suite retains.</b> The log holds the rendered HTTP request
     * and response for every step — bodies included — and an {@link StepResult.Embed} holds raw
     * {@code byte[]}, which for a UI suite means a screenshot per failed step. Both are kept only
     * so the report writers can render them, yet a {@link SuiteResult} holds every feature until
     * the run ends, so they stay reachable for hours after the last thing that reads them.
     * Measured on a 350,000-scenario soak with all four report formats on: ~3.2 KB retained per
     * scenario, of which roughly 70% was this text — and that share <em>grows</em> with payload
     * size, which is why a suite of scenarios making several calls with large bodies is where
     * this stops being footprint and becomes an OOM.
     *
     * <p>Called from the same place as {@link #releaseCallResults()} and under the same
     * precondition — after {@code onFeatureEnd}, so HTML, Cucumber JSON and JUnit XML have all
     * serialized the feature, and after {@code FEATURE_EXIT}, which is where the JSONL stream
     * carries {@code toJson()} with the embeds inline. Nothing downstream reads either field
     * later; what is lost is the ability to inspect a step's log or embeds from a
     * {@code SuiteResult} <em>after</em> the run, which {@code Runner.Builder.retainStepLogs(true)}
     * keeps for callers who need it.
     *
     * <p>Status, timing, failure messages and counts are untouched, so summaries, the console
     * output and every report are unaffected.
     */
    public void releaseStepLogs() {
        for (ScenarioResult scenarioResult : scenarioResults) {
            for (StepResult stepResult : scenarioResult.getStepResults()) {
                stepResult.releaseLogAndEmbeds();
            }
        }
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
        return firstFailedScenario().map(ScenarioResult::getFailureMessage).orElse(null);
    }

    /**
     * The failed step's {@code path/to/feature.feature:LINE step text}, or null when there is no
     * failure. See {@link ScenarioResult#getFailureMessageForDisplay()}.
     */
    public String getFailureMessageForDisplay() {
        return firstFailedScenario().map(ScenarioResult::getFailureMessageForDisplay).orElse(null);
    }

    /**
     * The failure reason without the Gherkin comment label, or null when there is no failure.
     * See {@link ScenarioResult#getFailureReason()}.
     */
    public String getFailureReason() {
        return firstFailedScenario().map(ScenarioResult::getFailureReason).orElse(null);
    }

    /**
     * The one-line "why" of the failure, e.g. {@code "match failed: EQUALS"}, or null when there
     * is no failure. See {@link ScenarioResult#getFailureReasonSummary()}.
     */
    public String getFailureReasonSummary() {
        return firstFailedScenario().map(ScenarioResult::getFailureReasonSummary).orElse(null);
    }

    /**
     * The Gherkin comment above the failed step (the assertion label), or null when there is none.
     * See {@link ScenarioResult#getFailedStepComment()}.
     */
    public String getFailedStepComment() {
        return firstFailedScenario().map(ScenarioResult::getFailedStepComment).orElse(null);
    }

    /**
     * The failed step's line number, or -1 when there is no failure or the failure is not tied to
     * a parsed step (hook / synthetic). Lets a caller that already knows the feature path — the
     * Gatling executor logs the path exactly as the simulation declared it — append {@code :LINE}
     * without swapping in the absolute path.
     */
    public int getFailedStepLine() {
        return firstFailedScenario().map(ScenarioResult::getFailedStepLine).orElse(-1);
    }

    private Optional<ScenarioResult> firstFailedScenario() {
        return scenarioResults.stream().filter(ScenarioResult::isFailed).findFirst();
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
        map.put("skippedCount", getSkippedCount());
        map.put("passedRate", getPassedRate());

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
        printSummary(false);
    }

    /** @param dryRun parse-only ({@code -D}) — every scenario is skipped and skips count toward
     *  {@code passed}, so the status word must say "parsed", not "passed". */
    public void printSummary(boolean dryRun) {
        String path = getDisplayName();
        int passed = getPassedCount();
        int failed = getFailedCount();
        int skipped = getSkippedCount();
        int total = getScenarioCount();
        double secs = getDurationMillis() / 1000.0;

        String status = failed > 0
                ? Console.red(failed + " failed")
                : dryRun ? Console.yellow("parsed, not run") : Console.green("passed");

        String featureLine = failed > 0
                ? Console.red(path)
                : Console.green(path);

        Console.println(Console.grey("-".repeat(57)));
        Console.println("feature: " + featureLine);
        if (skipped > 0) {
            Console.println(String.format("scenarios: %2d | passed: %2d | skipped: %2d | %s | time: %.4f",
                    total, passed, skipped, status, secs));
        } else {
            Console.println(String.format("scenarios: %2d | passed: %2d | %s | time: %.4f",
                    total, passed, status, secs));
        }
        Console.println(Console.grey("=".repeat(57)));
    }

}
