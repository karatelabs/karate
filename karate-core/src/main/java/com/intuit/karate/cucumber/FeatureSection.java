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

import gherkin.formatter.model.Tag;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author pthomas3
 */
public class FeatureSection {

    private final int index;
    private final FeatureWrapper feature;
    private final ScenarioWrapper scenario;
    private final ScenarioOutlineWrapper scenarioOutline;

    public FeatureSection(int index, FeatureWrapper feature, ScenarioWrapper scenario, ScenarioOutlineWrapper scenarioOutline) {
        this.index = index;
        this.feature = feature;
        this.scenario = scenario;
        this.scenarioOutline = scenarioOutline;
        if (scenario != null) {
            scenario.setSection(this);
        } else {
            scenarioOutline.setSection(this);
        }
    }

    public List<String> getTags() {
        List<Tag> tags;
        if (isOutline()) {
            tags = scenarioOutline.getScenarioOutline().getGherkinModel().getTags();
        } else {
            tags = scenario.getScenario().getGherkinModel().getTags();
        }
        return tags.stream().map(t -> t.getName()).collect(Collectors.toList());
    }

    public int getIndex() {
        return index;
    }

    public FeatureWrapper getFeature() {
        return feature;
    }

    public ScenarioWrapper getScenario() {
        return scenario;
    }

    public ScenarioOutlineWrapper getScenarioOutline() {
        return scenarioOutline;
    }

    public boolean isOutline() {
        return scenarioOutline != null;
    }

    public int getLine() {
        if (isOutline()) {
            return scenarioOutline.getLine();
        } else {
            return scenario.getLine();
        }
    }

}
