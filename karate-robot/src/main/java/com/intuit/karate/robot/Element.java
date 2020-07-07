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
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public interface Element {

    RobotBase getRobot();

    default Location inset(int fromLeft, int fromTop) {
        Region r = getRegion();
        Location l = new Location(r.robot, r.x + fromLeft, r.y + fromTop);
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
    
    default Element focus(String locator) {
        RobotBase robot = getRobot();
        return robot.locate(robot.getHighlightDuration(), this, locator).focus();
    }    

    default Element move(int fromLeft, int fromTop) {
        inset(fromLeft, fromTop).move();
        return this;
    }

    Element click();
    
    default Element click(String locator) {
        RobotBase robot = getRobot();
        return robot.locate(robot.getHighlightDuration(), this, locator).click();
    }

    Element clear();

    default Element click(int fromLeft, int fromTop) {
        inset(fromLeft, fromTop).click();
        return this;
    }

    default Element doubleClick(int fromLeft, int fromTop) {
        inset(fromLeft, fromTop).doubleClick();
        return this;
    }

    Element move();

    Element press();

    Element release();

    String getName(); // getter

    String getValue(); // getter

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
    
    default Element waitFor(String locator) {
        return getRobot().retryForAny(true, this, locator);
    }
    
    default Element waitForAny(String locator1, String locator2) {
        return getRobot().retryForAny(true, this, locator1, locator2);
    }
    
    default Element waitForAny(String[] locators) {
        return getRobot().retryForAny(true, this, locators);
    }    

    default Element retry(Integer count, Integer interval) {
        getRobot().retry(count, interval);
        return this;
    }

    default Element locate(String locator) {
        RobotBase robot = getRobot();
        return robot.locate(robot.getHighlightDuration(), this, locator);
    }

    default List<Element> locateAll(String locator) {
        RobotBase robot = getRobot();
        return robot.locateAll(robot.getHighlightDuration(), this, locator);
    }
    
    default Element highlight(int millis) {
        getRegion().highlight(millis);
        return this;
    }    

    default Element highlight() {
        return highlight(Config.DEFAULT_HIGHLIGHT_DURATION);
    }

    default Element highlight(String locator) {
        RobotBase robot = getRobot();
        return robot.locate(Config.DEFAULT_HIGHLIGHT_DURATION, this, locator);
    }

    default List<Element> highlightAll(String locator) {
        RobotBase robot = getRobot();
        return robot.locateAll(Config.DEFAULT_HIGHLIGHT_DURATION, this, locator);
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
    
    default Element select(String locator) {
        RobotBase robot = getRobot();
        return robot.locate(robot.getHighlightDuration(), this, locator).select();        
    }

    default String extract() {
        return extract(null, false);
    }

    default String extract(String lang, boolean debug) {
        return getRegion().extract(lang, debug);
    }

    default Element activate() {
        getRobot().setActive(this);
        return this;
    }

    default void debugCapture() {
        getRegion().debugCapture();
    }

    default String debugExtract() {
        return extract(null, true);
    }
    
    default String debugExtract(String lang) {
        return extract(lang, true);
    }    

}
