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

import com.intuit.karate.ScriptBindings;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/**
 *
 * @author pthomas3
 */
public class HeaderPanel extends BorderPane {
    
    private final HBox content;
    private final AppSession session;    
    private final MenuItem openFileMenuItem;
    private final MenuItem openDirectoryMenuItem;
    private final MenuItem openImportMenuItem;
    private final TextArea textContent;
    
    private String oldText;
    
    public HeaderPanel() {
        this(null);
    }
    
    public HeaderPanel(AppSession session) {
        this.session = session;
        content = new HBox(5);
        content.setPadding(new Insets(5));
        setCenter(content);
        textContent = new TextArea();
        textContent.setPrefRowCount(16);
        textContent.setVisible(false);
        setBottom(textContent);
        textContent.setManaged(false);
        textContent.setFont(App.getDefaultFont());
        textContent.focusedProperty().addListener((val, before, after) -> {
            if (!after) { // if we lost focus
                rebuildFeatureIfTextChanged();
            }
        });        
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        openFileMenuItem = new MenuItem("Open");
        fileMenu.getItems().addAll(openFileMenuItem);

        openDirectoryMenuItem = new MenuItem("Load Directory");
        fileMenu.getItems().addAll(openDirectoryMenuItem);

        Menu importMenu = new Menu("Import");
        openImportMenuItem = new MenuItem("Open");
        importMenu.getItems().addAll(openImportMenuItem);

        menuBar.getMenus().addAll(fileMenu, importMenu);
        setTop(menuBar);

        if (session != null) {
            Label envLabel = new Label(ScriptBindings.KARATE_ENV);
            envLabel.setPadding(new Insets(5, 0, 0, 0));
            TextField envTextField = new TextField();
            envTextField.setText(session.getEnv().env);
            Button envButton = new Button("Reset");
            envButton.setOnAction(e -> session.resetAll(envTextField.getText()));
            Button runAllButton = new Button("Run ►►");
            runAllButton.setOnAction(e -> session.runAll());            
            Button showContentButton = new Button(getContentButtonText(false));
            initTextContent();
            showContentButton.setOnAction(e -> { 
                boolean visible = !textContent.isVisible();
                textContent.setVisible(visible);
                textContent.setManaged(visible);
                showContentButton.setText(getContentButtonText(visible));
            });
            content.getChildren().addAll(envLabel, envTextField, envButton, runAllButton, showContentButton);            
        }
    }
    
    private String getContentButtonText(boolean visible) {
        return visible ? "Hide Raw" : "Show Raw";
    }
    
    public void setFileOpenAction(EventHandler<ActionEvent> handler) {
        openFileMenuItem.setOnAction(handler);
    }

    public void setDirectoryOpenAction(EventHandler<ActionEvent> handler) {
        openDirectoryMenuItem.setOnAction(handler);
    }

    public void setImportOpenAction(EventHandler<ActionEvent> handler) {
        openImportMenuItem.setOnAction(handler);
    }
    
    public void initTextContent() {
        oldText = session.getFeature().getText();
        textContent.setText(oldText);
    }
    
    public void rebuildFeatureIfTextChanged() {
        String newText = textContent.getText();
        if (!newText.equals(oldText)) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setHeaderText("Read Only");
            alert.setTitle("Not Implemented");
            alert.setContentText("Raw text editing is not supported.");
            alert.show();
            textContent.setText(oldText);
        }
    }
    
}
