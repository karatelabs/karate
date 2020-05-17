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
import com.intuit.karate.robot.Robot;
import com.intuit.karate.robot.RobotAware;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinDef;

/**
 *
 * @author pthomas3
 */
public class WinElement extends RobotAware implements Element {

    private final IUIAutomationElement e;

    public WinElement(Robot robot, IUIAutomationElement e) {
        super(robot);
        this.e = e;
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
        WinDef.POINT point = e.getClickablePoint();
        return new Location(robot, point.x, point.y);
    }

    @Override
    public Element focus() {
        e.setFocus();
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
    public Element highlight() {
        getRegion().highlight();
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

    @Override
    public String getValue() {
        if (isValuePatternAvailable()) {
            return e.getCurrentPattern(IUIAutomationValuePattern.class).getCurrentValue();
        }
        return null;
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
    public Element locate(String locator) {
        return robot.locateElement(this, locator);
    }     

    @Override
    public IUIAutomationElement toNative() {
        return e;
    }

}
