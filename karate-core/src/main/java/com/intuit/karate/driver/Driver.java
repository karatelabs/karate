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

/**
 *
 * @author pthomas3
 */
public interface Driver {

    // constructor takes a Map<String, Object> always
    void activate();

    void refresh();

    void reload();

    void back();

    void forward();

    void maximize();

    void minimize();

    void fullscreen();

    void focus(String id);

    void input(String name, String value);

    void click(String expression);
    
    void click(String expression, boolean waitForDialog);
    
    void select(String expression, String text);
    
    void select(String expression, int index);

    void submit(String expression);

    void close();

    void quit();

    String html(String id);

    String text(String id);

    String value(String id);
    
    String attribute(String id, String name);
    
    String property(String id, String name);
    
    String css(String id, String name);
    
    String name(String id);
    
    Map<String, Object> rect(String id);
    
    boolean enabled(String id);

    void waitUntil(String expression);
    
    Object eval(String expression);

    Map<String, Object> cookie(String name);

    void deleteCookie(String name);
    
    void clearCookies();
    
    void dialog(boolean accept);
    
    void dialog(boolean accept, String text);
    
    byte[] screenshot();
    
    byte[] screenshot(String id);   
    
    void highlight(String id);

    // javabean naming convention is intentional ===============================
    //
    void setLocation(String expression);    

    void setDimensions(Map<String, Object> map);

    Map<String, Object> getDimensions();

    String getLocation();

    String getTitle();

    void setCookie(Map<String, Object> cookie);

    List<Map> getCookies();
    
    String getDialog();

}
