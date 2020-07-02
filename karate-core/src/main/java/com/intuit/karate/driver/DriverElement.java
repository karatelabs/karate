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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TODO make this convert-able to JSON
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

    public static Element locatorExists(Driver driver, String locator) {
        return new DriverElement(driver, locator, true);
    }

    public static Element locatorUnknown(Driver driver, String locator) {
        return new DriverElement(driver, locator, null); // exists flag set to null
    }

    @Override
    public String getLocator() {
        return locator;
    }

    @Override
    public boolean isPresent() {
        if (exists == null) {
            exists = driver.optional(locator).isPresent();
        }
        return exists;
    }

    public void setExists(Boolean exists) {
        this.exists = exists;
    }

    @Override
    public Map<String, Object> getPosition() {
        return driver.position(locator);
    }

    @Override
    public byte[] screenshot() {
        return driver.screenshot();
    }

    @Override
    public boolean isEnabled() {
        return driver.enabled(locator);
    }

    @Override
    public Element highlight() {
        return driver.highlight(locator);
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
    public Element submit() {
        driver.submit();
        return this;
    }

    @Override
    public Mouse mouse() {
        return driver.mouse(locator);
    }

    @Override
    public Element input(String value) {
        return driver.input(locator, value);
    }

    @Override
    public Element input(String[] values) {
        return driver.input(locator, values);
    }

    @Override
    public Element input(String[] values, int delay) {
        return driver.input(locator, values, delay);
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
    public Element retry() {
        driver.retry();
        return this;
    }

    @Override
    public Element retry(int count) {
        driver.retry(count);
        return this;
    }

    @Override
    public Element retry(Integer count, Integer interval) {
        driver.retry(count, interval);
        return this;
    }

    @Override
    public Element waitFor() {
        driver.waitFor(locator); // will throw exception if not found
        return this;
    }

    @Override
    public Element waitForText(String text) {
        return driver.waitForText(locator, text);
    }

    @Override
    public Element waitUntil(String expression) {
        return driver.waitUntil(locator, expression); // will throw exception if not found
    }

    @Override
    public Object script(String expression) {
        return driver.script(locator, expression);
    }

    private String thisLocator() {
        String thisRef = (String) driver.script(locator, DriverOptions.KARATE_REF_GENERATOR);
        return DriverOptions.karateLocator(thisRef);
    }

    @Override
    public Element optional(String locator) {
        String childRefScript = driver.getOptions().scriptSelector(locator, DriverOptions.KARATE_REF_GENERATOR, thisLocator());
        try {
            String childRef = (String) driver.script(childRefScript);
            return DriverElement.locatorExists(driver, DriverOptions.karateLocator(childRef));
        } catch (Exception e) {
            return new MissingElement(driver, locator);
        }
    }

    @Override
    public boolean exists(String locator) {
        return optional(locator).isPresent();
    }

    @Override
    public Element locate(String locator) {
        Element e = optional(locator);
        if (e.isPresent()) {
            return e;
        }
        throw new RuntimeException("cannot find locator: " + locator);
    }

    @Override
    public List<Element> locateAll(String locator) {
        String childRefScript = driver.getOptions().scriptAllSelector(locator, DriverOptions.KARATE_REF_GENERATOR, thisLocator());
        List<String> childRefs = (List) driver.script(childRefScript);
        return refsToElements(childRefs);
    }
    
    private List<Element> refsToElements(List<String> refs) {
        List<Element> elements = new ArrayList(refs.size());
        for (String ref : refs) {
            String karateLocator = DriverOptions.karateLocator(ref);
            elements.add(DriverElement.locatorExists(driver, karateLocator));
        }
        return elements;        
    }

    @Override
    public String attribute(String name) {
        return driver.attribute(locator, name);
    }

    @Override
    public String property(String name) {
        return driver.property(locator, name);
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
    
    private Element relationLocator(String relation) {
        String js = "var gen = " + DriverOptions.KARATE_REF_GENERATOR + "; var e = " 
                + DriverOptions.selector(locator) + "; return gen(e." + relation + ")";
        String karateRef = (String) driver.script(DriverOptions.wrapInFunctionInvoke(js));
        return DriverElement.locatorExists(driver, DriverOptions.karateLocator(karateRef));        
    }

    @Override
    public Element getParent() {
        return relationLocator("parentElement");
    }

    @Override
    public List<Element> getChildren() {
        String js = "var gen = " + DriverOptions.KARATE_REF_GENERATOR + "; var es = " 
                + DriverOptions.selector(locator) + ".children; var res = []; var i;"
                + " for(i = 0; i < es.length; i++) res.push(gen(es[i])); return res";
        List<String> childRefs = (List) driver.script(DriverOptions.wrapInFunctionInvoke(js));
        return refsToElements(childRefs);
    }        

    @Override
    public Element getFirstChild() {
        return relationLocator("firstElementChild");
    }  

    @Override
    public Element getLastChild() {
        return relationLocator("lastElementChild");
    }     

    @Override
    public Element getPreviousSibling() {
        return relationLocator("previousElementSibling");
    }  

    @Override
    public Element getNextSibling() {
        return relationLocator("nextElementSibling");
    }        

    @Override
    public Finder rightOf() {
        return driver.rightOf(locator);
    }

    @Override
    public Finder leftOf() {
        return driver.leftOf(locator);
    }

    @Override
    public Finder above() {
        return driver.above(locator);
    }

    @Override
    public Finder below() {
        return driver.below(locator);
    }

    @Override
    public Finder near() {
        return driver.near(locator);
    }

}
