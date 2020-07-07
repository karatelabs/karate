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

import com.intuit.karate.Config;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Location {

    public final RobotBase robot;
    public final int x;
    public final int y;

    public Location(RobotBase robot, int x, int y) {
        this.robot = robot;
        this.x = x;
        this.y = y;
    }

    public Location move() {
        robot.move(x, y);
        return this;
    }

    public Location click() {
        return click(1);
    }

    public Location click(int num) {
        robot.move(x, y); // do not chain, causes recursion
        robot.click(num);
        return this;
    }

    public Location doubleClick() {
        robot.move(x, y); // do not chain, causes recursion        
        robot.doubleClick();
        return this;
    }

    public Location press() {
        robot.move(x, y); // do not chain, causes recursion
        robot.press();
        return this;
    }

    public Location release() {
        robot.move(x, y); // do not chain, causes recursion
        robot.release();
        return this;
    }
    
    public Location highlight() {
        return highlight(Config.DEFAULT_HIGHLIGHT_DURATION);
    }

    public Location highlight(int duration) {
        new Region(robot, x - 5, y - 5, 10, 10).highlight(duration);
        return this;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new HashMap(2);
        map.put("x", x);
        map.put("y", y);
        return map;
    }

    @Override
    public String toString() {
        return asMap().toString();
    }

}
