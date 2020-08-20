/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.driver;

import com.intuit.karate.Config;
import com.intuit.karate.core.AutoDef;
import com.intuit.karate.core.Plugin;
import com.intuit.karate.core.ScenarioContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public interface Driver extends Plugin {

    @AutoDef
    void activate();

    @AutoDef
    void refresh();

    @AutoDef
    void reload();

    @AutoDef
    void back();

    @AutoDef
    void forward();

    @AutoDef
    void maximize();

    @AutoDef
    void minimize();

    @AutoDef
    void fullscreen();

    @AutoDef
    void close();

    @AutoDef
    void quit();

    @AutoDef
    void switchPage(String titleOrUrl);

    @AutoDef
    void switchPage(int index);

    @AutoDef
    void switchFrame(int index);

    @AutoDef
    void switchFrame(String locator);

    String getUrl(); // getter

    void setUrl(String url); // setter    

    Map<String, Object> getDimensions(); // getter

    void setDimensions(Map<String, Object> map); // setter

    String getTitle(); // getter

    List<String> getPages(); // getter

    String getDialog(); // getter

    @AutoDef
    byte[] screenshot(boolean embed);

    @AutoDef
    default byte[] screenshot() {
        return screenshot(true);
    }

    @AutoDef
    Map<String, Object> cookie(String name);

    @AutoDef
    void cookie(Map<String, Object> cookie);

    @AutoDef
    void deleteCookie(String name);

    @AutoDef
    void clearCookies();

    List<Map> getCookies(); // getter    

    @AutoDef
    void dialog(boolean accept);

    @AutoDef
    void dialog(boolean accept, String input);

    @AutoDef
    Object script(String expression);

    @AutoDef
    boolean waitUntil(String expression);

    @AutoDef
    Driver submit();

    @AutoDef
    default Driver retry() {
        return retry(null, null);
    }

    @AutoDef
    default Driver retry(int count) {
        return retry(count, null);
    }

    @AutoDef
    default Driver retry(Integer count, Integer interval) {
        getOptions().enableRetry(count, interval);
        return this;
    }

    @AutoDef
    default Driver delay(int millis) {
        getOptions().sleep(millis);
        return this;
    }

    @AutoDef
    Driver timeout(Integer millis);

    @AutoDef
    Driver timeout();

    // element actions =========================================================
    //
    @AutoDef
    Element focus(String locator);

    @AutoDef
    Element clear(String locator);

    @AutoDef
    Element click(String locator);

    @AutoDef
    Element input(String locator, String value);

    @AutoDef
    default Element input(String locator, String[] values) {
        return input(locator, values, 0);
    }

    @AutoDef
    default Element input(String locator, String chars, int delay) {
        String[] array = new String[chars.length()];
        for (int i = 0; i < array.length; i++) {
            array[i] = Character.toString(chars.charAt(i));
        }
        return input(locator, array, delay);
    }

    @AutoDef
    default Element input(String locator, String[] values, int delay) {
        Element element = DriverElement.locatorUnknown(this, locator);
        for (String value : values) {
            if (delay > 0) {
                delay(delay);
            }
            element = input(locator, value);
        }
        return element;
    }

    @AutoDef
    Element select(String locator, String text);

    @AutoDef
    Element select(String locator, int index);

    @AutoDef
    Element value(String locator, String value);

    @AutoDef
    default Element waitFor(String locator) {
        return getOptions().waitForAny(this, locator);
    }

    @AutoDef
    default String waitForUrl(String expected) {
        return getOptions().waitForUrl(this, expected);
    }

    @AutoDef
    default Element waitForText(String locator, String expected) {
        return waitUntil(locator, "_.textContent.includes('" + expected + "')");
    }

    @AutoDef
    default Element waitForEnabled(String locator) {
        return waitUntil(locator, "!_.disabled");
    }

    @AutoDef
    default List<Element> waitForResultCount(String locator, int count) {
        return (List) waitUntil(() -> {
            List<Element> list = locateAll(locator);
            return list.size() == count ? list : null;
        });
    }

    @AutoDef
    default List waitForResultCount(String locator, int count, String expression) {
        return (List) waitUntil(() -> {
            List list = scriptAll(locator, expression);
            return list.size() == count ? list : null;
        });
    }

    @AutoDef
    default Element waitForAny(String locator1, String locator2) {
        return getOptions().waitForAny(this, new String[]{locator1, locator2});
    }

    @AutoDef
    default Element waitForAny(String[] locators) {
        return getOptions().waitForAny(this, locators);
    }

    @AutoDef
    default Element waitUntil(String locator, String expression) {
        return getOptions().waitUntil(this, locator, expression);
    }

    @AutoDef
    default Object waitUntil(Supplier<Object> condition) {
        return getOptions().retry(() -> condition.get(), o -> o != null, "waitUntil (function)", true);
    }

    @AutoDef
    default Element locate(String locator) {
        Element e = DriverElement.locatorUnknown(this, locator);
        if (e.isPresent()) {
            return e;
        }
        throw new RuntimeException("cannot find locator: " + locator);
    }

    @AutoDef
    default List<Element> locateAll(String locator) {
        return getOptions().findAll(this, locator);
    }

    @AutoDef
    default List<Element> locateAll(String locator, Predicate predicate) {
        List before = locateAll(locator);
        List after = new ArrayList(before.size());
        for (Object o : before) {
            if (predicate.test(o)) {
                after.add(o);
            }
        }
        return after;
    }

    @AutoDef
    default Element scroll(String locator) {
        script(locator, DriverOptions.SCROLL_JS_FUNCTION);
        return DriverElement.locatorExists(this, locator);
    }

    @AutoDef
    default Element highlight(String locator) {
        return highlight(locator, Config.DEFAULT_HIGHLIGHT_DURATION);
    }

    default Element highlight(String locator, int millis) {
        script(getOptions().highlight(locator, millis));
        delay(millis);
        return DriverElement.locatorExists(this, locator);
    }

    @AutoDef
    default void highlightAll(String locator) {
        highlightAll(locator, Config.DEFAULT_HIGHLIGHT_DURATION);
    }

    default void highlightAll(String locator, int millis) {
        script(getOptions().highlightAll(locator, millis));
        delay(millis);
    }

    // friendly locators =======================================================
    //
    @AutoDef
    default Finder rightOf(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.RIGHT);
    }

    @AutoDef
    default Finder leftOf(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.LEFT);
    }

    @AutoDef
    default Finder above(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.ABOVE);
    }

    @AutoDef
    default Finder below(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.BELOW);
    }

    @AutoDef
    default Finder near(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.NEAR);
    }

    // mouse and keys ==========================================================
    //
    @AutoDef
    default Mouse mouse() {
        return new DriverMouse(this);
    }

    @AutoDef
    default Mouse mouse(String locator) {
        return new DriverMouse(this).move(locator);
    }

    @AutoDef
    default Mouse mouse(int x, int y) {
        return new DriverMouse(this).move(x, y);
    }

    @AutoDef
    default Keys keys() {
        return new Keys(this);
    }

    @AutoDef
    void actions(List<Map<String, Object>> actions);

    // element state ===========================================================
    //
    @AutoDef
    String html(String locator);

    @AutoDef
    String text(String locator);

    @AutoDef
    String value(String locator);

    @AutoDef
    String attribute(String locator, String name);

    @AutoDef
    String property(String locator, String name);

    @AutoDef
    boolean enabled(String locator);

    @AutoDef
    default boolean exists(String locator) {
        return getOptions().optional(this, locator).isPresent();
    }

    @AutoDef
    default Element optional(String locator) {
        return getOptions().optional(this, locator);
    }

    @AutoDef
    Map<String, Object> position(String locator);

    @AutoDef
    byte[] screenshot(String locator, boolean embed);

    @AutoDef
    default byte[] screenshot(String locator) {
        return screenshot(locator, true);
    }

    @AutoDef
    default Object script(String locator, String expression) {
        String js = getOptions().scriptSelector(locator, expression);
        return script(js);
    }

    @AutoDef
    default List scriptAll(String locator, String expression) {
        String js = getOptions().scriptAllSelector(locator, expression);
        return (List) script(js);
    }

    @AutoDef
    default List scriptAll(String locator, String expression, Predicate predicate) {
        List before = scriptAll(locator, expression);
        List after = new ArrayList(before.size());
        for (Object o : before) {
            if (predicate.test(o)) {
                after.add(o);
            }
        }
        return after;
    }

    // for internal use ========================================================
    //        
    boolean isTerminated();

    DriverOptions getOptions();

    Object elementId(String locator);

    List elementIds(String locator);

    static final List<String> METHOD_NAMES = Plugin.methodNames(Driver.class);

    @Override
    default List<String> methodNames() {
        return METHOD_NAMES;
    }

    @Override
    default void setContext(ScenarioContext context) {
        getOptions().setContext(context);
    }

    @Override
    default Map<String, Object> afterScenario() {
        return Collections.EMPTY_MAP; // TODO
    }

}
