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

import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.KarateException;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureResult {

    private final Feature feature;
    private final List<ScenarioResult> scenarioResults = new ArrayList<>();

    private String resultDate;
    private String displayName; // mutable for users who want to customize

    private Map<String, Object> resultVariables;
    private Map<String, Object> callArg;
    private int loopIndex = -1;
    private int callDepth;

    public FeatureResult(Feature feature) {
        this.feature = feature;
        displayName = feature.getResource().getRelativePath();
    }

    public void printStats() {
        String featureName = feature.getResource().getPrefixedPath();
        if (feature.getCallLine() != -1) {
            featureName = featureName + ":" + feature.getCallLine();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("---------------------------------------------------------\n");
        sb.append("feature: ").append(featureName).append('\n');
        sb.append(String.format("scenarios: %2d | passed: %2d | failed: %2d | time: %.4f\n", getScenarioCount(), getPassedCount(), getFailedCount(), getDurationMillis() / 1000));
        sb.append("---------------------------------------------------------\n");
        System.out.println(sb);
    }

    public List<File> getAllEmbedFiles() {
        List<File> files = new ArrayList();
        for (ScenarioResult sr : scenarioResults) {
            for (StepResult stepResult : sr.getStepResults()) {
                if (stepResult.getEmbeds() != null) {
                    for (Embed embed : stepResult.getEmbeds()) {
                        files.add(embed.getFile());
                    }
                }
            }
        }
        return files;
    }

    public static FeatureResult fromKarateJson(File workingDir, Map<String, Object> map) {
        String featurePath = (String) map.get("prefixedPath");
        Resource resource = ResourceUtils.getResource(workingDir, featurePath);
        Feature feature = Feature.read(resource);
        FeatureResult fr = new FeatureResult(feature);
        fr.callArg = (Map) map.get("callArg");
        fr.loopIndex = (Integer) map.get("loopIndex");
        fr.resultDate = (String) map.get("resultDate");
        fr.callDepth = (Integer) map.get("callDepth");
        List<Map<String, Object>> list = (List) map.get("scenarioResults");
        if (list != null) {
            for (Map<String, Object> srMap : list) {
                ScenarioResult sr = ScenarioResult.fromKarateJson(workingDir, feature, srMap);
                if (!sr.getStepResults().isEmpty()) {
                    fr.addResult(sr);
                }                
            }
        }
        return fr;
    }

    public Map<String, Object> toInfoJson() {
        Map<String, Object> map = new HashMap();
        map.put("name", feature.getName());
        map.put("description", feature.getDescription());
        map.put("prefixedPath", feature.getResource().getPrefixedPath());
        File file = feature.getResource().getFile();
        if (file != null) {
            map.put("fileName", file.getName());
            map.put("parentDir", file.getParent());
        }
        return map;
    }

    public Map<String, Object> toSummaryJson() {
        Map<String, Object> map = new HashMap();
        map.put("failed", isFailed());
        map.put("name", feature.getName());
        map.put("description", feature.getDescription());
        map.put("durationMillis", getDurationMillis());
        map.put("passedCount", getPassedCount());
        map.put("failedCount", getFailedCount());
        map.put("scenarioCount", getScenarioCount());
        map.put("packageQualifiedName", feature.getPackageQualifiedName());
        map.put("relativePath", feature.getResource().getRelativePath());
        return map;
    }

    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        // these first few are only for the ease of reports
        // note that they are not involved in the reverse fromKarateJson()
        map.put("name", feature.getName());
        map.put("description", feature.getDescription());
        map.put("durationMillis", getDurationMillis());
        map.put("passedCount", getPassedCount());
        map.put("failedCount", getFailedCount());
        map.put("packageQualifiedName", feature.getPackageQualifiedName());
        map.put("relativePath", feature.getResource().getRelativePath());
        //======================================================================
        if (resultDate == null) {
            resultDate = ReportUtils.getDateString();
        }
        map.put("resultDate", resultDate);
        map.put("prefixedPath", feature.getResource().getPrefixedPath());
        List<Map<String, Object>> list = new ArrayList(scenarioResults.size());
        map.put("scenarioResults", list);
        for (ScenarioResult sr : scenarioResults) {
            list.add(sr.toKarateJson());
        }
        if (callArg != null) {
            String json = JsonUtils.toJsonSafe(callArg, false);
            map.put("callArg", JsonUtils.fromJson(json));
        }
        map.put("loopIndex", loopIndex);
        map.put("callDepth", callDepth);
        return map;
    }

    public Map<String, Object> toCucumberJson() {
        Map<String, Object> map = new HashMap();
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
            map.put("tags", ScenarioResult.tagsToCucumberJson(feature.getTags()));
        }
        List<Map<String, Object>> list = new ArrayList(scenarioResults.size());
        map.put("elements", list);
        for (ScenarioResult sr : scenarioResults) {
            Map<String, Object> backgroundMap = sr.backgroundToCucumberJson();
            if (backgroundMap != null) {
                list.add(backgroundMap);
            }
            list.add(sr.toCucumberJson());
        }
        return map;
    }

    public List<StepResult> getAllScenarioStepResultsNotHidden() {
        List<StepResult> list = new ArrayList();
        for (ScenarioResult sr : scenarioResults) {
            list.addAll(sr.getStepResultsNotHidden());
        }
        return list;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Feature getFeature() {
        return feature;
    }

    public String getDisplayName() {
        return displayName;
    }

    public KarateException getErrorMessagesCombined() {
        List<String> errors = getErrors();
        if (errors.size() == 1) {
            return new KarateException(errors.get(0));
        }
        return new KarateException(getErrorMessages());
    }

    public String getErrorMessages() {
        return StringUtils.join(getErrors(), "\n");
    }

    public String getCallNameForReport() {
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

    public void setCallDepth(int callDepth) {
        this.callDepth = callDepth;
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
        long durationNanos = 0;
        for (ScenarioResult sr : scenarioResults) {
            durationNanos += sr.getDurationNanos();
        }
        return ReportUtils.nanosToMillis(durationNanos);
    }

    public int getFailedCount() {
        return getErrors().size();
    }

    public boolean isEmpty() {
        return scenarioResults.isEmpty();
    }

    public int getScenarioCount() {
        return scenarioResults.size();
    }

    public int getPassedCount() {
        return getScenarioCount() - getFailedCount();
    }

    public boolean isFailed() {
        return getFailedCount() > 0;
    }

    public List<String> getErrors() {
        List<String> errors = new ArrayList();
        for (ScenarioResult sr : scenarioResults) {
            if (sr.isFailed()) {
                errors.add(sr.getErrorMessage());
            }
        }
        return errors;
    }

    public void addResult(ScenarioResult result) {
        scenarioResults.add(result);
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
