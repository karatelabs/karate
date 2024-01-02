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

import com.intuit.karate.driver.Mouse;
import com.microsoft.playwright.Locator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class PlaywrightMouse implements Mouse {

    private final PlaywrightDriver driver;
    final PlaywrightToken token;
    private Integer duration;

    public PlaywrightMouse(PlaywrightDriver driver, PlaywrightToken token) {
        this.driver = driver;
        this.token = token;
    }

    @Override
    public Mouse move(String locator) {
        // IS hover exactly what move() is about?
        resolveLocator().hover();
        if (duration != null) {
            pause(duration);
        }
        return this;
    }

    @Override
    public Mouse move(Number x, Number y) {
        getMouse().move(x.doubleValue(), y.doubleValue());
        return this;
    }

    @Override
    public Mouse offset(Number x, Number y) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mouse down() {
        getMouse().down();
        return this;
    }

    @Override
    public Mouse up() {
        getMouse().up();
        return this;
    }

    @Override
    public Mouse submit() {
        InvocationHandler h = InvocationHandlers.submitHandler(this, driver.getWaitingForPage());
        return (Mouse) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{Mouse.class}, h);
    }

    @Override
    public Mouse click() {
        resolveLocator().click();
        return this;
    }

    @Override
    public Mouse doubleClick() {
        resolveLocator().dblclick();
        return this;
    }

    @Override
    public Mouse go() {
        return this;
    }

    @Override
    public Mouse duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    @Override
    public Mouse pause(Integer duration) {
        driver.sleep(duration);
        return this;
    }

    private com.microsoft.playwright.Mouse getMouse() {
        return driver.getMouse();
    }

    private Locator resolveLocator() {
        return token.resolveLocator();
    }    
}
