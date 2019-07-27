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
import com.intuit.karate.shell.Command;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public abstract class DevToolsDriver implements Driver {

    protected final DriverOptions options;
    protected final Command command;
    protected final WebSocketClient client;

    private final WaitState waitState;
    protected final String rootFrameId;

    private Integer windowId;
    private String windowState;
    private Integer executionContextId;
    protected String sessionId;

    protected boolean domContentEventFired;
    protected final Set<String> framesStillLoading = new HashSet();
    private final Map<String, String> frameSessions = new HashMap();

    protected String currentUrl;
    protected String currentDialogText;
    private int nextId;

    public int nextId() {
        return ++nextId;
    }

    // mutable
    protected Logger logger;

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
        client.setLogger(logger);
        waitState.setLogger(logger);
    }

    protected DevToolsDriver(DriverOptions options, Command command, String webSocketUrl) {
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
        if (command == null) {
            return -1;
        }
        return command.waitSync();
    }

    public DevToolsMessage method(String method) {
        return new DevToolsMessage(this, method);
    }

    public DevToolsMessage sendAndWait(String text) {
        Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
        DevToolsMessage dtm = new DevToolsMessage(this, map);
        if (dtm.getId() == null) {
            dtm.setId(nextId());
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
        if (dtm.methodIs("Page.domContentEventFired")) {
            domContentEventFired = true;
            logger.trace("** set dom ready flag to true");
        }
        if (dtm.methodIs("Page.javascriptDialogOpening")) {
            currentDialogText = dtm.getParam("message").getAsString();
        }
        if (dtm.methodIs("Page.frameNavigated")) {
            String frameNavId = dtm.get("frame.id", String.class);
            String frameNavUrl = dtm.get("frame.url", String.class);
            if (rootFrameId.equals(frameNavId)) { // root page navigated
                currentUrl = frameNavUrl;
            }
        }
        if (dtm.methodIs("Page.frameStartedLoading")) {
            String frameLoadingId = dtm.get("frameId", String.class);
            if (rootFrameId.equals(frameLoadingId)) { // root page is loading
                domContentEventFired = false;
                framesStillLoading.clear();
                frameSessions.clear();
                logger.trace("** root frame started loading, cleared all page state: {}", frameLoadingId);
            } else {
                framesStillLoading.add(frameLoadingId);
                logger.trace("** frame started loading, added to in-progress list: {}", framesStillLoading);
            }
        }
        if (dtm.methodIs("Page.frameStoppedLoading")) {
            String frameLoadedId = dtm.get("frameId", String.class);
            framesStillLoading.remove(frameLoadedId);
            logger.trace("** frame stopped loading: {}, remaining in-progress: {}", frameLoadedId, framesStillLoading);
        }
        if (dtm.methodIs("Target.attachedToTarget")) {
            frameSessions.put(dtm.get("targetInfo.targetId", String.class), dtm.get("sessionId", String.class));
            logger.trace("** added frame session: {}", frameSessions);
        }
        // all needed state is set above before we get into conditional checks
        waitState.receive(dtm);
    }

    //==========================================================================
    //
    private DevToolsMessage evalOnce(String expression, Predicate<DevToolsMessage> condition) {
        DevToolsMessage toSend = method("Runtime.evaluate")
                .param("expression", expression).param("returnByValue", true);
        if (executionContextId != null) {
            toSend.param("contextId", executionContextId);
        }
        return toSend.send(condition);
    }

    protected DevToolsMessage eval(String expression, Predicate<DevToolsMessage> condition) {
        DevToolsMessage dtm = evalOnce(expression, condition);
        if (dtm.isResultError()) {
            String message = "js eval failed once:" + expression
                    + ", error: " + dtm.getResult().getAsString();
            logger.warn(message);
            options.sleep();
            dtm = evalOnce(expression, null); // no wait condition for the re-try
            if (dtm.isResultError()) {
                message = "js eval failed twice:" + expression
                        + ", error: " + dtm.getResult().getAsString();
                logger.error(message);
                throw new RuntimeException(message);
            }
        }
        return dtm;
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
    public Integer elementId(String locator) {
        DevToolsMessage dtm = method("DOM.querySelector")
                .param("nodeId", getRootNodeId())
                .param("selector", locator).send();
        if (dtm.isResultError()) {
            return null;
        }
        return dtm.getResult("nodeId").getAsInt();
    }

    @Override
    public List elementIds(String locator) {
        if (locator.startsWith("/")) { // special handling for xpath
            getRootNodeId(); // just so that DOM.getDocument is called else DOM.performSearch fails
            DevToolsMessage dtm = method("DOM.performSearch").param("query", locator).send();
            String searchId = dtm.getResult("searchId", String.class);
            int resultCount = dtm.getResult("resultCount", Integer.class);
            dtm = method("DOM.getSearchResults")
                    .param("searchId", searchId)
                    .param("fromIndex", 0).param("toIndex", resultCount).send();
            return dtm.getResult("nodeIds", List.class);
        }
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
        Map<String, Object> map = dtm.getResult("bounds").getAsMap();
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
        method("Page.navigate").param("url", url)
                .send(WaitState.ALL_FRAMES_LOADED);
    }

    @Override
    public void refresh() {
        method("Page.reload").send(WaitState.ALL_FRAMES_LOADED);
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
        method("Page.navigateToHistoryEntry").param("entryId", id).send(WaitState.ALL_FRAMES_LOADED);
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
        eval(options.selector(id) + ".click()", waitForDialog ? WaitState.DIALOG_OPENING : null);
    }

    @Override
    public void select(String id, String text) {
        waitIfNeeded(id);
        eval(options.optionSelector(id, text), null);
    }

    @Override
    public void select(String id, int index) {
        waitIfNeeded(id);
        eval(options.optionSelector(id, index), null);
    }

    @Override
    public void submit(String id) {
        waitIfNeeded(id);
        eval(options.selector(id) + ".click()", WaitState.ALL_FRAMES_LOADED);
    }

    @Override
    public void focus(String id) {
        waitIfNeeded(id);
        eval(options.selector(id) + ".focus()", null);
    }

    @Override
    public void clear(String id) {
        eval(options.selector(id) + ".value = ''", null);
    }

    @Override
    public void input(String id, String value) {
        waitIfNeeded(id);
        // focus
        eval(options.selector(id) + ".focus()", null);
        for (char c : value.toCharArray()) {
            DevToolsMessage toSend = method("Input.dispatchKeyEvent").param("type", "keyDown");
            Integer keyCode = Key.INSTANCE.CODES.get(c);
            if (keyCode == null) {
                toSend.param("text", c + "");
            } else {
                toSend.param("windowsVirtualKeyCode", keyCode);
            }
            toSend.send();
        }
    }

    @Override
    public String text(String id) {
        return property(id, "textContent");
    }

    private <T> T callFunctionOnNode(int nodeId, String function, Class<T> type) {
        DevToolsMessage dtm = method("DOM.resolveNode").param("nodeId", nodeId).send();
        String objectId = dtm.getResult("object.objectId", String.class);
        return callFunctionOnObject(objectId, function, type);
    }

    private <T> T callFunctionOnObject(String objectId, String function, Class<T> type) {
        DevToolsMessage dtm = method("Runtime.callFunctionOn")
                .param("objectId", objectId)
                .param("functionDeclaration", function)
                .send();
        return dtm.getResult().getValue(type);
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
    public void value(String id, String value) {
        waitIfNeeded(id);
        eval(options.selector(id) + ".value = '" + value + "'", null);
    }

    @Override
    public String attribute(String id, String name) {
        waitIfNeeded(id);
        DevToolsMessage dtm = eval(options.selector(id) + ".getAttribute('" + name + "')", null);
        return dtm.getResult().getAsString();
    }

    @Override
    public String property(String id, String name) {
        waitIfNeeded(id);
        DevToolsMessage dtm = eval(options.selector(id) + "['" + name + "']", null);
        return dtm.getResult().getAsString();
    }

    @Override
    public String name(String id) {
        return property(id, "tagName");
    }

    @Override
    public boolean enabled(String id) {
        waitIfNeeded(id);
        DevToolsMessage dtm = eval(options.selector(id) + ".disabled", null);
        return !dtm.getResult().isBooleanTrue();
    }

    @Override
    public boolean waitUntil(String expression) {
        int max = options.getRetryCount();
        int count = 0;
        ScriptValue sv;
        do {
            if (count > 0) {
                logger.debug("waitUntil retry #{}", count);
                options.sleep();
            }
            try {
                DevToolsMessage dtm = eval(expression, null);
                sv = dtm.getResult();
            } catch (Exception e) {
                sv = ScriptValue.FALSE;
                logger.warn("waitUntil evaluate failed: {}", e.getMessage());
            }
        } while (!sv.isBooleanTrue() && count++ < max);
        return sv.isBooleanTrue();
    }

    @Override
    public Object script(String expression) {
        return eval(expression, null).getResult().getValue();
    }

    @Override
    public String getTitle() {
        DevToolsMessage dtm = eval("document.title", null);
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
    public byte[] screenshot(boolean embed) {
        return screenshot(null, embed);
    }

    @Override
    public Map<String, Object> rect(String id) {
        String expression = options.selector(id) + ".getBoundingClientRect()";
        //  important to not set returnByValue to true
        DevToolsMessage dtm = method("Runtime.evaluate").param("expression", expression).send();
        String objectId = dtm.getResult("objectId").getAsString();
        dtm = method("Runtime.getProperties").param("objectId", objectId).param("accessorPropertiesOnly", true).send();        
        return options.newMapWithSelectedKeys(dtm.getResult().getAsMap(), "x", "y", "width", "height");
    }

    @Override
    public byte[] screenshot(String id, boolean embed) {
        DevToolsMessage dtm;
        if (id == null) {
            dtm = method("Page.captureScreenshot").send();
        } else {
            Map<String, Object> map = rect(id);
            map.put("scale", 1);
            dtm = method("Page.captureScreenshot").param("clip", map).send();
        }
        String temp = dtm.getResult("data").getAsString();
        byte[] bytes = Base64.getDecoder().decode(temp);
        if (embed) {
            options.embedPngImage(bytes);
        }
        return bytes;
    }

    // chrome only
    public byte[] screenshotFull() {
        DevToolsMessage layout = method("Page.getLayoutMetrics").send();
        Map<String, Object> size = layout.getResult("contentSize").getAsMap();
        Map<String, Object> map = options.newMapWithSelectedKeys(size, "height", "width");
        map.put("x", 0);
        map.put("y", 0);
        map.put("scale", 1);
        DevToolsMessage dtm = method("Page.captureScreenshot").param("clip", map).send();
        String temp = dtm.getResult("data").getAsString();
        return Base64.getDecoder().decode(temp);
    }

    @Override
    public void highlight(String id) {
        script(options.highlighter(id));
    }

    @Override
    public List<String> getPages() {
        DevToolsMessage dtm = method("Target.getTargets").send();
        return dtm.getResult("targetInfos.targetId").getAsList();
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
            executionContextId = null;
            sessionId = null;
            return;
        }
        List<Integer> ids = elementIds("iframe,frame");
        if (index < ids.size()) {
            Integer nodeId = ids.get(index);
            setExecutionContext(nodeId, index);
        } else {
            logger.warn("unable to switch frame by index: {}", index);
        }
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) {
            executionContextId = null;
            sessionId = null;
            return;
        }
        waitIfNeeded(locator);
        Integer nodeId = elementId(locator);
        if (nodeId == null) {
            return;
        }
        setExecutionContext(nodeId, locator);
    }

    private void setExecutionContext(int nodeId, Object locator) {
        DevToolsMessage dtm = method("DOM.describeNode")
                .param("nodeId", nodeId)
                .param("depth", 0)
                .send();
        String frameId = dtm.getResult("node.frameId", String.class);
        if (frameId == null) {
            logger.warn("unable to find frame by nodeId: {}", locator);
            return;
        }
        sessionId = frameSessions.get(frameId);
        if (sessionId != null) {
            logger.trace("found out-of-process frame - session: {} - {}", frameId, sessionId);
            return;
        }
        dtm = method("Page.createIsolatedWorld").param("frameId", frameId).send();
        executionContextId = dtm.getResult("executionContextId").getValue(Integer.class);
        if (executionContextId == null) {
            logger.warn("execution context is null, unable to switch frame by locator: {}", locator);
        }
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

    public void enableTargetEvents() {
        method("Target.setAutoAttach")
                .param("autoAttach", true)
                .param("waitForDebuggerOnStart", false)
                .param("flatten", true).send();
    }

}
