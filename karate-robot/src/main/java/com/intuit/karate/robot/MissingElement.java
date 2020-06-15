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
package com.intuit.karate.robot;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class MissingElement implements Element {

    private final RobotBase robot;

    public MissingElement(RobotBase robot) {
        this.robot = robot;
    }

    @Override
    public RobotBase getRobot() {
        return robot;
    }

    @Override
    public boolean isPresent() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isImage() {
        return false;
    }

    @Override
    public Region getRegion() {
        return robot.screen;
    }

    @Override
    public Element focus() {
        return this;
    }

    @Override
    public Element click() {
        return this;
    }

    @Override
    public Element click(String locator) {
        return this;
    }     

    @Override
    public Element click(int fromLeft, int fromTop) {
        return this;
    }        

    @Override
    public Element move() {
        return this;
    }

    @Override
    public Element press() {
        return this;
    }

    @Override
    public Element release() {
        return this;
    }

    @Override
    public Element highlight() {
        return this;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getValue() {
        return null;
    }

    @Override
    public Element input(String value) {
        return this;
    }

    @Override
    public Element clear() {
        return this;
    }

    @Override
    public Element delay(int millis) {
        robot.delay(millis);
        return this;
    }

    @Override
    public Element locate(String locator) {
        return this;
    }

    @Override
    public List<Element> getChildren() {
        return Collections.EMPTY_LIST;
    }        

    @Override
    public Element getParent() {
        return this;
    }        

    @Override
    public <T> T toNative() {
        return null;
    }

    @Override
    public String getDebugString() {
        return "(missing element)";
    }

    @Override
    public Element select() {
        return this;
    }

    @Override
    public Element select(String locator) {
        return this;
    }        

}
