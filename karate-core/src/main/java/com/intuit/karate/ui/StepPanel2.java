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
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.Step;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;

/**
 *
 * @author pthomas3
 */
public class StepPanel2 extends AnchorPane {
    
    private final AppSession2 session;
    private final ScenarioPanel2 scenarioPanel;
    private final Step step;
    private final Button runButton;
    private boolean last;

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }        
    
    public StepPanel2(AppSession2 session, ScenarioPanel2 scenarioPanel, Step step) {
        this.session = session;
        this.scenarioPanel = scenarioPanel;
        this.step = step;        
        setPadding(App2.PADDING_TOP);
        TextArea textArea = new TextArea();
        textArea.setFont(App2.getDefaultFont());
        textArea.setWrapText(true);
        textArea.setMinHeight(0);       
        String text = step.toString();
        int lines = StringUtils.wrappedLinesEstimate(text, 25);
        textArea.setText(text);
        textArea.setPrefRowCount(lines);        
        runButton = new Button("►");
        runButton.setOnAction(e -> run());
        // layout
        setLeftAnchor(textArea, 0.0);
        setRightAnchor(textArea, 32.0);
        setBottomAnchor(textArea, 0.0);
        setRightAnchor(runButton, 3.0);
        setTopAnchor(runButton, 0.0);
        setBottomAnchor(runButton, 0.0);         
        // add
        getChildren().addAll(textArea, runButton);
    }
    
    private void run() {
        ScenarioExecutionUnit unit = session.getScenarioPanel().getScenarioExecutionUnit();
        unit.execute(step);
        scenarioPanel.refreshVars();
    }
    
}
