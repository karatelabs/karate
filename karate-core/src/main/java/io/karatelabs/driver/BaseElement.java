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

import io.karatelabs.js.JavaCallable;

import java.util.List;
import java.util.Map;

/**
 * Locator-based Element implementation that delegates all operations to a Driver.
 * This is the default implementation used by all backends (CDP, W3C WebDriver, etc.).
 * Operations are performed by passing the locator to the Driver, which handles
 * backend-specific execution (JS eval for CDP, HTTP calls for W3C, etc.).
 *
 * @see Element
 * @see Driver
 */
public class BaseElement implements Element {

    protected final Driver driver;
    protected final String locator;
    protected final boolean exists;

    public BaseElement(Driver driver, String locator, boolean exists) {
        this.driver = driver;
        this.locator = locator;
        this.exists = exists;
    }

    // ========== Factory Methods ==========

    public static BaseElement of(Driver driver, String locator) {
        boolean exists = driver.exists(locator);
        return new BaseElement(driver, locator, exists);
    }

    public static BaseElement optional(Driver driver, String locator) {
        boolean exists = driver.exists(locator);
        return new BaseElement(driver, locator, exists);
    }

    /**
     * Construct an Element whose existence has already been verified by the caller.
     * Skips the exists() round-trip, which also avoids re-entering the driver at a
     * moment when it may be unable to evaluate JS — e.g. after click() opened a
     * blocking alert/confirm/prompt dialog (see issue #2801).
     */
    public static BaseElement existing(Driver driver, String locator) {
        return new BaseElement(driver, locator, true);
    }

    // ========== State ==========

    @Override
    public boolean exists() {
        return exists;
    }

    @Override
    public boolean isPresent() {
        return exists;
    }

    @Override
    public String getLocator() {
        return locator;
    }

    // ========== Text Content ==========

    @Override
    public String text() {
        assertExists();
        return driver.text(locator);
    }

    @Override
    public String html() {
        assertExists();
        return driver.html(locator);
    }

    @Override
    public String innerHtml() {
        assertExists();
        return (String) driver.script(Locators.innerHtmlJs(locator));
    }

    @Override
    public String value() {
        assertExists();
        return driver.value(locator);
    }

    // ========== Attributes and Properties ==========

    @Override
    public String attribute(String name) {
        assertExists();
        return driver.attribute(locator, name);
    }

    @Override
    public Object property(String name) {
        assertExists();
        return driver.property(locator, name);
    }

    @Override
    public boolean enabled() {
        assertExists();
        return driver.enabled(locator);
    }

    // ========== Position ==========

    @Override
    public Map<String, Object> position() {
        assertExists();
        return driver.position(locator);
    }

    @Override
    public Map<String, Object> position(boolean relative) {
        assertExists();
        return driver.position(locator, relative);
    }

    // ========== Actions ==========

    @Override
    public Element click() {
        assertExists();
        driver.click(locator);
        return this;
    }

    @Override
    public Element focus() {
        assertExists();
        driver.focus(locator);
        return this;
    }

    @Override
    public Element clear() {
        assertExists();
        driver.clear(locator);
        return this;
    }

    @Override
    public Element input(String value) {
        assertExists();
        driver.input(locator, value);
        return this;
    }

    @Override
    public Element value(String value) {
        assertExists();
        driver.value(locator, value);
        return this;
    }

    @Override
    public Element select(String text) {
        assertExists();
        driver.select(locator, text);
        return this;
    }

    @Override
    public Element select(int index) {
        assertExists();
        driver.select(locator, index);
        return this;
    }

    @Override
    public Element scroll() {
        assertExists();
        driver.scroll(locator);
        return this;
    }

    @Override
    public Element highlight() {
        assertExists();
        driver.highlight(locator);
        return this;
    }

    @Override
    public Element submit() {
        driver.submit();
        return this;
    }

    // ========== Child Elements ==========

