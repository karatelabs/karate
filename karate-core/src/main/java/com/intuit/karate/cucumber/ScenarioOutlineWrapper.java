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

import cucumber.runtime.model.CucumberExamples;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.CucumberScenarioOutline;
import java.util.ArrayList;
import java.util.List;

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
        for (CucumberExamples examples : scenarioOutline.getCucumberExamplesList()) { // TODO can this be more than 1
            List<CucumberScenario> exampleScenarios = examples.createExampleScenarios();
            int count = exampleScenarios.size();
            for (int i = 0; i < count; i++) {
                CucumberScenario scenario = exampleScenarios.get(i);
                ScenarioWrapper sw = new ScenarioWrapper(feature, i, scenario, this);
                scenarios.add(sw);
            }
        }         
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
