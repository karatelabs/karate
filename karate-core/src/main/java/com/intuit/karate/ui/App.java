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

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.convert.ConvertUtils;
import com.intuit.karate.convert.PostmanRequest;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class App extends Application {
    public static final double PADDING = 3.0;
    public static final Insets PADDING_INSET = new Insets(App.PADDING, App.PADDING, App.PADDING, App.PADDING);

    private final FileChooser fileChooser = new FileChooser();
    
    private File workingDir = new File(".");
    private final BorderPane rootPane = new BorderPane();
    
    public static Font getDefaultFont() {
    	return Font.font("Courier");
    }
    
    private File chooseFile(Stage stage, String description, String extension) {
        fileChooser.setTitle("Choose Feature File");
        fileChooser.setInitialDirectory(workingDir);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(description, extension);
        fileChooser.getExtensionFilters().add(extFilter);
        return fileChooser.showOpenDialog(stage);
    }

    void initUi(File file, String envString, Stage stage) {
        AppSession session = new AppSession(file, envString);
        rootPane.setTop(session.headerPanel);
        rootPane.setCenter(session.featurePanel);
        rootPane.setRight(session.varsPanel);
        rootPane.setBottom(session.logPanel);
        initFileOpenAction(session.headerPanel, envString, stage);
        initDirectoryOpenAction(session.headerPanel, envString, stage);
        initImportOpenAction(session.headerPanel, envString, stage);
        workingDir = file.getParentFile();        
    }
    
    private void initFileOpenAction(HeaderPanel header, String envString, Stage stage) {
        header.setFileOpenAction(e -> {
            if(rootPane.getLeft() != null) {
                rootPane.setLeft(null);
            }
            File file = chooseFile(stage, "*.feature files", "*.feature");
            initUi(file, envString, stage);
        });
    }

    private void initDirectoryOpenAction(HeaderPanel header, String envString, Stage stage) {
        App app = this;
        header.setDirectoryOpenAction(e -> {
                    DirectoryChooser directoryChooser = new DirectoryChooser();
                    directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
                    File choice = directoryChooser.showDialog(stage);
                    if (choice != null) {
                        if (!choice.isDirectory()) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setHeaderText("Could not open directory");
                            alert.setContentText("The directory is invalid.");
                            alert.showAndWait();
                        } else {
                            final DirectoryPanel directoryPanel = new DirectoryPanel(app, envString, stage);
                            directoryPanel.init(choice);
                            rootPane.setLeft(directoryPanel);
                        }
                    }
                }
        );
    }

    private void initImportOpenAction(HeaderPanel header, String envString, Stage stage) {
        header.setImportOpenAction(e -> {
            File file = chooseFile(stage, "*.postman_collection files", "*.postman_collection");
            String json = FileUtils.toString(file);
            List<PostmanRequest> requests = ConvertUtils.readPostmanJson(json);
            String featureText = ConvertUtils.toKarateFeature(requests);
            String featurePath = FileUtils.replaceFileExtension(file.getPath(), "feature");
            File featureFile = new File(featurePath);
            FileUtils.writeToFile(featureFile, featureText);
            initUi(featureFile, envString, stage);
        });
    }
    
    @Override
    public void start(Stage stage) throws Exception {        
        List<String> params = getParameters().getUnnamed();
        String envString = System.getProperty(ScriptBindings.KARATE_ENV);
        if (!params.isEmpty()) {
            String fileName = params.get(0);
            if (params.size() > 1) {
                envString = params.get(1);
            }
            initUi(new File(fileName), envString, stage);
        } else {
            HeaderPanel header = new HeaderPanel();
            rootPane.setTop(header);
            initFileOpenAction(header, envString, stage);
            initDirectoryOpenAction(header, envString, stage);
            initImportOpenAction(header, envString, stage);
        }

        Scene scene = new Scene(rootPane, 900, 750);
        stage.setScene(scene);
        stage.setTitle("Karate UI");
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        App.launch(args);
    }
    
    public static void run(String featurePath, String env) {
        App.launch(new String[]{featurePath, env});
    }

}
