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
import com.sun.jna.ptr.PointerByReference;

/**
 *
 * @author pthomas3
 */
public class IUIAutomationElement extends IUIAutomationBase {

    public String getCurrentName() {
        return invokeForString("CurrentName");
    }

    public ControlType getControlType() {
        int controlType = getCurrentPropertyValue(Property.ControlType).intValue();
        return ControlType.fromValue(controlType);
    }

    public String getClassName() {
        return getCurrentPropertyValue(Property.ClassName).stringValue();
    }

    public String getAutomationId() {
        return getCurrentPropertyValue(Property.AutomationId).stringValue();
    }

    public IUIAutomationElement findFirst(TreeScope scope, IUIAutomationCondition condition) {
        return invokeForElement("FindFirst", scope.value, condition);
    }

    public IUIAutomationElementArray findAll(TreeScope scope, IUIAutomationCondition condition) {
        return invoke(IUIAutomationElementArray.class, "FindAll", scope.value, condition);
    }

    public Variant.VARIANT getCurrentPropertyValue(Property property) {
        return invoke(Variant.VARIANT.class, "GetCurrentPropertyValue", property.value);
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
            return null;
        }
        return point;
    }

    public WinDef.RECT getCurrentBoundingRectangle() {
        return invoke(WinDef.RECT.class, "CurrentBoundingRectangle");
    }

    public <T> T getCurrentPattern(Class<T> type) {
        Pattern pattern = Pattern.fromType(type);
        if (pattern == null) {
            throw new RuntimeException("unsupported pattern: " + type);
        }
        return invoke(type, "GetCurrentPattern", pattern.value);
    }

    public WinDef.HWND getCurrentNativeWindowHandle() {
        PointerByReference pbr = new PointerByReference();
        invoke("CurrentNativeWindowHandle", pbr);
        return new WinDef.HWND(pbr.getValue());
    }
    
    public boolean getCurrentIsEnabled() {
        return invokeForBool("CurrentIsEnabled");
    }    

    @Override
    public String toString() {
        if (!isValid()) {
            return "(stale)";
        }
        try {
            return getControlType() + ":" + getCurrentName();
        } catch (Exception e) {
            return "(stale) " + e.getMessage();
        }
    }    
    
}
