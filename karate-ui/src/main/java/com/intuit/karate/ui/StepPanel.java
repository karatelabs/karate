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
    private String oldText;
    private StepWrapper step;

    public StepPanel(AppSession session, StepWrapper orig) {
        this.session = session;
        Button button = new Button("►");
        textArea = new TextArea();
        this.step = orig;
        initTextArea();
        button.setOnAction(e -> {
            String newText = textArea.getText();            
            if (!newText.equals(oldText)) {
                session.replace(orig, newText);               
            }
            StepResult result = step.run(session.backend);
            if (result.isPass()) {
                button.setStyle("-fx-base: green");
            } else {
                button.setStyle("-fx-base: red");
            }
        });        
        getChildren().addAll(textArea, button);
        setLeftAnchor(textArea, 5.0);
        setTopAnchor(textArea, 5.0);
        setRightAnchor(button, 5.0);
        setTopAnchor(button, 5.0);
        setBottomAnchor(button, 5.0);
    }
    
    public void refresh() {
        step = session.refresh(step);
        initTextArea();
    }
    
    private void initTextArea() {
        oldText = step.getText();
        textArea.setText(oldText);
        textArea.setPrefRowCount(step.getLineCount());         
    }

}
