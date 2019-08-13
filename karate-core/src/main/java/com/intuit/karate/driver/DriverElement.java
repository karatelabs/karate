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
public class DriverElement implements Element {

    private final Driver driver;
    private final String locator;
    
    private Boolean exists;

    private DriverElement(Driver driver, String locator, Boolean exists) {
        this.driver = driver;
        this.locator = locator;
        this.exists = exists;
    }
    
    public static DriverElement locatorExists(Driver driver, String locator) {
        return new DriverElement(driver, locator, true);
    }    
    
    public static DriverElement locatorUnknown(Driver driver, String locator) {
        return new DriverElement(driver, locator, null); // exists flag set to null
    }     

    @Override
    public String getLocator() {
        return locator;
    }

    @Override
    public boolean isExists() {
        if (exists == null) {
            exists = driver.exists(locator).isExists();
        }
        return exists;
    }

    public void setExists(Boolean exists) {
        this.exists = exists;
    }        

    @Override
    public Element focus() {
        return driver.focus(locator);
    }

    @Override
    public Element clear() {
        return driver.clear(locator);
    }

    @Override
    public Element click() {
        return driver.click(locator);
    }

    @Override
    public Mouse mouse() {
        return driver.mouse(locator);
    }

    @Override
    public Element input(String text) {
        return driver.input(locator, text);
    }

    @Override
    public Element select(String text) {
        return driver.select(locator, text);
    }

    @Override
    public Element select(int index) {
        return driver.select(locator, index);
    }

    @Override
    public Element switchFrame() {
        driver.switchFrame(locator);
        return this;
    }        

    @Override
    public Element delay(int millis) {
        driver.delay(millis);
        return this;
    }        

    @Override
    public Element waitFor() {
        driver.waitFor(locator); // will throw exception if not found
        return this;
    }

    @Override
    public Element waitUntil(String expression) {
        return driver.waitUntil(locator, expression); // will throw exception if not found
    }

    @Override
    public Object script(String expression) {
        return driver.script(locator, expression);
    }

    //java bean naming conventions =============================================        
    //        
    @Override
    public String getHtml() {
        return driver.html(locator);
    }

    @Override
    public void setHtml(String html) {
        driver.script(locator, "_.outerHTML = '" + html + "'");
    }

    @Override
    public String getText() {
        return driver.text(locator);
    }

    @Override
    public void setText(String text) {
        driver.script(locator, "_.innerHTML = '" + text + "'");
    }

    @Override
    public String getValue() {
        return driver.value(locator);
    }

    @Override
    public void setValue(String value) {
        driver.value(locator, value);
    }

}
