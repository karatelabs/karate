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
package com.intuit.karate.ui;

import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.Step;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 *
 * @author pthomas3
 */
public class ScenarioPanel2 extends BorderPane {

    private final AppSession2 session;
    private final ScenarioExecutionUnit unit;
    private final VBox content;
    private final VarsPanel2 varsPanel;
    private final ConsolePanel consolePanel;

    private final List<StepPanel2> stepPanels;
    private StepPanel2 lastStep;

    public ScenarioExecutionUnit getScenarioExecutionUnit() {
        return unit;
    }

    private final ScenarioContext initialContext;
    private int index;

    public ScenarioPanel2(AppSession2 session, ScenarioExecutionUnit unit) {
        this.session = session;
        this.unit = unit;
        unit.init();
        initialContext = unit.getActions().context.copy();
        content = new VBox(App2.PADDING);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        setCenter(scrollPane);
        VBox header = new VBox(App2.PADDING);
        header.setPadding(App2.PADDING_VER);
        setTop(header);
        String headerText = "Scenario: " + unit.scenario.getDisplayMeta() + " " + unit.scenario.getName();
        Label headerLabel = new Label(headerText);
        header.getChildren().add(headerLabel);
        HBox hbox = new HBox(App2.PADDING);
        header.getChildren().add(hbox);
        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> reset());
        Button runAllButton = new Button("Run All Steps");
        runAllButton.setOnAction(e -> Platform.runLater(() -> runAll()));
        hbox.getChildren().add(resetButton);
        hbox.getChildren().add(runAllButton);
        stepPanels = new ArrayList();
        unit.getSteps().forEach(step -> addStepPanel(step));
        lastStep.setLast(true);
        VBox vbox = new VBox(App2.PADDING);
        varsPanel = new VarsPanel2(session, this);
        vbox.getChildren().add(varsPanel);
        consolePanel = new ConsolePanel(session, this);
        vbox.getChildren().add(consolePanel);
        setRight(vbox);
        reset(); // clear any background results if dynamic scenario
    }

    private void addStepPanel(Step step) {
        lastStep = new StepPanel2(session, this, step, index++);
        content.getChildren().add(lastStep);
        stepPanels.add(lastStep);
    }

    public void refreshVars() {
        varsPanel.refresh();
    }

    public void runAll() {
        reset();
        for (StepPanel2 stepPanel : stepPanels) {
            if (stepPanel.run()) {
                break;
            }
        }      
    }

    public void reset() {
        unit.reset(initialContext.copy());
        refreshVars();
        for (StepPanel2 stepPanel : stepPanels) {
            stepPanel.initStyles();
        }
        session.getFeatureOutlinePanel().refresh();
    }

}
