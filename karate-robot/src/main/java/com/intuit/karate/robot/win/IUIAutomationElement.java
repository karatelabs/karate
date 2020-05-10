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

import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinDef;

/**
 *
 * @author pthomas3
 */
public class IUIAutomationElement extends IUIAutomationBase {

    public String getCurrentName() {
        return invokeForString("CurrentName");
    }

    public IUIAutomationElement findFirst(String scope, IUIAutomationCondition condition) {
        return invokeForElement("FindFirst", enumValue("TreeScope", scope), condition);
    }

    public IUIAutomationElementArray findAll(String scope, IUIAutomationCondition condition) {
        return invoke(IUIAutomationElementArray.class, "FindAll", enumValue("TreeScope", scope), condition);
    }
    
    public Variant.VARIANT getCurrentPropertyValue(String propertyName) {
        return invoke(Variant.VARIANT.class, "GetCurrentPropertyValue", enumValue("UIA_PropertyIds", propertyName));
    }
    
    public void setFocus() {
        invoke("SetFocus");
    }
    
    public WinDef.POINT getClickablePoint() {
        WinDef.POINT point = new WinDef.POINT.ByReference();
        WinDef.BOOLByReference status = new WinDef.BOOLByReference();
        invoke("GetClickablePoint", point, status);
        if (!status.getValue().booleanValue()) {
            logger.warn("failed to get clickable point");
        }
        return point;
    }

}
