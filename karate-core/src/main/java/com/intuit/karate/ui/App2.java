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
import java.io.File;
import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javax.swing.ImageIcon;

/**
 *
 * @author pthomas3
 */
public class App2 extends Application {

    private static final String KARATE_LOGO = "karate-logo.png";
    public static final double PADDING = 3.0;
    public static final Insets PADDING_ALL = new Insets(App2.PADDING, App2.PADDING, App2.PADDING, App2.PADDING);
    public static final Insets PADDING_HOR = new Insets(0, App2.PADDING, 0, App2.PADDING);
    public static final Insets PADDING_VER = new Insets(App2.PADDING, 0, App2.PADDING, 0);
    public static final Insets PADDING_TOP = new Insets(App2.PADDING, 0, 0, 0);
    public static final Insets PADDING_BOT = new Insets(0, 0, App2.PADDING, 0);

    public static Font getDefaultFont() {
        return Font.font("Courier");
    }

    private BorderPane rootPane;

    @Override
    public void start(Stage stage) throws Exception {
        String fileName;
        List<String> params = getParameters().getUnnamed();
        String envString = System.getProperty(ScriptBindings.KARATE_ENV);
        if (!params.isEmpty()) {
            fileName = params.get(0);
            if (params.size() > 1) {
                envString = params.get(1);
            }
        } else {
            fileName = null;
        }
        File file = new File(fileName);
        AppSession2 session = new AppSession2(file, envString);
        rootPane = session.getRootPane();
        Scene scene = new Scene(rootPane, 900, 750);
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
        App2.launch(args);
    }

    public static void run(String featurePath, String env) {
        App2.launch(new String[]{featurePath, env});
    }

}
