/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.driver.playwright;

import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.DriverElement;

import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Element;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketOptions;
import com.intuit.karate.shell.Command;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class PlaywrightDriver implements Driver {

    private final DriverOptions options;
    private final Command command;
    private final WebSocketClient client;
    private final PlaywrightWait wait;
    private final Logger logger;

    private boolean submit;
    private boolean initialized;
    private boolean terminated;

    private String browserGuid;
    private String browserContextGuid;

    private final Object LOCK = new Object();

    private void lockAndWait() {
        synchronized (LOCK) {
            try {
                LOCK.wait();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void unlockAndProceed() {
        initialized = true;
        synchronized (LOCK) {
            LOCK.notify();
        }
    }

    private int nextId;

    public int nextId() {
        return ++nextId;
    }

    public void waitSync() {
        client.waitSync();
    }

    public static PlaywrightDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 4444, "playwright");
        String playwrightUrl;
        Command command;
        if (options.start) {
            Map<String, Object> pwOptions = options.playwrightOptions == null ? Collections.EMPTY_MAP : options.playwrightOptions;
            options.arg(options.port + "");
            String browserType = (String) pwOptions.get("browserType");
            if (browserType == null) {
                browserType = "chromium";
            }
            options.arg(browserType);
            if (options.headless) {
                options.arg("true");
            }
            CompletableFuture<String> future = new CompletableFuture();
            command = options.startProcess(s -> {
                int pos = s.indexOf("ws://");
                if (pos != -1) {
                    s = s.substring(pos).trim();
                    pos = s.indexOf(' ');
                    if (pos != -1) {
                        s = s.substring(0, pos);
                    }
                    future.complete(s);
                }
            });
            try {
                playwrightUrl = future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            options.processLogger.debug("playwright server url ready: {}", playwrightUrl);
        } else {
            command = null;
            playwrightUrl = options.playwrightUrl;
            if (playwrightUrl == null) {
                throw new RuntimeException("playwrightUrl is mandatory if start == false");
            }
        }
        return new PlaywrightDriver(options, command, playwrightUrl);
    }

    public PlaywrightDriver(DriverOptions options, Command command, String webSocketUrl) {
        this.options = options;
        logger = options.driverLogger;
        this.command = command;
        wait = new PlaywrightWait(this, options);
        WebSocketOptions wsOptions = new WebSocketOptions(webSocketUrl);
        wsOptions.setMaxPayloadSize(options.maxPayloadSize);
        wsOptions.setTextConsumer(text -> {
            if (logger.isTraceEnabled()) {
                logger.trace("<< {}", text);
            } else {
                // to avoid swamping the console when large base64 encoded binary responses happen
                logger.debug("<< {}", StringUtils.truncate(text, 1024, true));
            }
            Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
            PlaywrightMessage pwm = new PlaywrightMessage(this, map);
            receive(pwm);
        });
        client = new WebSocketClient(wsOptions, logger);
        lockAndWait();
        logger.debug("contexts ready, frame: {}, page: {}, browser-context: {}, browser: {}",
                currentFrame, currentPage, browserContextGuid, browserGuid);
    }

    private PlaywrightMessage method(String method, String guid) {
        return new PlaywrightMessage(this, method, guid);
    }

    public void send(PlaywrightMessage pwm) {
        String json = JsonUtils.toJson(pwm.toMap());
        logger.debug(">> {}", json);
        client.send(json);
    }

    private String currentDialog;
    private String currentDialogText;
    private String currentDialogType;
    private boolean dialogAccept = true;
    private String dialogInput = "";

    private String currentFrame;
    private String currentPage;
    private final Map<String, Set<String>> pageFrames = new LinkedHashMap();

    private PlaywrightMessage page(String method) {
        return method(method, currentPage);
    }

    private PlaywrightMessage frame(String method) {
        return method(method, currentFrame);
    }

    public void receive(PlaywrightMessage pwm) {
        if (pwm.methodIs("__create__")) {
            if (pwm.paramHas("type", "Page")) {
                String pageGuid = pwm.getParam("guid");
                String frameGuid = pwm.getParam("initializer.mainFrame.guid");
                Set<String> frames = pageFrames.get(pageGuid);
                if (frames == null) {
                    frames = new HashSet();
                    pageFrames.put(pageGuid, frames);
                }
                frames.add(frameGuid);
                if (!initialized) {
                    currentPage = pageGuid;
                    currentFrame = frameGuid;
                    unlockAndProceed();
                }
            } else if (pwm.paramHas("type", "Dialog")) {
                currentDialog = pwm.getParam("guid");
                currentDialogText = pwm.getParam("initializer.message");
                currentDialogType = pwm.getParam("initializer.type");
                if ("alert".equals(currentDialogType)) {
                    method("dismiss", currentDialog).sendWithoutWaiting();
                } else {
                    if (dialogInput == null) {
                        dialogInput = "";
                    }                    
                    method(dialogAccept ? "accept" : "dismiss", currentDialog)
                            .param("promptText", dialogInput).sendWithoutWaiting();
                }
            } else if (pwm.paramHas("type", "RemoteBrowser")) {
                browserGuid = pwm.getParam("initializer.browser.guid");
                Map<String, Object> map = new HashMap();
                if (!options.headless) {
                    map.put("noDefaultViewport", false);
                }
                if (options.playwrightOptions != null) {
                    Map<String, Object> temp = (Map) options.playwrightOptions.get("context");
                    if (temp != null) {
                        map.putAll(temp);
                    }
                }
                method("newContext", browserGuid).params(map).sendWithoutWaiting();
            } else if (pwm.paramHas("type", "BrowserContext")) {
                browserContextGuid = pwm.getParam("guid");
                method("newPage", browserContextGuid).sendWithoutWaiting();
            } else {
                logger.trace("ignoring __create__: {}", pwm);
            }
        } else {
            wait.receive(pwm);
        }
    }

    public PlaywrightMessage sendAndWait(PlaywrightMessage pwm, Predicate<PlaywrightMessage> condition) {
        // do stuff inside wait to avoid missing messages
        PlaywrightMessage result = wait.send(pwm, condition);
        if (result == null) {
            throw new RuntimeException("failed to get reply for: " + pwm);
        }
        return result;
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    @Override
    public Driver timeout(Integer millis) {
        options.setTimeout(millis);
        return this;
    }

    @Override
    public Driver timeout() {
        return timeout(null);
    }

    private static final Map<String, Object> NO_ARGS = new Json("{ value: { v: 'undefined' }, handles: [] }").asMap();

    private PlaywrightMessage evalOnce(String expression, boolean quickly, boolean fireAndForget) {
        PlaywrightMessage toSend = frame("evaluateExpression")
                .param("expression", expression)
                .param("isFunction", false)
                .param("arg", NO_ARGS);
        if (quickly) {
            toSend.setTimeout(options.getRetryInterval());
        }
        if (fireAndForget) {
            toSend.sendWithoutWaiting();
            return null;
        }
        return toSend.send();
    }

    private PlaywrightMessage eval(String expression) {
        return eval(expression, false);
    }

    private PlaywrightMessage eval(String expression, boolean quickly) {
        PlaywrightMessage pwm = evalOnce(expression, quickly, false);
        if (pwm.isError()) {
            String message = "js eval failed once:" + expression
                    + ", error: " + pwm.getResult();
            logger.warn(message);
            options.sleep();
            pwm = evalOnce(expression, quickly, false); // no wait condition for the re-try
            if (pwm.isError()) {
                message = "js eval failed twice:" + expression
                        + ", error: " + pwm.getResult();
                logger.error(message);
                throw new RuntimeException(message);
            }
        }
        return pwm;
    }

    @Override
    public Object script(String expression) {
        return eval(expression).getResultValue();
    }

    @Override
    public String elementId(String locator) {
        return frame("querySelector").param("selector", locator).send().getResult("element.guid");
    }

    @Override
    public List<String> elementIds(String locator) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void retryIfEnabled(String locator) {
        if (options.isRetryEnabled()) {
            waitFor(locator); // will throw exception if not found
        }
        if (options.highlight) {
            // highlight(locator, options.highlightDuration); // instead of this
            String highlightJs = options.highlight(locator, options.highlightDuration);
            evalOnce(highlightJs, true, true); // do it safely, i.e. fire and forget
        }
    }

    @Override
    public void setUrl(String url) {
        frame("goto").param("url", url).param("waitUntil", "load").send();
    }

    @Override
    public void activate() {
        page("bringToFront").send();
    }

    @Override
    public void refresh() {
        page("reload").param("waitUntil", "load").send();
    }

    @Override
    public void reload() {
        refresh(); // TODO ignore cache ?
    }

    @Override
    public void back() {
        page("goBack").param("waitUntil", "load").send();
    }

    @Override
    public void forward() {
        page("goForward").param("waitUntil", "load").send();
    }

    @Override
    public void maximize() {
        // https://github.com/microsoft/playwright/issues/1086
    }

    @Override
    public void minimize() {
        // see maximize()
    }

    @Override
    public void fullscreen() {
        // TODO JS
    }

    @Override
    public void close() {
        page("close").send();
    }

    @Override
    public void quit() {
        if (terminated) {
            return;
        }
        terminated = true;
        method("close", browserGuid).sendWithoutWaiting();
        client.close();
        if (command != null) {
            // cannot force else node process does not terminate gracefully
            command.close(false);
        }
    }

    @Override
    public String property(String id, String name) {
        retryIfEnabled(id);
        return eval(DriverOptions.selector(id) + "['" + name + "']").getResultValue();
    }

    @Override
    public String html(String id) {
        return property(id, "outerHTML");
    }

    @Override
    public String text(String id) {
        return property(id, "textContent");
    }

    @Override
    public String value(String locator) {
        return property(locator, "value");
    }

    @Override
    public String getUrl() {
        return eval("document.location.href").getResultValue();
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        // todo
    }

    @Override
    public String getTitle() {
        return eval("document.title").getResultValue();
    }

    @Override
    public Element click(String locator) {
        retryIfEnabled(locator);
        eval(DriverOptions.selector(locator) + ".click()");
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public Element value(String locator, String value) {
        retryIfEnabled(locator);
        eval(DriverOptions.selector(locator) + ".value = '" + value + "'");
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public String attribute(String id, String name) {
        retryIfEnabled(id);
        return eval(DriverOptions.selector(id) + ".getAttribute('" + name + "')").getResultValue();
    }

    @Override
    public boolean enabled(String id) {
        retryIfEnabled(id);
        PlaywrightMessage pwm = eval(DriverOptions.selector(id) + ".disabled");
        Boolean disabled = pwm.getResultValue();
        return !disabled;
    }

    @Override
    public boolean waitUntil(String expression) {
        return options.retry(() -> {
            try {
                return eval(expression, true).getResultValue();
            } catch (Exception e) {
                logger.warn("waitUntil evaluate failed: {}", e.getMessage());
                return false;
            }
        }, b -> b, "waitUntil (js)", true);
    }

    @Override
    public Driver submit() {
        submit = true;
        return this;
    }

    @Override
    public Element focus(String locator) {
        retryIfEnabled(locator);
        eval(options.focusJs(locator));
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public Element clear(String locator) {
        eval(DriverOptions.selector(locator) + ".value = ''");
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public Map<String, Object> position(String locator) {
        boolean submitTemp = submit; // in case we are prepping for a submit().mouse(locator).click()
        submit = false;
        retryIfEnabled(locator);
        Map<String, Object> map = eval(DriverOptions.getPositionJs(locator)).getResultValue();
        submit = submitTemp;
        return map;
    }

    @Override
    public void switchPage(String titleOrUrl) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void switchPage(int index) {
        if (index == -1 || index >= pageFrames.size()) {
            logger.warn("not switching page for size {}: {}", pageFrames.size(), index);
            return;
        }
        List<String> temp = getPages();
        currentPage = temp.get(index);
        currentFrame = pageFrames.get(currentPage).iterator().next();
        activate();
    }

    @Override
    public void switchFrame(int index) {
        List<String> temp = new ArrayList(pageFrames.get(currentPage));
        if (index == -1) {
            index = 0;
        }
        if (index < temp.size()) {
            currentFrame = temp.get(index);
        } else {
            logger.warn("not switching frame for size {}: {}", temp.size(), index);
        }
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) {
            switchFrame(-1);
        } else {
            logger.warn("switch frame by selector not supported, use index");
        }
    }

    @Override
    public Map<String, Object> getDimensions() {
        logger.warn("getDimensions() not supported");
        return Collections.EMPTY_MAP;
    }

    @Override
    public List<String> getPages() {
        return new ArrayList(pageFrames.keySet());
    }

    @Override
    public String getDialog() {
        return currentDialogText;
    }

    @Override
    public byte[] screenshot(boolean embed) {
        return screenshot(null, embed);
    }

    @Override
    public Map<String, Object> cookie(String name) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void cookie(Map<String, Object> cookie) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteCookie(String name) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearCookies() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Map> getCookies() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void dialog(boolean accept) {
        dialog(accept, null);
    }

    @Override
    public void dialog(boolean accept, String input) {
        this.dialogAccept = accept;
        this.dialogInput = input;
    }

    @Override
    public Element input(String locator, String value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Element select(String locator, String text) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Element select(String locator, int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void actions(List<Map<String, Object>> actions) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] screenshot(String locator, boolean embed) {
        PlaywrightMessage toSend = page("screenshot").param("type", "png");
        if (locator != null) {
            toSend.param("clip", position(locator));
        }
        PlaywrightMessage pwm = toSend.send();
        String data = pwm.getResult("binary");
        byte[] bytes = Base64.getDecoder().decode(data);
        if (embed) {
            options.embedPngImage(bytes);
        }
        return bytes;
    }

    @Override
    public byte[] pdf(Map<String, Object> options) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

}
