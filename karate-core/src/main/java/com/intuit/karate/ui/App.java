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

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.formats.postman.PostmanItem;
import com.intuit.karate.formats.postman.PostmanUtils;
import java.io.File;
import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javax.swing.ImageIcon;

/**
 *
 * @author pthomas3
 */
public class App extends Application {

    private static final String KARATE_LOGO = "karate-logo.png";

    public static final double PADDING = 3.0;
    public static final Insets PADDING_ALL = new Insets(App.PADDING, App.PADDING, App.PADDING, App.PADDING);
    public static final Insets PADDING_HOR = new Insets(0, App.PADDING, 0, App.PADDING);
    public static final Insets PADDING_VER = new Insets(App.PADDING, 0, App.PADDING, 0);
    public static final Insets PADDING_TOP = new Insets(App.PADDING, 0, 0, 0);
    public static final Insets PADDING_BOT = new Insets(0, 0, App.PADDING, 0);

    private final FileChooser fileChooser = new FileChooser();

    private File workingDir = new File(".");
    private final BorderPane rootPane = new BorderPane();

    private AppSession session;
    private String featureName;
    private Feature feature;
    private String env;

    private File openFileChooser(Stage stage, String description, String extension) {
        fileChooser.setTitle("Choose Feature File");
        fileChooser.setInitialDirectory(workingDir);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(description, extension);
        fileChooser.getExtensionFilters().setAll(extFilter);
        return fileChooser.showOpenDialog(stage);
    }

    private File saveFileChooser(Stage stage, String description, String extension, String name) {
        fileChooser.setTitle("Save Feature File");
        fileChooser.setInitialDirectory(workingDir);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(description, extension);
        fileChooser.getExtensionFilters().setAll(extFilter);
        fileChooser.setInitialFileName(name);
        return fileChooser.showSaveDialog(stage);
    }

    public static Font getDefaultFont() {
        return Font.font("Courier");
    }

    private void initUi(Stage stage) {
        if (feature != null) {
            session = new AppSession(rootPane, workingDir, feature, env);
        }
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem openFileMenuItem = new MenuItem("Open");
        fileMenu.getItems().addAll(openFileMenuItem);
        openFileMenuItem.setOnAction(e -> {
            File file = openFileChooser(stage, "*.feature files", "*.feature");
            if (file != null) {
                feature = FeatureParser.parse(file);
                workingDir = file.getParentFile();
                initUi(stage);
            }
        });
        MenuItem saveFileMenuItem = new MenuItem("Save");
        fileMenu.getItems().addAll(saveFileMenuItem);
        saveFileMenuItem.setOnAction(e -> {
            String fileName = featureName == null ? "noname" : featureName;
            File file = saveFileChooser(stage, "*.feature files", "*.feature", fileName + ".feature");
            if (file != null) {
                FileUtils.writeToFile(file, feature.getText());
            }
        });
        Menu importMenu = new Menu("Import");
        MenuItem importMenuItem = new MenuItem("Open");
        importMenuItem.setOnAction(e -> {
            File file = openFileChooser(stage, "*.postman_collection files", "*.postman_collection");
            if (file == null) {
                return;
            }
            String json = FileUtils.toString(file);
            List<PostmanItem> items = PostmanUtils.readPostmanJson(json);
            featureName = FileUtils.removeFileExtension(file.getName());
            String text = PostmanUtils.toKarateFeature(featureName, items);
            feature = FeatureParser.parseText(null, text);
            initUi(stage);
        });
        importMenu.getItems().addAll(importMenuItem);
        menuBar.getMenus().addAll(fileMenu, importMenu);
        rootPane.setTop(menuBar);
    }

    @Override
    public void start(Stage stage) throws Exception {
        String fileName;
        List<String> params = getParameters().getUnnamed();
        env = System.getProperty(ScriptBindings.KARATE_ENV);
        if (!params.isEmpty()) {
            fileName = params.get(0);
            if (params.size() > 1) {
                env = params.get(1);
            }
        } else {
            fileName = null;
        }
        if (fileName != null) {
            File file = new File(fileName);
            feature = FeatureParser.parse(file);
            workingDir = file.getAbsoluteFile().getParentFile();
        }
        initUi(stage);
        Scene scene = new Scene(rootPane, 1080, 720);
        stage.setScene(scene);
        stage.setTitle("Karate UI");
        stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(KARATE_LOGO)));
        setDockIconForMac();
        stage.show();
    }

    private void setDockIconForMac() {
        if (FileUtils.isMac()) {
            try {
                ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource(KARATE_LOGO));
                // com.apple.eawt.Application.getApplication().setDockIconImage(icon.getImage());
                // TODO help
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        App.launch(args);
    }

    public static void run(String featurePath, String env) {
        App.launch(new String[]{featurePath, env});
    }

}
