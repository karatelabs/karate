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

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureSection;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

import static com.intuit.karate.ui.App.PADDING_INSET;

/**
 *
 * @author pthomas3
 */
public class FeaturePanel extends BorderPane {

    private final ScrollPane scrollPane;
    private final VBox content;
    private final AppSession session;
    private final List<SectionPanel> sectionPanels;

    public FeaturePanel(AppSession session) {
        this.setPadding(PADDING_INSET);
        this.scrollPane = new ScrollPane();
        content = new VBox(5.0);
        this.scrollPane.setContent(content);
        this.scrollPane.setFitToWidth(true);
        this.session = session;
        int sectionCount = session.getFeature().getSections().size();
        sectionPanels = new ArrayList(sectionCount);
        addSections();
        this.setCenter(scrollPane);
    }

    private void addSections() {
        final Feature feature = session.getFeature();
        TextFlow flow = new TextFlow();
        Text keyword = new Text("Feature: ");
        Text name = new Text(feature.getName());
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

    public void refresh() {
        sectionPanels.clear();
        content.getChildren().clear();
        addSections();
    }

    // only needed for our unit tests
    SectionPanel getSectionAtIndex(int index) {
        return sectionPanels.get(index);
    }

}
