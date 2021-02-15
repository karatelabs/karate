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

import com.sun.jna.platform.win32.WinDef;

/**
 *
 * @author pthomas3
 */
public class IUIAutomation extends IUIAutomationBase {
    
    public static final IUIAutomation INSTANCE = new IUIAutomation();

    private IUIAutomation() {
        super(ComUtils.AUTO_REF); // important !
    }

    public IUIAutomationElement getRootElement() {
        return invokeForElement("GetRootElement");
    }

    public IUIAutomationElement getFocusedElement() {
        return invokeForElement("GetFocusedElement");
    }

    public IUIAutomationElement elementFromPoint(int x, int y) {
        return invokeForElement("ElementFromPoint", new WinDef.POINT(x, y));
    }
    
    public IUIAutomationElement elementFromHandle(WinDef.HWND hwnd) {
        return invokeForElement("ElementFromHandle", hwnd);
    }    

    public IUIAutomationCondition createTrueCondition() {
        return invokeForCondition("CreateTrueCondition");
    }

    public IUIAutomationCondition createFalseCondition() {
        return invokeForCondition("CreateFalseCondition");
    }

    public IUIAutomationCondition createPropertyCondition(Property property, ComAllocated value) {
        return invokeForCondition("CreatePropertyCondition", property.value, value);
    }
    
    public IUIAutomationCondition createPropertyCondition(Property property, String value) {
        return createPropertyCondition(property, new ComAllocatedVarStr(value));
    }   
    
    public IUIAutomationCondition createPropertyCondition(Property property, int value) {   
        return createPropertyCondition(property, new ComAllocatedVarInt(value));
    }    

    public IUIAutomationCondition getContentViewCondition() {
        return invokeForCondition("ContentViewCondition");
    }

    public IUIAutomationCondition getControlViewCondition() {
        return invokeForCondition("ControlViewCondition");
    }

    public IUIAutomationCondition getRawViewCondition() {
        return invokeForCondition("RawViewCondition");
    }

    public IUIAutomationCondition createAndCondition(IUIAutomationCondition c1, IUIAutomationCondition c2) {
        return invokeForCondition("CreateAndCondition", c1, c2);
    }

    public IUIAutomationCondition createOrCondition(IUIAutomationCondition c1, IUIAutomationCondition c2) {
        return invokeForCondition("CreateOrCondition", c1, c2);
    }

    public IUIAutomationCondition createNotCondition(IUIAutomationCondition c) {
        return invokeForCondition("CreateNotCondition", c);
    }
    
    public IUIAutomationTreeWalker getControlViewWalker() {
        return invoke(IUIAutomationTreeWalker.class, "ControlViewWalker");
    }

}
