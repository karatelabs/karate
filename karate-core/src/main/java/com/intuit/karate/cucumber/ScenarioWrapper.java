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

import cucumber.runtime.model.CucumberBackground;
import cucumber.runtime.model.CucumberScenario;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author pthomas3
 */
public class ScenarioWrapper {

    private final int index;
    private final FeatureWrapper feature;
    private FeatureSection section;
    private final CucumberScenario scenario;
    private final ScenarioOutlineWrapper parent;
    private final List<StepWrapper> steps;

    public ScenarioWrapper(FeatureWrapper feature, int index, CucumberScenario scenario, ScenarioOutlineWrapper parent) {
        this.feature = feature;
        this.index = index;
        this.scenario = scenario;
        this.parent = parent;
        this.steps = new ArrayList<>();
        Optional<CucumberBackground> cucumberBackground = Optional.ofNullable(scenario.getCucumberBackground());
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger currentLine = new AtomicInteger(0);
        if (cucumberBackground.isPresent()) {
            cucumberBackground.get().getSteps().forEach(step -> {
                int firstLine = step.getLine();
                String priorText = feature.joinLines(currentLine.get(), firstLine - 1);
                steps.add(new StepWrapper(this, counter.getAndIncrement(), priorText, step, true));
                currentLine.set(step.getLineRange().getLast());
            });
        }
        scenario.getSteps().forEach(step -> {
            int firstLine = step.getLine();
            String priorText = feature.joinLines(currentLine.get(), firstLine - 1);
            steps.add(new StepWrapper(this, counter.getAndIncrement(), priorText, step, false));
            currentLine.set(step.getLineRange().getLast());
        });
    }

    public void setSection(FeatureSection section) {
        this.section = section;
    }

    public FeatureSection getSection() {
        return Optional.ofNullable(section).orElse(parent.getSection());
    }

    public int getIndex() {
        return index;
    }

    public List<StepWrapper> getSteps() {
        return steps;
    }

    public FeatureWrapper getFeature() {
        return feature;
    }

    public CucumberScenario getScenario() {
        return scenario;
    }

    public ScenarioOutlineWrapper getParent() {
        return parent;
    }

    public boolean isChild() {
        return Optional.ofNullable(parent).isPresent();
    }

    public int getLine() {
        return scenario.getGherkinModel().getLine();
    }

}
