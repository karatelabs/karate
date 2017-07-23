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

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;

/**
 *
 * @author pthomas3
 */
public class HeaderPanel extends BorderPane {
    
    private final HBox content;
    private final AppSession session;    
    private final MenuItem openFileMenuItem;
    
    public HeaderPanel() {
        this(null);
    }
    
    private static final Font SMALL_TEXT = Font.font("Courier", 10);
    private static final Font SMALL_LABEL = Font.font(10);
    
    public HeaderPanel(AppSession session) {
        this.session = session;
        content = new HBox(5);
        content.setPadding(new Insets(5));
        setCenter(content);
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        menuBar.getMenus().addAll(fileMenu);
        setTop(menuBar);
        openFileMenuItem = new MenuItem("Open");        
        fileMenu.getItems().addAll(openFileMenuItem);
        if (session != null) {
            Label envLabel = new Label("karate.env");
            envLabel.setPadding(new Insets(5, 0, 0, 0));
            TextField envTextField = new TextField();
            // envTextField.setFont(SMALL_TEXT);
            envTextField.setText(session.getEnv().env);
            Button envButton = new Button("Reset");
            envButton.setOnAction(e -> session.reset(envTextField.getText()));
            // envButton.setFont(SMALL_LABEL);
            Button runAllButton = new Button("Run ►►");
            runAllButton.setOnAction(e -> session.runAll());
            content.getChildren().addAll(envLabel, envTextField, envButton, runAllButton);
        }
    }
    
    public void setFileOpenAction(EventHandler<ActionEvent> handler) {
        openFileMenuItem.setOnAction(handler);
    }
    
}
