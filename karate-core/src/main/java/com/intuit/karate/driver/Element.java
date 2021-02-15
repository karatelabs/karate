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
package com.intuit.karate.driver;

import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public interface Element {

    String getLocator(); // getter

    boolean isPresent(); // getter

    boolean isEnabled(); // getter
    
    Map<String, Object> getPosition(); // getter
    
    byte[] screenshot();

    Element highlight();

    Element focus();

    Element clear();

    Element click();

    Element submit();

    Mouse mouse();

    Element input(String value);

    Element input(String[] values);

    Element input(String[] values, int delay);

    Element select(String text);

    Element select(int index);

    Element switchFrame();

    Element delay(int millis);

    Element retry();

    Element retry(int count);

    Element retry(Integer count, Integer interval);

    Element waitFor();

    Element waitUntil(String expression);

    Element waitForText(String text);

    Object script(String expression);
    
    Element optional(String locator);
    
    boolean exists(String locator);
    
    Element locate(String locator);
    
    List<Element> locateAll(String locator);

    String getHtml(); // getter

    void setHtml(String html); // setter

    String getText(); // getter

    void setText(String text); // setter    

    String getValue(); // getter

    void setValue(String value); // setter
    
    String attribute(String name);
    
    String property(String name);
    
    Element getParent(); // getter
    
    Element getFirstChild(); // getter
            
    Element getLastChild(); // getter
    
    Element getPreviousSibling(); // getter
    
    Element getNextSibling(); // getter
    
    List<Element> getChildren();
    
    Finder rightOf();
    
    Finder leftOf();
    
    Finder above();
    
    Finder below();
    
    Finder near();

}
