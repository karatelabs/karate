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

import com.intuit.karate.playwright.driver.util.KarateTokenParser;
import com.microsoft.playwright.Locator;

/**
 * A (possibly chained) token, where a token is a selector or a locator, which
 * can be resolved into a {link com.microsoft.playwright.Locator}
 *
 * Karate's friendly locators are supported and will be automatically converted
 * into Playwright compatible locators (xpath, css, pseudo or even possibly
 * custom locators).
 *
 * In essence, a PlaywrightToken could just wrap a {link
 * com.microsoft.playwright.Locator}. Since PW's Locators are by nature chained,
 * they meet all the requirements of PlaywrightToken. Current implementation,
 * however, is a bit more complicated. Locators are not stored internally but
 * only computed when requested so the chaining is implemented within the class,
 * hence the
 * <pre>parent<pre> reference.
 *
 * However, that implementation makes it possible to implement Karate's friendly locators using PW's native right-of/left-of/above/below/near pseudo locators.
 *
 * Let's consider locate('#div').rightOf().find('p')
 *
 * As far as I undertand, this would be:
 * Locator("p:right-of('#div')")
 *
 * in PW world. Note how it's a single Locator. However, by the time locate('#div') is called, Karate/PLaywrightDriver creates a first element referencing Locator('#div'),
 * and only when that element is chained with rightOf/find does PlaywrightDriver find out that locator should be replaced by Locator("p:right-of('#div')").
 * That "replaces" thing is where we need the parent to recreate the correct locator.
 *
 * Note that using PW's family of pseudo locators didn't make it to the final version, because i could not get it to work with xpath (locate('#div').rightOf().find('//p')) but maybe someone will at some point.
 *
 */
public class PlaywrightToken {

    private final String playwrightToken;

    private final PlaywrightToken parent;

    private final PlaywrightDriver driver;

    private final boolean first;

    PlaywrightToken(PlaywrightDriver driver, String playwrightToken, PlaywrightToken parent, boolean first) {
        this.driver = driver;
        this.playwrightToken = playwrightToken;
        this.parent = parent;
        this.first = first;
    }

    PlaywrightToken(PlaywrightDriver driver, String playwrightToken, PlaywrightToken parent) {
        this(driver, playwrightToken, parent, false);
    }

    public String getPlaywrightToken() {
        return playwrightToken;
    }

    public PlaywrightToken getParent() {
        return parent;
    }

    public PlaywrightToken first() {
        return new PlaywrightToken(driver, playwrightToken, parent, true);
    }

    public boolean isFirst() {
        return first;
    }

    public String toString() {
        return playwrightToken;
    }

    public static PlaywrightToken root(PlaywrightDriver driver, String token) {
        return new PlaywrightToken(driver, KarateTokenParser.toPlaywrightToken(token), null);
    }

    public PlaywrightToken child(String karateToken) {
        String newToken = KarateTokenParser.toPlaywrightToken(karateToken);
        return new PlaywrightToken(driver, newToken, this);
    }

    public PlaywrightToken friendlyLocator(String type, String token) {
        // friendly is registered as a customer selector in PlaywrightDriver
        return new PlaywrightToken(driver, "friendly=" + type.replace("-of", "") + ":" + KarateTokenParser.toPlaywrightToken(token), this);
        // alternative implementation, using PW's native locators, which unfortunately does not seem to support xpath tokens.
        // Note that it would require KarateTokenParser to use toPplaywrightToken.
        // return new PlaywrightElement(driver, token.wrap(pwLoc -> KarateTokenParser.toPlaywrightToken(tag)+":"+type+"("+ pwLoc+")").first());

    }

    // private PlaywrightToken wrap(Function<String, String> playwrightTokenRemapper) {
    //     return new PlaywrightToken(driver, playwrightTokenRemapper.apply(playwrightToken), parent);
    // }
    public Locator toLocator() {
        Locator locator;
        if (getParent() == null) {
            locator = driver.rootLocator(getPlaywrightToken());
        } else {
            locator = getParent().toLocator().locator(getPlaywrightToken());
        }
        return (isFirst()) ? locator.first() : locator;
    }

}
