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
package com.intuit.karate.driver.firefox;

import com.intuit.karate.FileUtils;
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
public class GeckoWebDriver extends WebDriver {

    public GeckoWebDriver(DriverOptions options) {
        super(options);
    }

    public static GeckoWebDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 4444, "geckodriver");
        options.arg("--port=" + options.port);
        return new GeckoWebDriver(options);
    }
    
    @Override
    protected String getJsonForFrame(String text) {
        return new Json().set("frameId", text).toString();
    }    

    @Override
    public void activate() {
        if (!options.headless) {
            try {
                switch (FileUtils.getOsType()) {
                    case MACOSX:
                        Runtime.getRuntime().exec(new String[]{"osascript", "-e", "tell app \"Firefox\" to activate"});
                        break;
                    default:

                }
            } catch (Exception e) {
                logger.warn("native window switch failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public void quit() {
        // geckodriver already closes all windows on delete session
        open = false;
        super.quit();
    }

}
