package com.intuit.karate.driver;


import com.intuit.karate.*;
import com.intuit.karate.shell.CommandThread;

import java.io.File;
import java.io.IOException;

/**
 * @author babusekaran
 */
public abstract class AppiumDriver extends WebDriver {

    protected final DriverOptions options;
    protected final Logger logger;
    protected final CommandThread command;
    protected final Http http;
    private final String sessionId;

    protected AppiumDriver(DriverOptions options, CommandThread command, Http http, String sessionId, String windowId) {
        super(options, command, http, sessionId, windowId);
        this.options = options;
        this.logger = options.driverLogger;
        this.command = command;
        this.http = http;
        this.sessionId = sessionId;
    }

    @Override
    public String attribute(String locator, String name) {
        String id = getElementId(locator);
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
    protected String getElementId(String id) {
        String body = getElementSelector(id);
        return http.path("element").post(body).jsonPath("get[0] $..ELEMENT").asString();
    }

    @Override
    public void clear(String selector) {
        String id = getElementId(selector);
        http.path("element", id, "clear").post("{}");
    }

    @Override
    public void click(String selector) {
        String id = getElementId(selector);
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
        String id = getElementId(locator);
        return http.path("element", id, "text").get().jsonPath("$.value").asString();
    }

}
