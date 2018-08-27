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
import com.intuit.karate.core.ScenarioOutline;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 *
 * @author pthomas3
 */
public class ScenarioOutlinePanel extends BorderPane {
    
    private final VBox content;
    private final AppSession session;
    
    private ScenarioOutline outline;
    private final List<ExamplesPanel> examplesPanels;
    
    public ScenarioOutlinePanel(AppSession session, ScenarioOutline outline) {
        super();
        this.session = session;
        this.outline = outline;
        content = new VBox(0);        
        setCenter(content);        
        examplesPanels = new ArrayList(outline.getScenarios().size());
        initTitleAndContent();
    }
    
    private void initTitleAndContent() {
        for (Scenario scenario : outline.getScenarios()) {
            ExamplesPanel examplePanel = new ExamplesPanel(session, scenario);
            content.getChildren().add(examplePanel);
            if (!examplesPanels.isEmpty()) {
                examplePanel.setExpanded(false);
            }
            examplesPanels.add(examplePanel);
        }       
    }
    
    public void action(AppAction action) {
        outline = session.refresh(outline);
        for (ExamplesPanel panel : examplesPanels) {
            panel.refresh(action);
        }
    }
    
}
