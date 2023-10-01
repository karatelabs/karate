package com.intuit.karate.playwright.driver;

import com.intuit.karate.driver.Mouse;

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
        this.token.toLocator().hover();
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
        token.toLocator().click();
        return this;
    }

    @Override
    public Mouse doubleClick() {
        token.toLocator().dblclick();
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
}
