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

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.ScenarioExecutionUnit;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 *
 * @author pthomas3
 */
public class FeatureOutlinePanel extends BorderPane {

    private final AppSession2 session;
    private ListView<ScenarioExecutionUnit> listView;
    private final ScrollPane scrollPane;
    private final List<ScenarioExecutionUnit> units;    

    public FeatureOutlinePanel(AppSession2 session) {
        this.session = session;
        this.units = session.getScenarioExecutionUnits();
        setPadding(App2.PADDING_HOR);
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox header = new VBox(App2.PADDING);
        header.setPadding(App2.PADDING_VER);
        setTop(header);
        Feature feature = session.getFeatureExecutionUnit().exec.featureContext.feature;
        Label featureLabel = new Label(feature.getPath().getFileName().toString());
        header.getChildren().add(featureLabel);
        HBox hbox = new HBox(App2.PADDING);
        header.getChildren().add(hbox);
        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> session.resetAll());
        Button runAllButton = new Button("Run All Scenarios");
        hbox.getChildren().add(resetButton);
        hbox.getChildren().add(runAllButton);
        setCenter(scrollPane);
        refresh();
        Platform.runLater(() -> {
            listView.getSelectionModel().select(0);
            listView.requestFocus();
        });
        runAllButton.setOnAction(e -> Platform.runLater(() -> session.runAll()));
    }

    public void refresh() {
        // unless we do ALL of this - the custom cell rendering has problems in javafx
        // and starts duplicating the last row for some reason, spent a lot of time on this :(
        listView = new ListView();
        listView.setItems(FXCollections.observableArrayList(units));
        listView.setCellFactory(lv -> new FeatureOutlineCell());
        scrollPane.setContent(listView);
        listView.getSelectionModel()
                .selectedIndexProperty()
                .addListener((o, prev, value) -> session.setSelectedScenario(value.intValue()));        
    }

}
