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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * element.retry(count, interval): the element is waited for under a count × interval
 * budget before every action and read, and the wait methods use the same budget.
 */
class RetryElementTest {

    private final DriverStub stub = new DriverStub();

    private static final Duration DEFAULT_TIMEOUT =
            Duration.ofMillis((long) DriverStub.RETRY_COUNT * DriverStub.RETRY_INTERVAL);

    private BaseElement late() {
        BaseElement element = new BaseElement(stub.driver, "#late", false);
        stub.calls.clear();
        stub.scripts.clear();
        return element;
    }

    private void assertWaitedThen(String action, Duration timeout) {
        assertEquals(timeout, stub.waitTimeout);
        assertTrue(stub.calls.contains("waitFor") && stub.calls.contains(action)
                        && stub.calls.indexOf("waitFor") < stub.calls.indexOf(action),
                "waitFor came first, then " + action + ": " + stub.calls);
    }

    @Test
    void testClickWaitsForTheElementFirst() {
        Element element = late().retry(10, 500);
        element.click();
        assertWaitedThen("click", Duration.ofMillis(5000));
        assertTrue(stub.scripts.contains(Locators.clickJs("#late")));
    }

    @Test
    void testCountOnlyUsesTheDefaultInterval() {
        late().retry(4).text();
        assertWaitedThen("text", Duration.ofMillis(4L * DriverStub.RETRY_INTERVAL));
    }

    @Test
    void testNoArgsUsesTheDriverDefaults() {
        late().retry().text();
        assertWaitedThen("text", DEFAULT_TIMEOUT);
    }

    @Test
    void testReadsWaitToo() {
        late().retry(2, 100).attribute("id");
        assertWaitedThen("attribute", Duration.ofMillis(200));
    }

    @Test
    void testWaitForUsesTheRetryBudget() {
        Element element = late().retry(10, 500);
        assertSame(element, element.waitFor());
        assertTrue(stub.calls.contains("waitFor"));
        assertEquals(Duration.ofMillis(5000), stub.waitTimeout);
    }

    @Test
    void testWaitsUseTheRetryBudget() {
        Element element = late().retry(10, 500);
        assertSame(element, element.waitForText("x"));
        assertTrue(stub.calls.contains("waitForText"));
        assertEquals(Duration.ofMillis(5000), stub.waitTimeout);
        assertSame(element, element.waitForEnabled());
        assertTrue(stub.calls.contains("waitForEnabled"));
        assertEquals(Duration.ofMillis(5000), stub.waitTimeout);
        assertSame(element, element.waitUntil("_.ok"));
        assertTrue(stub.calls.contains("waitUntil"));
        assertEquals(Duration.ofMillis(5000), stub.waitTimeout);
    }

    @Test
    void testPresentIsABooleanNotACallable() {
        // the v1 idiom: optional('#x').present
        assertEquals(Boolean.TRUE, BaseElement.existing(stub.driver, "#username").jsGet("present"));
        assertEquals(Boolean.FALSE, new MissingElement(stub.driver, "#late").jsGet("present"));
    }

    @Test
    void testExistsIsStillACallable() {
        Object exists = BaseElement.existing(stub.driver, "#username").jsGet("exists");
        assertInstanceOf(JavaCallable.class, exists);
        assertEquals(true, ((JavaCallable) exists).call(null));
        assertEquals(false, ((JavaCallable) late().jsGet("exists")).call(null));
    }

    @Test
    void testRetriedPresenceIsALiveCheck() {
        BaseElement element = (BaseElement) late().retry(10, 500);
        stub.scriptResult = true; // the element has appeared since the lookup
        assertTrue(element.isPresent());
        assertTrue(element.exists());
        assertEquals(Boolean.TRUE, element.jsGet("present"));
        assertEquals(true, ((JavaCallable) element.jsGet("exists")).call(null));
        assertFalse(stub.calls.contains("waitFor"), "presence does not wait: " + stub.calls);
        // a plain element still answers from the lookup snapshot
        assertEquals(Boolean.FALSE, new BaseElement(stub.driver, "#late", false).jsGet("present"));
    }

    @Test
    void testJsReadsWaitToo() {
        stub.scriptResult = "late text";
        BaseElement element = (BaseElement) late().retry(10, 500);
        Object text = element.jsGet("text");
        assertInstanceOf(JavaCallable.class, text);
        assertEquals("late text", ((JavaCallable) text).call(null));
        assertWaitedThen("text", Duration.ofMillis(5000));
        assertTrue(stub.scripts.contains(Locators.textJs("#late")));
    }

    @Test
    void testJsReadsPassTheirArguments() {
        BaseElement element = (BaseElement) late().retry(10, 500);
        JavaCallable value = (JavaCallable) element.jsGet("value");
        value.call(null, "foo");
        assertWaitedThen("value", Duration.ofMillis(5000));
        assertTrue(stub.scripts.contains(Locators.inputJs("#late", "foo")));
        value.call(null);
        assertEquals(Locators.valueJs("#late"), stub.scripts.getLast());
        ((JavaCallable) element.jsGet("position")).call(null, true);
        assertWaitedThen("position", Duration.ofMillis(5000));
        assertNotEquals(Locators.getPositionJs("#late"), stub.scripts.getLast());
        assertTrue(stub.scripts.getLast().contains("getBoundingClientRect"));
    }

    @Test
    void testRetryFromJs() {
        Object retry = late().jsGet("retry");
        assertInstanceOf(JavaCallable.class, retry);
        Element element = (Element) ((JavaCallable) retry).call(null, 10, 500);
        element.click();
        assertWaitedThen("click", Duration.ofMillis(5000));
    }

}
