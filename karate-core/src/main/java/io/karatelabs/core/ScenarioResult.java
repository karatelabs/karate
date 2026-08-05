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

    public static final String SUPPRESSED_FAILURE_MESSAGE =
            "output suppressed by @report=false (full detail in runtime logs)";

    private final Scenario scenario;
    private final List<StepResult> stepResults = new ArrayList<>();
    private long startTime;
    private long endTime;
    private String threadName;
    private boolean failTagApplied;
    private boolean aborted;
    // True when the scenario carried @report=false (or inherited it from a calling
    // scenario). Step detail is suppressed in HTML/Cucumber-JSON/JUnit/JSONL writers;
    // failures surface only a redacted message so secrets don't leak into CI artifacts.
    private boolean reportDisabled;
    // Author-set __id (a sibling of __row/__num), resolved by ScenarioRuntime at
    // scenario end while the engine is still live. When non-null it is the
    // scenario's stable identity, overriding the derived slug verbatim on both the
    // SCENARIO_EXIT event and the FEATURE_EXIT payload; null means derive the slug
    // from feature path + scenario name. See RunUtils / ScenarioRunEvent.
    private String stableId;

    public ScenarioResult(Scenario scenario) {
        this.scenario = scenario;
    }

    /** The author-set {@code __id} stable identity, or null when unset (derive the slug). */
    public String getStableId() {
        return stableId;
    }

    /** Set by {@link ScenarioRuntime} from the live {@code __id} variable before SCENARIO_EXIT fires. */
    public void setStableId(String stableId) {
        this.stableId = stableId;
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

    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    public boolean isReportDisabled() {
        return reportDisabled;
    }

    public void setReportDisabled(boolean reportDisabled) {
        this.reportDisabled = reportDisabled;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public List<StepResult> getStepResults() {
        return stepResults;
    }

    /** Drop this scenario's nested call trees. See {@link FeatureResult#releaseCallResults()}. */
    public void releaseCallResults() {
        for (StepResult stepResult : stepResults) {
            stepResult.setCallResults(null);
        }
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

    /**
     * True when this scenario was aborted via {@code karate.abort()} or had no step pass and
     * no step fail (suite-aborted pre-run or empty scenario). Reported additively alongside
     * {@link #isPassed()}; a skipped scenario is still counted as passed (preserves existing
     * count semantics). Surfaces in reports via a synthetic {@code @skipped} tag.
     */
    public boolean isSkipped() {
        if (failTagApplied || isFailed()) {
            return false;
        }
        if (aborted) {
            return true;
        }
        return stepResults.stream().noneMatch(s -> s.isPassed() || s.isFailed());
    }

    public long getDurationMillis() {
        return endTime - startTime;
    }

    public long getDurationNanos() {
        return stepResults.stream()
                .mapToLong(StepResult::getDurationNanos)
                .sum();
    }

    /** The first failed step, or null if the scenario has no failure. Shared by every failed-step accessor. */
    private StepResult firstFailedStep() {
        return stepResults.stream()
                .filter(StepResult::isFailed)
                .findFirst()
                .orElse(null);
    }

    public String getFailureMessage() {
        StepResult failedStep = firstFailedStep();
        return failedStep == null ? null : failedStep.getErrorMessage();
    }

    /**
     * The feature file path (without the {@code :line} suffix) of the failed step, preferring the
     * absolute path for IDE click-to-navigate and falling back to the relative/classpath path.
     * Null when there is no failure or the failure is not tied to a parsed step (hook/synthetic).
     */
    private String failedFeaturePath() {
        StepResult failedStep = firstFailedStep();
        if (failedStep == null || failedStep.getStep() == null) {
            return null;
        }
        var resource = scenario.getFeature().getResource();
        return resource.getPath() != null
                ? resource.getPath().toString()
                : resource.getRelativePath();
    }

    /**
     * Get the failed step location for IDE navigation.
     * Format: "path/to/feature.feature:LINE" (standard IDE clickable format)
     * Uses absolute path when available for better IDE compatibility.
     *
     * @return location string "path:line", or null if no failure
     */
    public String getFailedStepLocation() {
        String featurePath = failedFeaturePath();
        if (featurePath == null) {
            return null;
        }
        // Gherkin line numbers are 1-indexed (human-readable)
        return featurePath + ":" + firstFailedStep().getStep().getLine();
    }

    /**
     * The 1-indexed line of the failed step, or -1 when there is no failure or the failure is not
     * tied to a parsed step (hook / synthetic).
     */
    public int getFailedStepLine() {
        StepResult failedStep = firstFailedStep();
        if (failedStep == null || failedStep.getStep() == null) {
            return -1;
        }
        return failedStep.getStep().getLine();
    }

    /**
     * The Gherkin comment immediately above the failed step (the assertion label), e.g.
     * {@code "# user profile should match expected values"}, or null if the step has no comment.
     * The console summary renders this above the step line — where it sits in the feature — and
     * strips the same label from the front of the failure message so it isn't shown twice.
     */
    public String getFailedStepComment() {
        StepResult failedStep = firstFailedStep();
        if (failedStep == null || failedStep.getStep() == null) {
            return null;
        }
        List<String> comments = failedStep.getStep().getComments();
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        return comments.getLast();
    }

    /**
     * Get the Gherkin text of the failed step, including its prefix (e.g. "* match c == null").
     * Useful for error output so the reader can see the offending source line without
     * opening the feature file.
     *
     * @return "<prefix> <text>", or null if no failure
     */
    public String getFailedStepText() {
        StepResult failedStep = firstFailedStep();
        if (failedStep == null) {
            return null;
        }
        Step step = failedStep.getStep();
        if (step == null || step.getText() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (step.getPrefix() != null) {
            sb.append(step.getPrefix()).append(' ');
        }
        // keyword (match/def/etc.) is stored separately from the step body
        if (step.getKeyword() != null) {
            sb.append(step.getKeyword()).append(' ');
        }
        sb.append(step.getText());
        return sb.toString();
    }

    /**
     * The docstring (triple-quoted block) of the failed step, rendered with its {@code """}
     * delimiters exactly as it appears in the feature, or null if the step has none. Rendered
     * under the step line in the console summary so a {@code match}/{@code assert} whose RHS is a
     * docstring (e.g. {@code * match actual ==} followed by a JSON block) shows the expected block
     * instead of a step line that looks truncated. Kept separate from {@link #getFailedStepText()}
     * so the single-line step text still feeds the stack-frame label and perf/KO messages.
     */
    public String getFailedStepDocString() {
        StepResult failedStep = firstFailedStep();
        if (failedStep == null || failedStep.getStep() == null) {
            return null;
        }
        String docString = failedStep.getStep().getDocString();
        if (docString == null) {
            return null;
        }
        return "\"\"\"\n" + docString + "\n\"\"\"";
    }

    /**
     * Get the failure message with feature file path and line number for display.
     * Format: "path/to/feature.feature:LINE step text"
     *
     * @return formatted failure message, or null if no failure
     */
    public String getFailureMessageForDisplay() {
        String stepText = getFailedStepText();
        if (stepText == null) {
            return null;
        }
        String location = getFailedStepLocation();
        return location != null ? location + " " + stepText : stepText;
    }

    /**
     * The failure message with the Gherkin comment label stripped from the front. The label is
     * prepended to {@code match}/{@code assert} messages so it travels with the error, but every
     * surface that renders the comment separately (the console summary, the Gatling failure log)
     * would otherwise show it twice.
     *
     * @return the failure reason, or null if there is no failure
     */
    public String getFailureReason() {
        String message = getFailureMessage();
        String comment = getFailedStepComment();
        if (comment != null && message != null && message.startsWith(comment + "\n")) {
            return message.substring(comment.length() + 1);
        }
        return message;
    }

    /** Cap on the one-line reason — see {@link #getFailureReasonSummary()}. */
    private static final int REASON_SUMMARY_MAX = 200;

    /**
     * The first line of {@link #getFailureReason()} — e.g. {@code "match failed: EQUALS"} — capped
     * at {@value #REASON_SUMMARY_MAX} characters, or null if there is no failure. The one-line
     * "why" for surfaces that must stay short: a log summary line, or a KO message that a
     * performance tool groups by.
     */
    public String getFailureReasonSummary() {
        String reason = getFailureReason();
        if (reason == null) {
            return null;
        }
        // the first line that actually says something — a message that opens with a blank line
        // would otherwise report no reason at all, when there plainly is one
        String firstLine = reason.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(null);
        if (firstLine == null) {
            return null;
        }
        return firstLine.length() > REASON_SUMMARY_MAX
                ? firstLine.substring(0, REASON_SUMMARY_MAX) + "..."
                : firstLine;
    }

    /**
     * The failure rendered for a performance-tool KO message:
     * {@code "path/to/feature.feature:LINE step text - reason"}.
     * <p>
     * Only {@link #getFailureReasonSummary() the reason's first line} is included. Gatling groups
     * its errors table by this exact string, so the full match diff — which embeds the actual
     * values, different for every virtual user — would make each KO a unique row and bloat
     * {@code simulation.log}. The full detail belongs in the failure log instead. Falls back to the
     * reason alone when the failure is not tied to a parsed step (hook / synthetic), and null when
     * there is no failure.
     */
    public String getPerfFailureMessage() {
        String reason = getFailureReasonSummary();
        String display = getFailureMessageForDisplay();
        if (display == null) {
            return reason;
        }
        return reason == null ? display : display + " - " + reason;
    }

    public Throwable getError() {
        StepResult failedStep = firstFailedStep();
        return failedStep == null ? null : failedStep.getError();
    }

    /**
     * The failure decorated so the feature-file location travels with the {@link Throwable}
     * itself — for surfaces that re-throw or dump the error raw (JUnit / surefire, the fluent
     * {@code Runner} API). Unlike {@link #getError()} (the untouched step error), the returned
     * {@link KarateException} carries {@code path.feature:line} on its own message line (the
     * IDE-hyperlinkable shape) and a synthetic {@code <feature>} frame on top of the preserved
     * original stack. Falls back to the raw error when the failure is not tied to a parsed step
     * (hook / synthetic), and null when there is no failure.
     *
     * @see io.karatelabs.core.KarateException#forStep
     */
    public Throwable getErrorWithLocation() {
        StepResult failedStep = firstFailedStep();
        if (failedStep == null) {
            return null;
        }
        Throwable error = failedStep.getError();
        String featurePath = failedFeaturePath();
        if (error == null || featurePath == null) {
            return error;
        }
        return KarateException.forStep(error, featurePath, failedStep.getStep().getLine(), getFailedStepText());
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
        // Core identity + metadata (name, slug, description, line, indices,
        // exampleData) — the SAME routine that backs the karate.scenario JS API,
        // so the two never drift. An author-set __id wins as the slug (rename-proof,
        // per-row for outline examples), mirroring the SCENARIO_EXIT event's slug so
        // streaming and FEATURE_EXIT consumers agree on one identity.
        Map<String, Object> map = RunUtils.scenarioIdentity(scenario, stableId);

        // Status
        map.put("passed", isPassed());
        map.put("failed", isFailed());
        map.put("skipped", isSkipped());
        map.put("durationMillis", getDurationMillis());
        if (isFailed()) {
            // @report=false redacts the failure message so secrets don't leak into HTML /
            // JUnit XML / JSONL artifacts (which often get uploaded to CI). Full detail
            // still hits runtime logs via SLF4J for local debugging.
            map.put("error", reportDisabled ? SUPPRESSED_FAILURE_MESSAGE : getFailureMessage());
        }
        if (reportDisabled) {
            map.put("reportDisabled", true);
        }

        // RefId and outline flag (the identity routine already carries the indices + exampleData)
        map.put("refId", scenario.getRefId());
        map.put("isOutlineExample", scenario.isOutlineExample());

        // Execution info
        map.put("executorName", threadName);
        map.put("startTime", startTime);
        map.put("endTime", endTime);

        // Tags (effective = merged feature + scenario); synthetic 'skipped' added when isSkipped().
        // Emit the @-less text form (Tag.getText()) so machine consumers see tags identically to
        // the event stream (FeatureRunEvent / ScenarioRunEvent use RunUtils.tagTexts, also @-less);
        // the @-prefixed display form is reserved for the HTML / Cucumber-JSON / JUnit writers.
        List<Tag> effectiveTags = scenario.getTagsEffective();
        List<String> tagNames = new ArrayList<>();
        if (effectiveTags != null) {
            for (Tag tag : effectiveTags) {
                tagNames.add(tag.getText());
            }
        }
        if (isSkipped() && !tagNames.contains("skipped")) {
            tagNames.add("skipped");
        }
        if (!tagNames.isEmpty()) {
            map.put("tags", tagNames);
        }

        // Step results — emit empty list when @report=false so HTML / Cucumber JSON /
        // JsonLines all consistently show no step detail. Pass/fail status above is
        // preserved so summary counts still reflect this scenario's outcome.
        List<Map<String, Object>> stepResultsList = new ArrayList<>();
        if (!reportDisabled) {
            for (StepResult sr : stepResults) {
                stepResultsList.add(sr.toJson());
            }
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
