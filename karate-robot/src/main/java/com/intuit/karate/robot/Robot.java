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

import com.intuit.karate.Config;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.core.AutoDef;
import com.intuit.karate.core.Plugin;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScriptBridge;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.shell.Command;
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

/**
 *
 * @author pthomas3
 */
public abstract class Robot implements Plugin {

    public final java.awt.Robot robot;
    public final Toolkit toolkit;
    public final Dimension dimension;
    public final Map<String, Object> options;
    public final boolean highlight;
    public final int highlightDuration;
    public final int retryCount;
    public final int retryInterval;
    public final Region screen;

    protected ScriptBridge bridge;

    // mutables
    private String basePath;
    protected Command command;
    protected ScenarioContext context;
    protected Logger logger;

    @Override
    public void setContext(ScenarioContext context) {
        this.context = context;
    }

    @Override
    public Map<String, Object> afterScenario() {
        return Collections.EMPTY_MAP;
    }

    private <T> T get(String key, T defaultValue) {
        T temp = (T) options.get(key);
        return temp == null ? defaultValue : temp;
    }

    public Robot(ScenarioContext context) {
        this(context, Collections.EMPTY_MAP);
    }

    public Robot(ScenarioContext context, Map<String, Object> options) {
        this.context = context;
        this.logger = context.logger;
        bridge = context.bindings.bridge;
        try {
            this.options = options;
            basePath = get("basePath", null);
            highlight = get("highlight", false);
            highlightDuration = get("highlightDuration", Config.DEFAULT_HIGHLIGHT_DURATION);
            retryCount = get("retryCount", 3);
            retryInterval = get("retryInterval", Config.DEFAULT_RETRY_INTERVAL);
            toolkit = Toolkit.getDefaultToolkit();
            dimension = toolkit.getScreenSize();
            screen = new Region(this, 0, 0, dimension.width, dimension.height);
            robot = new java.awt.Robot();
            robot.setAutoDelay(40);
            robot.setAutoWaitForIdle(true);
            //==================================================================
            boolean attach = get("attach", true);
            boolean found = false;
            String window = get("window", null);
            if (window != null) {
                found = focusWindow(window);
            }
            if (found && attach) {
                logger.debug("window found, will re-use: {}", window);
            } else {
                ScriptValue sv = new ScriptValue(options.get("fork"));
                if (sv.isString()) {
                    command = bridge.fork(sv.getAsString());
                } else if (sv.isListLike()) {
                    command = bridge.fork(sv.getAsList());
                } else if (sv.isMapLike()) {
                    command = bridge.fork(sv.getAsMap());
                }
                if (command != null && window != null) {
                    delay(500); // give process time to start
                    retry(() -> focusWindow(window), r -> r, "finding window", true);
                    logger.debug("attached to process window: {} - {}", window, command.getArgList());
                }
                if (!found && window != null) {
                    throw new RuntimeException("failed to find window: " + window);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T retry(Supplier<T> action, Predicate<T> condition, String logDescription, boolean failWithException) {
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
            String message = logDescription + ": failed after " + (count - 1) + " retries and " + elapsedTime + " milliseconds";
            logger.warn(message);
            if (failWithException) {
                throw new RuntimeException(message);
            }
        }
        return result;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    private byte[] readBytes(String path) {
        if (basePath != null) {
            String slash = basePath.endsWith(":") ? "" : "/";
            path = basePath + slash + path;
        }
        ScriptValue sv = FileUtils.readFile(path, context);
        return sv.getAsByteArray();
    }

    @AutoDef
    public Robot delay(int millis) {
        robot.delay(millis);
        return this;
    }

    private static int mask(int num) {
        switch (num) {
            case 2:
                return InputEvent.BUTTON2_DOWN_MASK;
            case 3:
                return InputEvent.BUTTON3_DOWN_MASK;
            default:
                return InputEvent.BUTTON1_DOWN_MASK;
        }
    }

    @AutoDef
    public Robot click() {
        return click(1);
    }

    @AutoDef
    public Robot click(int num) {
        int mask = mask(num);
        robot.mousePress(mask);
        robot.mouseRelease(mask);
        return this;
    }

    @AutoDef
    public Robot press() {
        robot.mousePress(1);
        return this;
    }

    @AutoDef
    public Robot release() {
        robot.mouseRelease(1);
        return this;
    }

    public Robot input(char s) {
        return input(Character.toString(s));
    }

    @AutoDef
    public Robot input(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Keys.isModifier(c)) {
                sb.append(c);
                int[] codes = RobotUtils.KEY_CODES.get(c);
                if (codes == null) {
                    logger.warn("cannot resolve char: {}", c);
                    robot.keyPress(c);
                } else {
                    robot.keyPress(codes[0]);
                }
                continue;
            }
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
        for (char c : sb.toString().toCharArray()) {
            int[] codes = RobotUtils.KEY_CODES.get(c);
            if (codes == null) {
                logger.warn("cannot resolve char: {}", c);
                robot.keyRelease(c);
            } else {
                robot.keyRelease(codes[0]);
            }
        }
        return this;
    }

    @AutoDef
    public Element input(String locator, String value) {
        return locate(locator).input(value);
    }

    public BufferedImage capture() {
        return capture(screen);
    }

    public BufferedImage capture(int x, int y, int width, int height) {
        return capture(new Region(this, x, y, width, height));
    }

    public BufferedImage capture(Region region) {
        Image image = robot.createScreenCapture(new Rectangle(region.x, region.y, region.width, region.height));
        BufferedImage bi = new BufferedImage(region.width, region.height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = bi.createGraphics();
        g.drawImage(image, region.x, region.y, region.width, region.height, null);
        return bi;
    }

    public File captureAndSaveAs(String path) {
        BufferedImage image = capture();
        File file = new File(path);
        RobotUtils.save(image, file);
        return file;
    }

    @AutoDef
    public byte[] screenshot() {
        return screenshot(screen);
    }

    public byte[] screenshot(int x, int y, int width, int height) {
        return screenshot(new Region(this, x, y, width, height));
    }

    public byte[] screenshot(Region region) {
        BufferedImage image = capture(region);
        byte[] bytes = RobotUtils.toBytes(image);
        context.embed(bytes, "image/png");
        return bytes;
    }

    @AutoDef
    public Robot move(int x, int y) {
        robot.mouseMove(x, y);
        return this;
    }

    @AutoDef
    public Robot click(int x, int y) {
        return move(x, y).click();
    }

    @AutoDef
    public Element locate(String locator) {
        Element found;
        if (locator.endsWith(".png")) {
            found = locateImage(locator);
        } else {
            found = locateElement(locator);
        }
        if (highlight) {
            found.highlight();
        }
        return found;
    }

    public Element locate(Element root, String locator) {
        Element found;
        if (locator.endsWith(".png")) {
            found = locateImage(root.getRegion().capture(), locator);
        } else if (root.isImage()) {
            // TODO
            throw new RuntimeException("todo find non-image elements within region");
        } else {
            found = locateElementInternal(root, locator);
        }
        if (highlight) {
            found.highlight();
        }   
        return found;
    }    
    
    @AutoDef
    public Element move(String locator) {
        return locate(locator).move();
    }

    @AutoDef
    public Element click(String locator) {
        return locate(locator).click();
    }

    @AutoDef
    public Element press(String locator) {
        return locate(locator).press();
    }

    @AutoDef
    public Element release(String locator) {
        return locate(locator).release();
    }

    public Element locateImage(String path) {
        return locateImage(capture(), readBytes(path));
    }

    public Element locateImage(BufferedImage source, String path) {
        return locateImage(source, readBytes(path));
    }

    public Element locateImage(BufferedImage source, byte[] bytes) {
        AtomicBoolean resize = new AtomicBoolean();
        Region region = retry(() -> RobotUtils.find(this, source, bytes, resize.getAndSet(true)), r -> r != null, "find by image", true);
        return new ImageElement(region);
    }

    @AutoDef
    public boolean focusWindow(String title) {
        if (title.startsWith("^")) {
            return focusWindow(t -> t.contains(title.substring(1)));
        }
        return focusWindowInternal(title);
    }

    protected abstract boolean focusWindowInternal(String title);
    
    public abstract Element locateElementInternal(Element root, String locator);

    @AutoDef
    public abstract boolean focusWindow(Predicate<String> condition);

    @AutoDef
    public abstract Element locateElement(String locator);
    
    @AutoDef
    public abstract Element getRoot(); 

}
