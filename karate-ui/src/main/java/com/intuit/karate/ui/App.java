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

import java.io.File;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 *
 * @author pthomas3
 */
public class App extends Application {
    
    public static final Font DEFAULT_FONT = Font.font("Courier");
    
    private final FileChooser fileChooser = new FileChooser();
    
    private File workingDir = new File(".");
    private final BorderPane rootPane = new BorderPane();
    
    private File chooseFile(Stage stage) {
        fileChooser.setTitle("Choose Feature File");
        fileChooser.setInitialDirectory(workingDir);
        return fileChooser.showOpenDialog(stage);
    }

    private void initUi(File file) {
        AppSession session = AppSession.init(file, false);
        rootPane.setCenter(session.getFeaturePanel());
        rootPane.setRight(session.getVarsPanel());
        rootPane.setBottom(session.getLogPanel());
    }
    
    @Override
    public void start(Stage stage) throws Exception {        
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        menuBar.getMenus().addAll(fileMenu);
        rootPane.setTop(menuBar);
        MenuItem openFileMenuItem = new MenuItem("Open");        
        fileMenu.getItems().addAll(openFileMenuItem);
        openFileMenuItem.setOnAction(e -> {
            File file = chooseFile(stage);
            workingDir = file.getParentFile();
            initUi(file);
        });
        Scene scene = new Scene(rootPane, 900, 750);                
        stage.setScene(scene);
        stage.setTitle("Karate UI");
        stage.show();
    }

    public static void main(String[] args) {
        App.launch(args);
    }

}
