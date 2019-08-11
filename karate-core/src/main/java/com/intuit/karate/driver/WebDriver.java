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
package com.intuit.karate.driver;

import com.intuit.karate.Http;
import com.intuit.karate.Json;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.shell.Command;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public abstract class WebDriver implements Driver {

    protected final DriverOptions options;
    protected final Command command;
    protected final Http http;
    private final String sessionId;
    private final String windowId;

    protected boolean open = true;

    // mutable
    protected Logger logger;

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
        http.setLogger(logger);
    }

    protected WebDriver(DriverOptions options, Command command, Http http, String sessionId, String windowId) {
        this.options = options;
        this.logger = options.driverLogger;
        this.command = command;
        this.http = http;
        this.sessionId = sessionId;
        this.windowId = windowId;
    }

    protected <T> T retryIfEnabled(String locator, Supplier<T> action) {
        if (options.isRetryEnabled()) {
            waitFor(locator); // will throw exception if not found
        }
        String before = options.getSubmitTarget();
        if (before != null) {
            logger.trace("submit requested, will wait for page load after next action on : {}", locator);
            options.setSubmitTarget(null); // clear the submit flag
            T result = action.get();
            int count = 0, max = options.getRetryCount();
            String after;
            do {
                if (count > 0) {
                    logger.trace("waiting for document to change, retry #{}", count);
                    options.sleep();
                }
                after = elementId("html");
            } while (count++ < max && before.equals(after));
            waitForPage();
            return result;
        } else {
            return action.get();
        }
    }

    protected boolean isJavaScriptError(Http.Response res) {
        return res.status() != 200 
                && !res.jsonPath("$.value").asString().contains("unexpected alert open");
    }
    
    private Element evalInternal(String expression, String locator) {
        // here the locator is just passed on and nothing is done with it
        eval(expression);
        // the only case where the element exists flag is set to null
        return DriverElement.locatorUnknown(this, locator);
    }
    
    private ScriptValue eval(String expression) {
        Json json = new Json().set("script", expression).set("args", "[]");
        Http.Response res = http.path("execute", "sync").post(json);
        if (isJavaScriptError(res)) {
            logger.warn("javascript failed, will retry once: {}", res.body().asString());
            options.sleep();
            res = http.path("execute", "sync").post(json);
            if (isJavaScriptError(res)) {
                String message = "javascript failed twice: " + res.body().asString();
                logger.error(message);                
                throw new RuntimeException(message);
            }            
        }
        return res.jsonPath("$.value").value();
    }

    protected String getElementKey() {
        return "element-6066-11e4-a52e-4f735466cecf";
    }

    protected String getJsonForInput(String text) {
        return new Json().set("text", text).toString();
    }

    protected String getJsonForHandle(String text) {
        return new Json().set("handle", text).toString();
    }

    protected String getJsonForFrame(String text) {
        return new Json().set("id", text).toString();
    }

    protected String selectorPayload(String id) {
        Json json = new Json();
        if (id.startsWith("^")) {
            json.set("using", "link text").set("value", id.substring(1));
        } else if (id.startsWith("*")) {
            json.set("using", "partial link text").set("value", id.substring(1));
        } else if (id.startsWith("/")) {
            json.set("using", "xpath").set("value", id);
        } else {
            json.set("using", "css selector").set("value", id);
        }
        return json.toString();
    }

    @Override
    public String elementId(String locator) {
        return http.path("element")
                .post(selectorPayload(locator)).jsonPath("get[0] $.." + getElementKey()).asString();
    }

    @Override
    public List<String> elementIds(String locator) {
        return http.path("elements")
                .post(selectorPayload(locator)).jsonPath("$.." + getElementKey()).asList();
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    @Override
    public void setLocation(String url) {
        Json json = new Json().set("url", url);
        http.path("url").post(json);
    }

    @Override
    public Map<String, Object> getDimensions() {
        return http.path("window", "rect").get().jsonPath("$.value").asMap();
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        http.path("window", "rect").post(map);
    }

    @Override
    public void refresh() {
        http.path("refresh").post("{}");
    }

    @Override
    public void reload() {
        // not supported by webdriver
        refresh();
    }

    @Override
    public void back() {
        http.path("back").post("{}");
    }

    @Override
    public void forward() {
        http.path("forward").post("{}");
    }

    @Override
    public void maximize() {
        http.path("window", "maximize").post("{}");
    }

    @Override
    public void minimize() {
        http.path("window", "minimize").post("{}");
    }

    @Override
    public void fullscreen() {
        http.path("window", "fullscreen").post("{}");
    }

    @Override
    public Element focus(String locator) {
        return retryIfEnabled(locator, () -> evalInternal(options.selector(locator) + ".focus()", locator));
    }

    @Override
    public Element clear(String locator) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            http.path("element", id, "clear").post("{}");
            return DriverElement.locatorExists(this, locator);
        });
    }

    @Override
    public Element input(String locator, String value) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            http.path("element", id, "value").post(getJsonForInput(value));
            return DriverElement.locatorExists(this, locator);
        });
    }

    @Override
    public Element click(String locator) {
        return retryIfEnabled(locator, () -> evalInternal(options.selector(locator) + ".click()", locator));        
        // the spec is un-reliable :(
        // String id = get(locator);
        // http.path("element", id, "click").post("{}");        
    }

    @Override
    public Driver submit() {
        options.setSubmitTarget(elementId("html"));
        return this;
    }

    @Override
    public Element select(String locator, String text) {
        return retryIfEnabled(locator, () -> evalInternal(options.optionSelector(locator, text), locator));
    }

    @Override
    public Element select(String locator, int index) {
        return retryIfEnabled(locator, () -> evalInternal(options.optionSelector(locator, index), locator));
    }

    @Override
    public void actions(List<Map<String, Object>> actions) {
        http.path("actions").post(Collections.singletonMap("actions", actions));
    }        

    @Override
    public void close() {
        http.path("window").delete();
        open = false;
    }

    @Override
    public void quit() {
        if (open) {
            close();
        }
        // delete session
        try {
            http.delete();
        } catch (Exception e) {
            logger.warn("session delete failed: {}", e.getMessage());
        }
        if (command != null) {
            command.close();
        }
    }

    @Override
    public String getLocation() {
        return http.path("url").get().jsonPath("$.value").asString();
    }

    @Override
    public String html(String locator) {
        return property(locator, "outerHTML");
    }

    @Override
    public String text(String locator) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            return http.path("element", id, "text").get().jsonPath("$.value").asString();
        });
    }

    @Override
    public String value(String locator) {
        return property(locator, "value");
    }

    @Override
    public Element value(String locator, String value) {
        return retryIfEnabled(locator, () -> evalInternal(options.selector(locator) + ".value = '" + value + "'", locator));
    }

    @Override
    public String attribute(String locator, String name) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            return http.path("element", id, "attribute", name).get().jsonPath("$.value").asString();
        });
    }

    @Override
    public String property(String locator, String name) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            return http.path("element", id, "property", name).get().jsonPath("$.value").asString();
        });
    }

    @Override
    public Map<String, Object> position(String locator) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            return http.path("element", id, "rect").get().jsonPath("$.value").asMap();
        });
    }

    @Override
    public boolean enabled(String locator) {
        return retryIfEnabled(locator, () -> {
            String id = elementId(locator);
            return http.path("element", id, "enabled").get().jsonPath("$.value").isBooleanTrue();
        });
    }

    private String prefixReturn(String expression) {
        return expression.startsWith("return ") ? expression : "return " + expression;
    }

    @Override
    public boolean waitUntil(String expression) {
        expression = prefixReturn(expression);
        int max = options.getRetryCount();
        int count = 0;
        ScriptValue sv;
        do {
            if (count > 0) {
                logger.debug("waitUntil retry #{}", count);
                options.sleep();
            }
            sv = eval(expression);
        } while (!sv.isBooleanTrue() && count++ < max);
        return sv.isBooleanTrue();
    }

    @Override
    public Object script(String expression) {
        expression = prefixReturn(expression);
        return eval(expression).getValue();
    }

    @Override
    public String getTitle() {
        return http.path("title").get().jsonPath("$.value").asString();
    }

    @Override
    public List<Map> getCookies() {
        return http.path("cookie").get().jsonPath("$.value").asList();
    }

    @Override
    public Map<String, Object> cookie(String name) {
        return http.path("cookie", name).get().jsonPath("$.value").asMap();
    }

    @Override
    public void setCookie(Map<String, Object> cookie) {
        http.path("cookie").post(Collections.singletonMap("cookie", cookie));
    }

    @Override
    public void deleteCookie(String name) {
        http.path("cookie", name).delete();
    }

    @Override
    public void clearCookies() {
        http.path("cookie").delete();
    }

    @Override
    public void dialog(boolean accept) {
        dialog(accept, null);
    }

    @Override
    public String getDialog() {
        return http.path("alert", "text").get().jsonPath("$.value").asString();
    }

    @Override
    public void dialog(boolean accept, String text) {
        if (text == null) {
            http.path("alert", accept ? "accept" : "dismiss").post("{}");
        } else {
            http.path("alert", "text").post(Collections.singletonMap("text", text));
            http.path("alert", "accept").post("{}");
        }
    }

    @Override
    public byte[] screenshot(boolean embed) {
        return screenshot(null, embed);
    }

    @Override
    public byte[] screenshot(String locator, boolean embed) {
        String temp;
        if (locator == null) {
            temp = http.path("screenshot").get().jsonPath("$.value").asString();
        } else {
            temp = retryIfEnabled(locator, () -> {
                String id = elementId(locator);
                return http.path("element", id, "screenshot").get().jsonPath("$.value").asString();
            });
        }
        byte[] bytes = Base64.getDecoder().decode(temp);
        if (embed) {
            options.embedPngImage(bytes);
        }
        return bytes;
    }

    @Override
    public List<String> getPages() {
        return http.path("window", "handles").get().jsonPath("$.value").asList();
    }

    @Override
    public void switchPage(String titleOrUrl) {
        if (titleOrUrl == null) {
            return;
        }
        List<String> list = getPages();
        for (String handle : list) {
            http.path("window").post(getJsonForHandle(handle));
            String title = getTitle();
            if (titleOrUrl.equals(title)) {
                return;
            }
            String temp = options.removeProtocol(titleOrUrl);
            String url = options.removeProtocol(getLocation());
            if (temp.equals(url)) {
                return;
            }
        }
    }

    @Override
    public void switchFrame(int index) {
        if (index == -1) {
            http.path("frame", "parent").post("{}");
            return;
        }
        String json = new Json().set("id", index).toString();
        http.path("frame").post(json);
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) { // reset to parent frame
            http.path("frame", "parent").post("{}");
            return;
        }
        retryIfEnabled(locator, () -> {
            String frameId = elementId(locator);
            if (frameId == null) {
                return null;
            }
            List<String> ids = elementIds("iframe,frame");
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (frameId.equals(id)) {
                    switchFrame(i);
                    break;
                }
            }
            return null;
        });
    }

}
