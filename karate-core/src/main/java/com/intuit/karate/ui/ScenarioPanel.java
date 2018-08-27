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
package com.intuit.karate.ui;

import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 *
 * @author pthomas3
 */
public class ScenarioPanel extends BorderPane {

    private final VBox content;
    private final AppSession session;

    private Scenario scenario;
    private final List<StepPanel> stepPanels;

    public ScenarioPanel(AppSession session, Scenario scenario) {
        super();
        content = new VBox(0);
        setCenter(content);
        this.session = session;
        this.scenario = scenario;
        stepPanels = new ArrayList(scenario.getSteps().size());
        initTitleAndContent();
    }
    
    private Optional<StepPanel> previousStep = Optional.empty();

    private void addStep(Step step) {
        StepPanel stepPanel = new StepPanel(session, step, previousStep);
        content.getChildren().add(stepPanel);
        stepPanels.add(stepPanel);
        previousStep = Optional.of(stepPanel);
    }

    private void initTitleAndContent() {
        if (scenario.getFeature().getBackground() != null) {
            for (Step step : scenario.getFeature().getBackground().getSteps()) {
                addStep(step);
            }
        }
        for (Step step : scenario.getSteps()) {
            addStep(step);
        }
    }

    public void action(AppAction action) {
        scenario = session.refresh(scenario);
        for (StepPanel panel : stepPanels) {
            panel.action(action);
        }
    }

    // only needed for our unit tests
    StepPanel getStepAtIndex(int index) {
        return stepPanels.get(index);
    }

}
