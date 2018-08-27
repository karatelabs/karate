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

import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import static com.intuit.karate.ui.App.PADDING_INSET;

/**
 *
 * @author pthomas3
 */
public class LogPanel extends BorderPane {

    private final TextArea textArea;

    public LogPanel(Logger logger) {
        setPadding(PADDING_INSET);
        textArea = new TextArea();
        LogAppender appender = new TextAreaLogAppender(logger, textArea);
        textArea.setPrefRowCount(40);
        textArea.setPrefColumnCount(120);
        textArea.setFont(App.getDefaultFont());
        Button clearButton = new Button("Clear Log");
        clearButton.setOnAction(e -> textArea.clear());
        setCenter(textArea);
        setBottom(clearButton);
        setMargin(clearButton, new Insets(2.0, 0, 0, 0));
        DragResizer.makeResizable(textArea, false, true, true, true);
    }
    
    public void append(String s) {
        textArea.appendText(s);
    }
    
}
