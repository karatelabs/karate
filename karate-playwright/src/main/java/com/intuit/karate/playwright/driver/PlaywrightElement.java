/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
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
package com.intuit.karate.playwright.driver;

import com.intuit.karate.driver.*;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.ClearOptions;
import com.microsoft.playwright.Locator.ClickOptions;
import com.microsoft.playwright.Locator.FilterOptions;
import com.microsoft.playwright.Locator.FillOptions;
import com.microsoft.playwright.Locator.FocusOptions;
import com.microsoft.playwright.Locator.PressOptions;
import com.microsoft.playwright.Locator.ScrollIntoViewIfNeededOptions;
import com.microsoft.playwright.Locator.WaitForOptions;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlaywrightElement implements Element {

    private final PlaywrightDriver driver;
    private final PlaywrightToken token;
    private Boolean present;

    protected PlaywrightElement(PlaywrightDriver driver, PlaywrightToken token) {
        this.driver = driver;
        this.token = token;
        present = null;
    }

    // TO be used by locate and optional, which knows whether the element exists or not.
    // In all other cases, the other constructor should be used.
    protected PlaywrightElement(PlaywrightDriver driver, PlaywrightToken token, boolean present) {
        this(driver, token);
        this.present = present;
    }

    @Override
    public String getLocator() {
        return token.getPlaywrightToken();
    }

    @Override
    public boolean isEnabled() {
        return resolveLocator().isEnabled();
    }

    @Override
    public Map<String, Object> getPosition() {
        BoundingBox boundingBox = this.resolveLocator().boundingBox();
        return PlaywrightDriver.asCoordinatesMap(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
    }

    @Override
    public byte[] screenshot() {
        return resolveLocator().screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
    }

    @Override
    public Element highlight() {
        resolveLocator().highlight();
        return this;
    }

    @Override
    public Element focus() {
        resolveLocator().focus(new FocusOptions().setTimeout(driver.actionWaitTimeout()));
        return this;
    }

    @Override
    public Element clear() {
        resolveLocator().clear(new ClearOptions().setTimeout(driver.actionWaitTimeout()));
        return this;
    }

    @Override
    public Element click() {
        resolveLocator().click(new ClickOptions().setTimeout(driver.actionWaitTimeout()));
        return this;
    }

    @Override
    public Element input(String value) {
        return input(new String[]{value});
    }

    @Override
    public Element input(String[] values) {
        return input(values, 0);
    }

    @Override
    public Element input(String[] values, int delay) {
        for (String input : values) {
            StringBuilder press = new StringBuilder();
            int standardChars = 0;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                String charValue = Keys.keyValue(c);
                if (charValue != null) {// special value, handle it
                    if (standardChars > 0) {
                        resolveLocator().pressSequentially(input.substring(i - standardChars, i), new Locator.PressSequentiallyOptions().setDelay(delay).setTimeout(driver.actionWaitTimeout()));
                        standardChars = 0;
                    }
                    if (press.length() > 0) {
                        press.append("+");
                    }
                    press.append(charValue);
                    if (!Keys.isModifier(c)) {
                        // send it straight away
                        resolveLocator().press(press.toString(), new PressOptions().setTimeout(driver.actionWaitTimeout()));
                        press.setLength(0);
                    }
                } else {
                    if (press.length() > 0) {
                        resolveLocator().press(press.append("+").append(c).toString(), new PressOptions().setTimeout(driver.actionWaitTimeout()));
                        press.setLength(0);
                    } else {
                        standardChars++;
                    }
                }
            }

            if (standardChars > 0) {
                resolveLocator().pressSequentially(input.substring(input.length() - standardChars), new Locator.PressSequentiallyOptions().setDelay(delay).setTimeout(driver.actionWaitTimeout()));
            }
        }
        return this;
    }

    @Override
    public Element select(String text) {
        // selectOption(String) matches by label OR value https://playwright.dev/java/docs/api/class-locator#locator-select-option
        // My understanding of the Karate doc is that only the former should be checked.
        if (!resolveLocator().selectOption(new SelectOption().setLabel(text)).isEmpty()) {
            return this;
        }
        return missingElement();
    }

    @Override
    public Element select(int index) {
        if (!resolveLocator().selectOption(new SelectOption().setIndex(index)).isEmpty()) {
            return this;
        }
        return missingElement();
    }

    @Override
    public Element scroll() {
        resolveLocator().scrollIntoViewIfNeeded(new ScrollIntoViewIfNeededOptions().setTimeout(driver.actionWaitTimeout()));
        return this;
    }

    @Override
    public void setValue(String value) {
        resolveLocator().fill(value, new FillOptions().setTimeout(driver.actionWaitTimeout()));
    }

    @Override
    public Element submit() {
        InvocationHandler h = InvocationHandlers.submitHandler(this, driver.getWaitingForPage());
        return elementProxy(h);
    }

    private Element elementProxy(InvocationHandler h) {
        return (Element) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{Element.class}, h);
    }

    @Override
    public Mouse mouse() {
        return new PlaywrightMouse(driver, token);
    }

    @Override
    public Element switchFrame() {
        driver.switchTo(this.resolveLocator());
        return this;
    }

    @Override
    public Element delay(int millis) {
        driver.sleep(millis);
        return this;
    }

    @Override
    public Element retry() {
        return retry(null, null);
    }

    @Override
    public Element retry(int count) {
        return retry(count, null);
    }

    @Override
    public Element retry(Integer count, Integer interval) {
        return elementProxy(InvocationHandlers.retryHandler(this, count, interval, driver.options));
    }

    @Override
    public Element waitFor() {
        resolveLocator().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(driver.waitTimeout()));
        return this;
    }

    @Override
    public Element waitUntil(String expression) {
        driver.waitForFunction(expression, this.resolveLocator().elementHandle());
        return this;
    }

    @Override
    public Element waitForText(String text) {
        resolveLocator().filter(new FilterOptions().setHasText(text)).waitFor(new WaitForOptions().setTimeout(driver.waitTimeout()));
        return this;
    }


    @Override
    public boolean isPresent() {
        if (present == null) {
            present = resolveLocator().isVisible();
            // Per doc, isVisible does not wait and returns immediately, exactly what we need!
        }
        return present;
    }


    @Override
    public Element optional(String locator) {
        return token.child(locator).find(driver).orElseGet(() -> new MissingElement(driver, locator));
    }

    @Override
    public boolean exists(String locator) {
        return token.child(locator).find(driver).isPresent();        
    }
    

    @Override
    public Element locate(String locator) {
        return token.child(locator).find(driver)
            .orElseThrow(() -> new IllegalArgumentException(locator+" not found"));
    }

    @Override
    public List<Element> locateAll(String locator) {
        return token.child(locator).findAll(driver);
    }

    @Override
    public Object script(String expression) {
        return resolveLocator().evaluate(PlaywrightDriver.toJsExpression(expression));        
    }

    @Override
    public List<Object> scriptAll(String locator, String expression) {
        // element.script most likely convert expression so we will pay the conversion price for every item in the list.
        // But this makes the code consistently working with Element. 
        return locateAll(locator).stream().map(element -> element.script(expression)).toList();
    }

    @Override
    public String getHtml() {
        return (String) script("_.outerHTML");
    }

    @Override
    public void setHtml(String html) {
        resolveLocator().evaluate("el => el.innerHTML =" + html);
    }

    @Override
    public String getText() {
        return resolveLocator().textContent();
    }

    @Override
    public void setText(String text) {
        resolveLocator().fill(text);
    }

    @Override
    public String getValue() {
        return resolveLocator().inputValue();
    }

    @Override
    public String attribute(String name) {
        return resolveLocator().getAttribute(name);
    }

    @Override
    public String property(String name) {        
        return Objects.toString(resolveLocator().elementHandle().getProperty(name));
    }

    @Override
    public Element getParent() {
        return token.child("xpath=..").create(driver);
    }

    @Override
    public Element getFirstChild() {
        return token.child("nth=0").create(driver);
    }

    @Override
    public Element getLastChild() {
        return token.child("nth=-1").create(driver);
    }

    @Override
    public Element getPreviousSibling() {
        return token.child("/preceding-sibling").create(driver);
    }

    @Override
    public Element getNextSibling() {
        return token.child("/following-sibling").create(driver);
    }

    @Override
    public List<Element> getChildren() {
        return token.child("xpath=child::*").findAll(driver);
    }

    @Override
    public Finder rightOf() {
        return new PlaywrightFinder(driver, token, "right-of");
    }

    @Override
    public Finder leftOf() {
        return new PlaywrightFinder(driver, token, "left-of");
    }

    @Override
    public Finder above() {
        return new PlaywrightFinder(driver, token, "above");
    }

    @Override
    public Finder below() {
        return new PlaywrightFinder(driver, token, "below");
    }

    @Override
    public Finder near() {
        return new PlaywrightFinder(driver, token, "near");
    }

    private MissingElement missingElement() {
        return new MissingElement(driver, token.getPlaywrightToken());
    }

    private Locator resolveLocator() {
        return token.resolveLocator();
    }

}
