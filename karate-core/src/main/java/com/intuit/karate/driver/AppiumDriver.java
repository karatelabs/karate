/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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

import com.intuit.karate.*;
import com.intuit.karate.shell.Command;

/**
 * @author babusekaran
 */
public abstract class AppiumDriver extends WebDriver {

    protected final DriverOptions options;
    protected final Logger logger;
    protected final Command command;
    protected final Http http;
    private final String sessionId;

    protected AppiumDriver(DriverOptions options, Command command, Http http, String sessionId, String windowId) {
        super(options, command, http, sessionId, windowId);
        this.options = options;
        this.logger = options.driverLogger;
        this.command = command;
        this.http = http;
        this.sessionId = sessionId;
    }

    @Override
    public String attribute(String locator, String name) {
        String id = get(locator);
        return http.path("element", id, "attribute", name).get().jsonPath("$.value").asString();
    }

    private ScriptValue evalInternal(String expression) {
        Json json = new Json().set("script", expression).set("args", "[]");
        return http.path("execute", "sync").post(json).jsonPath("$.value").value();
    }

    private String getElementSelector(String id) {
        Json json = new Json();
        if (id.startsWith("/")) {
            json.set("using", "xpath").set("value", id);
        } else if (id.startsWith("@")) {
            json.set("using", "accessibility id").set("value", id.substring(1));
        } else if (id.startsWith("#")) {
            json.set("using", "id").set("value", id.substring(1));
        } else if (id.startsWith(":")) {
            json.set("using", "-ios predicate string").set("value", id.substring(1));
        } else if (id.startsWith("^")){
            json.set("using", "-ios class chain").set("value", id.substring(1));
        } else if (id.startsWith("-")){
            json.set("using", "-android uiautomator").set("value", id.substring(1));
        } else {
            json.set("using", "name").set("value", id);
        }
        return json.toString();
    }

    @Override
    public String get(String id) {
        String body = getElementSelector(id);
        return http.path("element").post(body).jsonPath("get[0] $..ELEMENT").asString();
    }

    @Override
    public void clear(String selector) {
        String id = get(selector);
        http.path("element", id, "clear").post("{}");
    }

    @Override
    public void click(String selector) {
        String id = get(selector);
        http.path("element", id, "click").post("{}");
    }

    public void setContext(String context) {
        Json contextBody = new Json();
        contextBody.set("name", context);
        http.path("context").post(contextBody);
    }

    public void hideKeyboard() {
        http.path("appium", "device", "hide_keyboard").post("{}");
    }

    @Override
    public String text(String locator) {
        String id = get(locator);
        return http.path("element", id, "text").get().jsonPath("$.value").asString();
    }

}
