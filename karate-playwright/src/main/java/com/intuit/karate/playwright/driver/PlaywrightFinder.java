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

import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.Finder;

import java.lang.reflect.Proxy;

public class PlaywrightFinder implements Finder {

    private final PlaywrightDriver driver;
    private final PlaywrightToken token;
    private final String type;

    public PlaywrightFinder(PlaywrightDriver driver, PlaywrightToken token, String type) {
        this.driver = driver;
        this.token = token;
        this.type = type;
    }

    @Override
    public Element input(String value) {
        return find("input").input(value);
    }

    @Override
    public Element select(String value) {
        return find("select").select(value);
    }

    @Override
    public Element select(int index) {
        return find("select").select(index);
    }

    @Override
    public Element click() {
        return find().click();
    }

    @Override
    public String getValue() {
        return find().getValue();
    }

    @Override
    public Element clear() {
        return token.create(driver).clear();
    }

    @Override
    public Element find() {
        return find("input");
    }

    @Override
    public Element find(String tag) {
        return token.friendlyLocator(type, tag).create(driver);
    }

    @Override
    public Element highlight() {
        return find().highlight();
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
        PlaywrightElement element = token.create(driver);
        return (Element) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{Element.class}, InvocationHandlers.retryHandler(element, count, interval, driver.options));
    }

}
