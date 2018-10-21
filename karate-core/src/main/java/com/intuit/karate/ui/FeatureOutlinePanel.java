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

import com.intuit.karate.core.ScenarioExecutionUnit;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;

/**
 *
 * @author pthomas3
 */
public class FeatureOutlinePanel extends BorderPane {

    private final AppSession2 session;

    public FeatureOutlinePanel(AppSession2 session) {
        this.session = session;
        setPadding(App2.PADDING_HOR);
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        setCenter(scrollPane);
        ListView<ScenarioExecutionUnit> listView = new ListView();
        ObservableList<ScenarioExecutionUnit> data = FXCollections.observableArrayList(session.getScenarioExecutionUnits());
        listView.setItems(data);
        scrollPane.setContent(listView);
        listView.setCellFactory(lv -> new FeatureOutlineCell());
        listView.getSelectionModel()
                .selectedItemProperty()
                .addListener((o, prev, value) -> session.setSelectedScenario(value));
        listView.getSelectionModel().select(0);
        Platform.runLater(() -> listView.requestFocus());
    }

}
