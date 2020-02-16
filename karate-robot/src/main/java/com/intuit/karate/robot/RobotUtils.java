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

import com.intuit.karate.StringUtils;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.shell.Command;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.awt.Color;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.javacv.OpenCVFrameConverter;
import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Point2f;
import org.bytedeco.opencv.opencv_core.Point2fVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RobotUtils {

    private static final Logger logger = LoggerFactory.getLogger(RobotUtils.class);   

    public static Region find(File source, File target, boolean resize) {
        return find(read(source), read(target), resize);
    }
    
    public static Region find(BufferedImage source, byte[] bytes, boolean resize) {
        Mat srcMat = Java2DFrameUtils.toMat(source);
        return find(srcMat, read(bytes), resize);
    }    

    public static Region find(BufferedImage source, File target, boolean resize) {
        Mat srcMat = Java2DFrameUtils.toMat(source);
        return find(srcMat, read(target), resize);
    }

    public static Mat rescale(Mat mat, double scale) {
        Mat resized = new Mat();
        resize(mat, resized, new Size(), scale, scale, CV_INTER_AREA);
        return resized;
    }

    public static Region find(Mat source, Mat target, boolean resize) {
        Double prevMinVal = null;
        double prevRatio = -1;
        Point prevMinPt = null;
        double prevScore = -1;
        //=====================
        double step = 0.1;
        int count = resize ? 5 : 0;
        int targetScore = target.size().area() * 300; // magic number
        for (int i = -count; i <= count; i++) {            
            double scale = 1 + step * i;
            Mat resized = scale == 1 ? source : rescale(source, scale);
            Size temp = resized.size();
            logger.debug("scale: {} - {}:{} - target: {}", scale, temp.width(), temp.height(), targetScore);
            Mat result = new Mat();
            matchTemplate(resized, target, result, CV_TM_SQDIFF);
            DoublePointer minVal = new DoublePointer(1);
            DoublePointer maxVal = new DoublePointer(1);
            Point minPt = new Point();
            Point maxPt = new Point();
            minMaxLoc(result, minVal, maxVal, minPt, maxPt, null);
            double tempMinVal = minVal.get();
            double ratio = (double) 1 / scale;
            double score = tempMinVal / targetScore;
            String minValString = String.format("%.1f", tempMinVal);            
            if (prevMinVal == null || tempMinVal < prevMinVal) {
                prevMinVal = tempMinVal;
                prevRatio = ratio;
                prevMinPt = minPt;
                prevScore = score;
                logger.debug("found minVal: {}, score: {}, ratio: {}", minValString, score, ratio);
            } else {
                logger.debug("ignore minVal: {}, score: {}, ratio: {}", minValString, score, ratio);
            }
        }
        if (prevScore > 1.5) {
            logger.debug("match quality insufficient: {}", prevScore);
            return null;
        }
        int x = (int) Math.round(prevMinPt.x() * prevRatio);
        int y = (int) Math.round(prevMinPt.y() * prevRatio);
        int width = (int) Math.round(target.cols() * prevRatio);
        int height = (int) Math.round(target.rows() * prevRatio);
        return new Region(x, y, width, height);
    }

    public static Mat loadAndShowOrExit(File file, int flags) {
        Mat image = read(file, flags);
        show(image, file.getName());
        return image;
    }

    public static BufferedImage readImage(File file) {
        Mat mat = read(file, IMREAD_GRAYSCALE);
        return toBufferedImage(mat);
    }

    public static Mat read(File file) {
        return read(file, IMREAD_GRAYSCALE);
    }
    
    public static Mat read(byte[] bytes) {
        return read(bytes, IMREAD_GRAYSCALE);
    }    
    
    public static Mat read(byte[] bytes, int flags) {
        Mat image = imdecode(new Mat(bytes), flags);
        if (image.empty()) {
            throw new RuntimeException("image decode failed");
        }
        return image;
    }    

    public static Mat read(File file, int flags) {
        Mat image = imread(file.getAbsolutePath(), flags);
        if (image.empty()) {
            throw new RuntimeException("image not found: " + file.getAbsolutePath());
        }
        return image;
    }

    public static File save(BufferedImage image, File file) {
        try {
            ImageIO.write(image, "png", file);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void show(Mat mat, String title) {
        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
        CanvasFrame canvas = new CanvasFrame(title, 1);
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        canvas.showImage(converter.convert(mat));
    }

    public static void show(Image image, String title) {
        CanvasFrame canvas = new CanvasFrame(title, 1);
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        canvas.showImage(image);
    }

    public static void save(Mat image, File file) {
        imwrite(file.getAbsolutePath(), image);
    }

    public static Mat drawOnImage(Mat image, Point2fVector points) {
        Mat dest = image.clone();
        int radius = 5;
        Scalar red = new Scalar(0, 0, 255, 0);
        for (int i = 0; i < points.size(); i++) {
            Point2f p = points.get(i);
            circle(dest, new Point(Math.round(p.x()), Math.round(p.y())), radius, red);
        }
        return dest;
    }

    public static Mat drawOnImage(Mat image, Rect overlay, Scalar color) {
        Mat dest = image.clone();
        rectangle(dest, overlay, color);
        return dest;
    }

    public static BufferedImage toBufferedImage(Mat mat) {
        OpenCVFrameConverter.ToMat openCVConverter = new OpenCVFrameConverter.ToMat();
        Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
        return java2DConverter.convert(openCVConverter.convert(mat));
    }

    //==========================================================================
    //
    private static final String MAC_GET_PROCS
            = "    tell application \"System Events\""
            + "\n    set procs to (processes whose background only is false)"
            + "\n    set results to {}"
            + "\n    repeat with n from 1 to the length of procs"
            + "\n      set p to item n of procs"
            + "\n      set entry to { name of p as text,\"|\"}"
            + "\n      set end of results to entry"
            + "\n    end repeat"
            + "\n  end tell"
            + "\n  results";

    public static List<String> getAppsMacOs() {
        String res = Command.exec(true, null, "osascript", "-e", MAC_GET_PROCS);
        res = res + ", ";
        res = res.replace(", |, ", "\n");
        return StringUtils.split(res, '\n');
    }

    public static boolean switchToMacOs(Predicate<String> condition) {
        List<String> list = getAppsMacOs();
        for (String s : list) {
            if (condition.test(s)) {
                Command.exec(true, null, "osascript", "-e", "tell app \"" + s + "\" to activate");
                return true; // TODO use command return code
            }
        }
        return false;
    }

    public static boolean switchToMacOs(String title) {
        Command.exec(true, null, "osascript", "-e", "tell app \"" + title + "\" to activate");
        return true; // TODO use command return code
    }

    public static boolean switchToWinOs(Predicate<String> condition) {
        final AtomicBoolean found = new AtomicBoolean();
        User32.INSTANCE.EnumWindows((HWND hwnd, com.sun.jna.Pointer p) -> {
            char[] windowText = new char[512];
            User32.INSTANCE.GetWindowText(hwnd, windowText, 512);
            String windowName = Native.toString(windowText);
            logger.debug("scanning window: {}", windowName);
            if (condition.test(windowName)) {
                found.set(true);
                focusWinOs(hwnd);
                return false;
            }
            return true;
        }, null);
        return found.get();
    }

    private static void focusWinOs(HWND hwnd) {
        User32.INSTANCE.ShowWindow(hwnd, 9); // SW_RESTORE
        User32.INSTANCE.SetForegroundWindow(hwnd);
    }

    public static boolean switchToWinOs(String title) {
        HWND hwnd = User32.INSTANCE.FindWindow(null, title);
        if (hwnd == null) {
            return false;
        } else {
            focusWinOs(hwnd);
            return true;
        }
    }

    public static boolean switchToLinuxOs(String title) {
        Command.exec(true, null, "wmctrl", "-FR", title);
        return true; // TODO ?
    }

    public static boolean switchToLinuxOs(Predicate<String> condition) {
        String res = Command.exec(true, null, "wmctrl", "-l");
        List<String> lines = StringUtils.split(res, '\n');
        for (String line : lines) {
            List<String> cols = StringUtils.split(line, ' ');
            String id = cols.get(0);
            String host = cols.get(2);
            int pos = line.indexOf(host);
            String name = line.substring(pos + host.length() + 1);
            if (condition.test(name)) {
                Command.exec(true, null, "wmctrl", "-iR", id);
                return true;
            }
        }
        return false;
    }

    public static void highlight(int x, int y, int width, int height, int time) {
        JFrame f = new JFrame();
        f.setUndecorated(true);
        f.setBackground(new Color(0, 0, 0, 0));
        f.setAlwaysOnTop(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setType(JFrame.Type.UTILITY);
        f.setFocusableWindowState(false);
        f.setAutoRequestFocus(false);
        f.setLocation(x, y);
        f.setSize(width, height);
        f.getRootPane().setBorder(BorderFactory.createLineBorder(Color.RED, 3));
        f.setVisible(true);
        delay(time);
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
