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
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

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
        this.driver = driver;
        this.token = token;
        this.present = present;
    }

    @Override
    public String getLocator() {
        return token.getPlaywrightToken();
    }

    @Override
    public boolean isPresent() {
        if (present == null) {
            present = isPresent(driver, token);
        }
        return present;
    }

    @Override
    public boolean isEnabled() {
        return resolveLocator().isEnabled();
    }

    @Override
    public Map<String, Object> getPosition() {
        return PlaywrightDriver.asCoordinatesMap(this.resolveLocator().boundingBox());
    }

    @Override
    public byte[] screenshot() {
        return driver.screenshot(this.resolveLocator());
    }

    @Override
    public Element highlight() {
        resolveLocator().highlight();
        return this;
    }

    @Override
    public Element focus() {
        resolveLocator().focus(new FocusOptions().setTimeout(driver.retryTimeout));
        return this;
    }

    @Override
    public Element clear() {
        resolveLocator().clear(new ClearOptions().setTimeout(driver.retryTimeout));
        return this;
    }

    @Override
    public Element click() {
        resolveLocator().click(new ClickOptions().setTimeout(driver.retryTimeout));
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
                        resolveLocator().pressSequentially(input.substring(i - standardChars, i), new Locator.PressSequentiallyOptions().setDelay(delay).setTimeout(driver.retryTimeout));
                        standardChars = 0;
                    }
                    if (press.length() > 0) {
                        press.append("+");
                    }
                    press.append(charValue);
                    if (!Keys.isModifier(c)) {
                        // send it straight away
                        resolveLocator().press(press.toString(), new PressOptions().setTimeout(driver.retryTimeout));
                        press.setLength(0);
                    }
                } else {
                    if (press.length() > 0) {
                        resolveLocator().press(press.append("+").append(c).toString(), new PressOptions().setTimeout(driver.retryTimeout));
                        press.setLength(0);
                    } else {
                        standardChars++;
                    }
                }
            }

            if (standardChars > 0) {
                resolveLocator().pressSequentially(input.substring(input.length() - standardChars), new Locator.PressSequentiallyOptions().setDelay(delay).setTimeout(driver.retryTimeout));
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
        resolveLocator().scrollIntoViewIfNeeded(new ScrollIntoViewIfNeededOptions().setTimeout(driver.retryTimeout));
        return this;
    }
    
    @Override
    public void setValue(String value) {
        resolveLocator().fill(value, new FillOptions().setTimeout(driver.retryTimeout));
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
        int retryCount = count == null ? driver.options.getRetryCount() : count;
        int retryInterval = interval == null ? driver.options.getRetryInterval() : interval;
        return elementProxy(InvocationHandlers.retryHandler(this, retryCount, retryInterval, driver.options.driverLogger, driver));
    }

    @Override
    public Element waitFor() {
        resolveLocator().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        return this;
    }

    @Override
    public Element waitUntil(String expression) {
        driver.waitForFunction(expression, this.resolveLocator().elementHandle());
        return this;
    }

    @Override
    public Element waitForText(String text) {
        resolveLocator().filter(new FilterOptions().setHasText(text)).waitFor();
        return this;
    }

    @Override
    public Object script(String expression) {
        return driver.script(token.toLocator(), expression);
    }

    @Override
    public Object scriptAll(String locator, String expression) {
        return driver.scriptAll(token.child(locator).toLocator(), expression);
    }

    @Override
    public Element optional(String locator) {
        return optional(driver, token.child(locator));
    }

    @Override
    public boolean exists(String locator) {
        return driver.exists(token.child(locator).toLocator());
    }

    @Override
    public Element locate(String locator) {
        return locate(driver, token.child(locator));
    }

    @Override
    public List<Element> locateAll(String locator) {
        return locateAll(driver, token.child(locator));
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
        return new PlaywrightElement(driver, token.child("xpath=.."));
    }

    @Override
    public Element getFirstChild() {
        return new PlaywrightElement(driver, token.child("nth=0"));
    }

    @Override
    public Element getLastChild() {
        return new PlaywrightElement(driver, token.child("nth=-1"));
    }

    @Override
    public Element getPreviousSibling() {
        return new PlaywrightElement(driver, token.child("/preceding-sibling"));
    }

    @Override
    public Element getNextSibling() {
        return new PlaywrightElement(driver, token.child("/following-sibling"));
    }

    @Override
    public List<Element> getChildren() {
        // todo test
        return findAll(driver, token, i -> "nth-child("+i+")");
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

    // Will fail immediately if the element is not found. Same as waitFor but without the "wait" part
    static Element locate(PlaywrightDriver driver, PlaywrightToken token) {
        if (isPresent(driver, token)) {
           return new PlaywrightElement(driver, token, true);
        }
        throw new RuntimeException(token + " not found");
    }

    static List<Element> locateAll(PlaywrightDriver driver, PlaywrightToken token) {
        return findAll(driver, token, i -> "nth=" + i);

    }

    private static List<Element> findAll(PlaywrightDriver driver, PlaywrightToken token, IntFunction<String> mapper) {
        Locator locator = token.toLocator();
        int count = locator.count();

        List<Element> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(new PlaywrightElement(driver, token.child(mapper.apply(i))));
        }
        return elements;
    }

    static Element optional(PlaywrightDriver driver, PlaywrightToken token) {
        try {
            return locate(driver, token);
        } catch (RuntimeException e) {
            return new MissingElement(driver, null);
        }
    }

    static boolean isPresent(PlaywrightDriver driver, PlaywrightToken token) {
        Locator locator = token.toLocator().first();
        // Per doc, isVisible does not wait and returns immediately, exactly what we need!
        return locator.isVisible();
    }

    private Locator resolveLocator() {
        return token.toLocator().first();
    }

}
