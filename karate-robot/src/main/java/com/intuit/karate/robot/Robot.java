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
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.shell.Command;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
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
    public final boolean autoClose;
    public final int autoDelay;
    public final int highlightDuration;
    public final Region screen;

    protected ScriptBridge bridge;

    // mutables
    private String basePath;
    protected Command command;
    protected ScenarioContext context;
    protected Logger logger;
    protected Element currentWindow;

    // retry
    private boolean retryEnabled;
    private Integer retryIntervalOverride = null;
    private Integer retryCountOverride = null;

    public void disableRetry() {
        retryEnabled = false;
        retryCountOverride = null;
        retryIntervalOverride = null;
    }

    public void enableRetry(Integer count, Integer interval) {
        retryEnabled = true;
        retryCountOverride = count; // can be null
        retryIntervalOverride = interval; // can be null
    }

    private int getRetryCount() {
        return retryCountOverride == null ? context.getConfig().getRetryCount() : retryCountOverride;
    }

    private int getRetryInterval() {
        return retryIntervalOverride == null ? context.getConfig().getRetryInterval() : retryIntervalOverride;
    }

    @AutoDef
    public Robot retry() {
        return retry(null, null);
    }

    @AutoDef
    public Robot retry(int count) {
        return retry(count, null);
    }

    @AutoDef
    public Robot retry(Integer count, Integer interval) {
        enableRetry(count, interval);
        return this;
    }

    @Override
    public void setContext(ScenarioContext context) {
        this.context = context;      
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
            autoDelay = get("autoDelay", 0);
            toolkit = Toolkit.getDefaultToolkit();
            dimension = toolkit.getScreenSize();
            screen = new Region(this, 0, 0, dimension.width, dimension.height);
            robot = new java.awt.Robot();
            robot.setAutoDelay(autoDelay);
            robot.setAutoWaitForIdle(true);
            //==================================================================
            autoClose = get("autoClose", true);
            boolean attach = get("attach", true);
            String window = get("window", null);
            if (window != null) {
                currentWindow = window(window, false); // don't retry
            }
            if (currentWindow != null && attach) {
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
                if (command != null) {
                    delay(500); // give process time to start
                    if (command.isFailed()) {
                        throw new KarateException("robot fork command failed: " + command.getFailureReason().getMessage());
                    }
                    if (window != null) {
                        retryCountOverride = get("retryCount", null);
                        retryIntervalOverride = get("retryInterval", null);
                        currentWindow = window(window); // will retry
                        logger.debug("attached to process window: {} - {}", window, command.getArgList());
                    }
                }
                if (currentWindow == null && window != null) {
                    throw new KarateException("failed to find window: " + window);
                }
            }
        } catch (Exception e) {
            throw new KarateException("robot init failed", e);
        }
    }

    public <T> T retry(Supplier<T> action, Predicate<T> condition, String logDescription, boolean failWithException) {
        long startTime = System.currentTimeMillis();
        int count = 0, max = getRetryCount();
        int interval = getRetryInterval();
        disableRetry(); // always reset
        T result;
        boolean success;
        do {
            if (count > 0) {
                logger.debug("{} - retry #{}", logDescription, count);
                delay(interval);
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
    public Robot doubleClick() {
        click();
        delay(40);
        click();
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

    @AutoDef
    public Robot input(String[] values) {
        for (String s : values) {
            input(s);
        }
        return this;
    }

    @AutoDef
    public Robot input(String value) {
        if (highlight) {
            locateFocus().highlight();
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
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

    public Robot clearFocused() {
        return input(Keys.CONTROL + "a" + Keys.DELETE);
    }

    protected int getHighlightDuration() {
        return highlight ? highlightDuration : -1;
    }

    @AutoDef
    public Element input(String locator, String value) {
        return locate(locator).input(value);
    }

    @AutoDef
    public byte[] screenshot() {
        return screenshot(screen);
    }

    public byte[] screenshot(int x, int y, int width, int height) {
        return screenshot(new Region(this, x, y, width, height));
    }

    public byte[] screenshot(Region region) {
        BufferedImage image = region.captureColor();
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
    public Element highlight(String locator) {
        return locate(Config.DEFAULT_HIGHLIGHT_DURATION, getSearchRoot(), locator);
    }

    @AutoDef
    public Element locate(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator);
    }

    @AutoDef
    public Element exists(String locator) {
        return exists(getSearchRoot(), locator);
    }

    @AutoDef
    public Element windowExists(String locator) {
        Element prevWindow = currentWindow;
        Element window = window(locator, false); // will update currentWindow        
        if (window == null) {
            currentWindow = prevWindow;
            return new MissingElement(this);
        }
        // note that currentWindow will point to the new window located
        return window;
    }

    protected Element exists(Element searchRoot, String locator) {
        Element found = locateImageOrElement(searchRoot, locator);
        if (found == null) {
            logger.warn("element does not exist: {}", locator);
            return new MissingElement(this);
        }
        if (highlight) {
            found.highlight();
        }
        return found;
    }

    protected Element locate(int duration, Element searchRoot, String locator) {
        Element found;
        if (retryEnabled) {
            found = waitForAny(searchRoot, locator); // will throw exception if not found
        } else {
            found = locateImageOrElement(searchRoot, locator);
            if (found == null) {
                throw new RuntimeException("cannot locate: " + locator + " (" + searchRoot.getDebugString() + ")");
            }
            if (duration > 0) {
                found.getRegion().highlight(duration);
            }
        }
        return found;
    }

    @AutoDef
    public Element move(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).move();
    }

    @AutoDef
    public Element click(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).click();
    }

    @AutoDef
    public Element press(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).press();
    }

    @AutoDef
    public Element release(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).release();
    }

    public Element locateImage(String path) {
        return locateImage(() -> screen.captureGreyScale(), readBytes(path));
    }

    public Element locateImage(Supplier<BufferedImage> source, String path) {
        return locateImage(source, readBytes(path));
    }

    public Element locateImage(Supplier<BufferedImage> source, byte[] bytes) {
        Region region = RobotUtils.find(this, source.get(), bytes, true);
        if (region == null) {
            return null;
        }
        return new ImageElement(region);
    }

    @AutoDef
    public Element window(String title) {
        return window(title, true);
    }

    private Element window(String title, boolean retry) {
        Predicate<String> condition = new StringMatcher(title);
        currentWindow = retry ? retry(() -> windowInternal(condition), w -> w != null, "find window", true) : windowInternal(condition);
        if (currentWindow != null && highlight) { // currentWindow can be null
            currentWindow.highlight();
        }
        return currentWindow;
    }

    @AutoDef
    public Element window(Predicate<String> condition) {
        currentWindow = retry(() -> windowInternal(condition), w -> w != null, "find window", true);
        if (currentWindow != null && highlight) { // currentWindow can be null
            currentWindow.highlight();
        }
        return currentWindow;
    }

    public Element locateElement(Element root, String locator) {
        return locateElementInternal(root, locator);
    }

    protected Element getSearchRoot() {
        return currentWindow == null ? getDesktop() : currentWindow;
    }
    
    @AutoDef
    public Object waitUntil(Supplier<Object> condition) {
        return retry(() -> condition.get(), o -> o != null, "waitUntil (function)", true);
    }    

    @AutoDef
    public Element waitFor(String locator) {
        return retryForAny(getSearchRoot(), locator);
    }

    @AutoDef
    public Element waitForAny(String locator1, String locator2) {
        return retryForAny(getSearchRoot(), locator1, locator2);
    }

    @AutoDef
    public Element waitForAny(String[] locators) {
        return retryForAny(getSearchRoot(), locators);
    }

    private Element retryForAny(Element searchRoot, String... locators) {
        return retry(() -> waitForAny(searchRoot, locators), r -> r != null, "find by locator(s): " + Arrays.asList(locators), true);
    }

    protected Element waitForAny(Element searchRoot, String... locators) {
        for (String locator : locators) {
            Element found = locateImageOrElement(searchRoot, locator);
            if (found != null) {
                if (highlight) {
                    found.getRegion().highlight();
                }
                return found;
            }
        }
        return null;
    }

    private Element locateImageOrElement(Element searchRoot, String locator) {
        if (locator.endsWith(".png")) {
            return locateImage(() -> searchRoot.getRegion().captureGreyScale(), locator);
        } else if (searchRoot.isImage()) {
            // TODO
            throw new RuntimeException("todo find non-image elements within region");
        } else {
            return locateElement(searchRoot, locator);
        }
    }

    @AutoDef
    public abstract Element getDesktop();

    @AutoDef
    public abstract Element locateFocus();

    //==========================================================================        
    //
    protected abstract Element windowInternal(String title);

    protected abstract Element windowInternal(Predicate<String> condition);

    protected abstract Element locateElementInternal(Element root, String locator);

}
