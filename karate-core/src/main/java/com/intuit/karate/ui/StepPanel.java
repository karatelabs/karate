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

import com.intuit.karate.cucumber.CucumberUtils;
import com.intuit.karate.cucumber.StepResult;
import com.intuit.karate.cucumber.StepWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class StepPanel extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(StepPanel.class);

    private final AppSession session;
    private final TextArea textArea;
    private final Button runButton;
    private String oldText;
    private StepWrapper step;
    private Boolean pass = null;

    private static final String STYLE_PASS = "-fx-base: #53B700";
    private static final String STYLE_FAIL = "-fx-base: #D52B1E";
    private static final String STYLE_METHOD = "-fx-base: #34BFFF";
    private static final String STYLE_DEFAULT = "-fx-base: #F0F0F0";
    private static final String STYLE_BACKGROUND = "-fx-text-fill: #8D9096";

    public StepPanel(AppSession session, StepWrapper step) {
        this.session = session;
        runButton = new Button("►");        
        textArea = new TextArea();
        textArea.setFont(App.getDefaultFont());
        textArea.setMinHeight(0);
        textArea.setWrapText(true);
        textArea.focusedProperty().addListener((val, before, after) -> {
            if (!after) { // if we lost focus
                rebuildFeatureIfTextChanged();
            }
        });
        this.step = step;
        initTextArea();
        runButton.setOnAction(e -> run());
        getChildren().addAll(textArea, runButton);
        setLeftAnchor(textArea, 0.0);
        setRightAnchor(textArea, 30.0);
        setBottomAnchor(textArea, 0.0);
        setRightAnchor(runButton, 0.0);
        setTopAnchor(runButton, 2.0);
        setBottomAnchor(runButton, 0.0);
    }
    
    private void rebuildFeatureIfTextChanged() {
        String newText = textArea.getText();
        if (!newText.equals(oldText)) {
            session.replace(step, newText);
        }        
    }
    
    private void run() {
        rebuildFeatureIfTextChanged();
        StepResult result = CucumberUtils.runCalledStep(step, session.backend);
        pass = result.isPass();
        initStyleColor();
        session.refreshVarsTable();
        if (!pass) {
            throw new StepException(result);
        }
    }

    public void action(AppAction action) {
        switch (action) {
            case REFRESH:
                step = session.refresh(step);
                initTextArea();
                break;
            case RESET:
                pass = null;
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
        } else if (pass) {
            runButton.setStyle(STYLE_PASS);
        } else {
            runButton.setStyle(STYLE_FAIL);
        }       
    }

    private void initTextArea() {
        oldText = step.getText();
        textArea.setText(oldText);
        int lineCount = step.getLineCount();
        if (lineCount == 1) {
            int wrapEstimate = oldText.length() / 40;
            if (wrapEstimate > 1) {
                lineCount = wrapEstimate;
            } else {
                lineCount = 0;
            }
        }
        textArea.setPrefRowCount(lineCount);
        if (step.isHttpCall()) {
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

}
