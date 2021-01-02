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
package com.intuit.karate.driver.appium;

import com.intuit.karate.Json;
import com.intuit.karate.driver.DriverElement;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.WebDriver;
import com.intuit.karate.http.ResourceType;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * @author babusekaran
 */
public abstract class AppiumDriver extends WebDriver {

    private boolean isBrowserSession;

    protected AppiumDriver(DriverOptions options) {
        super(options);
        // flag to know if driver runs for browser on mobile
        Map<String, Object> sessionPayload = (Map<String, Object>) options.getWebDriverSessionPayload();
        Map<String, Object> desiredCapabilities = (Map<String, Object>) sessionPayload.get("desiredCapabilities");
        isBrowserSession = (desiredCapabilities.get("browserName") != null) ? true : false;
    }

    @Override
    public String attribute(String locator, String name) {
        String id = elementId(locator);
        return http.path("element", id, "attribute", name).get().json().get("value");
    }

    @Override
    protected String selectorPayload(String id) {
        if (isBrowserSession) { // use WebDriver selector strategies for mobile browser
            return super.selectorPayload(id);
        }
        Json json = Json.object();
        if (id.startsWith("/")) {
            json.set("using", "xpath").set("value", id);
        } else if (id.startsWith("@")) {
            json.set("using", "accessibility id").set("value", id.substring(1));
        } else if (id.startsWith("#")) {
            json.set("using", "id").set("value", id.substring(1));
        } else if (id.startsWith(":")) {
            json.set("using", "-ios predicate string").set("value", id.substring(1));
        } else if (id.startsWith("^")) {
            json.set("using", "-ios class chain").set("value", id.substring(1));
        } else if (id.startsWith("-")) {
            json.set("using", "-android uiautomator").set("value", id.substring(1));
        } else {
            json.set("using", "name").set("value", id);
        }
        return json.toString();
    }

    @Override
    public Element click(String locator) {
        String id = elementId(locator);
        http.path("element", id, "click").postJson("{}");
        return DriverElement.locatorExists(this, locator);
    }

    public void setContext(String context) {
        Json contextBody = Json.object();
        contextBody.set("name", context);
        http.path("context").post(contextBody);
    }

    public void hideKeyboard() {
        http.path("appium", "device", "hide_keyboard").postJson("{}");
    }

    public String startRecordingScreen() {
        return http.path("appium", "start_recording_screen").postJson("{}").json().get("value");
    }

    public String startRecordingScreen(Map<String, Object> payload) {
        Map<String, Object> options = new HashMap<>();
        options.put("options", payload);
        return http.path("appium", "start_recording_screen").post(options).json().get("value");
    }

    public String stopRecordingScreen() {
        return http.path("appium", "stop_recording_screen").postJson("{}").json().get("value");
    }

    public String stopRecordingScreen(Map<String, Object> payload) {
        Map<String, Object> options = new HashMap<>();
        options.put("options", payload);
        return http.path("appium", "stop_recording_screen").post(options).json().get("value");
    }

    public void saveRecordingScreen(String fileName, boolean embed) {
        String videoTemp = stopRecordingScreen();
        byte[] bytes = Base64.getDecoder().decode(videoTemp);
        File src = new File(fileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(src.getAbsolutePath())) {
            fileOutputStream.write(bytes);
        } catch (Exception e) {
            logger.error("error while saveRecordingScreen {}", e.getMessage());
        }
        if (embed) {
            if (src.exists()) {
                getRuntime().embed(bytes, ResourceType.MP4);
            }
        }
    }

    public void saveRecordingScreen(String fileName) {
        saveRecordingScreen(fileName, false);
    }

    @Override
    public String text(String locator) {
        String id = elementId(locator);
        return http.path("element", id, "text").get().json().get("value");
    }

    @Override
    protected Base64.Decoder getDecoder() {
        return Base64.getMimeDecoder();
    }

    @Override
    public void close() {
        // TODO
    }

    @Override
    public Object script(String expression) {
        if (isBrowserSession) { // use WebDriver script for mobile browser
            return super.script(expression);
        }
        return eval(expression).getValue();
    }

    public Object script(String expression, List<Map<String, Object>> args) {
        return eval(expression, args).getValue();
    }

    public Object script(String expression, Map<String, Object> args) {
        List<Map<String, Object>> scriptArgs = new ArrayList<>(1);
        scriptArgs.add(args);
        return eval(expression, scriptArgs).getValue();
    }

}
