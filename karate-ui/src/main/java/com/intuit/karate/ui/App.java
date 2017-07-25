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
import java.util.List;

import com.intuit.karate.importer.KarateFeatureWriter;
import com.intuit.karate.importer.PostmanCollectionReader;
import com.intuit.karate.importer.PostmanRequest;
import javafx.application.Application;
import javafx.scene.Scene;
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
    
    private File chooseFile(Stage stage, String description, String extension) {
        fileChooser.setTitle("Choose Feature File");
        fileChooser.setInitialDirectory(workingDir);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(description, extension);
        fileChooser.getExtensionFilters().add(extFilter);
        return fileChooser.showOpenDialog(stage);
    }

    private void initUi(File file, String envString, Stage stage) {
        AppSession session = new AppSession(file, envString);
        rootPane.setTop(session.headerPanel);
        rootPane.setCenter(session.featurePanel);
        rootPane.setRight(session.varsPanel);
        rootPane.setBottom(session.logPanel);
        initFileOpenAction(session.headerPanel, envString, stage);
        initImportOpenAction(session.headerPanel, envString, stage);
        workingDir = file.getParentFile();        
    }
    
    private void initFileOpenAction(HeaderPanel header, String envString, Stage stage) {
        header.setFileOpenAction(e -> {
            File file = chooseFile(stage, "*.feature files", "*.feature");
            initUi(file, envString, stage);
        });
    }

    private void initImportOpenAction(HeaderPanel header, String envString, Stage stage) {
        header.setImportOpenAction(e -> {
            File file = chooseFile(stage, "*.postman_collection files", "*.postman_collection");
            List<PostmanRequest> requests = PostmanCollectionReader.parse(file.getPath());
            File featureFile = KarateFeatureWriter.write(requests, file.getPath());
            initUi(featureFile, envString, stage);
        });
    }
    
    @Override
    public void start(Stage stage) throws Exception {        
        List<String> params = getParameters().getUnnamed();
        String envString = System.getProperty("karate.env");
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
            initImportOpenAction(header, envString, stage);
        }
        Scene scene = new Scene(rootPane, 900, 750);                
        stage.setScene(scene);
        stage.setTitle("Karate UI");
        stage.show();
    }

    public static void main(String[] args) {
        App.launch(args);
    }
    
    public static void run(String featurePath, String env) {
        App.launch(new String[]{featurePath, env});
    }

}
