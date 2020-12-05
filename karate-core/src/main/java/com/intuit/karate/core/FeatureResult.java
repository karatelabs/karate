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
import com.intuit.karate.JsonUtils;
import com.intuit.karate.KarateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureResult {

    private final Feature feature;
    private final List<ScenarioResult> scenarioResults = new ArrayList<>();

    private String displayName; // mutable for users who want to customize
    private List<Throwable> errors = new ArrayList<>();
    private double durationMillis;

    private Map<String, Object> resultVariables;
    private Map<String, Object> callArg;
    private int loopIndex;

    public void printStats(String reportPath) {
        String featureName = feature.getResource().getPrefixedPath();
        if (feature.getCallLine() != -1) {
            featureName = featureName + ":" + feature.getCallLine();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("---------------------------------------------------------\n");
        sb.append("feature: ").append(featureName).append('\n');
        if (reportPath != null) {
            sb.append("report: ").append(reportPath).append('\n');
        }
        sb.append(String.format("scenarios: %2d | passed: %2d | failed: %2d | time: %.4f\n", getScenarioCount(), getPassedCount(), getFailedCount(), durationMillis / 1000));
        sb.append("---------------------------------------------------------\n");
        System.out.println(sb);
    }

    public String toCucumberJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        Iterator<ScenarioResult> iterator = scenarioResults.iterator();
        while (iterator.hasNext()) {
            ScenarioResult sr = iterator.next();
            Map<String, Object> backgroundMap = sr.backgroundToMap();
            if (backgroundMap != null) {
                sb.append(JsonUtils.toJson(backgroundMap)).append(',');
            }
            sb.append(JsonUtils.toJson(sr.toMap()));
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
        sb.append(']');
        Map<String, Object> map = new HashMap();
        map.put("elements", "@@replace@@");
        map.put("keyword", Feature.KEYWORD);
        map.put("line", feature.getLine());
        map.put("uri", displayName);
        map.put("name", displayName);
        map.put("id", StringUtils.toIdString(feature.getName()));
        String temp = feature.getName() == null ? "" : feature.getName();
        if (feature.getDescription() != null) {
            temp = temp + "\n" + feature.getDescription();
        }
        map.put("description", temp.trim());
        if (feature.getTags() != null) {
            map.put("tags", Tags.toResultList(feature.getTags()));
        }
        return JsonUtils.toJson(map).replace("\"@@replace@@\"", sb);
    }

    public List<StepResult> getAllScenarioStepResultsNotHidden() {
        List<StepResult> list = new ArrayList();
        for (ScenarioResult sr : scenarioResults) {
            list.addAll(sr.getStepResultsNotHidden());
        }
        return list;
    }

    public FeatureResult(Feature feature) {
        this.feature = feature;
        displayName = feature.getResource().getRelativePath();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Feature getFeature() {
        return feature;
    }

    public String getPackageQualifiedName() {
        return feature.getResource().getPackageQualifiedName();
    }

    public String getDisplayUri() {
        return displayName;
    }

    public KarateException getErrorsCombined() {
        if (errors == null) {
            return null;
        }
        if (errors.size() == 1) {
            Throwable error = errors.get(0);
            if (error instanceof KarateException) {
                return (KarateException) error;
            } else {
                return new KarateException("call failed", error);
            }
        }
        return new KarateException(getErrorMessages());
    }

    public String getErrorMessages() {
        StringBuilder sb = new StringBuilder();
        Iterator<Throwable> iterator = errors.iterator();
        while (iterator.hasNext()) {
            Throwable error = iterator.next();
            sb.append(error.getMessage());
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public String getCallName() {
        String append = loopIndex == -1 ? "" : "[" + loopIndex + "] ";
        return append + displayName;
    }

    public String getCallArgPretty() {
        if (callArg == null) {
            return null;
        }
        try {
            return JsonUtils.toJsonSafe(callArg, true);
        } catch (Throwable t) {
            return "#error: " + t.getMessage();
        }
    }

    public Map<String, Object> getCallArg() {
        return callArg;
    }

    public void setCallArg(Map<String, Object> callArg) {
        this.callArg = callArg;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    public double getDurationMillis() {
        return durationMillis;
    }

    public int getFailedCount() {
        return getErrors().size();
    }

    public boolean isEmpty() {
        return getScenarioCount() == 0;
    }

    public int getScenarioCount() {
        return getScenarioResults().size();
    }

    public int getPassedCount() {
        return getScenarioCount() - getFailedCount();
    }

    public boolean isFailed() {
        return getFailedCount() > 0;
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    private void addError(Throwable error) {
        errors.add(error);
    }

    public void addResult(ScenarioResult result) {
        scenarioResults.add(result);
        durationMillis += Engine.nanosToMillis(result.getDurationNanos());
        if (result.isFailed()) {
            Scenario scenario = result.getScenario();
            if (scenario.isOutline()) {
                Throwable error = result.getError();
                Throwable copy = new KarateException(scenario.getDisplayMeta() + " " + error.getMessage());
                copy.setStackTrace(error.getStackTrace());
                addError(copy);
            } else {
                addError(result.getError());
            }
        }
    }

    public void setVariables(Map<String, Object> resultVariables) {
        this.resultVariables = resultVariables;
    }

    public Map<String, Object> getVariables() {
        return resultVariables;
    }

    public void sortScenarioResults() {
        Collections.sort(scenarioResults);
    }

    public List<ScenarioResult> getScenarioResults() {
        return scenarioResults;
    }

    @Override
    public String toString() {
        return displayName;
    }

}
