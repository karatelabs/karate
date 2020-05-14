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

/**
 *
 * @author pthomas3
 */
public class IUIAutomationTreeWalker extends IUIAutomationBase {

    public IUIAutomationElement getFirstChildElement(IUIAutomationElement e) {
        return invokeForElement("GetFirstChildElement", e);
    }
    
    public IUIAutomationElement getLastChildElement(IUIAutomationElement e) {
        return invokeForElement("GetLastChildElement", e);
    }    

    public IUIAutomationElement getNextSiblingElement(IUIAutomationElement e) {
        return invokeForElement("GetNextSiblingElement", e);
    }

    public IUIAutomationElement getParentElement(IUIAutomationElement e) {
        return invokeForElement("GetParentElement", e);
    }

    public IUIAutomationElement getPreviousSiblingElement(IUIAutomationElement e) {
        return invokeForElement("GetPreviousSiblingElement", e);
    }
    
    public IUIAutomationElement normalizeElement(IUIAutomationElement e) {
        return invokeForElement("NormalizeElement", e);
    }    

}
