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
import com.intuit.karate.convert.PostmanItem;
import com.intuit.karate.core.Feature;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
    
    private boolean needsNameToSave = false;
    private File workingDir = new File(".");
    private final BorderPane rootPane = new BorderPane();
    
    private static final String DEFAULT_FEATURE_NAME = "noname.feature";
    private static final String DEFAULT_FEATURE_TEXT = "Feature: brief description of what is being tested\n\n" +
            "Scenario: description of this scenario\n" +
            "# steps for this scenario\n" +
            "Given url 'https://duckduckgo.com'\n" +
            "And param q = 'intuit karate'\n" +
            "When method GET\nThen status 200\n\n" +
            "Scenario: a different scenario\n" +
            "# steps for this other scenario";

    public static Font getDefaultFont() {
    	return Font.font("Courier");
    }
    
    private File chooseFile(Stage stage, String description, String extension) {
        fileChooser.setTitle("Choose Feature File");
        fileChooser.setInitialDirectory(workingDir);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(description, extension);
        fileChooser.getExtensionFilters().setAll(extFilter);
        return fileChooser.showOpenDialog(stage);
    }

    private File chooseFileToSave(Stage stage, String description, String extension, String initialName) {
        fileChooser.setTitle("Save Feature To File");
        fileChooser.setInitialDirectory(workingDir);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("*.feature files", "*.feature");
        fileChooser.getExtensionFilters().setAll(extFilter);
        fileChooser.setInitialFileName(initialName);
        return fileChooser.showSaveDialog(stage);
    }

    void initUi(File file, String envString, Stage stage) {
        AppSession session = new AppSession(file, envString);
        rootPane.setTop(session.headerPanel);
        rootPane.setCenter(session.featurePanel);
        rootPane.setRight(session.varsPanel);
        GridPane footerPanel = new GridPane();
        footerPanel.add(session.logPanel, 0, 0);
        footerPanel.add(session.httpPanel, 1, 0);
        footerPanel.setPrefHeight(180);
        DragResizer.makeResizable(footerPanel, false, true, true, false);
        rootPane.setBottom(footerPanel);
        initNewFileAction(session.headerPanel, envString, stage);
        initFileOpenAction(session.headerPanel, envString, stage);
        initFileSaveAction(session, envString, stage);
        initDirectoryOpenAction(session.headerPanel, envString, stage);
        initImportOpenAction(session.headerPanel, envString, stage);
        workingDir = file.getParentFile();        
    }
    
    private void initFileOpenAction(HeaderPanel header, String envString, Stage stage) {
        header.setFileOpenAction(e -> {
            if (rootPane.getLeft() != null) {
                rootPane.setLeft(null);
            }
            File file = chooseFile(stage, "*.feature files", "*.feature");
            initUi(file, envString, stage);
            needsNameToSave = false;
        });
    }

    private void initNewFileAction(HeaderPanel header, String envString, Stage stage) {
        header.setNewFileAction(e -> {
            if (rootPane.getLeft() != null) {
                rootPane.setLeft(null);
            }
            initUi(new File(initializeNoNameFeature()), envString, stage);
        });
    }

    private void initFileSaveAction(AppSession session, String envString, Stage stage) {
        session.headerPanel.setFileSaveAction(e -> {
            File file;
            Feature feature = session.getFeature();
            if (needsNameToSave) {
                String suggestedName = "noname.feature";
                file = chooseFileToSave(stage, "*.feature files", "*.feature", suggestedName);
            } else {
                file = feature.getFile();
            }
            FileUtils.writeToFile(file, feature.getText());
            if (needsNameToSave) {
                needsNameToSave = false;
                initUi(file, envString, stage);
            }
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
            List<PostmanItem> items = ConvertUtils.readPostmanJson(json);
            String featureText = ConvertUtils.toKarateFeature(file.getName(), items);
            File noNameFeature = new File(workingDir, DEFAULT_FEATURE_NAME);
            FileUtils.writeToFile(noNameFeature, featureText);
            needsNameToSave = true;
            initUi(noNameFeature, envString, stage);
        });
    }

    private String initializeNoNameFeature() {
        needsNameToSave = true;
        File noNameFeature = new File(workingDir, DEFAULT_FEATURE_NAME);
        FileUtils.writeToFile(noNameFeature, DEFAULT_FEATURE_TEXT);
        return noNameFeature.getPath();
    }
    
    @Override
    public void start(Stage stage) throws Exception {        
        String fileName = null;
        List<String> params = getParameters().getUnnamed();
        String envString = System.getProperty(ScriptBindings.KARATE_ENV);
        if (!params.isEmpty()) {
            fileName = params.get(0);
            if (params.size() > 1) {
                envString = params.get(1);
            }
        } else {
            fileName = initializeNoNameFeature();
        }
        initUi(new File(fileName), envString, stage);

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
