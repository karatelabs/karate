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
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.WebDriver;
import com.intuit.karate.http.Response;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ChromeWebDriver extends WebDriver {

    public static final String DRIVER_TYPE = "chromedriver";

    public ChromeWebDriver(DriverOptions options) {
        super(options);
    }

    public static ChromeWebDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        DriverOptions options = new DriverOptions(map, sr, 9515, "chromedriver");
        options.arg("--port=" + options.port);
        if (options.userDataDir != null) {
            options.arg("--user-data-dir=" + options.userDataDir);
        }
        return new ChromeWebDriver(options);
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
    protected boolean isJavaScriptError(Response res) {
        Object value = res.json().get("value");
        return value != null && value.toString().contains("javascript error");
    }

    @Override
    protected boolean isLocatorError(Response res) {
        Object value = res.json().get("value");
        return value.toString().contains("no such element");
    }

    @Override
    protected boolean isCookieError(Response res) {
        Object value = res.json().get("value");
        return value != null && value.toString().contains("unable to set cookie");
    }

}
