package com.intuit.karate.playwright.driver;

import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.Finder;
import com.intuit.karate.playwright.driver.util.KarateTokenParser;

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
        return new PlaywrightElement(driver, token).clear();
    }

    @Override
    public Element find() {
        return find("input");
    }

    @Override
    public Element find(String tag) {
        return new PlaywrightElement(driver, token.friendlyLocator(type, tag));
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
        PlaywrightElement element = new PlaywrightElement(driver, token);
        PlaywrightDriverOptions options = driver.options;
        return (Element) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{Element.class}, InvocationHandlers.retryHandler(element, count == null ? options.getRetryCount() : count, interval == null ? options.getRetryInterval() : interval, options.driverLogger, driver));
    }

}
