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

import com.intuit.karate.cucumber.FeatureSection;
import com.intuit.karate.cucumber.ScenarioOutlineWrapper;
import com.intuit.karate.cucumber.ScenarioWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 *
 * @author pthomas3
 */
public class SectionPanel extends TitledPane {
    
    private final VBox content;    
    private final AppSession session;
    
    private FeatureSection section;
    private ScenarioOutlinePanel outlinePanel;
    private ScenarioPanel scenarioPanel;
    private boolean runStarted;
    
    public SectionPanel(AppSession session, FeatureSection section) {
        super();
        this.section = section;
        this.session = session;
        content = new VBox(0);        
        setContent(content);
        initTitleAndContent();
    }
    
    private void initTitleAndContent() {
        if (section.isOutline()) {
            ScenarioOutlineWrapper outline = section.getScenarioOutline();
            setText(outline.getScenarioOutline().getVisualName());
            outlinePanel = new ScenarioOutlinePanel(session, outline);           
            content.setPadding(new Insets(5, 5, 5, 5));
            content.getChildren().add(outlinePanel);
        } else {
            ScenarioWrapper scenario = section.getScenario();
            setText(scenario.getScenario().getVisualName());
            scenarioPanel = new ScenarioPanel(session, scenario);
            content.getChildren().add(scenarioPanel);
        }
    }
    
    public void action(AppAction action) {
        switch (action) {
            case REFRESH:
                section = session.refresh(section);
                break;
            case RUN:
                if (!runStarted) {
                    session.resetBackendAndVarsTable(null);
                    runStarted = true;
                }
                break;
            case RESET:
                runStarted = false;
                break;
        }
        if (section.isOutline()) {
            outlinePanel.action(action);
        } else {
            scenarioPanel.action(action);
        }
    }

    // only needed for our unit tests
    StepPanel getStepAtIndex(int index) {
        return (section.isOutline() ? null : scenarioPanel.getStepAtIndex(index));
    }

}
