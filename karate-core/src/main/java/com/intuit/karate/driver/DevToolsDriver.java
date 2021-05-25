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

import com.intuit.karate.Constants;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.MockHandler;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.Variable;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.http.Response;
import com.intuit.karate.http.WebSocketClient;
import com.intuit.karate.http.WebSocketOptions;
import com.intuit.karate.shell.Command;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public abstract class DevToolsDriver implements Driver {

    protected final DriverOptions options;
    protected final Command command;
    protected final WebSocketClient client;
    private boolean terminated;

    private final DevToolsWait wait;
    protected final String rootFrameId;

    private Integer windowId;
    private String windowState;
    protected String sessionId;

    // iframe support
    private Frame frame;
    private final Map<String, Integer> frameContexts = new HashMap();

    protected boolean domContentEventFired;
    protected final Set<String> framesStillLoading = new HashSet();
    private boolean submit;

    protected String currentDialogText;

    private int nextId;

    public int nextId() {
        return ++nextId;
    }

    private MockHandler mockHandler;

    protected final Logger logger;

    protected DevToolsDriver(DriverOptions options, Command command, String webSocketUrl) {
        logger = options.driverLogger;
        this.options = options;
        this.command = command;

        if (options.isRemoteHost()) {
            String host = options.host;
            Integer port = options.port;
            webSocketUrl = webSocketUrl.replace("ws://localhost/", "ws://" + host + ":" + port + "/");
        }

        this.wait = new DevToolsWait(this, options);
        int pos = webSocketUrl.lastIndexOf('/');
        rootFrameId = webSocketUrl.substring(pos + 1);
        logger.debug("root frame id: {}", rootFrameId);
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
            DevToolsMessage dtm = new DevToolsMessage(this, map);
            receive(dtm);
        });
        client = new WebSocketClient(wsOptions, logger);
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

    public DevToolsMessage method(String method) {
        return new DevToolsMessage(this, method);
    }

    // this can be used for exploring / sending any raw message !
    public Map<String, Object> send(Map<String, Object> map) {
        DevToolsMessage dtm = new DevToolsMessage(this, map);
        dtm.setId(nextId());
        return sendAndWait(dtm, null).toMap();
    }

    public void send(DevToolsMessage dtm) {
        String json = JsonUtils.toJson(dtm.toMap());
        logger.debug(">> {}", json);
        client.send(json);
    }

    public DevToolsMessage sendAndWait(DevToolsMessage dtm, Predicate<DevToolsMessage> condition) {
        boolean wasSubmit = submit;
        if (condition == null && submit) {
            submit = false;
            condition = DevToolsWait.ALL_FRAMES_LOADED;
        }
        // do stuff inside wait to avoid missing messages
        DevToolsMessage result = wait.send(dtm, condition);
        if (result == null && !wasSubmit) {
            throw new RuntimeException("failed to get reply for: " + dtm);
        }
        return result;
    }

    public void receive(DevToolsMessage dtm) {
        if (dtm.methodIs("Page.domContentEventFired")) {
            domContentEventFired = true;
            logger.trace("** set dom ready flag to true");
        }
        if (dtm.methodIs("Page.javascriptDialogOpening")) {
            currentDialogText = dtm.getParam("message");
            // this will stop waiting NOW
            wait.setCondition(DevToolsWait.DIALOG_OPENING);
        }
        if (dtm.methodIs("Page.frameStartedLoading")) {
            String frameLoadingId = dtm.getParam("frameId");
            if (rootFrameId.equals(frameLoadingId)) { // root page is loading
                domContentEventFired = false;
                framesStillLoading.clear();
                logger.trace("** root frame started loading, cleared all page state: {}", frameLoadingId);
            } else {
                framesStillLoading.add(frameLoadingId);
                logger.trace("** frame started loading, added to in-progress list: {}", framesStillLoading);
            }
        }
        if (dtm.methodIs("Page.frameStoppedLoading")) {
            String frameLoadedId = dtm.getParam("frameId");
            framesStillLoading.remove(frameLoadedId);
            logger.trace("** frame stopped loading: {}, remaining in-progress: {}", frameLoadedId, framesStillLoading);
        }
        if (dtm.methodIs("Page.frameNavigated")) {
            Frame newFrame = new Frame(dtm.getParam("frame.id"), dtm.getParam("frame.url"), dtm.getParam("frame.name"));
            logger.trace("** detected new frame: {}", newFrame);
            if (frame != null && (frame.name.equals(newFrame.name) || frame.url.equals(newFrame.url))) {
                logger.trace("** auto switching frame: {} -> {}", frame, newFrame);
                frame = newFrame;
            }
        }
        if (dtm.methodIs("Runtime.executionContextCreated")) {
            String newFrameId = dtm.getParam("context.auxData.frameId");
            Integer contextId = dtm.getParam("context.id");
            frameContexts.put(newFrameId, contextId);
            logger.trace("** new frame execution context: {} - {}", newFrameId, contextId);
        }
        if (dtm.methodIs("Runtime.executionContextsCleared")) {
            frame = null;
            frameContexts.clear();
            framesStillLoading.clear();
        }
        if (dtm.methodIs("Runtime.consoleAPICalled") && options.showBrowserLog) {
            List<String> values = dtm.getParam("args[*].value");
            for (String value : values) {
                logger.debug("[console] {}", value);
            }
        }
        if (dtm.methodIs("Fetch.requestPaused")) {
            handleInterceptedRequest(dtm);
        }
        // all needed state is set above before we get into conditional checks
        wait.receive(dtm);
    }

    private void handleInterceptedRequest(DevToolsMessage dtm) {
        String requestId = dtm.getParam("requestId");
        String requestUrl = dtm.getParam("request.url");
        if (mockHandler != null) {
            String method = dtm.getParam("request.method");
            Map<String, String> headers = dtm.getParam("request.headers");
            String postData = dtm.getParam("request.postData");
            logger.debug("intercepting request for url: {}", requestUrl);
            HttpRequest request = new HttpRequest();
            request.setUrl(requestUrl);
            request.setMethod(method);
            headers.forEach((k, v) -> request.putHeader(k, v));
            if (postData != null) {
                request.setBody(FileUtils.toBytes(postData));
            } else {
                request.setBody(Constants.ZERO_BYTES);
            }
            Response response = mockHandler.handle(request.toRequest());
            String responseBody = response.getBody() == null ? "" : Base64.getEncoder().encodeToString(response.getBody());
            List<Map> responseHeaders = new ArrayList();
            Map<String, List<String>> map = response.getHeaders();
            if (map != null) {
                map.forEach((k, v) -> {
                    if (v != null && !v.isEmpty()) {
                        Map<String, Object> nv = new HashMap(2);
                        nv.put("name", k);
                        nv.put("value", v.get(0));
                        responseHeaders.add(nv);
                    }
                });
            }
            method("Fetch.fulfillRequest")
                    .param("requestId", requestId)
                    .param("responseCode", response.getStatus())
                    .param("responseHeaders", responseHeaders)
                    .param("body", responseBody).sendWithoutWaiting();
        } else {
            logger.warn("no mock server, continuing paused request to url: {}", requestUrl);
            method("Fetch.continueRequest").param("requestId", requestId).sendWithoutWaiting();
        }
    }

    //==========================================================================
    //
    private DevToolsMessage evalOnce(String expression, boolean quickly, boolean fireAndForget, boolean returnByValue) {
        DevToolsMessage toSend = method("Runtime.evaluate")
                .param("expression", expression);
        if (returnByValue) {
            toSend.param("returnByValue", true);
        }
        Integer contextId = getFrameContext();
        if (contextId != null) {
            toSend.param("contextId", contextId);
        }
        if (quickly) {
            toSend.setTimeout(options.getRetryInterval());
        }
        if (fireAndForget) {
            toSend.sendWithoutWaiting();
            return null;
        }
        return toSend.send();
    }

    protected DevToolsMessage eval(String expression) {
        return evalInternal(expression, false, true);
    }

    protected DevToolsMessage evalQuickly(String expression) {
        return evalInternal(expression, true, true);
    }

    protected String evalForObjectId(String expression) {
        return options.retry(() -> {
            DevToolsMessage dtm = evalInternal(expression, true, false);
            return dtm.getResult("objectId", String.class);
        }, returned -> returned != null, "eval for object id: " + expression, true);
    }

    private DevToolsMessage evalInternal(String expression, boolean quickly, boolean returnByValue) {
        DevToolsMessage dtm = evalOnce(expression, quickly, false, returnByValue);
        if (dtm.isResultError()) {
            String message = "js eval failed once:" + expression
                    + ", error: " + dtm.getResult();
            logger.warn(message);
            options.sleep();
            dtm = evalOnce(expression, quickly, false, returnByValue); // no wait condition for the re-try
            if (dtm.isResultError()) {
                message = "js eval failed twice:" + expression
                        + ", error: " + dtm.getResult();
                logger.error(message);
                throw new RuntimeException(message);
            }
        }
        return dtm;
    }

    protected void retryIfEnabled(String locator) {
        if (options.isRetryEnabled()) {
            waitFor(locator); // will throw exception if not found
        }
        if (options.highlight) {
            // highlight(locator, options.highlightDuration); // instead of this
            String highlightJs = options.highlight(locator, options.highlightDuration);
            evalOnce(highlightJs, true, true, true); // do it safely, i.e. fire and forget
        }
    }

    protected int getRootNodeId() {
        return method("DOM.getDocument").param("depth", 0).send().getResult("root.nodeId", Integer.class);
    }

    @Override
    public String elementId(String locator) {
        return evalForObjectId(DriverOptions.selector(locator));
    }

    @Override
    public List elementIds(String locator) {
        List<Element> elements = locateAll(locator);
        List<String> objectIds = new ArrayList(elements.size());
        for (Element e : elements) {
            String objectId = evalForObjectId(e.getLocator());
            objectIds.add(objectId);
        }
        return objectIds;
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    private void attachAndActivate(String targetId) {
        DevToolsMessage dtm = method("Target.attachToTarget").param("targetId", targetId).param("flatten", true).send();
        sessionId = dtm.getResult("sessionId", String.class);
        method("Target.activateTarget").param("targetId", targetId).send();
    }

    @Override
    public void activate() {
        attachAndActivate(rootFrameId);
    }

    protected void initWindowIdAndState() {
        DevToolsMessage dtm = method("Browser.getWindowForTarget").param("targetId", rootFrameId).send();
        if (!dtm.isResultError()) {
            windowId = dtm.getResult("windowId").getValue();
            windowState = (String) dtm.getResult("bounds").<Map>getValue().get("windowState");
        }
    }

    @Override
    public Map<String, Object> getDimensions() {
        DevToolsMessage dtm = method("Browser.getWindowForTarget").param("targetId", rootFrameId).send();
        Map<String, Object> map = dtm.getResult("bounds").getValue();
        Integer x = (Integer) map.remove("left");
        Integer y = (Integer) map.remove("top");
        map.put("x", x);
        map.put("y", y);
        return map;
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        Integer left = (Integer) map.remove("x");
        Integer top = (Integer) map.remove("y");
        map.put("left", left);
        map.put("top", top);
        Map temp = getDimensions();
        temp.putAll(map);
        temp.remove("windowState");
        method("Browser.setWindowBounds")
                .param("windowId", windowId)
                .param("bounds", temp).send();
    }

    public void emulateDevice(int width, int height, String userAgent) {
        method("Network.setUserAgentOverride").param("userAgent", userAgent).send();
        method("Emulation.setDeviceMetricsOverride")
                .param("width", width)
                .param("height", height)
                .param("deviceScaleFactor", 1)
                .param("mobile", true)
                .send();
    }

    @Override
    public void close() {
        method("Page.close").sendWithoutWaiting();
    }

    @Override
    public void quit() {
        if (terminated) {
            return;
        }
        terminated = true;
        // don't wait, may fail and hang
        method("Target.closeTarget").param("targetId", rootFrameId).sendWithoutWaiting();
        // method("Browser.close").send();
        client.close();
        if (command != null) {
            command.close(true);
        }
        getRuntime().engine.setDriverToNull();
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public void setUrl(String url) {
        method("Page.navigate").param("url", url)
                .send(DevToolsWait.ALL_FRAMES_LOADED);
    }

    @Override
    public void refresh() {
        method("Page.reload").send(DevToolsWait.ALL_FRAMES_LOADED);
    }

    @Override
    public void reload() {
        method("Page.reload").param("ignoreCache", true).send();
    }

    private void history(int delta) {
        DevToolsMessage dtm = method("Page.getNavigationHistory").send();
        int currentIndex = dtm.getResult("currentIndex").getValue();
        List<Map> list = dtm.getResult("entries").getValue();
        int targetIndex = currentIndex + delta;
        if (targetIndex < 0 || targetIndex == list.size()) {
            return;
        }
        Map<String, Object> entry = list.get(targetIndex);
        Integer id = (Integer) entry.get("id");
        method("Page.navigateToHistoryEntry").param("entryId", id).send(DevToolsWait.ALL_FRAMES_LOADED);
    }

    @Override
    public void back() {
        history(-1);
    }

    @Override
    public void forward() {
        history(1);
    }

    private void setWindowState(String state) {
        if (!"normal".equals(windowState)) {
            method("Browser.setWindowBounds")
                    .param("windowId", windowId)
                    .param("bounds", Collections.singletonMap("windowState", "normal"))
                    .send();
            windowState = "normal";
        }
        if (!state.equals(windowState)) {
            method("Browser.setWindowBounds")
                    .param("windowId", windowId)
                    .param("bounds", Collections.singletonMap("windowState", state))
                    .send();
            windowState = state;
        }
    }

    @Override
    public void maximize() {
        setWindowState("maximized");
    }

    @Override
    public void minimize() {
        setWindowState("minimized");
    }

    @Override
    public void fullscreen() {
        setWindowState("fullscreen");
    }

    @Override
    public Element click(String locator) {
        retryIfEnabled(locator);
        eval(DriverOptions.selector(locator) + ".click()");
        return DriverElement.locatorExists(this, locator);
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

    private void sendKey(char c, int modifiers, String type, Integer keyCode) {
        DevToolsMessage dtm = method("Input.dispatchKeyEvent")
                .param("modifiers", modifiers)
                .param("type", type);
        if (keyCode == null) {
            dtm.param("text", c + "");
        } else {
            switch (keyCode) {
                case 13:
                    dtm.param("text", "\r"); // important ! \n does NOT work for chrome
                    break;
                case 9: // TAB
                    if ("char".equals(type)) {
                        return; // special case
                    }
                    dtm.param("text", "");
                    break;
                case 46: // DOT
                    if ("rawKeyDown".equals(type)) {
                        dtm.param("type", "keyDown"); // special case
                    }
                    dtm.param("text", ".");
                    break;
                default:
                    dtm.param("text", c + "");
            }
            dtm.param("windowsVirtualKeyCode", keyCode);
        }
        dtm.send();
    }

    @Override
    public Element input(String locator, String value) {
        retryIfEnabled(locator);
        // focus
        eval(options.focusJs(locator));
        Input input = new Input(value);
        while (input.hasNext()) {
            char c = input.next();
            int modifiers = input.getModifierFlags();
            Integer keyCode = Keys.code(c);
            if (keyCode != null) {
                sendKey(c, modifiers, "rawKeyDown", keyCode);
                sendKey(c, modifiers, "char", keyCode);
                sendKey(c, modifiers, "keyUp", keyCode);
            } else {
                logger.warn("unknown character / key code: {}", c);
                sendKey(c, modifiers, "char", null);
            }
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
                String chromeType;
                switch (type) {
                    case "pointerMove":
                        chromeType = "mouseMoved";
                        break;
                    case "pointerDown":
                        chromeType = "mousePressed";
                        break;
                    case "pointerUp":
                        chromeType = "mouseReleased";
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
                DevToolsMessage toSend = method("Input.dispatchMouseEvent")
                        .param("type", chromeType)
                        .param("x", currentMouseXpos).param("y", currentMouseYpos);
                if ("mousePressed".equals(chromeType) || "mouseReleased".equals(chromeType)) {
                    toSend.param("button", "left").param("clickCount", 1);
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
    public String text(String id) {
        return property(id, "textContent");
    }

    @Override
    public String html(String id) {
        return property(id, "outerHTML");
    }

    @Override
    public String value(String locator) {
        return property(locator, "value");
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
        DevToolsMessage dtm = eval(DriverOptions.selector(id) + ".getAttribute('" + name + "')");
        return dtm.getResult().getAsString();
    }

    @Override
    public String property(String id, String name) {
        retryIfEnabled(id);
        DevToolsMessage dtm = eval(DriverOptions.selector(id) + "['" + name + "']");
        return dtm.getResult().getAsString();
    }

    @Override
    public boolean enabled(String id) {
        retryIfEnabled(id);
        DevToolsMessage dtm = eval(DriverOptions.selector(id) + ".disabled");
        return !dtm.getResult().isTrue();
    }

    @Override
    public boolean waitUntil(String expression) {
        return options.retry(() -> {
            try {
                return evalQuickly(expression).getResult().isTrue();
            } catch (Exception e) {
                logger.warn("waitUntil evaluate failed: {}", e.getMessage());
                return false;
            }
        }, b -> b, "waitUntil (js)", true);
    }

    @Override
    public Object script(String expression) {
        return eval(expression).getResult().getValue();
    }

    @Override
    public String getTitle() {
        return eval("document.title").getResult().getAsString();
    }

    @Override
    public String getUrl() {
        return eval("document.location.href").getResult().getAsString();
    }

    @Override
    public List<Map> getCookies() {
        DevToolsMessage dtm = method("Network.getAllCookies").send();
        return dtm.getResult("cookies").getValue();
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
        method("Network.setCookie").params(cookie).send();
    }

    @Override
    public void deleteCookie(String name) {
        method("Network.deleteCookies").param("name", name).param("url", getUrl()).send();
    }

    @Override
    public void clearCookies() {
        method("Network.clearBrowserCookies").send();
    }

    @Override
    public void dialog(boolean accept) {
        dialog(accept, null);
    }

    @Override
    public void dialog(boolean accept, String text) {
        DevToolsMessage temp = method("Page.handleJavaScriptDialog").param("accept", accept);
        if (text == null) {
            temp.send();
        } else {
            temp.param("promptText", text).send();
        }
    }

    @Override
    public String getDialogText() {
        return currentDialogText;
    }

    @Override
    public byte[] pdf(Map<String, Object> options) {
        DevToolsMessage dtm = method("Page.printToPDF").params(options).send();
        String temp = dtm.getResult("data").getAsString();
        return Base64.getDecoder().decode(temp);
    }

    @Override
    public byte[] screenshot(boolean embed) {
        return screenshot(null, embed);
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
        Map<String, Object> map = eval(relative ? DriverOptions.getRelativePositionJs(locator) : DriverOptions.getPositionJs(locator)).getResult().getValue();
        submit = submitTemp;
        return map;
    }

    @Override
    public byte[] screenshot(String id, boolean embed) {
        DevToolsMessage dtm;
        if (id == null) {
            dtm = method("Page.captureScreenshot").send();
        } else {
            Map<String, Object> map = position(id);
            map.put("scale", 1);
            dtm = method("Page.captureScreenshot").param("clip", map).send();
        }
        String temp = dtm.getResult("data").getAsString();
        byte[] bytes = Base64.getDecoder().decode(temp);
        if (embed) {
            getRuntime().embed(bytes, ResourceType.PNG);
        }
        return bytes;
    }

    // chrome only
    public byte[] screenshotFull() {
        DevToolsMessage layout = method("Page.getLayoutMetrics").send();
        Map<String, Object> size = layout.getResult("contentSize").getValue();
        Map<String, Object> map = options.newMapWithSelectedKeys(size, "height", "width");
        map.put("x", 0);
        map.put("y", 0);
        map.put("scale", 1);
        DevToolsMessage dtm = method("Page.captureScreenshot").param("clip", map).send();
        if (dtm.isResultError()) {
            logger.error("unable to capture screenshot: {}", dtm);
            return new byte[0];
        }
        String temp = dtm.getResult("data").getAsString();
        return Base64.getDecoder().decode(temp);
    }

    @Override
    public List<String> getPages() {
        DevToolsMessage dtm = method("Target.getTargets").send();
        return dtm.getResult("targetInfos.targetId").getValue();
    }

    @Override
    public void switchPage(String titleOrUrl) {
        if (titleOrUrl == null) {
            return;
        }
        String targetId = options.retry(() -> {
            DevToolsMessage dtm = method("Target.getTargets").send();
            List<Map> targets = dtm.getResult("targetInfos").getValue();
            for (Map map : targets) {
                String title = (String) map.get("title");
                String url = (String) map.get("url");
                if ((title != null && title.contains(titleOrUrl))
                        || (url != null && url.contains(titleOrUrl))) {
                    return (String) map.get("targetId");
                }
            }
            return null;
        }, returned -> returned != null, "waiting to switch to tab: " + titleOrUrl, true);
        attachAndActivate(targetId);
    }

    @Override
    public void switchPage(int index) {
        if (index == -1) {
            return;
        }
        DevToolsMessage dtm = method("Target.getTargets").send();
        List<Map> targets = dtm.getResult("targetInfos").getValue();
        if (index < targets.size()) {
            Map target = targets.get(index);
            String targetId = (String) target.get("targetId");
            attachAndActivate(targetId);
        } else {
            logger.warn("failed to switch frame by index: {}", index);
        }
    }

    @Override
    public void switchFrame(int index) {
        if (index == -1) {
            frame = null;
            return;
        }
        List<String> objectIds = elementIds("iframe,frame");
        if (index < objectIds.size()) {
            String objectId = objectIds.get(index);
            if (!setExecutionContext(objectId)) {
                logger.warn("failed to switch frame by index: {}", index);
            }
        } else {
            logger.warn("unable to switch frame by index: {}", index);
        }
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) {
            frame = null;
            return;
        }
        retryIfEnabled(locator);
        String objectId = evalForObjectId(DriverOptions.selector(locator));
        if (!setExecutionContext(objectId)) {
            logger.warn("failed to switch frame by locator: {}", locator);
        }
    }

    private Integer getFrameContext() {
        if (frame == null) {
            return null;
        }
        Integer result = frameContexts.get(frame.id);
        logger.trace("** get frame context: {} - {}", frame, result);
        return result;
    }

    private boolean setExecutionContext(String objectId) { // locator just for logging      
        DevToolsMessage dtm = method("DOM.describeNode")
                .param("objectId", objectId)
                .param("depth", 0)
                .send();
        String frameId = dtm.getResult("node.frameId", String.class);
        if (frameId == null) {
            return false;
        }
        dtm = method("Page.getFrameTree").send();
        frame = null;
        List<Map> frames = dtm.getResult("frameTree.childFrames[*].frame", List.class);
        for (Map<String, Object> map : frames) {
            String temp = (String) map.get("id");
            if (frameId.equals(temp)) {
                String frameUrl = (String) map.get("url");
                String frameName = (String) map.get("name");
                frame = new Frame(frameId, frameUrl, frameName);
                logger.trace("** switched to frame: {}", frame);
                break;
            }
        }
        if (frame == null) {
            return false;
        }
        Integer contextId = getFrameContext();
        if (contextId != null) {
            return true;
        }
        dtm = method("Page.createIsolatedWorld").param("frameId", frameId).send();
        contextId = dtm.getResult("executionContextId").getValue();
        frameContexts.put(frameId, contextId);
        return true;
    }

    public void enableNetworkEvents() {
        method("Network.enable").send();
    }

    public void enablePageEvents() {
        method("Page.enable").send();
    }

    public void enableRuntimeEvents() {
        method("Runtime.enable").send();
    }

    public void intercept(Value value) {
        Map<String, Object> config = (Map) JsValue.toJava(value);
        config = new Variable(config).getValue();
        intercept(config);
    }

    public void intercept(Map<String, Object> config) {
        List<String> patterns = (List) config.get("patterns");
        if (patterns == null) {
            throw new RuntimeException("missing array argument 'patterns': " + config);
        }
        if (mockHandler != null) {
            throw new RuntimeException("'intercept()' can be called only once");
        }
        String mock = (String) config.get("mock");
        if (mock == null) {
            throw new RuntimeException("missing argument 'mock': " + config);
        }
        Object o = getRuntime().engine.fileReader.readFile(mock);
        if (!(o instanceof Feature)) {
            throw new RuntimeException("'mock' is not a feature file: " + config + ", " + mock);
        }
        Feature feature = (Feature) o;
        mockHandler = new MockHandler(feature);
        method("Fetch.enable").param("patterns", patterns).send();
    }

    public void inputFile(String locator, String... relativePaths) {
        retryIfEnabled(locator);
        List<String> files = new ArrayList(relativePaths.length);
        ScenarioEngine engine = ScenarioEngine.get();
        for (String p : relativePaths) {
            files.add(engine.fileReader.toAbsolutePath(p));
        }
        String objectId = evalForObjectId(DriverOptions.selector(locator));
        method("DOM.setFileInputFiles").param("files", files).param("objectId", objectId).send();
    }

    public Object scriptAwait(String expression) {
        DevToolsMessage toSend = method("Runtime.evaluate")
                .param("expression", expression)
                .param("returnByValue", true)
                .param("awaitPromise", true);
        Integer contextId = getFrameContext();
        if (contextId != null) {
            toSend.param("contextId", contextId);
        }
        return toSend.send().getResult().getValue();
    }

}
