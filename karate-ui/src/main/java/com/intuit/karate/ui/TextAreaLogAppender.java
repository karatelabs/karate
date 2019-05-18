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
import javafx.scene.control.TextArea;

/**
 *
 * @author pthomas3
 */
public class TextAreaLogAppender implements LogAppender {

    private final TextArea textArea;

    public static TextAreaLogAppender init(Logger logger, TextArea textArea) {
        return new TextAreaLogAppender(logger, textArea);
    }
    
    private TextAreaLogAppender(Logger logger, TextArea textArea) {
        this.textArea = textArea;
        logger.setLogAppender(this);
    }

    @Override
    public String collect() {
        String text = textArea.getText();
        textArea.clear();
        return text;
    }

    @Override
    public void append(String text) {
        textArea.appendText(text);
    }

    @Override
    public void close() {

    }    

}
