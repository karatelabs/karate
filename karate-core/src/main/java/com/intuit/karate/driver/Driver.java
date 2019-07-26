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
import com.intuit.karate.core.ScriptBridge;
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

    void focus(String locator);

    void clear(String locator);

    void input(String locator, String value);

    void click(String locator);

    void click(String locator, boolean waitForDialog);

    void select(String locator, String text);

    void select(String locator, int index);

    void submit(String locator);

    void close();

    void quit();

    String html(String locator);

    List<String> htmls(String locator);

    String text(String locator);

    List<String> texts(String locator);

    String value(String locator);

    List<String> values(String locator);

    void value(String locator, String value);

    String attribute(String locator, String name);

    String property(String locator, String name);

    String css(String locator, String name);

    String name(String locator);

    Map<String, Object> rect(String locator);

    boolean enabled(String locator);    

    Map<String, Object> cookie(String name);

    void deleteCookie(String name);

    void clearCookies();

    void dialog(boolean accept);

    void dialog(boolean accept, String input);

    void highlight(String locator);

    void switchPage(String titleOrUrl);

    void switchFrame(int index);

    void switchFrame(String locator);

    byte[] screenshot(boolean embed);

    byte[] screenshot(String locator, boolean embed);

    default byte[] screenshot() {
        return screenshot(true);
    }

    default byte[] screenshot(String locator) {
        return screenshot(locator, true);
    }
    
    Object eval(String expression);
    
    default Object eval(String locator, String expression) {
        String js = getOptions().elementSelectorFunction(locator, expression);
        return eval(js);
    }

    // waits ===================================================================
    //
    boolean waitUntil(String expression);

    default void waitForPage() {
        waitUntil("document.readyState == 'complete'");
    }

    default boolean wait(String locator) {
        String js = getOptions().elementSelector(locator);
        return waitUntil(js + " != null");
    }
    
    default boolean wait(String locator, String expression) {
        String js = getOptions().elementSelectorFunction(locator, expression);        
        return waitUntil(js);
    }    

    default void setAlwaysWait(boolean always) {
        getOptions().setAlwaysWait(always);
    }

    default boolean isAlwaysWait() {
        return getOptions().isAlwaysWait();
    }

    default void setRetryInterval(Integer interval) {
        getOptions().setRetryInterval(interval);
    }

    default boolean exists(String locator) {
        String js = getOptions().elementSelector(locator);
        String evalJs = js + " != null";
        Object o = eval(evalJs);
        if (o instanceof Boolean && (Boolean) o) {
            return true;
        }        
        // one more time only after one sleep
        getOptions().sleep();
        o = eval(evalJs);
        return o instanceof Boolean ? (Boolean) o : false;
    }    

    // javabean naming convention is intentional ===============================       
    //    
    DriverOptions getOptions(); // for internal use

    void setLogger(Logger logger); // for internal use

    Object get(String locator); // for internal use

    List getAll(String locator); // for internal use

    void setLocation(String url);

    void setDimensions(Map<String, Object> map);

    Map<String, Object> getDimensions();

    String getLocation();

    String getTitle();

    void setCookie(Map<String, Object> cookie);

    List<Map> getCookies();

    List<String> getWindowHandles();

    String getDialog();

}
