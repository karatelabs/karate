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

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

/**
 *
 * @author pthomas3
 */
public class StepPanel2 extends AnchorPane {

    private final AppSession2 session;
    private final ScenarioPanel2 scenarioPanel;
    private final ScenarioExecutionUnit unit;
    private final Step step;
    private final SplitMenuButton runButton;
    private final int index;

    private String text;
    private boolean last;

    private static final String STYLE_PASS = "-fx-base: #53B700";
    private static final String STYLE_FAIL = "-fx-base: #D52B1E";
    private static final String STYLE_METHOD = "-fx-base: #34BFFF";
    private static final String STYLE_DEFAULT = "-fx-base: #F0F0F0";
    private static final String STYLE_BACKGROUND = "-fx-text-fill: #8D9096";

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    private final MenuItem runMenuItem;
    private final MenuItem calledMenuItem;
    private boolean runUpto;
    private boolean showCalled;
    
    private String getRunMenuText() {
        return runUpto ? "run this step" : "TODO: run upto this step";
    }
    
    private String getCalledMenuText() {
        return showCalled ? "hide called" : "show called";
    }    

    public StepPanel2(AppSession2 session, ScenarioPanel2 scenarioPanel, Step step, int index) {
        this.session = session;
        this.unit = scenarioPanel.getScenarioExecutionUnit();
        this.scenarioPanel = scenarioPanel;
        this.step = step;
        this.index = index;
        TextArea textArea = new TextArea();
        textArea.setFont(App2.getDefaultFont());
        textArea.setWrapText(true);
        textArea.setMinHeight(0);
        text = step.toString();
        int lines = StringUtils.wrappedLinesEstimate(text, 30);
        textArea.setText(text);
        textArea.setPrefRowCount(lines);
        textArea.focusedProperty().addListener((val, before, after) -> {
            if (!after) { // if we lost focus
                String temp = textArea.getText();
                if (!text.equals(temp)) {
                    text = temp;
                    FeatureParser.updateStepFromText(step, text);
                }
            }
        });
        runMenuItem = new MenuItem(getRunMenuText());
        runMenuItem.setOnAction(e -> { runUpto = !runUpto; runMenuItem.setText(getRunMenuText());});
        calledMenuItem = new MenuItem(getCalledMenuText());
        calledMenuItem.setOnAction(e -> { showCalled = !showCalled; calledMenuItem.setText(getCalledMenuText());});
        runButton = new SplitMenuButton(runMenuItem, calledMenuItem);
        runButton.setText("►");
        runButton.setOnAction(e -> {
            if (FeatureParser.updateStepFromText(step, text)) {
                run(false);
            } else {
                runButton.setStyle(STYLE_FAIL);
            }
        });
        // layout
        setLeftAnchor(textArea, 0.0);
        setRightAnchor(textArea, 32.0);
        setBottomAnchor(textArea, 0.0);
        setRightAnchor(runButton, 3.0);
        setTopAnchor(runButton, 0.0);
        setBottomAnchor(runButton, 0.0);
        // add
        getChildren().addAll(textArea, runButton);
        initStyles();
    }

    public void initStyles() {
        StepResult sr = unit.result.getStepResult(index);
        if (sr == null) {
            runButton.setStyle("");
        } else if (sr.getResult().getStatus().equals("passed")) {
            runButton.setStyle(STYLE_PASS);
        } else {
            runButton.setStyle(STYLE_FAIL);
        }
    }

    public boolean run(boolean nonStop) {
        if (!nonStop && showCalled) {
            unit.getContext().setCallable(callContext -> {
                AppSession2 calledSession = new AppSession2(callContext.feature, null, callContext);
                Stage stage = new Stage();
                stage.setTitle(callContext.feature.getRelativePath());
                stage.setScene(new Scene(calledSession.getRootPane(), 700, 450));
                stage.showAndWait();
                FeatureResult result = calledSession.getFeatureExecutionUnit().exec.result;
                result.setResultVars(calledSession.getCurrentlyExecutingScenario().getContext().vars);
                return result;
            });
        } else {
            unit.getContext().setCallable(null);
        }
        StepResult stepResult = unit.execute(step);
        unit.result.setStepResult(index, stepResult);
        session.setCurrentlyExecutingScenario(unit);
        initStyles();
        scenarioPanel.refreshVars();
        return stepResult.isStopped();
    }

}
