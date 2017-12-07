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

import cucumber.runtime.model.CucumberScenarioOutline;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author pthomas3
 */
public class ScenarioOutlineWrapper {
    
    private final FeatureWrapper feature;
    private FeatureSection section;
    private final CucumberScenarioOutline scenarioOutline;
    private final List<ScenarioWrapper> scenarios;
    
    public ScenarioOutlineWrapper(FeatureWrapper feature, CucumberScenarioOutline scenarioOutline) {
        this.feature = feature;
        this.scenarioOutline = scenarioOutline;
        this.scenarios = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(0);
        scenarioOutline.getCucumberExamplesList().forEach(cucumberExamples -> {
            cucumberExamples.createExampleScenarios().forEach(cucumberScenario -> {
                scenarios.add(new ScenarioWrapper(feature, index.getAndIncrement(), cucumberScenario, this));
            });
        });
    }

    public FeatureSection getSection() {
        return section;
    }

    public void setSection(FeatureSection section) {
        this.section = section;
    }        

    public FeatureWrapper getFeature() {
        return feature;
    }

    public CucumberScenarioOutline getScenarioOutline() {
        return scenarioOutline;
    } 

    public List<ScenarioWrapper> getScenarios() {
        return scenarios;
    }        
    
    public int getLine() {
        return scenarioOutline.getGherkinModel().getLine();
    }
    
}
