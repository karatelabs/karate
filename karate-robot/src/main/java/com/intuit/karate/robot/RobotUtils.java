/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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

import com.intuit.karate.driver.Keys;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RobotUtils {

    private static final Logger logger = LoggerFactory.getLogger(RobotUtils.class);

    public static void highlight(Region region, int time) {
        JFrame f = new JFrame();
        f.setUndecorated(true);
        f.setBackground(new Color(0, 0, 0, 0));
        f.setAlwaysOnTop(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setType(JFrame.Type.UTILITY);
        f.setFocusableWindowState(false);
        f.setAutoRequestFocus(false);
        f.setLocation(region.x, region.y);
        f.setSize(region.width, region.height);
        f.getRootPane().setBorder(BorderFactory.createLineBorder(Color.RED, 3));
        f.setVisible(true);
        delay(time);
        f.dispose();
    }

    static class RegionBox {

        RegionBox(int x, int y, int width, int height, String text) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
        }

        int x;
        int y;
        int width;
        int height;
        String text;

    }

    public static void highlightAll(Region parent, List<Element> elements, int time, boolean showValue) {
        JFrame f = new JFrame();
        f.setUndecorated(true);
        f.setBackground(new Color(0, 0, 0, 0));
        f.setAlwaysOnTop(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setType(JFrame.Type.UTILITY);
        f.setFocusableWindowState(false);
        f.setAutoRequestFocus(false);
        f.setLocation(parent.x, parent.y);
        f.setSize(parent.width, parent.height);
        f.getRootPane().setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
        // important to extract these so that swing awt ui thread doesn't clash with AUT native ui rendering
        List<RegionBox> boxes = new ArrayList(elements.size());
        for (Element e : elements) {
            Region region = e.getRegion();
            int x = region.x - parent.x;
            int y = region.y - parent.y;
            if (x > 0 && y > 0 && region.width > 0 && region.height > 0) {
                boxes.add(new RegionBox(x, y, region.width, region.height, showValue ? e.getValue() : null));
            }
        }
        f.add(new JComponent() {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setStroke(new BasicStroke(2));
                for (RegionBox box : boxes) {
                    g.setColor(Color.RED);
                    g.drawRect(box.x, box.y, box.width, box.height);
                    if (showValue) {
                        String text = box.text;
                        FontMetrics fm = g.getFontMetrics();
                        Rectangle2D rect = fm.getStringBounds(text, g);
                        g.setColor(Color.BLACK);
                        g.fillRect(box.x, box.y - fm.getAscent(), (int) rect.getWidth(), (int) rect.getHeight());
                        g.setColor(Color.YELLOW);
                        g.drawString(box.text, box.x, box.y);
                    }                    
                }
            }
        });
        f.setVisible(true);
        delay(time);
        if (showValue) {
            BufferedImage image = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
            f.paint(image.getGraphics());
            OpenCvUtils.show(image, parent.toString());
        }        
        f.dispose();
    }

    public static void delay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //==========================================================================
    //
    public static final Map<Character, int[]> KEY_CODES = new HashMap();

    private static void key(char c, int... i) {
        KEY_CODES.put(c, i);
    }

    static {
        key('a', KeyEvent.VK_A);
        key('b', KeyEvent.VK_B);
        key('c', KeyEvent.VK_C);
        key('d', KeyEvent.VK_D);
        key('e', KeyEvent.VK_E);
        key('f', KeyEvent.VK_F);
        key('g', KeyEvent.VK_G);
        key('h', KeyEvent.VK_H);
        key('i', KeyEvent.VK_I);
        key('j', KeyEvent.VK_J);
        key('k', KeyEvent.VK_K);
        key('l', KeyEvent.VK_L);
        key('m', KeyEvent.VK_M);
        key('n', KeyEvent.VK_N);
        key('o', KeyEvent.VK_O);
        key('p', KeyEvent.VK_P);
        key('q', KeyEvent.VK_Q);
        key('r', KeyEvent.VK_R);
        key('s', KeyEvent.VK_S);
        key('t', KeyEvent.VK_T);
        key('u', KeyEvent.VK_U);
        key('v', KeyEvent.VK_V);
        key('w', KeyEvent.VK_W);
        key('x', KeyEvent.VK_X);
        key('y', KeyEvent.VK_Y);
        key('z', KeyEvent.VK_Z);
        key('A', KeyEvent.VK_SHIFT, KeyEvent.VK_A);
        key('B', KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        key('C', KeyEvent.VK_SHIFT, KeyEvent.VK_C);
        key('D', KeyEvent.VK_SHIFT, KeyEvent.VK_D);
        key('E', KeyEvent.VK_SHIFT, KeyEvent.VK_E);
        key('F', KeyEvent.VK_SHIFT, KeyEvent.VK_F);
        key('G', KeyEvent.VK_SHIFT, KeyEvent.VK_G);
        key('H', KeyEvent.VK_SHIFT, KeyEvent.VK_H);
        key('I', KeyEvent.VK_SHIFT, KeyEvent.VK_I);
        key('J', KeyEvent.VK_SHIFT, KeyEvent.VK_J);
        key('K', KeyEvent.VK_SHIFT, KeyEvent.VK_K);
        key('L', KeyEvent.VK_SHIFT, KeyEvent.VK_L);
        key('M', KeyEvent.VK_SHIFT, KeyEvent.VK_M);
        key('N', KeyEvent.VK_SHIFT, KeyEvent.VK_N);
        key('O', KeyEvent.VK_SHIFT, KeyEvent.VK_O);
        key('P', KeyEvent.VK_SHIFT, KeyEvent.VK_P);
        key('Q', KeyEvent.VK_SHIFT, KeyEvent.VK_Q);
        key('R', KeyEvent.VK_SHIFT, KeyEvent.VK_R);
        key('S', KeyEvent.VK_SHIFT, KeyEvent.VK_S);
        key('T', KeyEvent.VK_SHIFT, KeyEvent.VK_T);
        key('U', KeyEvent.VK_SHIFT, KeyEvent.VK_U);
        key('V', KeyEvent.VK_SHIFT, KeyEvent.VK_V);
        key('W', KeyEvent.VK_SHIFT, KeyEvent.VK_W);
        key('X', KeyEvent.VK_SHIFT, KeyEvent.VK_X);
        key('Y', KeyEvent.VK_SHIFT, KeyEvent.VK_Y);
        key('Z', KeyEvent.VK_SHIFT, KeyEvent.VK_Z);
        key('1', KeyEvent.VK_1);
        key('2', KeyEvent.VK_2);
        key('3', KeyEvent.VK_3);
        key('4', KeyEvent.VK_4);
        key('5', KeyEvent.VK_5);
        key('6', KeyEvent.VK_6);
        key('7', KeyEvent.VK_7);
        key('8', KeyEvent.VK_8);
        key('9', KeyEvent.VK_9);
        key('0', KeyEvent.VK_0);
        key('!', KeyEvent.VK_SHIFT, KeyEvent.VK_1);
        key('@', KeyEvent.VK_SHIFT, KeyEvent.VK_2);
        key('#', KeyEvent.VK_SHIFT, KeyEvent.VK_3);
        key('$', KeyEvent.VK_SHIFT, KeyEvent.VK_4);
        key('%', KeyEvent.VK_SHIFT, KeyEvent.VK_5);
        key('^', KeyEvent.VK_SHIFT, KeyEvent.VK_6);
        key('&', KeyEvent.VK_SHIFT, KeyEvent.VK_7);
        key('*', KeyEvent.VK_SHIFT, KeyEvent.VK_8);
        key('(', KeyEvent.VK_SHIFT, KeyEvent.VK_9);
        key(')', KeyEvent.VK_SHIFT, KeyEvent.VK_0);
        key('`', KeyEvent.VK_BACK_QUOTE);
        key('~', KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_QUOTE);
        key('-', KeyEvent.VK_MINUS);
        key('_', KeyEvent.VK_SHIFT, KeyEvent.VK_MINUS);
        key('=', KeyEvent.VK_EQUALS);
        key('+', KeyEvent.VK_SHIFT, KeyEvent.VK_EQUALS);
        key('[', KeyEvent.VK_OPEN_BRACKET);
        key('{', KeyEvent.VK_SHIFT, KeyEvent.VK_OPEN_BRACKET);
        key(']', KeyEvent.VK_CLOSE_BRACKET);
        key('}', KeyEvent.VK_SHIFT, KeyEvent.VK_CLOSE_BRACKET);
        key('\\', KeyEvent.VK_BACK_SLASH);
        key('|', KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_SLASH);
        key(';', KeyEvent.VK_SEMICOLON);
        key(':', KeyEvent.VK_SHIFT, KeyEvent.VK_SEMICOLON);
        key('\'', KeyEvent.VK_QUOTE);
        key('"', KeyEvent.VK_SHIFT, KeyEvent.VK_QUOTE);
        key(',', KeyEvent.VK_COMMA);
        key('<', KeyEvent.VK_SHIFT, KeyEvent.VK_COMMA);
        key('.', KeyEvent.VK_PERIOD);
        key('|', KeyEvent.VK_SHIFT, KeyEvent.VK_PERIOD);
        key('/', KeyEvent.VK_SLASH);
        key('?', KeyEvent.VK_SHIFT, KeyEvent.VK_SLASH);
        //======================================================================
        key('\b', KeyEvent.VK_BACK_SPACE);
        key('\t', KeyEvent.VK_TAB);
        key('\r', KeyEvent.VK_ENTER);
        key('\n', KeyEvent.VK_ENTER);
        key(' ', KeyEvent.VK_SPACE);
        key(Keys.CONTROL, KeyEvent.VK_CONTROL);
        key(Keys.ALT, KeyEvent.VK_ALT);
        key(Keys.META, KeyEvent.VK_META);
        key(Keys.SHIFT, KeyEvent.VK_SHIFT);
        key(Keys.TAB, KeyEvent.VK_TAB);
        key(Keys.ENTER, KeyEvent.VK_ENTER);
        key(Keys.SPACE, KeyEvent.VK_SPACE);
        key(Keys.BACK_SPACE, KeyEvent.VK_BACK_SPACE);
        //======================================================================
        key(Keys.UP, KeyEvent.VK_UP);
        key(Keys.RIGHT, KeyEvent.VK_RIGHT);
        key(Keys.DOWN, KeyEvent.VK_DOWN);
        key(Keys.LEFT, KeyEvent.VK_LEFT);
        key(Keys.PAGE_UP, KeyEvent.VK_PAGE_UP);
        key(Keys.PAGE_DOWN, KeyEvent.VK_PAGE_DOWN);
        key(Keys.END, KeyEvent.VK_END);
        key(Keys.HOME, KeyEvent.VK_HOME);
        key(Keys.DELETE, KeyEvent.VK_DELETE);
        key(Keys.ESCAPE, KeyEvent.VK_ESCAPE);
        key(Keys.F1, KeyEvent.VK_F1);
        key(Keys.F2, KeyEvent.VK_F2);
        key(Keys.F3, KeyEvent.VK_F3);
        key(Keys.F4, KeyEvent.VK_F4);
        key(Keys.F5, KeyEvent.VK_F5);
        key(Keys.F6, KeyEvent.VK_F6);
        key(Keys.F7, KeyEvent.VK_F7);
        key(Keys.F8, KeyEvent.VK_F8);
        key(Keys.F9, KeyEvent.VK_F9);
        key(Keys.F10, KeyEvent.VK_F10);
        key(Keys.F11, KeyEvent.VK_F11);
        key(Keys.F12, KeyEvent.VK_F12);
        key(Keys.INSERT, KeyEvent.VK_INSERT);
        key(Keys.PAUSE, KeyEvent.VK_PAUSE);
        key(Keys.NUMPAD1, KeyEvent.VK_NUMPAD1);
        key(Keys.NUMPAD2, KeyEvent.VK_NUMPAD2);
        key(Keys.NUMPAD3, KeyEvent.VK_NUMPAD3);
        key(Keys.NUMPAD4, KeyEvent.VK_NUMPAD4);
        key(Keys.NUMPAD5, KeyEvent.VK_NUMPAD5);
        key(Keys.NUMPAD6, KeyEvent.VK_NUMPAD6);
        key(Keys.NUMPAD7, KeyEvent.VK_NUMPAD7);
        key(Keys.NUMPAD8, KeyEvent.VK_NUMPAD8);
        key(Keys.NUMPAD9, KeyEvent.VK_NUMPAD9);
        key(Keys.NUMPAD0, KeyEvent.VK_NUMPAD0);
        key(Keys.SEPARATOR, KeyEvent.VK_SEPARATOR);
        key(Keys.ADD, KeyEvent.VK_ADD);
        key(Keys.SUBTRACT, KeyEvent.VK_SUBTRACT);
        key(Keys.MULTIPLY, KeyEvent.VK_MULTIPLY);
        key(Keys.DIVIDE, KeyEvent.VK_DIVIDE);
        key(Keys.DECIMAL, KeyEvent.VK_DECIMAL);
        // TODO SCROLL_LOCK, NUM_LOCK, CAPS_LOCK, PRINTSCREEN, CONTEXT_MENU, WINDOWS
    }

}
