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
import com.intuit.karate.driver.DriverElement;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.WebDriver;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class WinAppDriver extends WebDriver {

    public WinAppDriver(DriverOptions options) {
        super(options);
    }

    public static WinAppDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 4727, 
                "C:/Program Files (x86)/Windows Application Driver/WinAppDriver");
        options.arg(options.port + "");
        return new WinAppDriver(options);
    }

    @Override
    public void activate() {
        // TODO
    }
    
    private String getElementSelector(String id) {
        Json json = new Json();
        if (id.startsWith("/")) {
            json.set("using", "xpath").set("value", id);
        } else if (id.startsWith("@")){
            json.set("using", "accessibility id").set("value", id.substring(1));
        } else if (id.startsWith("#")){
            json.set("using", "id").set("value", id.substring(1));
        } else {
            json.set("using", "name").set("value", id);
        }
        return json.toString();
    }

    @Override
    public String elementId(String id) {
        String body = getElementSelector(id);
        return http.path("element").post(body).jsonPath("get[0] $..ELEMENT").asString();
    }

    @Override
    public Element click(String locator) {
        String id = elementId(locator);
        http.path("element", id, "click").post("{}");
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public String text(String locator) {
        String id = elementId(locator);
        return http.path("element", id, "text").get().jsonPath("$.value").asString();
    }

    @Override
    protected String getJsonForInput(String text) {
        return new Json().set("value[0]", text).toString();
    }

}
