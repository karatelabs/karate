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

import javafx.collections.ObservableList;
import javafx.scene.control.Accordion;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;

import static com.intuit.karate.ui.App.PADDING_INSET;

/**
 * @author vmchukky
 */
public class HttpPanel extends BorderPane {

    private final TextArea request;
    private final TextArea response;

    public HttpPanel() {
        this.setPadding(PADDING_INSET);
        request = new TextArea();
        response = new TextArea();
        request.setPrefColumnCount(60);
        response.setPrefColumnCount(60);
        request.setFont(App.getDefaultFont());
        response.setFont(App.getDefaultFont());
        TitledPane requestPane = new TitledPane("View request", request);
        TitledPane responsePane = new TitledPane("View response", response);
        Accordion accordion = new Accordion();
        accordion.getPanes().addAll(requestPane, responsePane);
        accordion.setExpandedPane(responsePane);
        setCenter(accordion);
        DragResizer.makeResizable(accordion, false, false, true, true);
    }

    public void refresh(VarLists varLists) {
        if (varLists != null) {
            request.setText(getLines(varLists.getRequestVarList()));
            response.setText(getLines(varLists.getResponseVarList()));
        } else {
            request.clear();
            response.clear();
        }
    }

    private String getLines(ObservableList<Var> vars) {
        StringBuilder sb = new StringBuilder();
        for (Var var : vars) {
            sb.append(var.getName()).append(" : ")
                    .append((var.getValue() != null ? var.getValue().getAsPrettyString() : ""))
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

}
