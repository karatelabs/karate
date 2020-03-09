/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScenarioResult {

    private List<StepResult> stepResults = new ArrayList();
    private final Scenario scenario;

    private StepResult failedStep;

    private String threadName;
    private long startTime;
    private long endTime;
    private long durationNanos;

    private Map<String, Object> backgroundJson;
    private Map<String, Object> json;

    public void reset() {
        stepResults = new ArrayList();
        failedStep = null;
    }

    public void appendEmbed(Embed embed) {
        if (json != null) {
            List<Map<String, Object>> steps = (List) json.get("steps");
            if (steps == null || steps.isEmpty()) {
                return;
            }
            Map<String, Object> map = steps.get(steps.size() - 1);
            List<Map<String, Object>> embedList = (List) map.get("embeddings");
            if (embedList == null) {
                embedList = new ArrayList();
                map.put("embeddings", embedList);
            }
            embedList.add(embed.toMap());
        } else {
            getLastStepResult().addEmbed(embed);
        }
    }

    public StepResult getLastStepResult() {
        if (stepResults.isEmpty()) {
            return null;
        }
        return stepResults.get(stepResults.size() - 1);
    }

    public StepResult getStepResult(int index) {
        if (stepResults.size() > index) {
            return stepResults.get(index);
        } else {
            return null;
        }
    }

    public void setStepResult(int index, StepResult sr) {
        if (sr.getResult().isFailed()) {
            failedStep = sr;
        }
        if (stepResults.size() > index) {
            stepResults.set(index, sr);
        } else {
            for (int i = stepResults.size(); i < index; i++) {
                stepResults.add(null);
            }
            stepResults.add(sr);
        }
    }

    public String getFailureMessageForDisplay() {
        if (failedStep == null) {
            return null;
        }
        // val message = feature + ":" + step.getLine + " " + result.getStep.getText
        Step step = failedStep.getStep();
        String featureName = scenario.getFeature().getResource().getRelativePath();
        return featureName + ":" + step.getLine() + " " + step.getText();
    }

    public void addError(String message, Throwable error) {
        Step step = new Step(scenario.getFeature(), scenario, -1);
        step.setLine(scenario.getLine());
        step.setPrefix("*");
        step.setText(message);
        StepResult sr = new StepResult(step, Result.failed(0, error, step), null, null, null);
        addStepResult(sr);
    }

    public void addStepResult(StepResult stepResult) {
        stepResults.add(stepResult);
        Result result = stepResult.getResult();
        durationNanos += result.getDurationNanos();
        if (result.isFailed()) {
            failedStep = stepResult;
        }
    }

    private static void recurse(List<Map> list, StepResult stepResult, int depth) {
        if (stepResult.getCallResults() != null) {
            for (FeatureResult fr : stepResult.getCallResults()) {
                Step call = new Step(stepResult.getStep().getFeature(), stepResult.getStep().getScenario(), -1);
                call.setLine(stepResult.getStep().getLine());
                call.setPrefix(StringUtils.repeat('>', depth));
                call.setText(fr.getCallName());
                call.setDocString(fr.getCallArgPretty());
                StepResult callResult = new StepResult(call, Result.passed(0), null, null, null);
                callResult.setHidden(stepResult.isHidden());
                list.add(callResult.toMap());
                for (StepResult sr : fr.getAllScenarioStepResultsNotHidden()) {
                    Map<String, Object> map = sr.toMap();
                    String temp = (String) map.get("keyword");
                    map.put("keyword", StringUtils.repeat('>', depth + 1) + ' ' + temp);
                    list.add(map);
                    recurse(list, sr, depth + 1);
                }
            }
        }
    }

    private List<Map> getStepResults(boolean background) {
        List<Map> list = new ArrayList(stepResults.size());
        for (StepResult stepResult : stepResults) {
            if (stepResult.isHidden()) {
                continue;
            }
            if (background == stepResult.getStep().isBackground()) {
                list.add(stepResult.toMap());
                recurse(list, stepResult, 0);
            }
        }
        return list;
    }

    public Map<String, Object> backgroundToMap() {
        if (backgroundJson != null) {
            return backgroundJson;
        }
        if (!scenario.getFeature().isBackgroundPresent()) {
            return null;
        }
        Map<String, Object> map = new HashMap();
        map.put("name", "");
        map.put("steps", getStepResults(true));
        map.put("line", scenario.getFeature().getBackground().getLine());
        map.put("description", "");
        map.put("type", Background.TYPE);
        map.put("keyword", Background.KEYWORD);
        return map;
    }

    public Map<String, Object> toMap() {
        if (json != null) {
            return json;
        }
        Map<String, Object> map = new HashMap();
        map.put("name", scenario.getName());
        map.put("steps", getStepResults(false));
        map.put("line", scenario.getLine());
        map.put("id", StringUtils.toIdString(scenario.getName()));
        map.put("description", scenario.getDescription());
        map.put("type", Scenario.TYPE);
        map.put("keyword", scenario.getKeyword());
        if (scenario.getTags() != null) {
            map.put("tags", Tags.toResultList(scenario.getTags()));
        }
        return map;
    }

    public ScenarioResult(Scenario scenario, List<StepResult> stepResults) {
        this.scenario = scenario;
        if (stepResults != null) {
            this.stepResults.addAll(stepResults);
        }
    }

    private void addStepsFromJson(Map<String, Object> parentJson) {
        if (parentJson == null) {
            return;
        }
        List<Map<String, Object>> list = (List) parentJson.get("steps");
        if (list == null) {
            return;
        }
        for (Map<String, Object> stepMap : list) {
            addStepResult(new StepResult(stepMap));
        }
    }

    // for converting cucumber-json to result server-executor mode
    public ScenarioResult(Scenario scenario, List<Map<String, Object>> jsonList, boolean dummy) {
        this.scenario = scenario;
        if (jsonList != null && !jsonList.isEmpty()) {
            if (jsonList.size() > 1) {
                backgroundJson = jsonList.get(0);
                json = jsonList.get(1);
            } else {
                json = jsonList.get(0);
            }
            addStepsFromJson(backgroundJson);
            addStepsFromJson(json);
        }
    }

    public Scenario getScenario() {
        return scenario;
    }

    public List<StepResult> getStepResults() {
        return stepResults;
    }

    public List<StepResult> getStepResultsNotHidden() {
        List<StepResult> list = new ArrayList(stepResults.size());
        for (StepResult sr : stepResults) {
            if (sr.isHidden()) {
                continue;
            }
            list.add(sr);
        }
        return list;
    }

    public boolean isFailed() {
        return failedStep != null;
    }

    public StepResult getFailedStep() {
        return failedStep;
    }

    public Throwable getError() {
        return failedStep == null ? null : failedStep.getResult().getError();
    }

    public long getDurationNanos() {
        return durationNanos;
    }
    
    public double getDurationMillis() {
        return Engine.nanosToMillis(durationNanos);
    }    

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

}
