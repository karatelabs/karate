/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.driver;

import java.util.List;
import java.util.Map;

/**
 * The element {@link Driver#optional(String)} returns when nothing matched: actions are
 * no-ops, reads return null / false / empty, and the wait methods delegate to the driver
 * and return the element that eventually appears. {@code retry()} is inherited: like the
 * waits it is an explicit opt-in to the driver's auto-wait, so a target that never appears
 * throws after the retry budget.
 */
public class MissingElement extends BaseElement {

    public MissingElement(Driver driver, String locator) {
        super(driver, locator, false);
    }

    @Override
    public String text() {
        return null;
    }

    @Override
    public String html() {
        return null;
    }

    @Override
    public String innerHtml() {
        return null;
    }

    @Override
    public String value() {
        return null;
    }

    @Override
    public String attribute(String name) {
        return null;
    }

    @Override
    public Object property(String name) {
        return null;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Map<String, Object> position() {
        return null;
    }

    @Override
    public Map<String, Object> position(boolean relative) {
        return null;
    }

    @Override
    public Element click() {
        return this;
    }

    @Override
    public Element focus() {
        return this;
    }

    @Override
    public Element clear() {
        return this;
    }

    @Override
    public Element input(String value) {
        return this;
    }

    @Override
    public Element inputFile(String... files) {
        return this;
    }

    @Override
    public Element value(String value) {
        return this;
    }

    @Override
    public Element select(String text) {
        return this;
    }

    @Override
    public Element select(int index) {
        return this;
    }

    @Override
    public Element scroll() {
        return this;
    }

    @Override
    public Element highlight() {
        return this;
    }

    @Override
    public Element submit() {
        return this;
    }

    @Override
    public Element locate(String childLocator) {
        String child = isPureJsLocator(locator)
                ? Locators.scopedSelectorJs(locator, childLocator)
                : locator + " " + childLocator;
        return new MissingElement(driver, child);
    }

    @Override
    public List<Element> locateAll(String childLocator) {
        return List.of();
    }

    @Override
    public Element closest(String selector) {
        return new MissingElement(driver, Locators.closestJs(locator, selector));
    }

    @Override
    public boolean matches(String selector) {
        return false;
    }

    @Override
    public Object script(String expression) {
        return null;
    }

    @Override
    public Element waitFor() {
        return driver.waitFor(locator);
    }

    @Override
    public Element waitForText(String expected) {
        return driver.waitForText(locator, expected);
    }

    @Override
    public Element waitForEnabled() {
        return driver.waitForEnabled(locator);
    }

    @Override
    public Element waitUntil(String expression) {
        return driver.waitUntil(locator, expression);
    }

    @Override
    public String toString() {
        return "MissingElement[" + locator + "]";
    }

}
