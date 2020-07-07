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
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Region  {

    public final RobotBase robot;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    
    Region toAbsolute(Region offset) {
        return new Region(robot, x + offset.x, y + offset.y, width, height);
    }

    public Region(RobotBase robot, int x, int y) {
        this(robot, x, y, 0, 0);
    }

    public Region(RobotBase robot, int x, int y, int width, int height) {
        this.robot = robot;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    private BufferedImage capture(int type) {
        Image image = robot.robot.createScreenCapture(new Rectangle(x, y, width, height));
        BufferedImage bi = new BufferedImage(width, height, type);
        Graphics g = bi.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        return bi;
    }    
    
    public BufferedImage capture() {
        return Region.this.capture(BufferedImage.TYPE_INT_RGB);
    }
    
    public BufferedImage captureGreyScale() {
        return Region.this.capture(BufferedImage.TYPE_BYTE_GRAY);
    }    

    public Location getCenter() {
        return new Location(robot, x + width / 2, y + height / 2);
    }
    
    public Location inset(int deltaX, int deltaY) {
        return new Location(robot, x + deltaX, y + deltaY);
    }

    public void highlight() {
        highlight(Config.DEFAULT_HIGHLIGHT_DURATION);
    }
    
    public void highlight(int millis) {
        RobotUtils.highlight(this, millis);
    }    

    public Region click() {
        return click(1);
    }

    public Region click(int num) {
        getCenter().click(num);
        return this;
    }

    public Region move() {
        getCenter().move();
        return this;
    }

    public Region press() {
        getCenter().press();
        return this;
    }
    
    public Region release() {
        getCenter().release();
        return this;
    }    
    
    public Map<String, Object> getPosition() {
        Map<String, Object> map = new HashMap(4);
        map.put("x", x);
        map.put("y", y);
        map.put("width", width);
        map.put("height", height);
        return map;
    }
    
    public byte[] screenshot() {
        return robot.screenshot(this);
    }   
    
    public String extract(String lang, boolean debug) {
        if (lang == null) {
            lang = robot.tessLang;
        }
        if (lang.length() < 2) {
            lang = lang + robot.tessLang;
        }
        boolean negative = lang.charAt(0) == '-';
        if (negative) {
            lang = lang.substring(1);
        }
        Tesseract tess = Tesseract.init(robot, lang, this, negative);
        if (debug) {
            tess.highlightWords(robot, this, Config.DEFAULT_HIGHLIGHT_DURATION);
        }
        return tess.getAllText();
    }    
    
    public void debugCapture() {
        OpenCvUtils.show(capture(), toString());
    } 
    
    public String debugExtract() {
        return extract(null, true);
    }
    
    public String debugExtract(String lang) {
        return extract(lang, true);
    }    

    @Override
    public String toString() {
        return x + ":" + y + "(" + width + ":" + height + ")";
    }        
    
}
