/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.robot.win;

import com.sun.jna.ptr.IntByReference;

/**
 *
 * @author pthomas3
 */
public class IUIAutomationWindowPattern extends IUIAutomationBase {

    public void close() {
        invoke("Close");
    }

    public boolean canMaximize() {
        return invokeForBool("CurrentCanMaximize");
    }

    public boolean canMinimize() {
        return invokeForBool("CurrentCanMinimize");
    }

    public boolean isModal() {
        return invokeForBool("CurrentIsModal");
    }

    public boolean isTopmost() {
        return invokeForBool("CurrentIsTopmost");
    }

    public int getCurrentWindowInteractionState() {
        return invokeForInt("CurrentWindowInteractionState");
    }

    public int getCurrentWindowVisualState() {
        return invokeForInt("CurrentWindowVisualState");
    }

    public void setWindowVisualState(int state) {
        invoke("SetWindowVisualState", state);
    }
    
    public void minimize() {
        setWindowVisualState(2);
    }
    
    public void maximize() {
        setWindowVisualState(1);
    }    
    
    public void restore() {
        setWindowVisualState(0);
    }    

    public boolean waitForInputIdle(int timeoutMillis) {
        IntByReference intRef = new IntByReference();
        invoke("WaitForInputIdle", timeoutMillis, intRef);
        return intRef.getValue() != 0;
    }

}
