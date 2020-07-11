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
import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Plugin;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScriptBridge;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.shell.Command;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public abstract class RobotBase implements Robot, Plugin {

    private static final int CLICK_POST_DELAY = 500;

    public final java.awt.Robot robot;
    public final Toolkit toolkit;
    public final Dimension dimension;
    public final Map<String, Object> options;

    public final boolean autoClose;
    public final int autoDelay;
    public final Region screen;
    public final String tessData;
    public final String tessLang;

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

    // debug
    protected boolean debug;
    public boolean highlight;
    public int highlightDuration;

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setHighlight(boolean highlight) {
        this.highlight = highlight;
    }

    public void setHighlightDuration(int highlightDuration) {
        this.highlightDuration = highlightDuration;
    }

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

    private <T> T get(String key, T defaultValue) {
        T temp = (T) options.get(key);
        return temp == null ? defaultValue : temp;
    }

    public RobotBase(ScenarioContext context) {
        this(context, Collections.EMPTY_MAP);
    }

    public RobotBase(ScenarioContext context, Map<String, Object> options) {
        this.context = context;
        this.logger = context.logger;
        bridge = context.bindings.bridge;
        try {
            this.options = options;
            basePath = get("basePath", null);
            highlight = get("highlight", false);
            highlightDuration = get("highlightDuration", Config.DEFAULT_HIGHLIGHT_DURATION);
            autoDelay = get("autoDelay", 0);
            tessData = get("tessData", "tessdata");
            tessLang = get("tessLang", "eng");
            toolkit = Toolkit.getDefaultToolkit();
            dimension = toolkit.getScreenSize();
            screen = new Region(this, 0, 0, dimension.width, dimension.height);
            logger.debug("screen dimensions: {}", screen);
            robot = new java.awt.Robot();
            robot.setAutoDelay(autoDelay);
            robot.setAutoWaitForIdle(true);
            //==================================================================
            autoClose = get("autoClose", true);
            boolean attach = get("attach", true);
            String window = get("window", null);
            if (window != null) {
                currentWindow = window(window, false, false); // don't retry
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
                        logger.debug("attached to process window: {} - {}", currentWindow, command.getArgList());
                    }
                }
                if (currentWindow == null && window != null) {
                    throw new KarateException("failed to find window: " + window);
                }
            }
        } catch (Exception e) {
            String message = "robot init failed: " + e.getMessage();
            throw new KarateException(message, e);
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

    @Override
    public void setContext(ScenarioContext context) {
        this.context = context;
    }

    @Override
    public Robot retry() {
        return retry(null, null);
    }

    @Override
    public Robot retry(int count) {
        return retry(count, null);
    }

    @Override
    public Robot retry(Integer count, Integer interval) {
        enableRetry(count, interval);
        return this;
    }

    @Override
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

    @Override
    public Robot click() {
        return click(1);
    }
    
    @Override
    public Robot rightClick() {
        return click(3);
    }    

    @Override
    public Robot click(int num) {
        int mask = mask(num);
        robot.mousePress(mask);
        if (highlight) {
            getLocation().highlight(highlightDuration);
            int toDelay = CLICK_POST_DELAY - highlightDuration;
            if (toDelay > 0) {
                RobotUtils.delay(toDelay);
            }
        } else {
            RobotUtils.delay(CLICK_POST_DELAY);
        }
        robot.mouseRelease(mask);
        return this;
    }

    @Override
    public Robot doubleClick() {
        click();
        delay(40);
        click();
        return this;
    }

    @Override
    public Robot press() {
        robot.mousePress(1);
        return this;
    }

    @Override
    public Robot release() {
        robot.mouseRelease(1);
        return this;
    }

    @Override
    public Robot input(String[] values) {
        return input(values, 0);
    }

    @Override
    public Robot input(String chars, int delay) {
        String[] array = new String[chars.length()];
        for (int i = 0; i < array.length; i++) {
            array[i] = Character.toString(chars.charAt(i));
        }
        return input(array, delay);
    }

    @Override
    public Robot input(String[] values, int delay) {
        for (String s : values) {
            if (delay > 0) {
                delay(delay);
            }
            input(s);
        }
        return this;
    }

    @Override
    public Robot input(String value) {
        if (highlight) {
            getFocused().highlight(highlightDuration);
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

    @Override
    public Element input(String locator, String value) {
        return locate(locator).input(value);
    }

    @Override
    public byte[] screenshot() {
        return screenshot(screen);
    }

    @Override
    public byte[] screenshotActive() {
        return getActive().screenshot();
    }

    public byte[] screenshot(int x, int y, int width, int height) {
        return screenshot(new Region(this, x, y, width, height));
    }

    public byte[] screenshot(Region region) {
        BufferedImage image = region.capture();
        byte[] bytes = OpenCvUtils.toBytes(image);
        context.embed(bytes, "image/png");
        return bytes;
    }

    @Override
    public Robot move(int x, int y) {
        robot.mouseMove(x, y);
        return this;
    }

    @Override
    public Robot click(int x, int y) {
        return move(x, y).click();
    }

    @Override
    public Element highlight(String locator) {
        return locate(Config.DEFAULT_HIGHLIGHT_DURATION, getSearchRoot(), locator);
    }

    @Override
    public List<Element> highlightAll(String locator) {
        return locateAll(Config.DEFAULT_HIGHLIGHT_DURATION, getSearchRoot(), locator);
    }

    @Override
    public Element focus(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).focus();
    }

    @Override
    public Element locate(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator);
    }

    @Override
    public List<Element> locateAll(String locator) {
        return locateAll(getHighlightDuration(), getSearchRoot(), locator);
    }

    @Override
    public boolean exists(String locator) {
        return optional(locator).isPresent();
    }

    @Override
    public Element optional(String locator) {
        return optional(getSearchRoot(), locator);
    }

    @Override
    public boolean windowExists(String locator) {
        return windowOptional(locator).isPresent();
    }

    @Override
    public Element windowOptional(String locator) {
        return waitForWindowOptional(locator, false);
    }

    @Override
    public Element waitForWindowOptional(String locator) {
        return waitForWindowOptional(locator, true);
    }

    protected Element waitForWindowOptional(String locator, boolean retry) {
        Element prevWindow = currentWindow;
        Element window = window(locator, retry, false); // will update currentWindow     
        currentWindow = prevWindow; // so we reset it
        if (window == null) {
            return new MissingElement(this);
        }
        // note that currentWindow will NOT point to the new window located
        return window;
    }

    protected Element optional(Element searchRoot, String locator) {
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
            found = retryForAny(true, searchRoot, locator);
        } else {
            found = locateImageOrElement(searchRoot, locator);
            if (found == null) {
                String message = "cannot locate: '" + locator + "' (" + searchRoot.getDebugString() + ")";
                logger.error(message);
                throw new RuntimeException(message);
            }
            if (duration > 0) {
                found.getRegion().highlight(duration);
            }
        }
        return found;
    }

    protected List<Element> locateAll(int duration, Element searchRoot, String locator) {
        List<Element> found;
        if (locator.endsWith(".png")) {
            found = locateAllImages(searchRoot, locator);
        } else if (locator.startsWith("{")) {
            found = locateAllText(searchRoot, locator);
        } else {
            found = locateAllInternal(searchRoot, locator);
        }
        if (duration > 0) {
            RobotUtils.highlightAll(searchRoot.getRegion(), found, duration, false);
        }
        return found;
    }

    @Override
    public Element move(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).move();
    }

    @Override
    public Element click(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).click();
    }

    @Override
    public Element select(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).select();
    }

    @Override
    public Element press(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).press();
    }

    @Override
    public Element release(String locator) {
        return locate(getHighlightDuration(), getSearchRoot(), locator).release();
    }

    private StringUtils.Pair parseOcr(String raw) { // TODO make object
        int pos = raw.indexOf('}');
        String lang = raw.substring(1, pos);
        if (lang.length() < 2) {
            lang = lang + tessLang;
        }
        String text = raw.substring(pos + 1);
        return StringUtils.pair(lang, text);
    }

    public List<Element> locateAllText(Element searchRoot, String path) {
        StringUtils.Pair pair = parseOcr(path);
        String lang = pair.left;
        boolean negative = lang.charAt(0) == '-';
        if (negative) {
            lang = lang.substring(1);
        }
        String text = pair.right;
        return Tesseract.findAll(this, lang, searchRoot.getRegion(), text, negative);
    }

    public Element locateText(Element searchRoot, String path) {
        StringUtils.Pair pair = parseOcr(path);
        String lang = pair.left;
        boolean negative = lang.charAt(0) == '-';
        if (negative) {
            lang = lang.substring(1);
        }
        String text = pair.right;
        return Tesseract.find(this, lang, searchRoot.getRegion(), text, negative);
    }

    private static class PathAndStrict {

        final int strictness;
        final String path;

        public PathAndStrict(String path) {
            int pos = path.indexOf(':');
            if (pos > 0 && pos < 3) {
                strictness = Integer.valueOf(path.substring(0, pos));
                this.path = path.substring(pos + 1);
            } else {
                strictness = 10;
                this.path = path;
            }
        }

    }

    public List<Element> locateAllImages(Element searchRoot, String path) {
        PathAndStrict ps = new PathAndStrict(path);
        List<Region> found = OpenCvUtils.findAll(ps.strictness, this, searchRoot.getRegion(), readBytes(ps.path), true);
        List<Element> list = new ArrayList(found.size());
        for (Region region : found) {
            list.add(new ImageElement(region));
        }
        return list;
    }

    public Element locateImage(Region region, String path) {
        PathAndStrict ps = new PathAndStrict(path);
        return locateImage(region, ps.strictness, readBytes(ps.path));
    }

    public Element locateImage(Region searchRegion, int strictness, byte[] bytes) {
        Region region = OpenCvUtils.find(strictness, this, searchRegion, bytes, true);
        if (region == null) {
            return null;
        }
        return new ImageElement(region);
    }

    @Override
    public Element window(String title) {
        return window(title, true, true);
    }

    private Element window(String title, boolean retry, boolean failWithException) {
        return window(new StringMatcher(title), retry, failWithException);
    }

    @Override
    public Element window(Predicate<String> condition) {
        return window(condition, true, true);
    }

    private Element window(Predicate<String> condition, boolean retry, boolean failWithException) {
        try {
            currentWindow = retry ? retry(() -> windowInternal(condition), w -> w != null, "find window", failWithException) : windowInternal(condition);
        } catch (Exception e) {
            if (failWithException) {
                throw e;
            }
            logger.warn("failed to find window: {}", e.getMessage());
            currentWindow = null;
        }
        if (currentWindow != null && highlight) { // currentWindow can be null
            currentWindow.highlight(getHighlightDuration());
        }
        return currentWindow;
    }

    protected Element getSearchRoot() {
        if (currentWindow == null) {
            logger.warn("using desktop as search root, activate a window or parent element for better performance");
            return getRoot();
        }
        return currentWindow;
    }

    @Override
    public Object waitUntil(Supplier<Object> condition) {
        return waitUntil(condition, true);
    }

    @Override
    public Object waitUntilOptional(Supplier<Object> condition) {
        return waitUntil(condition, false);
    }

    protected Object waitUntil(Supplier<Object> condition, boolean failWithException) {
        return retry(() -> condition.get(), o -> o != null, "waitUntil (function)", failWithException);
    }

    @Override
    public Element waitFor(String locator) {
        return retryForAny(true, getSearchRoot(), locator);
    }

    @Override
    public Element waitForOptional(String locator) {
        return retryForAny(false, getSearchRoot(), locator);
    }

    @Override
    public Element waitForAny(String locator1, String locator2) {
        return retryForAny(true, getSearchRoot(), locator1, locator2);
    }

    @Override
    public Element waitForAny(String[] locators) {
        return retryForAny(true, getSearchRoot(), locators);
    }

    protected Element retryForAny(boolean failWithException, Element searchRoot, String... locators) {
        Element found = retry(() -> waitForAny(searchRoot, locators), r -> r != null, "find by locator(s): " + Arrays.asList(locators), failWithException);
        return found == null ? new MissingElement(this) : found;
    }

    private Element waitForAny(Element searchRoot, String... locators) {
        for (String locator : locators) {
            Element found = locateImageOrElement(searchRoot, locator);
            if (found != null) {
                if (highlight) {
                    found.getRegion().highlight(highlightDuration);
                }
                return found;
            }
        }
        return null;
    }

    private Element locateImageOrElement(Element searchRoot, String locator) {
        if (locator.endsWith(".png")) {
            return locateImage(searchRoot.getRegion(), locator);
        } else if (locator.startsWith("{")) {
            return locateText(searchRoot, locator);
        } else if (searchRoot.isImage()) {
            // TODO
            throw new RuntimeException("todo find non-image elements within region");
        } else {
            return locateInternal(searchRoot, locator);
        }
    }

    @Override
    public Element activate(String locator) {
        return locate(locator).activate();
    }

    @Override
    public Element getActive() {
        if (currentWindow == null) {
            throw new RuntimeException("no window has been selected or activated");
        }
        return currentWindow;
    }

    @Override
    public Robot setActive(Element e) {
        if (e.isPresent()) {
            currentWindow = e;
        }
        return this;
    }

    public void debugImage(String path) {
        byte[] bytes = readBytes(path);
        OpenCvUtils.show(bytes, path);
    }

    @Override
    public String getClipboard() {
        try {
            return (String) toolkit.getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            logger.warn("unable to return clipboard as string: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Location getLocation() {
        Point p = MouseInfo.getPointerInfo().getLocation();
        return new Location(this, p.x, p.y);
    }

    public Location location(int x, int y) {
        return new Location(this, x, y);
    }

    public Region region(Map<String, Integer> map) {
        return new Region(this, map.get("x"), map.get("y"), map.get("width"), map.get("height"));
    }

    @Override
    public abstract Element getRoot();

    @Override
    public abstract Element getFocused();

    //==========================================================================        
    //
    protected abstract Element windowInternal(String title);

    protected abstract Element windowInternal(Predicate<String> condition);

    protected abstract Element locateInternal(Element root, String locator);

    protected abstract List<Element> locateAllInternal(Element root, String locator);

}
