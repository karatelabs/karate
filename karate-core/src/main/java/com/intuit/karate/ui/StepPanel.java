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

import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.Step;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 *
 * @author pthomas3
 */
public class StepPanel extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(StepPanel.class);

    private final AppSession session;
    private final TextArea textArea;
    private final Button runButton;
    private Button stepIntoFeatureButton;
    private Optional<Button> runAllUptoButton = Optional.empty();
    private final Optional<StepPanel> previousPanel;
    private String oldText;
    private Step step;
    private Boolean pass = null;
    private BooleanProperty nonFeature = new SimpleBooleanProperty(Boolean.TRUE);
    private BooleanBinding featureCall = nonFeature.not();
    private VarLists stepVarLists;

    private static final String STYLE_PASS = "-fx-base: #53B700";
    private static final String STYLE_FAIL = "-fx-base: #D52B1E";
    private static final String STYLE_METHOD = "-fx-base: #34BFFF";
    private static final String STYLE_DEFAULT = "-fx-base: #F0F0F0";
    private static final String STYLE_BACKGROUND = "-fx-text-fill: #8D9096";
    private static final Pattern callPattern = Pattern.compile("\\s*(.*=)?\\s*call\\s*read.*");

    public StepPanel(AppSession session, Step step, Optional<StepPanel> previousPanel) {
        this.session = session;
        this.previousPanel = previousPanel;
        runButton = new Button("►");
        textArea = new TextArea();
        textArea.setFont(App.getDefaultFont());
        textArea.setMinHeight(0);
        textArea.setWrapText(true);
        textArea.focusedProperty().addListener((val, before, after) -> {
            if (!after) { // if we lost focus
                rebuildFeatureIfTextChanged();
            } else {
                session.refreshVarsTable(stepVarLists);
            }
        });
        this.step = step;
        initTextArea();
        runButton.disableProperty().bind(session.isRunningNow());
        runButton.setOnAction(e -> run());
        stepIntoFeatureButton = new Button("⇲");
        stepIntoFeatureButton.visibleProperty().bind(featureCall);
        stepIntoFeatureButton.setTooltip(new Tooltip("Step into feature"));
        stepIntoFeatureButton.setOnAction(e -> session.stepIntoFeature(this));
        setUpTextAndRunButtons(previousPanel, getChildren());
    }

    private void setUpTextAndRunButtons(Optional<StepPanel> previousPanel, ObservableList<Node> children) {
        children.addAll(textArea, runButton, stepIntoFeatureButton);
        setUpRunAllUptoButton(previousPanel, children);
        setLeftAnchor(textArea, 0.0);
        setRightAnchor(textArea, 104.0);
        setBottomAnchor(textArea, 0.0);
        setRightAnchor(runButton, 32.0);
        setTopAnchor(runButton, 2.0);
        setBottomAnchor(runButton, 0.0);
        setRightAnchor(stepIntoFeatureButton, 0.0);
        setTopAnchor(stepIntoFeatureButton, 2.0);
        setBottomAnchor(stepIntoFeatureButton, 0.0);
    }

    private void setUpRunAllUptoButton(Optional<StepPanel> previousPanel, ObservableList<Node> children) {
        if (previousPanel.isPresent()) {
            final Button button = new Button("►►");
            button.disableProperty().bind(session.isRunningNow());
            runAllUptoButton = Optional.of(button);
            button.setTooltip(new Tooltip("Run all steps upto current step"));
            button.setOnAction(e -> session.runUpto(this));
            children.add(button);
            setRightAnchor(button, 64.0);
            setTopAnchor(button, 2.0);
            setBottomAnchor(button, 0.0);
        }
    }
    
    private void rebuildFeatureIfTextChanged() {
        String newText = textArea.getText();
        if (!newText.equals(oldText)) {
            nonFeature.setValue(callPattern.matcher(oldText).matches() ? false : true);
            session.replace(step, newText);
        }        
    }
    
    private void run() {
        rebuildFeatureIfTextChanged();
        Feature feature = session.getFeature();
        Result result = Engine.executeStep(step, session.getActions());
        pass = !result.isFailed();
        initStyleColor();
        stepVarLists = session.getVars();
        session.refreshVarsTable(stepVarLists);
        if (!pass) {
            throw new StepException(result);
        }
    }

    void runAllUpto() {
        previousPanel.ifPresent(p -> p.runAllUpto());
        if (pass == null) {
            run();
        }
    }

    private void setStyleForRunAllUptoButton(String style) {
        runAllUptoButton.ifPresent(b -> b.setStyle(style));
    }

    public void action(AppAction action) {
        switch (action) {
            case REFRESH:
                step = session.refresh(step);
                initTextArea();
                break;
            case RESET:
                pass = null;
                stepVarLists = null;
                initStyleColor();
                break;
            case RUN:
                if (pass == null) {
                    run();
                }
                break;
        }
    }
    
    private void initStyleColor() {
        if (pass == null) {
            runButton.setStyle("");
            setStyleForRunAllUptoButton("");
        } else if (pass) {
            runButton.setStyle(STYLE_PASS);
            setStyleForRunAllUptoButton(STYLE_PASS);
        } else {
            runButton.setStyle(STYLE_FAIL);
            setStyleForRunAllUptoButton(STYLE_FAIL);
        }       
    }

    private void initTextArea() {
        oldText = step.getPrefix() + " " + step.getText();
        nonFeature.setValue(callPattern.matcher(oldText).matches() ? false : true);
        textArea.setText(oldText);
        int lineCount = step.getLineCount();
        if (lineCount == 1) {
            int wrapEstimate = (int) Math.ceil(oldText.length() / 35);
            if (wrapEstimate > 1) {
                lineCount = wrapEstimate;
            } else {
                lineCount = 0;
            }
        }
        textArea.setPrefRowCount(lineCount);
        String stepText = step.getText();
        boolean isMethod = stepText.startsWith("method") || stepText.startsWith("soap");
        if (isMethod) {
            setStyle(STYLE_METHOD);
            textArea.setStyle(STYLE_METHOD);
        } else {
            setStyle(STYLE_DEFAULT);
        }
        if (step.isBackground()) {
            textArea.setStyle(STYLE_BACKGROUND);
        }
        initStyleColor();
    }

    public int getStepIndex() {
        return step.getIndex();
    }

}
