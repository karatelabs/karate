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

import java.util.List;
import java.util.Map;
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

    void deleteCookie(String name);

    void clearCookies();

    List<Map> getCookies(); // getter

    void setCookie(Map<String, Object> cookie); // setter

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
        Element element = DriverElement.locatorUnknown(this, locator);
        for (String value : values) {
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

    default Element waitForAny(String locator1, String locator2) {
        return getOptions().waitForAny(this, new String[]{locator1, locator2});
    }

    default Element waitForAny(String[] locators) {
        return getOptions().waitForAny(this, locators);
    }

    default Element waitUntil(String locator, String expression) {
        return getOptions().waitUntil(this, locator, expression);
    }

    default Element waitUntilEnabled(String locator) {
        return waitUntil(locator, "!_.disabled");
    }

    default Object waitUntil(Supplier<Object> condition) {
        return getOptions().retry(() -> condition.get(), o -> o != null, "waitUntil (function)");
    }

    default List<Element> findAll(String locator) {
        return getOptions().findAll(this, locator);
    }

    default Element scroll(String locator) {
        script(locator, DriverOptions.SCROLL_JS_FUNCTION);
        return DriverElement.locatorExists(this, locator);
    }

    default Element highlight(String locator) {
        script(getOptions().highlighter(locator));
        return DriverElement.locatorExists(this, locator);
    }

    // friendly locators =======================================================
    //
    default Finder rightOf(String locator) {
        return new Finder(this, locator, Finder.Type.RIGHT);
    }

    default Finder leftOf(String locator) {
        return new Finder(this, locator, Finder.Type.LEFT);
    }

    default Finder above(String locator) {
        return new Finder(this, locator, Finder.Type.ABOVE);
    }

    default Finder below(String locator) {
        return new Finder(this, locator, Finder.Type.BELOW);
    }

    default Finder near(String locator) {
        return new Finder(this, locator, Finder.Type.NEAR);
    }

    // mouse and keys ==========================================================
    //
    default Mouse mouse() {
        return new Mouse(this);
    }

    default Mouse mouse(String locator) {
        return new Mouse(this).move(locator);
    }

    default Mouse mouse(int x, int y) {
        return new Mouse(this).move(x, y);
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
        String js = getOptions().selectorScript(locator, expression);
        return script(js);
    }

    default List scripts(String locator, String expression) {
        String js = getOptions().selectorAllScript(locator, expression);
        return (List) script(js);
    }

    // for internal use ========================================================
    //
    DriverOptions getOptions();

    Object elementId(String locator);

    List elementIds(String locator);

}
