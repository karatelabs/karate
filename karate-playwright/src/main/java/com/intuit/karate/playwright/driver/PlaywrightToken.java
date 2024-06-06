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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.intuit.karate.driver.Element;
import com.intuit.karate.playwright.driver.PlaywrightDriver.FrameTrait;
import com.intuit.karate.playwright.driver.util.KarateTokenParser;
import com.microsoft.playwright.Locator;

/**
 * A (possibly chained) token, where a token is a selector or a locator, which
 * can be resolved into a {link com.microsoft.playwright.Locator}.
 *
 * 
 * A {@link PlaywrightToken} provides two factory methods to create {@link PlaywrightElement}
 * {@link #create(PlaywrightDriver)}, {@link #find(PlaywrightDriver)}.
 * which should be used over calling the constructor.
 * 
 * 
 * It also provides a single place where:
 * - Karate's friendly and wildcard locators are handled.
 * - Playwright's strict mode is handled.
 * 
 * Karate's friendly and wildcard locators will be automatically converted 
*  into Playwright compatible locators (xpath, css, pseudo or even possibly
 * custom locators).
 * 
 * Playwright's strict mode is designed to fail when a {link com.microsoft.playwright.Locator} 
 * references more than one element.
 * 
 * A solution to this problem is typically to call locator.first() to resolve the conflict.
 * However, since Karate does not - and should not - expose such an API, {@link PlaywrightToken} 
 * must be smart enough to do it automatically.
 * This is done in {@link #resolveLocator()} which is expected to be called whenever:
 * - an "action" (UI actions such as click(), scroll(), input(), or state actions such as getText(), getAttribute()) happens on the current locator
 * - or a sub locator is created through {@link #child(String)} or {@link #friendlyLocator(String, String)}.
 * 
 * For example:
 * <pre>
 * Element first = driver.locate("foo") // Not yet resolved
 * Element second = first.child("bar")  // first resolved, second not yet resolved
 * second.click(); // resolved
 * driver.click("bar"); // an internal Element is created, similar to first, and resolved when click is called.
 * </pre>
 * 
 *                      
 * Note that there is another solution to resolve the conflict, which is to call locator.all() to get a list of all the matching elements.
 * {@link PlaywrightToken} also supports this through the {@link #findAll(PlaywrightDriver)} method.
 *  
 * */
public class PlaywrightToken {

    private final Locator locator;

    PlaywrightToken(Locator root) {
        this.locator = Objects.requireNonNull(root);
    }

    public String getPlaywrightToken() {
        return locator.toString();
    }

    public String toString() {
        return locator.toString();
    }

    public static PlaywrightToken root(FrameTrait root, String token) {
        return of(root.locator(KarateTokenParser.toPlaywrightToken(token)));
    }

    public static PlaywrightToken of(Locator locator) {
        return new PlaywrightToken(locator);
    }

    public PlaywrightToken child(String karateToken) {
        String newToken = KarateTokenParser.toPlaywrightToken(karateToken);
        return new PlaywrightToken(resolveLocator().locator(newToken));
    }

    public PlaywrightToken friendlyLocator(String type, String token) {
        // friendly is registered as a customer selector in PlaywrightDriver
        return new PlaywrightToken(resolveLocator().locator("friendly=" + type.replace("-of", "") + ":" + KarateTokenParser.toPlaywrightToken(token)));
        // alternative implementation, using PW's native locators, which unfortunately does not seem to support xpath tokens.
        // Note that it would require KarateTokenParser to use toPplaywrightToken.
        // return new PlaywrightElement(driver, token.wrap(pwLoc -> KarateTokenParser.toPlaywrightToken(tag)+":"+type+"("+ pwLoc+")").first());

    }

    public Locator resolveLocator() {
        return locator.first();
    }

    Optional<Element> find(PlaywrightDriver driver) {
        // Per doc, isVisible does not wait and returns immediately, exactly what we need!    
        // Also, isPresent implemented using isVisible() seems to be the general view per https://stackoverflow.com/questions/64784781/how-to-check-if-an-element-exists-on-the-page-in-playwright-js
        // although, if isAttached was available, it probably would have made more sense.            
        if (locator.count() > 0) {
            // Make locator the new root. Note that any Element located is unambigously "resolved" by using the first locator.
            return Optional.of(new PlaywrightElement(driver, of(locator), true));
        }
        return Optional.empty();    
    }

    List<Element> findAll(PlaywrightDriver driver) {
        List<Locator> locators = locator.all();
        List<Element> elements = new ArrayList<>(locators.size());
        for (Locator locator: locators) {
            elements.add(new PlaywrightElement(driver, of(locator), true));
        }
        return elements;        
    }

    /**
     * Returns an Element, which may exist or not.
     *
     * If it does not exist, next call to click, text, or any action or state method, will fail.
     * {@link Element#isPresent()}, however, will never fail and may be used to check the existence of the element. 
     * @param driver
     * @return
     */
    PlaywrightElement create(PlaywrightDriver driver) {
        return new PlaywrightElement(driver, of(locator));
    }
}
