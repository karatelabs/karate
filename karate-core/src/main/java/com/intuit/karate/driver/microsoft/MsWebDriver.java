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
package com.intuit.karate.driver.microsoft;

import com.intuit.karate.Json;
import com.intuit.karate.LogAppender;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.WebDriver;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class MsWebDriver extends WebDriver {

    public MsWebDriver(DriverOptions options) {
        super(options);
    }

    public static MsWebDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 17556, "MicrosoftWebDriver");
        options.arg("--port=" + options.port);
        return new MsWebDriver(options);
    }

    @Override
    protected String getJsonForInput(String text) {
        return new Json().set("keysToSend[0]", text).toString();
    }

    @Override
    public void activate() {
        logger.warn("activate not implemented for mswebdriver");
    }

}
