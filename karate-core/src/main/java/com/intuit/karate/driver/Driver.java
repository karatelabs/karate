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

import com.intuit.karate.Logger;
import java.util.List;
import java.util.Map;

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

    default void waitForPage() {
        waitUntil("document.readyState == 'complete'");
    }

    void switchPage(String titleOrUrl);

    void switchFrame(int index);

    void switchFrame(String locator);

    String getLocation(); // getter

    void setLocation(String url); // setter    

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

    Element select(String locator, String text);

    Element select(String locator, int index);

    Element value(String locator, String value);

    default Element waitFor(String locator) {
        return waitForAny(locator);
    }

    default Element waitForAny(String... locators) {
        return getOptions().waitForAny(this, locators);
    }

    default Element waitUntil(String locator, String expression) {
        return getOptions().waitUntil(this, locator, expression);
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

    void setLogger(Logger logger);

    Object elementId(String locator);

    List elementIds(String locator);

}
