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

import com.intuit.karate.JsonUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketOptions;
import com.intuit.karate.shell.CommandThread;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public abstract class DevToolsDriver implements Driver {

    protected final DriverOptions options;
    protected final CommandThread command;
    protected final WebSocketClient client;

    private final WaitState waitState;
    protected final String rootFrameId;

    private Integer windowId;
    private String windowState;
    private String frameId;

    private final Map<String, String> frameUrlIdMap = new LinkedHashMap();
    private final Map<String, Integer> frameContextMap = new LinkedHashMap();

    protected String currentUrl;
    protected String currentDialogText;
    private int nextId;

    public int getNextId() {
        return ++nextId;
    }

    // mutable
    protected Logger logger;

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
        client.setLogger(logger);
    }

    protected DevToolsDriver(DriverOptions options, CommandThread command, String webSocketUrl) {
        logger = options.driverLogger;
        this.options = options;
        this.command = command;
        this.waitState = new WaitState(options);
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
            Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
            DevToolsMessage dtm = new DevToolsMessage(this, map);
            receive(dtm);
        });
        client = new WebSocketClient(wsOptions, logger);
    }

    public int waitSync() {
        return command.waitSync();
    }

    public DevToolsMessage method(String method) {
        return new DevToolsMessage(this, method);
    }

    public DevToolsMessage sendAndWait(String text) {
        Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
        DevToolsMessage dtm = new DevToolsMessage(this, map);
        if (dtm.getId() == null) {
            dtm.setId(getNextId());
        }
        return sendAndWait(dtm, null);
    }

    public DevToolsMessage sendAndWait(DevToolsMessage dtm, Predicate<DevToolsMessage> condition) {
        String json = JsonUtils.toJson(dtm.toMap());
        logger.debug(">> {}", json);
        client.send(json);
        return waitState.waitAfterSend(dtm, condition);
    }

    public void receive(DevToolsMessage dtm) {
        waitState.receive(dtm);
        if (dtm.isMethod("Page.javascriptDialogOpening")) {
            currentDialogText = dtm.getParam("message").getAsString();
        }
        if (dtm.isMethod("Page.frameNavigated")) {
            String frameNavId = dtm.get("frame.id", String.class);
            String frameNavUrl = dtm.get("frame.url", String.class);
            if (rootFrameId.equals(frameNavId)) { // root page navigated
                currentUrl = frameNavUrl;
            } else { // important: exclude root from frame lookup we maintain
                frameUrlIdMap.put(frameNavUrl, frameNavId);
            }
        }
        if (dtm.isMethod("Page.windowOpen")) {
            currentUrl = dtm.getParam("url").getAsString();
        }
        if (dtm.isMethod("Page.frameStartedLoading")) {
            String frameLoadingId = dtm.get("frameId", String.class);
            if (rootFrameId.equals(frameLoadingId)) { // root page is loading
                frameUrlIdMap.clear();
            }
        }
        if (dtm.isMethod("Runtime.executionContextCreated")) {
            String contextFrameId = dtm.get("context.auxData.frameId", String.class);
            Integer contextId = dtm.get("context.id", Integer.class);
            frameContextMap.put(contextFrameId, contextId);
        }
    }

    //==========================================================================
    //
    private Integer getContextId() {
        if (frameId == null) {
            return null;
        }
        return frameContextMap.get(frameId);
    }

    protected DevToolsMessage evaluate(String expression, Predicate<DevToolsMessage> condition) {
        int count = 0;
        DevToolsMessage dtm;
        Integer contextId = getContextId();
        do {
            if (count > 0) {
                logger.debug("evaluate attempt #{}", count + 1);
            }
            DevToolsMessage toSend = method("Runtime.evaluate").param("expression", expression);
            if (contextId != null) {
                toSend.param("contextId", contextId);
            }
            dtm = toSend.send(condition);
            condition = null; // retries don't care about user-condition, e.g. page on-load
        } while (dtm != null && dtm.isResultError() && count++ < 3);
        return dtm;
    }

    protected DevToolsMessage evaluateAndGetResult(String expression, Predicate<DevToolsMessage> condition) {
        DevToolsMessage dtm = evaluate(expression, condition);
        String objectId = dtm.getResult("objectId").getAsString();
        return method("Runtime.getProperties").param("objectId", objectId).param("accessorPropertiesOnly", true).send();
    }

    protected void waitIfNeeded(String name) {
        if (options.isAlwaysWait()) {
            wait(name);
        }
    }

    protected int getRootNodeId() {
        return method("DOM.getDocument").param("depth", 0).send().getResult("root.nodeId", Integer.class);
    }

    @Override
    public Object get(String locator) {
        DevToolsMessage dtm = method("DOM.querySelector")
                .param("nodeId", getRootNodeId())
                .param("selector", locator).send();
        if (dtm.isResultError()) {
            return null;
        }
        return dtm.getResult("nodeId").getAsInt();
    }

    @Override
    public List getAll(String locator) {
        DevToolsMessage dtm = method("DOM.querySelectorAll")
                .param("nodeId", getRootNodeId())
                .param("selector", locator).send();
        if (dtm.isResultError()) {
            return Collections.EMPTY_LIST;
        }
        return dtm.getResult("nodeIds").getAsList();
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    @Override
    public void activate() {
        method("Target.activateTarget").param("targetId", rootFrameId).send();
    }

    protected void initWindowIdAndState() {
        DevToolsMessage dtm = method("Browser.getWindowForTarget").param("targetId", rootFrameId).send();
        windowId = dtm.getResult("windowId").getValue(Integer.class);
        windowState = (String) dtm.getResult("bounds").getAsMap().get("windowState");
    }

    @Override
    public Map<String, Object> getDimensions() {
        DevToolsMessage dtm = method("Browser.getWindowForTarget").param("targetId", rootFrameId).send();
        return dtm.getResult("bounds").getAsMap();
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        Map temp = getDimensions();
        temp.putAll(map);
        temp.remove("windowState");
        method("Browser.setWindowBounds")
                .param("windowId", windowId)
                .param("bounds", temp).send();
    }

    @Override
    public void close() {
        method("Page.close").send();
    }

    @Override
    public void quit() {
        if (!options.headless) {
            method("Browser.close").send(WaitState.INSPECTOR_DETACHED);
        }
        if (command != null) {
            command.close();
        }
    }

    @Override
    public void setLocation(String url) {
        method("Page.navigate").param("url", url).send(WaitState.rootFrameStoppedLoading());
    }

    @Override
    public void refresh() {
        method("Page.reload").send(WaitState.rootFrameStoppedLoading());
    }

    @Override
    public void reload() {
        method("Page.reload").param("ignoreCache", true).send();
    }

    private void history(int delta) {
        DevToolsMessage dtm = method("Page.getNavigationHistory").send();
        int currentIndex = dtm.getResult("currentIndex").getValue(Integer.class);
        List<Map> list = dtm.getResult("entries").getValue(List.class);
        int targetIndex = currentIndex + delta;
        if (targetIndex < 0 || targetIndex == list.size()) {
            return;
        }
        Map<String, Object> entry = list.get(targetIndex);
        Integer id = (Integer) entry.get("id");
        method("Page.navigateToHistoryEntry").param("entryId", id).send(WaitState.rootFrameStoppedLoading());
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
    public void click(String id) {
        click(id, false);
    }

    @Override
    public void click(String id, boolean waitForDialog) {
        waitIfNeeded(id);
        evaluate(options.elementSelector(id) + ".click()", waitForDialog ? WaitState.DIALOG_OPENING : null);
    }

    @Override
    public void select(String id, String text) {
        waitIfNeeded(id);
        evaluate(options.optionSelector(id, text), null);
    }

    @Override
    public void select(String id, int index) {
        waitIfNeeded(id);
        evaluate(options.optionSelector(id, index), null);
    }

    @Override
    public void submit(String id) {
        waitIfNeeded(id);
        evaluate(options.elementSelector(id) + ".click()", WaitState.rootFrameStoppedLoading());
    }

    @Override
    public void focus(String id) {
        waitIfNeeded(id);
        evaluate(options.elementSelector(id) + ".focus()", null);
    }

    @Override
    public void clear(String id) {
        evaluate(options.elementSelector(id) + ".value = ''", null);
    }

    @Override
    public void input(String id, String value) {
        waitIfNeeded(id);
        // focus
        evaluate(options.elementSelector(id) + ".focus()", null);
        for (char c : value.toCharArray()) {
            method("Input.dispatchKeyEvent").param("type", "keyDown").param("text", c + "").send();
        }
    }

    @Override
    public String text(String id) {
        return property(id, "textContent");
    }

    private String callFunction(int nodeId, String function) {
        DevToolsMessage dtm = method("DOM.resolveNode").param("nodeId", nodeId).send();
        String objectId = dtm.getResult("object.objectId", String.class);
        dtm = method("Runtime.callFunctionOn")
                .param("objectId", objectId)
                .param("functionDeclaration", function)
                .send();
        return dtm.getResult().getAsString();
    }

    @Override
    public List<String> texts(String locator) {
        List<Integer> ids = getAll(locator);
        List<String> list = new ArrayList(ids.size());
        for (int id : ids) {
            String text = callFunction(id, "function(){ return this.textContent }");
            list.add(text);
        }
        return list;
    }

    @Override
    public String html(String id) {
        return property(id, "outerHTML");
    }

    @Override
    public List<String> htmls(String locator) {
        List<Integer> ids = getAll(locator);
        List<String> list = new ArrayList(ids.size());
        for (int id : ids) {
            DevToolsMessage dtm = method("DOM.getOuterHTML").param("nodeId", id).send();
            list.add(dtm.getResult().getAsString());
        }
        return list;
    }

    @Override
    public String value(String locator) {
        return property(locator, "value");
    }

    @Override
    public List<String> values(String locator) {
        List<Integer> ids = getAll(locator);
        List<String> list = new ArrayList(ids.size());
        for (int id : ids) {
            String value = callFunction(id, "function(){ return this.value }");
            list.add(value);
        }
        return list;
    }

    @Override
    public void value(String id, String value) {
        waitIfNeeded(id);
        evaluate(options.elementSelector(id) + ".value = '" + value + "'", null);
    }

    @Override
    public String attribute(String id, String name) {
        waitIfNeeded(id);
        DevToolsMessage dtm = evaluate(options.elementSelector(id) + ".getAttribute('" + name + "')", null);
        return dtm.getResult().getAsString();
    }

    @Override
    public String property(String id, String name) {
        waitIfNeeded(id);
        DevToolsMessage dtm = evaluate(options.elementSelector(id) + "['" + name + "']", null);
        return dtm.getResult().getAsString();
    }

    @Override
    public String css(String id, String name) {
        waitIfNeeded(id);
        DevToolsMessage dtm = evaluate("getComputedStyle(" + options.elementSelector(id) + ")['" + name + "']", null);
        return dtm.getResult().getAsString();
    }

    @Override
    public String name(String id) {
        return property(id, "tagName");
    }

    @Override
    public Map<String, Object> rect(String id) {
        waitIfNeeded(id);
        DevToolsMessage dtm = evaluateAndGetResult(options.elementSelector(id) + ".getBoundingClientRect()", null);
        return options.newMapWithSelectedKeys(dtm.getResult().getAsMap(), "x", "y", "width", "height");
    }

    @Override
    public boolean enabled(String id) {
        waitIfNeeded(id);
        DevToolsMessage dtm = evaluate(options.elementSelector(id) + ".disabled", null);
        return !dtm.getResult().isBooleanTrue();
    }

    @Override
    public boolean waitUntil(String expression) {
        int max = options.getRetryCount();
        int count = 0;
        ScriptValue sv;
        do {
            options.sleep();
            logger.debug("poll try #{}", count + 1);
            DevToolsMessage dtm = evaluate(expression, null);
            sv = dtm.getResult();
        } while (!sv.isBooleanTrue() && count++ < max);
        return sv.isBooleanTrue();
    }

    @Override
    public Object eval(String expression) {
        return evaluate(expression, null).getResult().getValue();
    }

    @Override
    public String getTitle() {
        DevToolsMessage dtm = evaluate("document.title", null);
        return dtm.getResult().getAsString();
    }

    @Override
    public String getLocation() {
        return currentUrl;
    }

    @Override
    public List<Map> getCookies() {
        DevToolsMessage dtm = method("Network.getAllCookies").send();
        return dtm.getResult("cookies").getAsList();
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
    public void setCookie(Map<String, Object> cookie) {
        if (cookie.get("url") == null && cookie.get("domain") == null) {
            cookie = new HashMap(cookie); // don't mutate test
            cookie.put("url", currentUrl);
        }
        method("Network.setCookie").params(cookie).send();
    }

    @Override
    public void deleteCookie(String name) {
        method("Network.deleteCookies").param("name", name).param("url", currentUrl).send();
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
    public String getDialog() {
        return currentDialogText;
    }

    public byte[] pdf(Map<String, Object> options) {
        DevToolsMessage dtm = method("Page.printToPDF").params(options).send();
        String temp = dtm.getResult("data").getAsString();
        return Base64.getDecoder().decode(temp);
    }

    @Override
    public byte[] screenshot() {
        return screenshot(null);
    }

    @Override
    public byte[] screenshot(String id) {
        DevToolsMessage dtm;
        if (id == null) {
            dtm = method("Page.captureScreenshot").send();
        } else {
            Map<String, Object> map = rect(id);
            map.put("scale", 1);
            dtm = method("Page.captureScreenshot").param("clip", map).send();
        }
        String temp = dtm.getResult("data").getAsString();
        return Base64.getDecoder().decode(temp);
    }

    public byte[] screenshot(boolean fullPage) {
        if (fullPage) {
            DevToolsMessage layout = method("Page.getLayoutMetrics").send();
            Map<String, Object> size = layout.getResult("contentSize").getAsMap();
            Map<String, Object> map = options.newMapWithSelectedKeys(size, "height", "width");
            map.put("x", 0);
            map.put("y", 0);
            map.put("scale", 1);
            DevToolsMessage dtm = method("Page.captureScreenshot").param("clip", map).send();
            String temp = dtm.getResult("data").getAsString();
            return Base64.getDecoder().decode(temp);
        } else {
            return screenshot();
        }
    }

    @Override
    public void highlight(String id) {
        eval(options.highlighter(id));
    }

    @Override
    public void switchPage(String titleOrUrl) {
        if (titleOrUrl == null) {
            return;
        }
        titleOrUrl = options.removeProtocol(titleOrUrl);
        DevToolsMessage dtm = method("Target.getTargets").send();
        List<Map> targets = dtm.getResult("targetInfos").getAsList();
        String targetId = null;
        String targetUrl = null;
        for (Map map : targets) {
            String title = (String) map.get("title");
            String url = (String) map.get("url");
            String trimmed = options.removeProtocol(url);
            if (titleOrUrl.equals(title) || titleOrUrl.equals(trimmed)) {
                targetId = (String) map.get("targetId");
                targetUrl = url;
                break;
            }
        }
        if (targetId != null) {
            method("Target.activateTarget").param("targetId", targetId).send();
            currentUrl = targetUrl;
        }
    }

    @Override
    public void switchFrame(int index) {
        if (index == -1) {
            frameId = null;
            return;
        }
        if (index < frameUrlIdMap.size()) {
            List<String> frameIds = new ArrayList(frameUrlIdMap.values());
            frameId = frameIds.get(index);
        }
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) {
            frameId = null;
            return;
        }
        waitIfNeeded(locator);
        DevToolsMessage dtm = evaluate(options.elementSelector(locator) + ".contentWindow.location.href", null);
        String url = dtm.getResult().getAsString();
        if (url == null) {
            return;
        }
        frameId = frameUrlIdMap.get(url);
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

}
