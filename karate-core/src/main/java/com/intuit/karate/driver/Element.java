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

/**
 *
 * @author pthomas3
 */
public class Element {

    public final Driver driver;
    public final String locator;

    private String id;

    public Element(Driver driver, String locator) {
        this.driver = driver;
        this.locator = locator;
    }

    public Element focus() {
        return driver.focus(locator);
    }

    public Element clear() {
        return driver.clear(locator);
    }
    
    public Element input(String text) {
        return driver.input(locator, text);
    }
    
    public Element select(String text) {
        return driver.select(locator, text);
    }
    
    public Element select(int index) {
        return driver.select(locator, index);
    }    

    public Element waitFor() {
        return driver.waitFor(locator); // will throw exception if not found
    }

    public Element waitUntil(String expression) {
        return driver.waitUntil(locator, expression); // will throw exception if not found
    }

    public byte[] screenshot() {
        return driver.screenshot(locator);
    }

    public Object script(String expression) {
        return driver.script(locator, expression);
    }

    //java bean naming conventions =============================================        
    //    
    public String getHtml() {
        return driver.html(locator);
    }

    public void setHtml(String html) {
        driver.script(locator, "_.outerHTML = '" + html + "'");
    }

    public String getText() {
        return driver.text(locator);
    }

    public void setText(String text) {
        driver.script(locator, "_.innerHTML = '" + text + "'");
    }

    public String getValue() {
        return driver.value(locator);
    }

    public void setValue(String value) {
        driver.value(locator, value);
    }

}
