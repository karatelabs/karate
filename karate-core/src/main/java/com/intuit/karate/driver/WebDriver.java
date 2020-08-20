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
    private boolean terminated;
    //private final String windowId;

    protected boolean open = true;

    protected final Logger logger;

    protected WebDriver(DriverOptions options) {
        this.options = options;
        this.logger = options.driverLogger;
        command = options.startProcess();
        http = options.getHttp();
        Http.Response response = http.path("session").post(options.getWebDriverSessionPayload());
        if (response.status() != 200) {
            String message = "webdriver session create status " + response.status() + ", " + response.body().asString();
            logger.error(message);
            if (command != null) {
                command.close(true);
            }
            throw new RuntimeException(message);
        }
        sessionId = response.jsonPath("get[0] response..sessionId").asString();
        logger.debug("init session id: {}", sessionId);
        http.url("/session/" + sessionId);
        //windowId = http.path("window").get().jsonPath("$.value").asString();
        //logger.debug("init window id: {}", windowId);
        if (options.start) {
            activate();
        }
    }

    @Override
    public Driver timeout(Integer millis) {
        options.setTimeout(millis);
        // this will "reset" to default if null was set above
        http.config("readTimeout", options.getTimeout() + "");
        return this;
    }

    @Override
    public Driver timeout() {
        return timeout(null);
    }

    public String getSessionId() {
        return sessionId;
    }

    // can be used directly if you know what you are doing !
    public Http getHttp() {
        return http;
    }

    private String getSubmitHash() {
        return getUrl() + elementId("html");
    }

    protected <T> T retryIfEnabled(String locator, Supplier<T> action) {
        if (options.isRetryEnabled()) {
            waitFor(locator); // will throw exception if not found
        }
        if (options.highlight) {
            highlight(locator, options.highlightDuration);
        }
        String before = options.getPreSubmitHash();
        if (before != null) {
            logger.trace("submit requested, will wait for page load after next action on : {}", locator);
            options.setPreSubmitHash(null); // clear the submit flag            
            T result = action.get();
            Integer retryInterval = options.getRetryInterval();
            options.setRetryInterval(500); // reduce retry interval for this special case
            options.retry(() -> getSubmitHash(), hash -> !before.equals(hash), "waiting for document to change", false);
            // extra precaution TODO is this needed
            // waitUntil("document.readyState == 'complete'");
            options.setRetryInterval(retryInterval); // restore
            return result;
        } else {
            return action.get();
        }
    }

    protected boolean isJavaScriptError(Http.Response res) {
        return res.status() != 200
                && !res.jsonPath("$.value").asString().contains("unexpected alert open");
    }

    protected boolean isLocatorError(Http.Response res) {
        return res.status() != 200;
    }

    protected boolean isCookieError(Http.Response res) {
        return res.status() != 200;
    }

    private Element evalLocator(String locator, String dotExpression) {
        eval(prefixReturn(options.selector(locator) + "." + dotExpression));
        // if the js above did not throw an exception, the element exists
        return DriverElement.locatorExists(this, locator);
    }

    private Element evalFocus(String locator) {
        eval(options.focusJs(locator));
        // if the js above did not throw an exception, the element exists
        return DriverElement.locatorExists(this, locator);
    }

    protected ScriptValue eval(String expression, List args) {
        Json json = new Json().set("script", expression).set("args", (args == null) ? Collections.EMPTY_LIST : args);
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

    protected ScriptValue eval(String expression) {
        return eval(expression, null);
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

    protected String selectorPayload(String locator) {
        if (locator.startsWith("{")) {
            locator = DriverOptions.preProcessWildCard(locator);
        }
        Json json = new Json();
        if (locator.startsWith("/")) {
            json.set("using", "xpath").set("value", locator);
        } else {
            json.set("using", "css selector").set("value", locator);
        }
        return json.toString();
    }

    @Override
    public String elementId(String locator) {
        String json = selectorPayload(locator);
        Http.Response res = http.path("element").post(json);
        if (isLocatorError(res)) {
            logger.warn("locator failed, will retry once: {}", res.body().asString());
            options.sleep();
            res = http.path("element").post(json);
            if (isLocatorError(res)) {
                String message = "locator failed twice: " + res.body().asString();
                logger.error(message);
                throw new RuntimeException(message);
            }
        }
        return res.jsonPath("get[0] $.." + getElementKey()).asString();
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
    public void setUrl(String url) {
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
        return retryIfEnabled(locator, () -> evalFocus(locator));
    }

    @Override
    public Element clear(String locator) {
        return retryIfEnabled(locator, () -> evalLocator(locator, "value = ''"));
    }

    @Override
    public Element input(String locator, String value) {
        return retryIfEnabled(locator, () -> {
            String elementId;
            if (locator.startsWith("(")) {
                evalFocus(locator);
                elementId = http.path("element", "active").get()
                        .jsonPath("get[0] $.." + getElementKey()).asString();
            } else {
                elementId = elementId(locator);
            }
            http.path("element", elementId, "value").post(getJsonForInput(value));
            return DriverElement.locatorExists(this, locator);
        });
    }

    @Override
    public Element click(String locator) {
        return retryIfEnabled(locator, () -> evalLocator(locator, "click()"));
    }

    @Override
    public Driver submit() {
        options.setPreSubmitHash(getSubmitHash());
        return this;
    }

    @Override
    public Element select(String locator, String text) {
        return retryIfEnabled(locator, () -> {
            eval(options.optionSelector(locator, text));
            // if the js above did not throw an exception, the element exists
            return DriverElement.locatorExists(this, locator);
        });
    }

    @Override
    public Element select(String locator, int index) {
        return retryIfEnabled(locator, () -> {
            eval(options.optionSelector(locator, index));
            // if the js above did not throw an exception, the element exists
            return DriverElement.locatorExists(this, locator);
        });
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
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public void quit() {
        if (terminated) {
            return;
        }
        terminated = true;
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
            command.close(true);
        }
    }

    @Override
    public String getUrl() {
        return http.path("url").get().jsonPath("$.value").asString();
    }

    private String evalReturn(String locator, String dotExpression) {
        return eval("return " + options.selector(locator) + "." + dotExpression).getAsString();
    }

    @Override
    public String html(String locator) {
        return retryIfEnabled(locator, () -> evalReturn(locator, "outerHTML"));
    }

    @Override
    public String text(String locator) {
        return retryIfEnabled(locator, () -> evalReturn(locator, "textContent"));
    }

    @Override
    public String value(String locator) {
        return retryIfEnabled(locator, () -> evalReturn(locator, "value"));
    }

    @Override
    public Element value(String locator, String value) {
        return retryIfEnabled(locator, () -> evalLocator(locator, "value = '" + value + "'"));
    }

    @Override
    public String attribute(String locator, String name) {
        return retryIfEnabled(locator, () -> evalReturn(locator, "getAttribute('" + name + "')"));
    }

    @Override
    public String property(String locator, String name) {
        return retryIfEnabled(locator, () -> evalReturn(locator, name));
    }

    @Override
    public Map<String, Object> position(String locator) {
        return retryIfEnabled(locator, ()
                -> eval("return " + options.selector(locator) + ".getBoundingClientRect()").getAsMap());
    }

    @Override
    public boolean enabled(String locator) {
        return retryIfEnabled(locator, ()
                -> eval("return !" + options.selector(locator) + ".disabled").isBooleanTrue());
    }

    private String prefixReturn(String expression) {
        return expression.startsWith("return ") ? expression : "return " + expression;
    }

    @Override
    public boolean waitUntil(String expression) {
        return options.retry(() -> {
            try {
                return eval(prefixReturn(expression)).isBooleanTrue();
            } catch (Exception e) {
                logger.warn("waitUntil evaluate failed: {}", e.getMessage());
                return false;
            }
        }, b -> b, "waitUntil (js)", true);
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
    public void cookie(Map<String, Object> cookie) {
        Http.Response res = http.path("cookie").post(Collections.singletonMap("cookie", cookie));
        if (isCookieError(res)) {
            throw new RuntimeException("set-cookie failed: " + res.body().asString());
        }
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
        byte[] bytes = getDecoder().decode(temp);
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
            if (title != null && title.contains(titleOrUrl)) {
                return;
            }
            String url = getUrl();
            if (url != null && url.contains(titleOrUrl)) {
                return;
            }
        }
    }

    @Override
    public void switchPage(int index) {
        if (index == -1) {
            return;
        }
        String json = new Json().set("id", index).toString();
        http.path("window").post(json);
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

    protected Base64.Decoder getDecoder() {
        return Base64.getDecoder();
    }
}
