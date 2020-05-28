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
public class ImageElement implements Element {

    private final Region region;
    private final RobotBase robot;
    private final String value;

    public ImageElement(Region region) {
        this(region, null);
    }
    
    public ImageElement(Region region, String value) {
        this.region = region;
        robot = region.robot;
        this.value = value == null ? region.toString() : value;
    }    

    @Override
    public RobotBase getRobot() {
        return robot;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isImage() {
        return true;
    }

    @Override
    public Region getRegion() {
        return region;
    }

    @Override
    public Element focus() {
        region.click();
        return this;
    }

    @Override
    public Element click() {
        region.click();
        return this;
    }

    @Override
    public Element move() {
        region.move();
        return this;
    }

    @Override
    public Element press() {
        region.press();
        return this;
    }

    @Override
    public Element release() {
        region.release();
        return this;
    }

    @Override
    public String getName() {
        return region.toString();
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Element input(String value) {
        region.click();
        robot.input(value);
        return this;
    }

    @Override
    public Element clear() {
        region.click();
        robot.clearFocused();
        return this;
    }

    @Override
    public Element delay(int millis) {
        robot.delay(millis);
        return this;
    }

    @Override
    public List<Element> getChildren() {
        return Collections.EMPTY_LIST;
    }        

    @Override
    public Element getParent() {
        return null;
    }        

    @Override
    public Region toNative() {
        return region;
    }

    @Override
    public String getDebugString() {
        return getName();
    }

    @Override
    public Element select() {
        return this;
    }

}
