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
package com.intuit.karate.web;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.Engine;
import com.intuit.karate.shell.CommandThread;
import com.intuit.karate.web.Driver;
import com.intuit.karate.web.DriverUtils;
import java.io.File;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public abstract class WebDriver implements Driver {

    protected static final Logger logger = LoggerFactory.getLogger(WebDriver.class);

    private final CommandThread command;
    private final boolean headless;
    private final Http http;
    private final String sessionId;
    private final String windowId;

    protected WebDriver(CommandThread command, boolean headless, Http http, String sessionId, String windowId) {
        this.command = command;
        this.headless = headless;
        this.http = http;
        this.sessionId = sessionId;
        this.windowId = windowId;
    }

    private void eval(String expression) {
        String body = "{ script: \"" + JsonUtils.escapeValue(expression) + "\", args: [] }";
        http.path("execute", "sync").post(body);
    }

    private String getElementId(String id) {
        String body;
        if (id.startsWith("/")) {
            body = "{ using: 'xpath', value: \"" + id + "\" }";
        } else {
            body = "{ using: 'css selector', value: \"" + id + "\" }";
        }
        logger.debug("body: {}", body);
        return http.path("element").post(body).jsonPath("$.value.ELEMENT").asString();
    }

    @Override
    public void location(String url) {
        http.path("url").post("{ url: '" + url + "'}");
    }

    @Override
    public void activate() {
        if (!headless) {
            try {
                switch (FileUtils.getPlatform()) {
                    case MAC:
                        Runtime.getRuntime().exec(new String[]{"osascript", "-e", "tell app \"Chrome\" to activate"});
                        break;
                    default:

                }
            } catch (Exception e) {
                logger.warn("native window switch failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public void focus(String id) {
        eval(DriverUtils.selectorScript(id) + ".focus()");
    }

    @Override
    public void input(String name, String value) {
        String id = getElementId(name);
        http.path("element", id, "value").post("{ value: ['" + value + "'] }");
    }

    @Override
    public void click(String id) {
        eval(DriverUtils.selectorScript(id) + ".click()");
    }

    @Override
    public void submit(String name) {
        click(name);
    }

    @Override
    public void close() {
        http.path("window").delete();
    }

    @Override
    public void stop() {
        http.delete();
        if (command != null) {
            command.interrupt();
        }
    }

    @Override
    public String getLocation() {
        return http.path("url").get().jsonPath("$.value").asString();
    }

    @Override
    public String html(String locator) {
        String id = getElementId(locator);
        return http.path("element", id, "attribute", "innerHTML").get().jsonPath("$.value").asString();
    }

    @Override
    public String text(String locator) {
        String id = getElementId(locator);
        return http.path("element", id, "text").get().jsonPath("$.value").asString();
    }

}
