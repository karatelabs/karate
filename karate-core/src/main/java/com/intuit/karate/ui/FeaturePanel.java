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
import gherkin.formatter.model.Feature;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class FeaturePanel extends ScrollPane {

    private final VBox content;
    private final AppSession session;
    private final List<SectionPanel> sectionPanels;

    public FeaturePanel(AppSession session) {
        content = new VBox(0);
        setContent(content);
        setFitToWidth(true);
        this.session = session;
        int sectionCount = session.getFeature().getSections().size();
        sectionPanels = new ArrayList(sectionCount);
        addSections();
    }
    
    private void addSections() {
        final Feature gherkinFeature = session.getFeature().getFeature().getGherkinFeature();

        TextFlow flow = new TextFlow();
        Text keyword=new Text(gherkinFeature.getKeyword()+" : ");
        Text name=new Text(gherkinFeature.getName());
        flow.getChildren().addAll(keyword, name);

        flow.setMaxHeight(8);
        content.getChildren().add(flow);
        for (FeatureSection section : session.getFeature().getSections()) {
            SectionPanel sectionPanel = new SectionPanel(session, section);            
            content.getChildren().add(sectionPanel);
            if (!sectionPanels.isEmpty()) {
                sectionPanel.setExpanded(false);
            }
            sectionPanels.add(sectionPanel);
        }
    }
    
    public void action(AppAction action) {
        for (SectionPanel panel : sectionPanels) {
            panel.action(action);
        }
    }    

}
