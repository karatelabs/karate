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

import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public interface Element {

    RobotBase getRobot();

    default Location offset(int fromLeft, int fromTop) {
        Region r = getRegion();
        Location l = new Location(r.robot, r.x + fromLeft, r.y + fromTop);
        if (l.robot.highlight) {
            l.highlight();
        }
        return l;
    }

    boolean isPresent(); // getter    

    boolean isImage(); // getter

    boolean isEnabled(); // getter    

    default Map<String, Object> getPosition() { // getter
        return getRegion().getPosition();
    }

    Region getRegion();

    default byte[] screenshot() {
        return getRegion().screenshot();
    }

    Element focus();

    default Element move(int fromLeft, int fromTop) {
        offset(fromLeft, fromTop).move();
        return this;
    }

    Element click();

    Element clear();

    default Element click(int fromLeft, int fromTop) {
        offset(fromLeft, fromTop).click();
        return this;
    }
    
    default Element doubleClick(int fromLeft, int fromTop) {
        offset(fromLeft, fromTop).doubleClick();
        return this;
    }    

    Element move();

    Element press();

    Element release();

    Element highlight();

    String getName();

    String getValue();

    Element input(String value);

    Element delay(int millis);

    default Element retry() {
        getRobot().retry();
        return this;
    }

    default Element retry(int count) {
        getRobot().retry(count);
        return this;
    }

    default Element retry(Integer count, Integer interval) {
        getRobot().retry(count, interval);
        return this;
    }

    default Element locate(String locator) {
        RobotBase robot = getRobot();
        return robot.locate(robot.getHighlightDuration(), this, locator);
    }   

    default Element optional(String locator) {
        return getRobot().optional(this, locator);
    }
    
    default boolean exists(String locator) {
        return optional(locator).isPresent();
    }
    
    List<Element> getChildren();
    
    Element getParent();

    <T> T toNative();
    
    String getDebugString();

    Element select();
    
    default String extract(String lang) {
        boolean negative = lang.charAt(0) == '-';
        if (negative) {
            lang = lang.substring(1);
        }
        Tesseract tess = Tesseract.init(getRobot(), lang, getRegion(), negative);
        return tess.getAllText();
    }

}
