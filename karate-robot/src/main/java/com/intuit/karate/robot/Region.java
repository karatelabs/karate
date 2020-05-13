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

import java.awt.image.BufferedImage;

/**
 *
 * @author pthomas3
 */
public class Region extends RobotAware {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Region(Robot robot, int x, int y) {
        this(robot, x, y, 0, 0);
    }

    public Region(Robot robot, int x, int y, int width, int height) {
        super(robot);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public BufferedImage capture() {
        return robot.capture(this);
    }

    public Location center() {
        return new Location(robot, x + width / 2, y + height / 2);
    }

    public void highlight() {
        RobotUtils.highlight(x, y, width, height, robot.highlightDuration);
    }

    public Region click() {
        return click(1);
    }

    public Region click(int num) {
        center().click(num);
        return this;
    }

    public Region move() {
        center().move();
        return this;
    }

    public Region press() {
        center().press();
        return this;
    }
    
    public Region release() {
        center().release();
        return this;
    }    
    
}
