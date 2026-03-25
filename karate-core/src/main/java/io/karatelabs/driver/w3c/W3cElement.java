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
package io.karatelabs.driver.w3c;

import io.karatelabs.driver.BaseElement;
import io.karatelabs.driver.Driver;
import io.karatelabs.driver.Element;

/**
 * W3C WebDriver Element implementation that holds a W3C element ID.
 * Operations that have native W3C endpoints use the element ID directly
 * for efficiency. Other operations delegate to the driver via locator
 * (inherited from BaseElement).
 *
 * @see BaseElement
 * @see W3cSession
 */
public class W3cElement extends BaseElement {

    private final String elementId;
    private final W3cSession session;

    public W3cElement(Driver driver, String locator, boolean exists,
                      String elementId, W3cSession session) {
        super(driver, locator, exists);
        this.elementId = elementId;
        this.session = session;
    }

    /**
     * Get the W3C element reference ID.
     */
    public String getElementId() {
        return elementId;
    }

    // ========== Override with native W3C calls for efficiency ==========

    @Override
    public Element click() {
        assertExists();
        session.clickElement(elementId);
        return this;
    }

    @Override
    public Element clear() {
        assertExists();
        session.clearElement(elementId);
        return this;
    }

    @Override
    public String text() {
        assertExists();
        return session.getElementText(elementId);
    }

    @Override
    public String attribute(String name) {
        assertExists();
        return session.getElementAttribute(elementId, name);
    }

    @Override
    public boolean enabled() {
        assertExists();
        return session.isElementEnabled(elementId);
    }

    @Override
    public String toString() {
        return "W3cElement[" + getLocator() + ", id=" + elementId + ", exists=" + exists() + "]";
    }

}
