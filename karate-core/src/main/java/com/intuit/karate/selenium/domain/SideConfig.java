/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.selenium.domain;

import com.intuit.karate.FileUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.io.File;

/**
 * @author vmchukky
 */
// useful while running selenium tests from karate-ui
// and also to sanitize the input given (should we do this???)
public class SideConfig {

    public static final String DEFAULT_BROWSER_NAME = "chrome";
    public static final String DEFAULT_CHROME_DRIVER_URL = "http://localhost:9515";

    private String configFilePath;
    private DocumentContext jsonDoc;
    public SideConfig(String configFilePath) {
        this.configFilePath = configFilePath;
        File file = new File(configFilePath);
        String jsonString = FileUtils.toString(file);
        this.jsonDoc = JsonPath.parse(jsonString);
    }

    public Object get(String key) {
        return jsonDoc.read(key);
    }

    // http://localhost:9515
    public String getDriverUrl() {
        return jsonDoc.read("$.driverUrl");
    }

    public String getBrowser() {
        return jsonDoc.read("$.browser");
    }

    private void sanitize() {
        String value = getDriverUrl();
        if ((value == null) || (value.trim().length() == 0)) {
            value = DEFAULT_CHROME_DRIVER_URL;
            updateValue("$.driverUrl", value);
        } else if (value.endsWith("/")) {
            value = value.substring(0, value.length()-1);
            updateValue("$.driverUrl", value);
        }

        value = getBrowser();
        if ((value == null) || (value.trim().length() == 0)) {
            value = DEFAULT_BROWSER_NAME;
            updateValue("$.browser", value);
        }
    }

    private void updateValue(String key, String value) {
        jsonDoc.set(key, value);
    }

    public static String getCleanJson(String path) {
        return (new SideConfig(path)).jsonDoc.jsonString();
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    // TODO add getters for commonly used configuration properties
}
