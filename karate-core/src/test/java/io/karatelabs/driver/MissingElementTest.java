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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * optional() on a locator that matched nothing: actions are silent no-ops instead of
 * "element not found", reads answer null / false / empty, and the waits still delegate.
 */
class MissingElementTest {

    private final DriverStub stub = new DriverStub();

    private BaseElement missing() {
        stub.scriptResult = false;  // exists() → false
        BaseElement element = BaseElement.optional(stub.driver, "#missing");
        stub.calls.clear();
        stub.scripts.clear();
        return element;
    }

    @Test
    void testMissingElementIsNotPresent() {
        BaseElement element = missing();
        assertInstanceOf(MissingElement.class, element);
        assertFalse(element.isPresent());
        assertFalse(element.exists());
    }

    @Test
    void testActionsAreNoOps() {
        BaseElement element = missing();
        assertSame(element, element.click());
        assertSame(element, element.input("x"));
        assertSame(element, element.clear());
        assertSame(element, element.focus());
        assertTrue(stub.calls.isEmpty(), "the driver was never touched: " + stub.calls);
    }

    @Test
    void testReadsAreEmpty() {
        BaseElement element = missing();
        assertNull(element.text());
        assertNull(element.value());
        assertNull(element.html());
        assertNull(element.attribute("id"));
        assertNull(element.position());
        assertFalse(element.enabled());
        assertFalse(element.matches("input"));
        assertTrue(element.locateAll("li").isEmpty());
        assertInstanceOf(MissingElement.class, element.locate("li"));
        assertTrue(stub.calls.isEmpty(), "the driver was never touched: " + stub.calls);
    }

    @Test
    void testWaitForDelegatesToDriver() {
        BaseElement element = missing();
        Element appeared = BaseElement.existing(stub.driver, "#missing");
        stub.waitForResult = appeared;
        // the element may yet appear — the wait answers with what the driver found
        assertSame(appeared, element.waitFor());
        assertTrue(stub.calls.contains("waitFor"));
    }

    @Test
    void testWaitsDelegateToDriver() {
        BaseElement element = missing();
        Element appeared = BaseElement.existing(stub.driver, "#missing");
        stub.waitForResult = appeared;
        assertSame(appeared, element.waitForText("x"));
        assertSame(appeared, element.waitForEnabled());
        assertSame(appeared, element.waitUntil("_.ok"));
        assertTrue(stub.calls.contains("waitForText"));
        assertTrue(stub.calls.contains("waitForEnabled"));
        assertTrue(stub.calls.contains("waitUntil"));
    }

    @Test
    void testRemainingActionsAndReadsAreNoOps() {
        BaseElement element = missing();
        assertSame(element, element.submit());
        assertSame(element, element.inputFile("a"));
        assertSame(element, element.select("x"));
        assertSame(element, element.select(1));
        assertSame(element, element.scroll());
        assertSame(element, element.highlight());
        assertNull(element.script("_.x"));
        assertNull(element.property("p"));
        assertNull(element.innerHtml());
        assertNull(element.position(true));
        assertTrue(stub.calls.isEmpty(), "the driver was never touched: " + stub.calls);
    }

    @Test
    void testClosestStaysMissing() {
        BaseElement element = missing();
        assertInstanceOf(MissingElement.class, element.closest("form"));
        assertTrue(stub.calls.isEmpty(), "the driver was never touched: " + stub.calls);
    }

    @Test
    void testLocateOffAClosestKeepsThePureJsLocator() {
        stub.scriptResult = false;
        Element element = BaseElement.optional(stub.driver, "#later").closest("section").locate("button");
        assertInstanceOf(MissingElement.class, element);
        // a "(...) button" string-concat would not be valid JS
        assertEquals(Locators.scopedSelectorJs(Locators.closestJs("#later", "section"), "button"),
                element.getLocator());
    }

    @Test
    void testRetryStaysMissing() {
        // v1: optional(x).retry().click() is a no-op, it never waits for what is not there
        BaseElement element = missing();
        assertSame(element, element.retry());
        assertSame(element, element.retry(3));
        assertSame(element, element.retry(3, 100));
        element.retry().click();
        assertTrue(stub.calls.isEmpty(), "the driver was never touched: " + stub.calls);
    }

    @Test
    void testClickFromJsIsANoOp() {
        // the Gherkin path: optional('#missing').click()
        BaseElement element = missing();
        Object click = element.jsGet("click");
        assertInstanceOf(JavaCallable.class, click);
        assertSame(element, ((JavaCallable) click).call(null));
        assertTrue(stub.calls.isEmpty(), "the driver was never touched: " + stub.calls);
    }

    @Test
    void testPresentElementActsNormally() {
        stub.scriptResult = true;  // exists() → true
        BaseElement element = BaseElement.optional(stub.driver, "#username");
        assertFalse(element instanceof MissingElement);
        assertTrue(element.isPresent());
        stub.calls.clear();
        element.click();
        assertTrue(stub.calls.contains("click"));
    }

}
