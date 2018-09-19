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
package com.intuit.karate.web.chrome;

import com.intuit.karate.ui.App;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 *
 * @author pthomas3
 */
public class ChromeApp extends Application {

    private final BorderPane rootPane = new BorderPane();
    
    private Chrome chrome;

    @Override
    public void start(Stage stage) throws Exception {
        TextArea input = new TextArea();
        input.setText("{ method: 'Page.navigate', params: { url: 'https://google.com'} }");
        input.setPrefRowCount(5);
        rootPane.setTop(input);
        Button send = new Button("Send");
        rootPane.setBottom(send);
        
        chrome = Chrome.start(9222);
                
        send.setOnAction(e -> {
            chrome.sendAndWait(input.getText());
        });
        
        Scene scene = new Scene(rootPane, 900, 200);
        stage.setScene(scene);
        stage.setTitle("Chrome Debug");
        stage.show();        
    }
    
    public static void main(String[] args) {
        App.launch(args);
    }    

}
