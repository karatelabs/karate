/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.cucumber;

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptEnv;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.CucumberScenarioOutline;
import cucumber.runtime.model.CucumberTagStatement;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class FeatureWrapper {

    private final String path;
    private final String callTag;
    private final String text;
    private final List<String> lines;
    private final CucumberFeature feature;
    private final List<FeatureSection> featureSections;

    private ScriptEnv scriptEnv;

    public ScriptEnv getEnv() {
        return scriptEnv;
    }

    public String getCallTag() {
        return callTag;
    }

    public void setEnv(ScriptEnv scriptEnv) {
        this.scriptEnv = scriptEnv;
    }

    public static FeatureWrapper fromFileAndTag(File file, String callTag) {
        return fromFile(file, callTag, Thread.currentThread().getContextClassLoader(), null);
    }

    public static FeatureWrapper fromFile(File file, String callTag, KarateReporter reporter) {
        return fromFile(file, callTag, Thread.currentThread().getContextClassLoader(), reporter);
    }

    public static FeatureWrapper fromFile(File file, String callTag, ClassLoader classLoader, KarateReporter reporter) {
        String text = FileUtils.toString(file);
        ScriptEnv env = new ScriptEnv(null, null, file.getParentFile(), file.getName(), classLoader, reporter);
        return new FeatureWrapper(text, env, file.getPath(), callTag);
    }

    public static FeatureWrapper fromFile(File file, ScriptEnv env) {
        String text = FileUtils.toString(file);
        return new FeatureWrapper(text, env, file.getPath(), null);
    }

    public static FeatureWrapper fromString(String text, ScriptEnv scriptEnv, String path, String callTag) {
        return new FeatureWrapper(text, scriptEnv, path, callTag);
    }

    public static FeatureWrapper fromStream(InputStream is, ScriptEnv scriptEnv, String path) {
        String text = FileUtils.toString(is);
        return new FeatureWrapper(text, scriptEnv, path, null);
    }

    public String joinLines(int startLine, int endLine) {
        StringBuilder sb = new StringBuilder();
        if (endLine > lines.size()) {
            endLine = lines.size();
        }
        for (int i = startLine; i < endLine; i++) {
            String line = lines.get(i);
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public String joinLines() {
        int lineCount = lines.size();
        return joinLines(0, lineCount);
    }

    public List<String> getLines() {
        return lines;
    }

    public String getFirstScenarioName() {
        if (featureSections == null || featureSections.isEmpty()) {
            return null;
        }
        FeatureSection fs = featureSections.get(0);
        if (fs.isOutline()) {
            return fs.getScenarioOutline().getScenarioOutline().getGherkinModel().getName();
        } else {
            return fs.getScenario().getScenario().getGherkinModel().getName();
        }
    }

    public CucumberFeature getFeature() {
        return feature;
    }

    public List<FeatureSection> getSections() {
        return featureSections;
    }

    public List<FeatureSection> getSectionsByCallTag() {
        if (callTag == null) {
            return featureSections;
        }
        List<FeatureSection> filtered = new ArrayList(featureSections.size());
        for (FeatureSection fs : getSections()) {
            List<String> tags = fs.getTags();
            if (tags == null || tags.isEmpty()) {
                continue;
            }
            for (String tag : tags) {
                if (callTag.equals(tag)) {
                    filtered.add(fs);
                }
            }
        }
        return filtered;
    }

    public String getPath() {
        return path;
    }

    public String getText() {
        return text;
    }

    public FeatureWrapper addLine(int index, String line) {
        lines.add(index, line);
        return new FeatureWrapper(joinLines(), scriptEnv, path, callTag);
    }

    public FeatureSection getSection(int sectionIndex) {
        return featureSections.get(sectionIndex);
    }

    public ScenarioWrapper getScenario(int sectionIndex, int scenarioIndex) {
        FeatureSection section = getSection(sectionIndex);
        if (scenarioIndex == -1) {
            return section.getScenario();
        }
        ScenarioOutlineWrapper outline = section.getScenarioOutline();
        return outline.getScenarios().get(scenarioIndex);

    }

    public StepWrapper getStep(int sectionIndex, int scenarioIndex, int stepIndex) {
        ScenarioWrapper scenario = getScenario(sectionIndex, scenarioIndex);
        return scenario.getSteps().get(stepIndex);
    }

    public FeatureWrapper replaceStep(StepWrapper step, String text) {
        return replaceLines(step.getStartLine(), step.getEndLine(), text);
    }

    public FeatureWrapper replaceLines(int start, int end, String text) {
        for (int i = start; i < end; i++) {
            lines.remove(start);
        }
        lines.set(start, text);
        return new FeatureWrapper(joinLines(), scriptEnv, path, callTag);
    }

    public FeatureWrapper removeLine(int index) {
        lines.remove(index);
        return new FeatureWrapper(joinLines(), scriptEnv, path, callTag);
    }

    public FeatureWrapper replaceText(String newText) {
        return new FeatureWrapper(newText, scriptEnv, path, callTag);
    }

    private FeatureWrapper(String text, ScriptEnv scriptEnv, String path, String callTag) {
        this.path = path;
        this.callTag = callTag;
        this.text = text;
        this.scriptEnv = scriptEnv;
        this.feature = CucumberUtils.parse(text, path);
        this.lines = FileUtils.toStringLines(text);
        featureSections = new ArrayList<>();
        List<CucumberTagStatement> elements = feature.getFeatureElements();
        int count = elements.size();
        for (int i = 0; i < count; i++) {
            CucumberTagStatement cts = elements.get(i);
            if (cts instanceof CucumberScenario) {
                CucumberScenario sw = (CucumberScenario) cts;
                ScenarioWrapper scenario = new ScenarioWrapper(this, -1, sw, null);
                FeatureSection section = new FeatureSection(i, this, scenario, null);
                featureSections.add(section);
            } else if (cts instanceof CucumberScenarioOutline) {
                CucumberScenarioOutline cso = (CucumberScenarioOutline) cts;
                ScenarioOutlineWrapper scenarioOutline = new ScenarioOutlineWrapper(this, cso);
                FeatureSection section = new FeatureSection(i, this, null, scenarioOutline);
                featureSections.add(section);
            } else {
                throw new RuntimeException("unexpected type: " + cts.getClass());
            }
        }
    }

}