    @Override
    public Element locate(String childLocator) {
        assertExists();
        String fullLocator = locator + " " + childLocator;
        return BaseElement.of(driver, fullLocator);
    }

    @Override
    public List<Element> locateAll(String childLocator) {
        assertExists();
        String fullLocator = locator + " " + childLocator;
        return driver.locateAll(fullLocator);
    }

    // ========== Script Execution ==========

    @Override
    public Object script(String expression) {
        assertExists();
        return driver.script(locator, expression);
    }

    // ========== Wait Methods ==========

    @Override
    public Element waitFor() {
        driver.waitFor(locator);
        return this;
    }

    @Override
    public Element waitForText(String expected) {
        driver.waitForText(locator, expected);
        return this;
    }

    @Override
    public Element waitForEnabled() {
        driver.waitForEnabled(locator);
        return this;
    }

    @Override
    public Element waitUntil(String expression) {
        driver.waitUntil(locator, expression);
        return this;
    }

    // ========== Retry ==========

    @Override
    public Element retry() {
        return new RetryElement(driver, locator, exists);
    }

    @Override
    public Element retry(int count) {
        return new RetryElement(driver, locator, exists, count, null);
    }

    @Override
    public Element retry(int count, int interval) {
        return new RetryElement(driver, locator, exists, count, interval);
    }

    // ========== SimpleObject Implementation (JS interop) ==========

    @Override
    public Object jsGet(String name) {
        return switch (name) {
            // Properties as getters (return callables that return the value)
            case "exists", "present" -> (JavaCallable) (ctx, args) -> exists;
            case "locator" -> (JavaCallable) (ctx, args) -> locator;
            case "text" -> (JavaCallable) (ctx, args) -> exists ? text() : null;
            case "html" -> (JavaCallable) (ctx, args) -> exists ? html() : null;
            case "value" -> (JavaCallable) (ctx, args) -> exists ? value() : null;
            case "enabled" -> (JavaCallable) (ctx, args) -> enabled();
            case "position" -> (JavaCallable) (ctx, args) -> exists ? position() : null;
            // Actions (return callables)
            case "click" -> (JavaCallable) (ctx, args) -> click();
            case "focus" -> (JavaCallable) (ctx, args) -> focus();
            case "clear" -> (JavaCallable) (ctx, args) -> clear();
            case "scroll" -> (JavaCallable) (ctx, args) -> scroll();
            case "highlight" -> (JavaCallable) (ctx, args) -> highlight();
            case "input" -> (JavaCallable) (ctx, args) -> input(args.length > 0 ? String.valueOf(args[0]) : "");
            case "attribute" -> (JavaCallable) (ctx, args) -> attribute(args.length > 0 ? String.valueOf(args[0]) : "");
            case "property" -> (JavaCallable) (ctx, args) -> property(args.length > 0 ? String.valueOf(args[0]) : "");
            case "script" -> (JavaCallable) (ctx, args) -> script(args.length > 0 ? String.valueOf(args[0]) : "");
            default -> null;
        };
    }

    // ========== Utilities ==========

    protected void assertExists() {
        if (!exists) {
            throw new DriverException("element not found: " + locator);
        }
    }

    @Override
    public String toString() {
        return "Element[" + locator + ", exists=" + exists + "]";
    }

    // ========== Retry Element ==========

    private static class RetryElement extends BaseElement {
        private final Integer retryCount;
        private final Integer retryInterval;

        RetryElement(Driver driver, String locator, boolean exists) {
            super(driver, locator, exists);
            this.retryCount = null;
            this.retryInterval = null;
        }

        RetryElement(Driver driver, String locator, boolean exists, Integer count, Integer interval) {
            super(driver, locator, exists);
            this.retryCount = count;
            this.retryInterval = interval;
        }

        @Override
        public Element click() {
            getDriver().click(getLocator());
            return this;
        }

        @Override
        public Element input(String value) {
            getDriver().input(getLocator(), value);
            return this;
        }

        private Driver getDriver() {
            return super.driver;
        }
    }

}
