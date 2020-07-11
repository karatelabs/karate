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

import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.Location;
import com.intuit.karate.robot.Region;
import com.intuit.karate.robot.RobotBase;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinDef;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class WinElement implements Element {

    protected final IUIAutomationElement e;
    private final WinRobot robot;

    public WinElement(WinRobot robot, IUIAutomationElement e) {
        this.robot = robot;
        this.e = e;
    }

    @Override
    public RobotBase getRobot() {
        return robot;
    }

    @Override
    public boolean isPresent() {
        return e.isValid() && !e.isNull();
    }

    @Override
    public boolean isEnabled() {
        return e.getCurrentIsEnabled();
    }

    @Override
    public boolean isImage() {
        return false;
    }

    @Override
    public Region getRegion() {
        WinDef.RECT rect = e.getCurrentBoundingRectangle();
        return new Region(robot, rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
    }

    private Location getClickablePoint() {
        WinDef.POINT p = e.getClickablePoint();
        return p == null ? getRegion().getCenter() : new Location(robot, p.x, p.y);
    }

    @Override
    public Element focus() {
        e.setFocus();
        return this;
    }

    public Element invoke() {
        if (isInvokePatternAvailable()) {
            IUIAutomationInvokePattern invokePattern = e.getCurrentPattern(IUIAutomationInvokePattern.class);
            invokePattern.invoke();
        } else {
            click();
        }
        return this;
    }

    @Override
    public Element click() {
        getClickablePoint().click();
        return this;
    }

    @Override
    public Element move() {
        getClickablePoint().move();
        return this;
    }

    @Override
    public Element press() {
        getClickablePoint().press();
        return this;
    }

    @Override
    public Element release() {
        getClickablePoint().release();
        return this;
    }

    @Override
    public String getName() {
        return e.getCurrentName();
    }

    private boolean isValuePatternAvailable() {
        Variant.VARIANT variant = e.getCurrentPropertyValue(Property.IsValuePatternAvailable);
        return variant.booleanValue();
    }

    private boolean isInvokePatternAvailable() {
        Variant.VARIANT variant = e.getCurrentPropertyValue(Property.IsInvokePatternAvailable);
        return variant.booleanValue();
    }

    @Override
    public String getValue() {
        if (isValuePatternAvailable()) {
            return e.getCurrentPattern(IUIAutomationValuePattern.class).getCurrentValue();
        }
        return null;
    }

    @Override
    public Element clear() {
        if (isValuePatternAvailable()) {
            IUIAutomationValuePattern valuePattern = e.getCurrentPattern(IUIAutomationValuePattern.class);
            valuePattern.setCurrentValue("");
        } else {
            e.setFocus();
            robot.clearFocused();
        }
        return this;
    }

    @Override
    public Element input(String value) {
        if (isValuePatternAvailable()) {
            IUIAutomationValuePattern valuePattern = e.getCurrentPattern(IUIAutomationValuePattern.class);
            valuePattern.setCurrentValue(value);
        } else {
            e.setFocus();
            robot.input(value);
        }
        return this;
    }

    @Override
    public Element delay(int millis) {
        robot.delay(millis);
        return this;
    }

    @Override
    public List<Element> getChildren() {
        IUIAutomationElementArray array = e.findAll(TreeScope.Children, WinRobot.UIA.createTrueCondition());
        int count = array.getLength();
        List<Element> list = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            IUIAutomationElement child = array.getElement(i);
            list.add(new WinElement(robot, child));
        }
        return list;
    }

    private IUIAutomationTreeWalker walk() {
        return WinRobot.UIA.getControlViewWalker();
    }

    @Override
    public Element getParent() {
        return new WinElement(robot, walk().getParentElement(e));
    }

    public Element getFirstChild() {
        return new WinElement(robot, walk().getFirstChildElement(e));
    }

    public Element getLastChild() {
        return new WinElement(robot, walk().getLastChildElement(e));
    }

    public Element getNextSibling() {
        return new WinElement(robot, walk().getNextSiblingElement(e));
    }

    public Element getPreviousSibling() {
        return new WinElement(robot, walk().getPreviousSiblingElement(e));
    }

    @Override
    public IUIAutomationElement toNative() {
        return e;
    }

    @Override
    public String getDebugString() {
        if (!e.isValid()) {
            return "(null)";
        }
        try {
            return "'" + e.getCurrentName() + "' " + e.getControlType();
        } catch (Exception e) {
            return "(stale) " + e.getMessage();
        }
    }

    @Override
    public String toString() {
        return getDebugString();
    }

    @Override
    public Element select() {
        IUIAutomationSelectionItemPattern pattern = e.getCurrentPattern(IUIAutomationSelectionItemPattern.class);
        pattern.select();
        return this;
    }

    public Object as(String patternName) {
        Pattern pattern = Pattern.fromName(patternName);
        if (pattern == null) {
            throw new RuntimeException("no such pattern: " + patternName);
        }
        if (pattern.type == null) {
            throw new RuntimeException("pattern not implemented: " + pattern);
        }
        return e.getCurrentPattern(pattern.type);
    }

}
