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

import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.DriverElement;

import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.Input;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.http.WebSocketClient;
import com.intuit.karate.http.WebSocketOptions;
import com.intuit.karate.shell.Command;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    public static final String DRIVER_TYPE = "playwright";

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

    public static PlaywrightDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        DriverOptions options = new DriverOptions(map, sr, 4444, "playwright");
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
            Map<String, Object> map = Json.of(text).value();
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
    private final Map<String, Frame> frameInfo = new HashMap();

    private PlaywrightMessage page(String method) {
        return method(method, currentPage);
    }

    private PlaywrightMessage frame(String method) {
        return method(method, currentFrame);
    }

    private static class Frame {

        final String frameGuid;
        final String url;
        final String name;

        Frame(String frameGuid, String url, String name) {
            this.frameGuid = frameGuid;
            this.url = url;
            this.name = name;
        }

    }

    public void receive(PlaywrightMessage pwm) {
        if (pwm.methodIs("frameAttached")) {
            String pageGuid = pwm.getGuid();
            String frameGuid = pwm.getParam("frame.guid");
            Set<String> frames = pageFrames.get(pageGuid);
            if (frames == null) {
                frames = new LinkedHashSet(); // order important !!
                pageFrames.put(pageGuid, frames);
            }
            frames.add(frameGuid);
        } else if (pwm.methodIs("frameDetached")) {
            String pageGuid = pwm.getGuid();
            String frameGuid = pwm.getParam("frame.guid");
            frameInfo.remove(frameGuid);
            Set<String> frames = pageFrames.get(pageGuid);
            frames.remove(frameGuid);
        } else if (pwm.methodIs("navigated")) {
            String frameGuid = pwm.getGuid();
            String url = pwm.getParam("url");
            String name = pwm.getParam("name");
            frameInfo.put(frameGuid, new Frame(frameGuid, url, name));
        } else if (pwm.methodIs("__create__")) {
            if (pwm.paramHas("type", "Page")) {
                String pageGuid = pwm.getParam("guid");
                String frameGuid = pwm.getParam("initializer.mainFrame.guid");
                Set<String> frames = pageFrames.get(pageGuid);
                if (frames == null) {
                    frames = new LinkedHashSet(); // order important !!
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
            } else if (pwm.paramHas("type", "Browser")) {
                browserGuid = pwm.getParam("guid");
                Map<String, Object> map = new HashMap();
                map.put("sdkLanguage", "javascript");
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
        boolean wasSubmit = submit;
        if (condition == null && submit) {
            submit = false;
            condition = PlaywrightWait.DOM_CONTENT_LOADED;
        }
        // do stuff inside wait to avoid missing messages
        PlaywrightMessage result = wait.send(pwm, condition);
        if (result == null && !wasSubmit) {
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

    private static final Map<String, Object> NO_ARGS = Json.of("{ value: { v: 'undefined' }, handles: [] }").value();

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
        return position(locator, false);
    }

    @Override
    public Map<String, Object> position(String locator, boolean relative) {
        boolean submitTemp = submit; // in case we are prepping for a submit().mouse(locator).click()
        submit = false;
        retryIfEnabled(locator);
        Map<String, Object> map = eval(relative ? DriverOptions.getRelativePositionJs(locator) : DriverOptions.getPositionJs(locator)).getResultValue();
        submit = submitTemp;
        return map;
    }

    private PlaywrightMessage evalFrame(String frameGuid, String expression) {
        return method("evaluateExpression", frameGuid)
                .param("expression", expression)
                .param("isFunction", false)
                .param("arg", NO_ARGS).send();
    }

    @Override
    public void switchPage(String titleOrUrl) {
        if (titleOrUrl == null) {
            return;
        }
        for (String pageGuid : pageFrames.keySet()) {
            String frameGuid = pageFrames.get(pageGuid).iterator().next();
            String title = evalFrame(frameGuid, "document.title").getResultValue();
            if (title != null && title.contains(titleOrUrl)) {
                currentPage = pageGuid;
                currentFrame = frameGuid;
                activate();
                return;
            }
            String url = evalFrame(frameGuid, "document.location.href").getResultValue();
            if (url != null && url.contains(titleOrUrl)) {
                currentPage = pageGuid;
                currentFrame = frameGuid;
                activate();
                return;
            }
        }
        logger.warn("failed to find page by title / url: {}", titleOrUrl);
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

    private void waitForFrame(String previousFrame) {
        String previousFrameUrl = frameInfo.get(previousFrame).url;
        logger.debug("waiting for frame url to switch from: {} - {}", previousFrame, previousFrameUrl);
        Integer retryInterval = options.getRetryInterval();
        options.setRetryInterval(1000); // reduce retry interval for this special case
        options.retry(() -> evalFrame(currentFrame, "document.location.href"),
                pwm -> !pwm.isError() && !pwm.getResultValue().equals(previousFrameUrl), "waiting for frame context", false);
        options.setRetryInterval(retryInterval); // restore
    }

    @Override
    public void switchFrame(int index) {
        String previousFrame = currentFrame;
        List<String> temp = new ArrayList(pageFrames.get(currentPage));
        index = index + 1; // the root frame is always zero, api here is consistent with webdriver etc
        if (index < temp.size()) {
            currentFrame = temp.get(index);
            logger.debug("switched to frame: {} - pages: {}", currentFrame, pageFrames);
            waitForFrame(previousFrame);
        } else {
            logger.warn("not switching frame for size {}: {}", temp.size(), index);
        }
    }

    @Override
    public void switchFrame(String locator) {
        String previousFrame = currentFrame;
        if (locator == null) {
            switchFrame(-1);
        } else {
            if (locator.startsWith("#")) { // TODO get reference to frame element via locator
                locator = locator.substring(1);
            }
            for (Frame frame : frameInfo.values()) {
                if (frame.url.contains(locator) || frame.name.contains(locator)) {
                    currentFrame = frame.frameGuid;
                    logger.debug("switched to frame: {} - pages: {}", currentFrame, pageFrames);
                    waitForFrame(previousFrame);
                    return;
                }
            }
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
    public String getDialogText() {
        return currentDialogText;
    }

    @Override
    public byte[] screenshot(boolean embed) {
        return screenshot(null, embed);
    }

    @Override
    public Map<String, Object> cookie(String name) {
        List<Map> list = getCookies();
        if (list == null) {
            return null;
        }
        for (Map<String, Object> map : list) {
            if (map != null && name.equals(map.get("name"))) {
                return map;
            }
        }
        return null;
    }

    @Override
    public void cookie(Map<String, Object> cookie) {
        if (cookie.get("url") == null && cookie.get("domain") == null) {
            cookie = new HashMap(cookie); // don't mutate test
            cookie.put("url", getUrl());
        }
        method("addCookies", browserContextGuid).param("cookies", Collections.singletonList(cookie)).send();
    }

    @Override
    public void deleteCookie(String name) {
        List<Map> cookies = getCookies();
        List<Map> filtered = new ArrayList(cookies.size());
        for (Map m : cookies) {
            if (!name.equals(m.get("name"))) {
                filtered.add(m);
            }
        }
        clearCookies();
        method("addCookies", browserContextGuid).param("cookies", filtered).send();
    }

    @Override
    public void clearCookies() {
        method("clearCookies", browserContextGuid).send();
    }

    @Override
    public List<Map> getCookies() {
        return method("cookies", browserContextGuid).param("urls", Collections.EMPTY_LIST).send().getResult("cookies", List.class);
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
        retryIfEnabled(locator);
        // focus
        eval(options.focusJs(locator));
        Input input = new Input(value);
        Set<String> pressed = new HashSet();
        while (input.hasNext()) {
            char c = input.next();
            String keyValue = Keys.keyValue(c);
            if (keyValue != null) {
                if (Keys.isModifier(c)) {
                    pressed.add(keyValue);
                    page("keyboardDown").param("key", keyValue).send();
                } else {
                    page("keyboardPress").param("key", keyValue).send();
                }
            } else {
                page("keyboardType").param("text", c + "").send();
            }
        }
        for (String keyValue : pressed) {
            page("keyboardUp").param("key", keyValue).send();
        }
        return DriverElement.locatorExists(this, locator);
    }

    protected int currentMouseXpos;
    protected int currentMouseYpos;

    @Override
    public void actions(List<Map<String, Object>> sequence) {
        boolean submitRequested = submit;
        submit = false; // make sure only LAST action is handled as a submit()
        for (Map<String, Object> map : sequence) {
            List<Map<String, Object>> actions = (List) map.get("actions");
            if (actions == null) {
                logger.warn("no actions property found: {}", sequence);
                return;
            }
            Iterator<Map<String, Object>> iterator = actions.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> action = iterator.next();
                String type = (String) action.get("type");
                if (type == null) {
                    logger.warn("no type property found: {}", action);
                    continue;
                }
                String pageAction;
                switch (type) {
                    case "pointerMove":
                        pageAction = "mouseMove";
                        break;
                    case "pointerDown":
                        pageAction = "mouseDown";
                        break;
                    case "pointerUp":
                        pageAction = "mouseUp";
                        break;
                    default:
                        logger.warn("unexpected action type: {}", action);
                        continue;

                }
                Integer x = (Integer) action.get("x");
                Integer y = (Integer) action.get("y");
                if (x != null) {
                    currentMouseXpos = x;
                }
                if (y != null) {
                    currentMouseYpos = y;
                }
                Integer duration = (Integer) action.get("duration");
                PlaywrightMessage toSend = page(pageAction);
                if ("mouseMove".equals(pageAction) && x != null && y != null) {
                    toSend.param("x", x).param("y", y);
                } else {
                    toSend.params(Collections.EMPTY_MAP);
                }
                if (!iterator.hasNext() && submitRequested) {
                    submit = true;
                }
                toSend.send();
                if (duration != null) {
                    options.sleep(duration);
                }
            }
        }
    }

    @Override
    public Element select(String locator, String text) {
        retryIfEnabled(locator);
        eval(options.optionSelector(locator, text));
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public Element select(String locator, int index) {
        retryIfEnabled(locator);
        eval(options.optionSelector(locator, index));
        return DriverElement.locatorExists(this, locator);
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
            getRuntime().embed(bytes, ResourceType.PNG);
        }
        return bytes;
    }

    @Override
    public byte[] pdf(Map<String, Object> options) {
        if (options == null) {
            options = Collections.EMPTY_MAP;
        }
        PlaywrightMessage pwm = page("pdf").params(options).send();
        String temp = pwm.getResult("pdf");
        return Base64.getDecoder().decode(temp);
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

}
