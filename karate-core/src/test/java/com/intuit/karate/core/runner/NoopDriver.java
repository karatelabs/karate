package com.intuit.karate.core.runner;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NoopDriver implements Driver {

    public static final String DRIVER_TYPE = "noopdriver";
    static final Logger logger = LoggerFactory.getLogger(NoopDriver.class);

    public final DriverOptions options;

    public static NoopDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        return new NoopDriver(map, sr);
    }

    public NoopDriver(Map<String, Object> map, ScenarioRuntime sr) {
        this.options = new DriverOptions(map, sr, 8402, DRIVER_TYPE);
    }

    @Override
    public void activate() {
        logger.debug("NoopDriver: activate()");
    }

    @Override
    public void refresh() {
        logger.debug("NoopDriver: refresh()");

    }

    @Override
    public void reload() {
        logger.debug("NoopDriver: reload()");

    }

    @Override
    public void back() {
        logger.debug("NoopDriver: back()");

    }

    @Override
    public void forward() {
        logger.debug("NoopDriver: forward()");

    }

    @Override
    public void maximize() {
        logger.debug("NoopDriver: maximize()");

    }

    @Override
    public void minimize() {
        logger.debug("NoopDriver: minimize()");

    }

    @Override
    public void fullscreen() {
        logger.debug("NoopDriver: fullscreen()");

    }

    @Override
    public void close() {
        logger.debug("NoopDriver: close()");

    }

    @Override
    public void quit() {
        logger.debug("NoopDriver: quit()");

    }

    @Override
    public void switchPage(String titleOrUrl) {
        logger.debug("NoopDriver: switchPage()");

    }

    @Override
    public void switchPage(int index) {
        logger.debug("NoopDriver: switchPage()");

    }

    @Override
    public void switchFrame(int index) {
        logger.debug("NoopDriver: switchFrame()");

    }

    @Override
    public void switchFrame(String locator) {
        logger.debug("NoopDriver: switchFrame()");

    }

    @Override
    public String getUrl() {
        logger.debug("NoopDriver: getUrl()");
        return null;
    }

    @Override
    public void setUrl(String url) {
        logger.debug("NoopDriver: setUrl()");

    }

    @Override
    public Map<String, Object> getDimensions() {
        logger.debug("NoopDriver: getDimensions()");
        return Collections.EMPTY_MAP;
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        logger.debug("NoopDriver: setDimensions()");

    }

    @Override
    public String getTitle() {
        logger.debug("NoopDriver: getTitle()");
        return null;
    }

    @Override
    public List<String> getPages() {
        logger.debug("NoopDriver: getPages()");
        return Collections.EMPTY_LIST;
    }

    @Override
    public String getDialogText() {
        logger.debug("NoopDriver: getDialogText()");
        return null;
    }

    @Override
    public byte[] screenshot(boolean embed) {
        logger.debug("NoopDriver: screenshot()");
        return new byte[0];
    }

    @Override
    public Map<String, Object> cookie(String name) {
        logger.debug("NoopDriver: cookie()");
        return Collections.EMPTY_MAP;
    }

    @Override
    public void cookie(Map<String, Object> cookie) {
        logger.debug("NoopDriver: cookie()");

    }

    @Override
    public void deleteCookie(String name) {
        logger.debug("NoopDriver: deleteCookie()");

    }

    @Override
    public void clearCookies() {
        logger.debug("NoopDriver: clearCookies()");

    }

    @Override
    public List<Map> getCookies() {
        logger.debug("NoopDriver: getCookies()");
        return Collections.EMPTY_LIST;
    }

    @Override
    public void dialog(boolean accept) {
        logger.debug("NoopDriver: dialog()");

    }

    @Override
    public void dialog(boolean accept, String input) {
        logger.debug("NoopDriver: dialog()");

    }

    @Override
    public Object script(String expression) {
        logger.debug("NoopDriver: script()");
        return null;
    }

    @Override
    public boolean waitUntil(String expression) {
        logger.debug("NoopDriver: waitUntil()");
        return false;
    }

    @Override
    public Driver submit() {
        logger.debug("NoopDriver: submit()");
        return this;
    }

    @Override
    public Driver timeout(Integer millis) {
        logger.debug("NoopDriver: timeout()");
        return this;
    }

    @Override
    public Driver timeout() {
        logger.debug("NoopDriver: timeout()");
        return this;
    }

    @Override
    public Element focus(String locator) {
        logger.debug("NoopDriver: focus()");
        return null;
    }

    @Override
    public Element clear(String locator) {
        logger.debug("NoopDriver: clear()");
        return null;
    }

    @Override
    public Element click(String locator) {
        logger.debug("NoopDriver: click()");
        return null;
    }

    @Override
    public Element input(String locator, String value) {
        logger.debug("NoopDriver: input()");
        return null;
    }

    @Override
    public Element select(String locator, String text) {
        logger.debug("NoopDriver: select()");
        return null;
    }

    @Override
    public Element select(String locator, int index) {
        logger.debug("NoopDriver: select()");
        return null;
    }

    @Override
    public Element value(String locator, String value) {
        logger.debug("NoopDriver: value()");
        return null;
    }

    @Override
    public void actions(List<Map<String, Object>> actions) {
        logger.debug("NoopDriver: actions()");

    }

    @Override
    public String html(String locator) {
        logger.debug("NoopDriver: html()");
        return null;
    }

    @Override
    public String text(String locator) {
        logger.debug("NoopDriver: text()");
        return null;
    }

    @Override
    public String value(String locator) {
        logger.debug("NoopDriver: value()");
        return null;
    }

    @Override
    public String attribute(String locator, String name) {
        logger.debug("NoopDriver: attribute()");
        return null;
    }

    @Override
    public String property(String locator, String name) {
        logger.debug("NoopDriver: property()");
        return null;
    }

    @Override
    public boolean enabled(String locator) {
        logger.debug("NoopDriver: enabled()");
        return false;
    }

    @Override
    public Map<String, Object> position(String locator) {
        logger.debug("NoopDriver: position()");
        return Collections.EMPTY_MAP;
    }

    @Override
    public Map<String, Object> position(String locator, boolean relative) {
        logger.debug("NoopDriver: position()");
        return Collections.EMPTY_MAP;
    }

    @Override
    public byte[] screenshot(String locator, boolean embed) {
        logger.debug("NoopDriver: screenshot()");
        return new byte[0];
    }

    @Override
    public byte[] pdf(Map<String, Object> options) {
        logger.debug("NoopDriver: pdf()");
        return new byte[0];
    }

    @Override
    public boolean isTerminated() {
        logger.debug("NoopDriver: isTerminated()");
        return false;
    }

    @Override
    public DriverOptions getOptions() {
        logger.debug("NoopDriver: getOptions()");
        return this.options;
    }

    @Override
    public Object elementId(String locator) {
        logger.debug("NoopDriver: elementId()");
        return null;
    }

    @Override
    public List elementIds(String locator) {
        logger.debug("NoopDriver: elementIds()");
        return Collections.EMPTY_LIST;
    }

}
