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

import com.intuit.karate.FileUtils;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Robot {

    private static final Logger logger = LoggerFactory.getLogger(Robot.class);

    public final java.awt.Robot robot;
    public final Toolkit toolkit;
    public final Dimension dimension;
    public final Map<String, Object> options;
    public final boolean highlight;
    public final int highlightDuration;
    public final int retryCount;
    public final int retryInterval;

    private <T> T get(String key, T defaultValue) {
        T temp = (T) options.get(key);
        return temp == null ? defaultValue : temp;
    }

    public Robot() {
        this(Collections.EMPTY_MAP);
    }

    public Robot(Map<String, Object> options) {
        try {
            this.options = options;
            highlight = get("highlight", false);
            highlightDuration = get("highlightDuration", 1000);
            retryCount = get("retryCount", 3);
            retryInterval = get("retryInterval", 2000);
            toolkit = Toolkit.getDefaultToolkit();
            dimension = toolkit.getScreenSize();
            robot = new java.awt.Robot();
            robot.setAutoDelay(40);
            robot.setAutoWaitForIdle(true);
            String app = (String) options.get("app");
            if (app != null) {
                switchTo(app);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public <T> T retry(Supplier<T> action, Predicate<T> condition, String logDescription) {
        long startTime = System.currentTimeMillis();
        int count = 0, max = retryCount;
        T result;
        boolean success;
        do {
            if (count > 0) {
                logger.debug("{} - retry #{}", logDescription, count);
                delay(retryInterval);
            }
            result = action.get();
            success = condition.test(result);
        } while (!success && count++ < max);
        if (!success) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.warn("failed after {} retries and {} milliseconds", (count - 1), elapsedTime);
        }
        return result;
    }    

    public void delay(int millis) {
        robot.delay(millis);
    }

    public void move(int x, int y) {
        robot.mouseMove(x, y);
    }

    public void click() {
        click(InputEvent.BUTTON1_MASK);
    }

    public void click(int buttonMask) {
        robot.mousePress(buttonMask);
        robot.mouseRelease(buttonMask);
    }

    public void input(char s) {
        input(Character.toString(s));
    }

    public void input(String mod, char s) {
        input(mod, Character.toString(s));
    }

    public void input(char mod, String s) {
        input(Character.toString(mod), s);
    }

    public void input(char mod, char s) {
        input(Character.toString(mod), Character.toString(s));
    }

    public void input(String mod, String s) { // TODO refactor
        for (char c : mod.toCharArray()) {
            int[] codes = RobotUtils.KEY_CODES.get(c);
            if (codes == null) {
                logger.warn("cannot resolve char: {}", c);
                robot.keyPress(c);
            } else {
                robot.keyPress(codes[0]);
            }
        }
        input(s);
        for (char c : mod.toCharArray()) {
            int[] codes = RobotUtils.KEY_CODES.get(c);
            if (codes == null) {
                logger.warn("cannot resolve char: {}", c);
                robot.keyRelease(c);
            } else {
                robot.keyRelease(codes[0]);
            }
        }
    }

    public void input(String s) {
        for (char c : s.toCharArray()) {
            int[] codes = RobotUtils.KEY_CODES.get(c);
            if (codes == null) {
                logger.warn("cannot resolve char: {}", c);
                robot.keyPress(c);
                robot.keyRelease(c);
            } else if (codes.length > 1) {
                robot.keyPress(codes[0]);
                robot.keyPress(codes[1]);
                robot.keyRelease(codes[1]);
                robot.keyRelease(codes[0]);
            } else {
                robot.keyPress(codes[0]);
                robot.keyRelease(codes[0]);
            }
        }
    }

    public BufferedImage capture() {
        int width = dimension.width;
        int height = dimension.height;
        Image image = robot.createScreenCapture(new Rectangle(0, 0, width, height));
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = bi.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        return bi;
    }

    public File captureAndSave(String path) {
        BufferedImage image = capture();
        File file = new File(path);
        RobotUtils.save(image, file);
        return file;
    }

    public Region click(String path) {
        return find(new File(path)).with(this).click();
    }

    public Region find(String path) {
        return find(new File(path)).with(this);
    }

    public Region find(File file) {
        AtomicBoolean resize = new AtomicBoolean();
        Region region = retry(() -> RobotUtils.find(capture(), file, resize.getAndSet(true)), r -> r != null, "find by image");
        if (highlight) {
            region.highlight(highlightDuration);
        }
        return region;
    }

    public boolean switchTo(String title) {
        if (title.startsWith("^")) {
            return switchTo(t -> t.contains(title.substring(1)));
        }
        FileUtils.OsType type = FileUtils.getOsType();
        switch (type) {
            case LINUX:
                return RobotUtils.switchToLinuxOs(title);
            case MACOSX:
                return RobotUtils.switchToMacOs(title);
            case WINDOWS:
                return RobotUtils.switchToWinOs(title);
            default:
                logger.warn("unsupported os: {}", type);
                return false;
        }
    }

    public boolean switchTo(Predicate<String> condition) {
        FileUtils.OsType type = FileUtils.getOsType();
        switch (type) {
            case LINUX:
                return RobotUtils.switchToLinuxOs(condition);
            case MACOSX:
                return RobotUtils.switchToMacOs(condition);
            case WINDOWS:
                return RobotUtils.switchToWinOs(condition);
            default:
                logger.warn("unsupported os: {}", type);
                return false;
        }
    }

}
