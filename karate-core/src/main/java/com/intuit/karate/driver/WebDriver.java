/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.driver;

import com.intuit.karate.Http;
import com.intuit.karate.Json;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.shell.CommandThread;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public abstract class WebDriver implements Driver {

    protected final DriverOptions options;
    protected final CommandThread command;
    protected final Http http;
    private final String sessionId;
    private final String windowId;

    protected boolean open = true;

    // mutable
    protected Logger logger;

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
        http.setLogger(logger);
    }

    protected WebDriver(DriverOptions options, CommandThread command, Http http, String sessionId, String windowId) {
        this.options = options;
        this.logger = options.driverLogger;
        this.command = command;
        this.http = http;
        this.sessionId = sessionId;
        this.windowId = windowId;
    }

    // currently duplicated in the driver implementations
    protected void waitIfNeeded(String name) {
        if (options.isAlwaysWait()) {
            wait(name);
        }
    }

    private ScriptValue evalInternal(String expression) {
        Json json = new Json().set("script", expression).set("args", "[]");
        return http.path("execute", "sync").post(json).jsonPath("$.value").value();
    }

    protected String getElementKey() {
        return "element-6066-11e4-a52e-4f735466cecf";
    }

    protected String getJsonForInput(String text) {
        return new Json().set("text", text).toString();
    }

    protected String getJsonForHandle(String text) {
        return new Json().set("handle", text).toString();
    }

    protected String getJsonForFrame(String text) {
        return new Json().set("id", text).toString();
    }

    protected String getElementLocator(String id) {
        Json json = new Json();
        if (id.startsWith("^")) {
            json.set("using", "link text").set("value", id.substring(1));
        } else if (id.startsWith("*")) {
            json.set("using", "partial link text").set("value", id.substring(1));
        } else if (id.startsWith("/")) {
            json.set("using", "xpath").set("value", id);
        } else {
            json.set("using", "css selector").set("value", id);
        }
        return json.toString();
    }

    @Override
    public String get(String id) {
        String body = getElementLocator(id);
        return http.path("element").post(body).jsonPath("get[0] $.." + getElementKey()).asString();
    }

    @Override
    public List<String> getAll(String id) {
        String body = getElementLocator(id);
        return http.path("elements").post(body).jsonPath("$.." + getElementKey()).asList();
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    @Override
    public void setLocation(String url) {
        Json json = new Json().set("url", url);
        http.path("url").post(json);
    }

    @Override
    public Map<String, Object> getDimensions() {
        Map map = http.path("window", "rect").get().asMap();
        Integer left = (Integer) map.remove("x");
        Integer top = (Integer) map.remove("y");
        map.put("left", left);
        map.put("top", top);
        return map;
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        Integer x = (Integer) map.remove("left");
        Integer y = (Integer) map.remove("top");
        map.put("x", x);
        map.put("y", y);
        Json json = new Json(map);
        http.path("window", "rect").post(json);
    }

    @Override
    public void refresh() {
        http.path("refresh").post("{}");
    }

    @Override
    public void reload() {
        // not supported by webdriver
        refresh();
    }

    @Override
    public void back() {
        http.path("back").post("{}");
    }

    @Override
    public void forward() {
        http.path("forward").post("{}");
    }

    @Override
    public void maximize() {
        http.path("window", "maximize").post("{}");
    }

    @Override
    public void minimize() {
        http.path("window", "minimize").post("{}");
    }

    @Override
    public void fullscreen() {
        http.path("window", "fullscreen").post("{}");
    }

    @Override
    public void focus(String id) {
        waitIfNeeded(id);
        evalInternal(options.elementSelector(id) + ".focus()");
    }

    @Override
    public void clear(String locator) {
        String id = get(locator);
        http.path("element", id, "clear").post("{}");
    }

    @Override
    public void input(String name, String value) {
        waitIfNeeded(name);
        http.path("element", get(name), "value").post(getJsonForInput(value));
    }

    @Override
    public void click(String id) {
        click(id, false);
    }

    @Override
    public void click(String id, boolean ignored) {
        waitIfNeeded(id);
        evalInternal(options.elementSelector(id) + ".click()");
    }

    @Override
    public void select(String id, String text) {
        waitIfNeeded(id);
        evalInternal(options.optionSelector(id, text));
    }

    @Override
    public void select(String id, int index) {
        waitIfNeeded(id);
        evalInternal(options.optionSelector(id, index));
    }

    @Override
    public void submit(String name) {
        click(name);
        waitForPage();
    }

    @Override
    public void close() {
        http.path("window").delete();
        open = false;
    }

    @Override
    public void quit() {
        if (open) {
            close();
        }
        // delete session
        http.delete();
        if (command != null) {
            command.close();
        }
    }

    @Override
    public String getLocation() {
        return http.path("url").get().jsonPath("$.value").asString();
    }

    @Override
    public String html(String locator) {
        return property(locator, "outerHTML");
    }

    @Override
    public List<String> htmls(String locator) {
        List<String> ids = getAll(locator);
        List<String> list = new ArrayList(ids.size());
        for (String id : ids) {
            String html = getPropertyById(id, "outerHTML");
            list.add(html);
        }
        return list;
    }

    protected String getTextById(String id) {
        return http.path("element", id, "text").get().jsonPath("$.value").asString();
    }

    @Override
    public String text(String locator) {
        waitIfNeeded(locator);
        String id = get(locator);
        return getTextById(id);
    }

    @Override
    public List<String> texts(String locator) {
        List<String> ids = getAll(locator);
        List<String> list = new ArrayList(ids.size());
        for (String id : ids) {
            String text = getTextById(id);
            list.add(text);
        }
        return list;
    }

    @Override
    public String value(String locator) {
        return property(locator, "value");
    }

    @Override
    public List<String> values(String locator) {
        List<String> ids = getAll(locator);
        List<String> list = new ArrayList(ids.size());
        for (String id : ids) {
            String property = getPropertyById(id, "value");
            list.add(property);
        }
        return list;
    }

    @Override
    public void value(String locator, String value) {
        evalInternal(options.elementSelector(locator) + ".value = '" + value + "'");
    }

    @Override
    public String attribute(String locator, String name) {
        waitIfNeeded(locator);
        String id = get(locator);
        return http.path("element", id, "attribute", name).get().jsonPath("$.value").asString();
    }

    protected String getPropertyById(String id, String name) {
        return http.path("element", id, "property", name).get().jsonPath("$.value").asString();
    }

    @Override
    public String property(String locator, String name) {
        waitIfNeeded(locator);
        String id = get(locator);
        return getPropertyById(id, name);
    }

    @Override
    public String css(String locator, String name) {
        waitIfNeeded(locator);
        String id = get(locator);
        return http.path("element", id, "css", name).get().jsonPath("$.value").asString();
    }

    @Override
    public String name(String locator) {
        return property(locator, "tagName");
    }

    @Override
    public Map<String, Object> rect(String locator) {
        waitIfNeeded(locator);
        String id = get(locator);
        return http.path("element", id, "rect").get().jsonPath("$.value").asMap();
    }

    @Override
    public boolean enabled(String locator) {
        waitIfNeeded(locator);
        String id = get(locator);
        return http.path("element", id, "enabled").get().jsonPath("$.value").isBooleanTrue();
    }

    private String prefixReturn(String expression) {
        return expression.startsWith("return ") ? expression : "return " + expression;
    }

    @Override
    public boolean waitUntil(String expression) {
        expression = prefixReturn(expression);
        int max = options.getRetryCount();
        int count = 0;
        ScriptValue sv;
        do {
            options.sleep();
            sv = evalInternal(expression);
        } while (!sv.isBooleanTrue() && count++ < max);
        return sv.isBooleanTrue();
    }

    @Override
    public Object eval(String expression) {
        expression = prefixReturn(expression);
        return evalInternal(expression).getValue();
    }

    @Override
    public String getTitle() {
        return http.path("title").get().jsonPath("$.value").asString();
    }

    @Override
    public List<Map> getCookies() {
        return http.path("cookie").get().jsonPath("$.value").asList();
    }

    @Override
    public Map<String, Object> cookie(String name) {
        return http.path("cookie", name).get().jsonPath("$.value").asMap();
    }

    @Override
    public void setCookie(Map<String, Object> cookie) {
        http.path("cookie").post(Collections.singletonMap("cookie", cookie));
    }

    @Override
    public void deleteCookie(String name) {
        http.path("cookie", name).delete();
    }

    @Override
    public void clearCookies() {
        http.path("cookie").delete();
    }

    @Override
    public void dialog(boolean accept) {
        dialog(accept, null);
    }

    @Override
    public String getDialog() {
        return http.path("alert", "text").get().jsonPath("$.value").asString();
    }

    @Override
    public void dialog(boolean accept, String text) {
        if (text == null) {
            http.path("alert", accept ? "accept" : "dismiss").post("{}");
        } else {
            http.path("alert", "text").post(Collections.singletonMap("text", text));
            http.path("alert", "accept").post("{}");
        }
    }

    @Override
    public byte[] screenshot() {
        return screenshot(null);
    }

    @Override
    public byte[] screenshot(String locator) {
        String id = locator == null ? null : get(locator);
        String temp;
        if (id == null) {
            temp = http.path("screenshot").get().jsonPath("$.value").asString();
        } else {
            temp = http.path("element", id, "screenshot").get().jsonPath("$.value").asString();
        }
        return Base64.getDecoder().decode(temp);
    }

    @Override
    public void highlight(String id) {
        eval(options.highlighter(id));
    }

    protected String getWindowHandleKey() {
        return "handle";
    }

    @Override
    public List<String> getWindowHandles() {
        return http.path("window", "handles").get().jsonPath("$.value").asList();
    }

    @Override
    public void switchPage(String titleOrUrl) {
        if (titleOrUrl == null) {
            return;
        }
        List<String> list = getWindowHandles();
        for (String handle : list) {
            http.path("window").post(getJsonForHandle(handle));
            String title = getTitle();
            if (titleOrUrl.equals(title)) {
                return;
            }
            String temp = options.removeProtocol(titleOrUrl);
            String url = options.removeProtocol(getLocation());
            if (temp.equals(url)) {
                return;
            }
        }
    }

    @Override
    public void switchFrame(int index) {
        if (index == -1) {
            http.path("frame", "parent").post("{}");
            return;
        }
        String json = new Json().set("id", index).toString();
        http.path("frame").post(json);
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) { // reset to parent frame
            http.path("frame", "parent").post("{}");
            return;
        }
        String frameId = get(locator);
        if (frameId == null) {
            return;
        }
        List<String> ids = getAll("iframe,frame");
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (frameId.equals(id)) {
                switchFrame(i);
                break;
            }
        }
    }

}
