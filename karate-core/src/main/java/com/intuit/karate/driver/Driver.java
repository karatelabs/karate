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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public interface Driver {

    void activate();

    void refresh();

    void reload();

    void back();

    void forward();

    void maximize();

    void minimize();

    void fullscreen();

    void close();

    void quit();

    void switchPage(String titleOrUrl);

    void switchFrame(int index);

    void switchFrame(String locator);

    String getUrl(); // getter

    void setUrl(String url); // setter    

    Map<String, Object> getDimensions(); // getter

    void setDimensions(Map<String, Object> map); // setter

    String getTitle(); // getter

    List<String> getPages(); // getter

    String getDialog(); // getter

    byte[] screenshot(boolean embed);

    default byte[] screenshot() {
        return screenshot(true);
    }

    Map<String, Object> cookie(String name);

    void cookie(Map<String, Object> cookie);

    void deleteCookie(String name);

    void clearCookies();

    List<Map> getCookies(); // getter    

    void dialog(boolean accept);

    void dialog(boolean accept, String input);

    Object script(String expression);

    boolean waitUntil(String expression);

    Driver submit();

    default Driver retry() {
        return retry(null, null);
    }

    default Driver retry(int count) {
        return retry(count, null);
    }

    default Driver retry(Integer count, Integer interval) {
        getOptions().enableRetry(count, interval);
        return this;
    }

    default Driver delay(int millis) {
        getOptions().sleep(millis);
        return this;
    }

    // element actions =========================================================
    //
    Element focus(String locator);

    Element clear(String locator);

    Element click(String locator);

    Element input(String locator, String value);
    
    default Element input(String locator, String[] values) {
        return input(locator, values, 0);
    }

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

    Element select(String locator, String text);

    Element select(String locator, int index);

    Element value(String locator, String value);

    default Element waitFor(String locator) {
        return getOptions().waitForAny(this, locator);
    }

    default String waitForUrl(String expected) {
        return getOptions().waitForUrl(this, expected);
    }

    default Element waitForText(String locator, String expected) {
        return waitUntil(locator, "_.textContent.includes('" + expected + "')");
    }

    default Element waitForEnabled(String locator) {
        return waitUntil(locator, "!_.disabled");
    }

    default List<Element> waitForResultCount(String locator, int count) {
        return (List) waitUntil(() -> {
            List<Element> list = locateAll(locator);
            return list.size() == count ? list : null;
        });
    }

    default List waitForResultCount(String locator, int count, String expression) {
        return (List) waitUntil(() -> {
            List list = scriptAll(locator, expression);
            return list.size() == count ? list : null;
        });
    }

    default Element waitForAny(String locator1, String locator2) {
        return getOptions().waitForAny(this, new String[]{locator1, locator2});
    }

    default Element waitForAny(String[] locators) {
        return getOptions().waitForAny(this, locators);
    }

    default Element waitUntil(String locator, String expression) {
        return getOptions().waitUntil(this, locator, expression);
    }

    default Object waitUntil(Supplier<Object> condition) {
        return getOptions().retry(() -> condition.get(), o -> o != null, "waitUntil (function)");
    }
    
    default Element locate(String locator) {
        return DriverElement.locatorUnknown(this, locator);
    }

    default List<Element> locateAll(String locator) {
        return getOptions().findAll(this, locator);
    }

    default Element scroll(String locator) {
        script(locator, DriverOptions.SCROLL_JS_FUNCTION);
        return DriverElement.locatorExists(this, locator);
    }

    default Element highlight(String locator) {
        script(getOptions().highlight(locator));
        return DriverElement.locatorExists(this, locator);
    }

    default void highlightAll(String locator) {
        script(getOptions().highlightAll(locator));
    }

    // friendly locators =======================================================
    //
    default Finder rightOf(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.RIGHT);
    }

    default Finder leftOf(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.LEFT);
    }

    default Finder above(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.ABOVE);
    }

    default Finder below(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.BELOW);
    }

    default Finder near(String locator) {
        return new ElementFinder(this, locator, ElementFinder.Type.NEAR);
    }

    // mouse and keys ==========================================================
    //
    default Mouse mouse() {
        return new DriverMouse(this);
    }

    default Mouse mouse(String locator) {
        return new DriverMouse(this).move(locator);
    }

    default Mouse mouse(int x, int y) {
        return new DriverMouse(this).move(x, y);
    }

    default Keys keys() {
        return new Keys(this);
    }

    void actions(List<Map<String, Object>> actions);

    // element state ===========================================================
    //
    String html(String locator);

    String text(String locator);

    String value(String locator);

    String attribute(String locator, String name);

    String property(String locator, String name);

    boolean enabled(String locator);

    default Element exists(String locator) {
        return getOptions().exists(this, locator);
    }

    Map<String, Object> position(String locator);

    byte[] screenshot(String locator, boolean embed);

    default byte[] screenshot(String locator) {
        return screenshot(locator, true);
    }

    default Object script(String locator, String expression) {
        String js = getOptions().scriptSelector(locator, expression);
        return script(js);
    }

    default List scriptAll(String locator, String expression) {
        String js = getOptions().scriptAllSelector(locator, expression);
        return (List) script(js);
    }
    
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
    DriverOptions getOptions();

    Object elementId(String locator);

    List elementIds(String locator);

}
