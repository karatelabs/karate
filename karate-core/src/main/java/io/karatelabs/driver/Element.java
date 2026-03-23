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
import io.karatelabs.js.SimpleObject;

import java.util.List;
import java.util.Map;

/**
 * Represents a DOM element found by a locator.
 * Provides element-scoped operations and fluent API for chaining.
 * Implements SimpleObject for JavaScript interoperability.
 */
public class Element implements SimpleObject {

    private final Driver driver;
    private final String locator;
    private final boolean exists;

    public Element(Driver driver, String locator, boolean exists) {
        this.driver = driver;
        this.locator = locator;
        this.exists = exists;
    }

    // ========== Factory Methods ==========

    public static Element of(Driver driver, String locator) {
        boolean exists = driver.exists(locator);
        return new Element(driver, locator, exists);
    }

    public static Element optional(Driver driver, String locator) {
        boolean exists = driver.exists(locator);
        return new Element(driver, locator, exists);
    }

    // ========== State ==========

    /**
     * Check if the element exists in the DOM.
     */
    public boolean exists() {
        return exists;
    }

    /**
     * Check if the element is present (alias for exists).
     */
    public boolean isPresent() {
        return exists;
    }

    /**
     * Get the locator used to find this element.
     */
    public String getLocator() {
        return locator;
    }

    // ========== Text Content ==========

    /**
     * Get the text content of the element.
     */
    public String text() {
        assertExists();
        return driver.text(locator);
    }

    /**
     * Get the outer HTML of the element.
     */
    public String html() {
        assertExists();
        return driver.html(locator);
    }

    /**
     * Get the inner HTML of the element.
     */
    public String innerHtml() {
        assertExists();
        return (String) driver.script(Locators.innerHtmlJs(locator));
    }

    /**
     * Get the value of an input element.
     */
    public String value() {
        assertExists();
        return driver.value(locator);
    }

    // ========== Attributes and Properties ==========

    /**
     * Get an attribute value.
     */
    public String attribute(String name) {
        assertExists();
        return driver.attribute(locator, name);
    }

    /**
     * Get a property value.
     */
    public Object property(String name) {
        assertExists();
        return driver.property(locator, name);
    }

    /**
     * Check if the element is enabled.
     */
    public boolean enabled() {
        assertExists();
        return driver.enabled(locator);
    }

    // ========== Position ==========

    /**
     * Get the position of the element.
     */
    public Map<String, Object> position() {
        assertExists();
        return driver.position(locator);
    }

    /**
     * Get the position of the element.
     * @param relative if true, position is relative to viewport
     */
    public Map<String, Object> position(boolean relative) {
        assertExists();
        return driver.position(locator, relative);
    }

    // ========== Actions ==========

    /**
     * Click the element.
     * @return this element for chaining
     */
    public Element click() {
        assertExists();
        driver.click(locator);
        return this;
    }

    /**
     * Focus the element.
     * @return this element for chaining
     */
    public Element focus() {
        assertExists();
        driver.focus(locator);
        return this;
    }

    /**
     * Clear the element's value.
     * @return this element for chaining
     */
    public Element clear() {
        assertExists();
        driver.clear(locator);
        return this;
    }

    /**
     * Input text into the element.
     * @return this element for chaining
     */
    public Element input(String value) {
        assertExists();
        driver.input(locator, value);
        return this;
    }

    /**
     * Set the value of an input element.
     * @return this element for chaining
     */
    public Element value(String value) {
        assertExists();
        driver.value(locator, value);
        return this;
    }

    /**
     * Select an option from a dropdown by text.
     * @return this element for chaining
     */
    public Element select(String text) {
        assertExists();
        driver.select(locator, text);
        return this;
    }

    /**
     * Select an option from a dropdown by index.
     * @return this element for chaining
     */
    public Element select(int index) {
        assertExists();
        driver.select(locator, index);
        return this;
    }

    /**
     * Scroll the element into view.
     * @return this element for chaining
     */
    public Element scroll() {
        assertExists();
        driver.scroll(locator);
        return this;
    }

    /**
     * Highlight the element.
     * @return this element for chaining
     */
    public Element highlight() {
        assertExists();
        driver.highlight(locator);
        return this;
    }

    // ========== Child Elements ==========

    /**
     * Find a child element matching the locator.
     */
    public Element locate(String childLocator) {
        assertExists();
        // Use relative locator within this element's context
        String fullLocator = locator + " " + childLocator;
        return Element.of(driver, fullLocator);
    }

    /**
     * Find all child elements matching the locator.
     */
    public List<Element> locateAll(String childLocator) {
        assertExists();
        String fullLocator = locator + " " + childLocator;
        return driver.locateAll(fullLocator);
    }

    // ========== Script Execution ==========

    /**
     * Execute a JavaScript expression on this element.
     * The element is available as '_' in the expression.
     */
    public Object script(String expression) {
        assertExists();
        return driver.script(locator, expression);
    }

    // ========== Wait Methods ==========

    /**
     * Wait for this element to exist.
     * @return this element
     */
    public Element waitFor() {
        driver.waitFor(locator);
        return this;
    }

    /**
     * Wait for this element to contain specific text.
     * @return this element
     */
    public Element waitForText(String expected) {
        driver.waitForText(locator, expected);
        return this;
    }

    /**
     * Wait for this element to be enabled.
     * @return this element
     */
    public Element waitForEnabled() {
        driver.waitForEnabled(locator);
        return this;
    }

    /**
     * Wait until the expression evaluates to truthy.
     * The element is available as '_' in the expression.
     * @return this element
     */
    public Element waitUntil(String expression) {
        driver.waitUntil(locator, expression);
        return this;
    }

    // ========== Retry ==========

    /**
     * Get a retry-enabled version of this element.
     */
    public Element retry() {
        return new RetryElement(driver, locator, exists);
    }

    /**
     * Get a retry-enabled version with custom count.
     */
    public Element retry(int count) {
        return new RetryElement(driver, locator, exists, count, null);
    }

    /**
     * Get a retry-enabled version with custom count and interval.
     */
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

    private void assertExists() {
        if (!exists) {
            throw new DriverException("element not found: " + locator);
        }
    }

    @Override
    public String toString() {
        return "Element[" + locator + ", exists=" + exists + "]";
    }

    // ========== Retry Element ==========

    private static class RetryElement extends Element {
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
            // For now, just return the base driver
            // Full retry implementation will be added later
            return super.driver;
        }
    }

}
