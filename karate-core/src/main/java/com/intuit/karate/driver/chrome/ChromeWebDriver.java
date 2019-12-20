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
package com.intuit.karate.driver.chrome;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.Json;
import com.intuit.karate.LogAppender;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.shell.Command;
import com.intuit.karate.driver.WebDriver;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ChromeWebDriver extends WebDriver {

    public ChromeWebDriver(DriverOptions options, Command command, Http http, String sessionId, String windowId) {
        super(options, command, http, sessionId, windowId);
    }

    public static ChromeWebDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 9515, "chromedriver");
        options.arg("--port=" + options.port);
        options.arg("--user-data-dir=" + options.workingDirPath);
        Command command = options.startProcess();
        String urlBase = "http://" + options.host + ":" + options.port;
        Http http = Http.forUrl(options.driverLogger.getAppender(), urlBase);
        String sessionId = http.path("session")
                .post(options.getCapabilities())
                .jsonPath("get[0] response..sessionId").asString();
        options.driverLogger.debug("init session id: {}", sessionId);
        http.url(urlBase + "/session/" + sessionId);
        String windowId = http.path("window").get().jsonPath("$.value").asString();
        options.driverLogger.debug("init window id: {}", windowId);
        ChromeWebDriver driver = new ChromeWebDriver(options, command, http, sessionId, windowId);
        driver.activate();
        return driver;
    }

    @Override
    protected String getElementKey() {
        return "ELEMENT";
    }

    @Override
    protected String getJsonForInput(String text) {
        return "{ value: ['" + text + "'] }";
    }

    @Override
    protected String getJsonForHandle(String text) {
        return new Json().set("name", text).toString();
    }

    @Override
    protected String getJsonForFrame(String text) {
        return new Json().set("id.ELEMENT", text).toString();
    }

    @Override
    public void activate() {
        if (!options.headless) {
            try {
                switch (FileUtils.getOsType()) {
                    case MACOSX:
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
    public void switchFrame(String locator) {
        if (locator == null) { // reset to parent frame
            http.path("frame", "parent").post("{}");
            return;
        }
        retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            http.path("frame").post(getJsonForFrame(id));
            return null;
        });
    }

    @Override
    protected boolean isJavaScriptError(Http.Response res) {
        ScriptValue value = res.jsonPath("$.value").value();
        return !value.isNull() && value.getAsString().contains("javascript error");
    }        

    @Override
    protected boolean isLocatorError(Http.Response res) {
        ScriptValue value = res.jsonPath("$.value").value();
        return value.getAsString().contains("no such element");
    }  

    @Override
    protected boolean isCookieError(Http.Response res) {
        ScriptValue value = res.jsonPath("$.value").value();
        return !value.isNull() && value.getAsString().contains("unable to set cookie");
    }        

}
