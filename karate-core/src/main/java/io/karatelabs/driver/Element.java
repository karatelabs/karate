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

import io.karatelabs.js.SimpleObject;

import java.util.List;
import java.util.Map;

/**
 * Represents a DOM element found by a locator.
 * Provides element-scoped operations and fluent API for chaining.
 * Extends SimpleObject for JavaScript interoperability.
 *
 * <p>Implementations include:</p>
 * <ul>
 *   <li>{@link BaseElement} - Locator-based, delegates to Driver (works with any backend)</li>
 * </ul>
 *
 * @see BaseElement
 * @see Driver
 */
public interface Element extends SimpleObject {

    // ========== State ==========

    boolean exists();

    boolean isPresent();

    String getLocator();

    // ========== Text Content ==========

    String text();

    String html();

    String innerHtml();

    String value();

    // ========== Attributes and Properties ==========

    String attribute(String name);

    Object property(String name);

    boolean enabled();

    // ========== Position ==========

    Map<String, Object> position();

    Map<String, Object> position(boolean relative);

    // ========== Actions ==========

    Element click();

    Element focus();

    Element clear();

    Element input(String value);

    Element value(String value);

    Element select(String text);

    Element select(int index);

    Element scroll();

    Element highlight();

    // ========== Child Elements ==========

    Element locate(String childLocator);

    List<Element> locateAll(String childLocator);

    // ========== Script Execution ==========

    Object script(String expression);

    // ========== Wait Methods ==========

    Element waitFor();

    Element waitForText(String expected);

    Element waitForEnabled();

    Element waitUntil(String expression);

    // ========== Retry ==========

    Element retry();

    Element retry(int count);

    Element retry(int count, int interval);

}
