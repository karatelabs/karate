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

import com.intuit.karate.ScriptValue;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;

/**
 *
 * @author pthomas3
 */
public class VarsPanel extends BorderPane {
    
    private final AppSession session;
    private final TableView<Var> table;
        
    public VarsPanel(AppSession session) {
        this.session = session;        
        table = new TableView();
        setCenter(table);
        TableColumn nameCol = new TableColumn("Variable");
        nameCol.setCellValueFactory(new PropertyValueFactory("name"));
        TableColumn typeCol = new TableColumn("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory("type"));
        TableColumn<Var, ScriptValue> valueCol = new TableColumn("Value");
        valueCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper(c.getValue().getValue()));        
        valueCol.setCellFactory(c -> {
            VarValueCell cell = new VarValueCell();
            cell.setOnMouseEntered(e -> {
                if (cell.getCustomTooltip() != null) {
                    Point2D p = cell.localToScreen(cell.getLayoutBounds().getMaxX(), cell.getLayoutBounds().getMaxY());
                    cell.getCustomTooltip().show(cell, p.getX(), p.getY());
                }
            });
            cell.setOnMouseExited(e -> {
                if (cell.getCustomTooltip() != null) {
                    cell.getCustomTooltip().hide();
                }
            });
            return cell;
        });
        table.getColumns().addAll(nameCol, typeCol, valueCol);
        table.setItems(session.getVars());
        table.setRowFactory(tv -> {
            TableRow<Var> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Var var = row.getItem();
                    session.logVar(var);
                }
            });
            return row ;
        });        
    }
    
    public void refresh() {
        table.setItems(session.getVars());
        table.refresh();
    }
    
}
