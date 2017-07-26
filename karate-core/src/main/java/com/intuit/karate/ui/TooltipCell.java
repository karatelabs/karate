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

import javafx.geometry.Point2D;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;

/**
 *
 * @author pthomas3
 * @param <S>
 * @param <T>
 */
public abstract class TooltipCell<S, T> extends TableCell<S, T> {

    private Tooltip customTooltip;
    
    protected abstract String getCellText(T t);
    protected abstract String getTooltipText(T t);

    private void initTooltipMouseEvents() {
        setOnMouseEntered(e -> {
            if (customTooltip != null) {
                Point2D p = localToScreen(getLayoutBounds().getMaxX(), getLayoutBounds().getMaxY());
                customTooltip.show(this, p.getX(), p.getY());
            }
        });
        setOnMouseExited(e -> {
            if (customTooltip != null) {
                customTooltip.hide();
            }
        });
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty) {
            setText(getCellText(item));
            customTooltip = new Tooltip(getTooltipText(item));
            initTooltipMouseEvents();
        }
    }    

}
