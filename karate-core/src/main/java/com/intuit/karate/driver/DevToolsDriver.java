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

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeaturesBackend;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScriptBridge;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.MultiValuedMap;
import com.intuit.karate.netty.NettyUtils;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketOptions;
import com.intuit.karate.shell.Command;
import org.slf4j.MDC;

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
    private Integer executionContextId;
    protected String sessionId;

    protected boolean domContentEventFired;
    protected final Set<String> framesStillLoading = new HashSet();
    private final Map<String, String> frameSessions = new HashMap();
    private boolean submit;

    protected String currentUrl;
    protected String currentDialogText;
    protected int currentMouseXpos;
    protected int currentMouseYpos;

    private int nextId;

    public int nextId() {
        return ++nextId;
    }

    private FeaturesBackend backend;

    protected final Logger logger;

    protected DevToolsDriver(DriverOptions options, Command command, String webSocketUrl) {
        logger = options.driverLogger;
        this.options = options;
        this.command = command;
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
        //Do stuff inside wait to avoid missing messages
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
            currentDialogText = dtm.getParam("message").getAsString();
            // this will stop waiting NOW
            wait.setCondition(DevToolsWait.DIALOG_OPENING);
        }
        if (dtm.methodIs("Page.frameNavigated")) {
            String frameNavId = dtm.get("frame.id", String.class);
            String frameNavUrl = dtm.get("frame.url", String.class);
            if (rootFrameId.equals(frameNavId)) { // root page navigated
                currentUrl = frameNavUrl;
            }
        }
        if (dtm.methodIs("Page.navigatedWithinDocument")) { // js variant of above (SPA, history nav)
            String frameNavId = dtm.get("frameId", String.class);
            String frameNavUrl = dtm.get("url", String.class);
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
        if (dtm.methodIs("Fetch.requestPaused")) {
            handleInterceptedRequest(dtm);
        }
        // all needed state is set above before we get into conditional checks
        wait.receive(dtm);
    }

    private void handleInterceptedRequest(DevToolsMessage dtm) {
        String requestId = dtm.get("requestId", String.class);
        String requestUrl = dtm.get("request.url", String.class);
        if (backend != null) {
            String method = dtm.get("request.method", String.class);
            Map<String, String> headers = dtm.get("request.headers", Map.class);
            String postData = dtm.get("request.postData", String.class);
            logger.debug("intercepting request for url: {}", requestUrl);
            HttpRequest request = new HttpRequest();
            // needed pre-processing for mock feature backend
            StringUtils.Pair pair = NettyUtils.parseUriIntoUrlBaseAndPath(requestUrl);
            request.setUrlBase(pair.left);
            request.setUri(pair.right);
            request.setMethod(method);
            String karateRequestId = System.identityHashCode(dtm) + "";
            MDC.put("karateRequestId", karateRequestId);
            request.setRequestId(karateRequestId);
            headers.forEach((k, v) -> request.addHeader(k, v));
            if (postData != null) {
                request.setBody(FileUtils.toBytes(postData));
            } else {
                request.setBody(FileUtils.EMPTY_BYTES);
            }
            HttpResponse response = backend.buildResponse(request, System.currentTimeMillis());
            String responseBody = response.getBody() == null ? "" : Base64.getEncoder().encodeToString(response.getBody());
            List<Map> responseHeaders = new ArrayList();
            MultiValuedMap mvm = response.getHeaders();
            if (mvm != null) {
                for (String name : mvm.keySet()) {
                    Object value = mvm.getFirst(name);
                    Map<String, Object> nv = new HashMap(2);
                    nv.put("name", name);
                    nv.put("value", value);
                    responseHeaders.add(nv);
                }
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
    private DevToolsMessage evalOnce(String expression, boolean quickly, boolean fireAndForget) {
        DevToolsMessage toSend = method("Runtime.evaluate")
                .param("expression", expression).param("returnByValue", true);
        if (executionContextId != null) {
            toSend.param("contextId", executionContextId);
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
        return eval(expression, false);
    }

    private DevToolsMessage eval(String expression, boolean quickly) {
        DevToolsMessage dtm = evalOnce(expression, quickly, false);
        if (dtm.isResultError()) {
            String message = "js eval failed once:" + expression
                    + ", error: " + dtm.getResult().getAsString();
            logger.warn(message);
            options.sleep();
            dtm = evalOnce(expression, quickly, false); // no wait condition for the re-try
            if (dtm.isResultError()) {
                message = "js eval failed twice:" + expression
                        + ", error: " + dtm.getResult().getAsString();
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
            evalOnce(highlightJs, true, true); // do it safely, i.e. fire and forget
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
        if (!dtm.isResultError()) {
            windowId = dtm.getResult("windowId").getValue(Integer.class);
            windowState = (String) dtm.getResult("bounds").getAsMap().get("windowState");
        }
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

    public void emulateDevice(int width, int height, String userAgent) {
        logger.info("Setting deviceMetrics width={}, height={}, userAgent={}", width, height, userAgent);
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
        int currentIndex = dtm.getResult("currentIndex").getValue(Integer.class);
        List<Map> list = dtm.getResult("entries").getValue(List.class);
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
        eval(options.selector(locator) + ".click()");
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
        eval(options.selector(locator) + ".value = ''");
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
                        chromeType = null;

                }
                if (chromeType == null) {
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
    public Element value(String locator, String value) {
        retryIfEnabled(locator);
        eval(options.selector(locator) + ".value = '" + value + "'");
        return DriverElement.locatorExists(this, locator);
    }

    @Override
    public String attribute(String id, String name) {
        retryIfEnabled(id);
        DevToolsMessage dtm = eval(options.selector(id) + ".getAttribute('" + name + "')");
        return dtm.getResult().getAsString();
    }

    @Override
    public String property(String id, String name) {
        retryIfEnabled(id);
        DevToolsMessage dtm = eval(options.selector(id) + "['" + name + "']");
        return dtm.getResult().getAsString();
    }

    @Override
    public boolean enabled(String id) {
        retryIfEnabled(id);
        DevToolsMessage dtm = eval(options.selector(id) + ".disabled");
        return !dtm.getResult().isBooleanTrue();
    }

    @Override
    public boolean waitUntil(String expression) {
        return options.retry(() -> {
            try {
                return eval(expression, true).getResult().isBooleanTrue();
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
        DevToolsMessage dtm = eval("document.title");
        return dtm.getResult().getAsString();
    }

    @Override
    public String getUrl() {
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
    public void cookie(Map<String, Object> cookie) {
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
    public Map<String, Object> position(String locator) {
        boolean submitTemp = submit; // in case we are prepping for a submit().mouse(locator).click()
        submit = false;
        retryIfEnabled(locator);
        String expression = options.selector(locator) + ".getBoundingClientRect()";
        //  important to not set returnByValue to true
        DevToolsMessage dtm = method("Runtime.evaluate").param("expression", expression).send();
        String objectId = dtm.getResult("objectId").getAsString();
        dtm = method("Runtime.getProperties").param("objectId", objectId).param("accessorPropertiesOnly", true).send();
        submit = submitTemp;
        return options.newMapWithSelectedKeys(dtm.getResult().getAsMap(), "x", "y", "width", "height");
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
        return dtm.getResult("targetInfos.targetId").getAsList();
    }

    @Override
    public void switchPage(String titleOrUrl) {
        if (titleOrUrl == null) {
            return;
        }
        DevToolsMessage dtm = method("Target.getTargets").send();
        List<Map> targets = dtm.getResult("targetInfos").getAsList();
        String targetId = null;
        String targetUrl = null;
        for (Map map : targets) {
            String title = (String) map.get("title");
            String url = (String) map.get("url");
            if ((title != null && title.contains(titleOrUrl))
                    || (url != null && url.contains(titleOrUrl))) {
                targetId = (String) map.get("targetId");
                targetUrl = url;
                break;
            }
        }
        if (targetId != null) {
            method("Target.activateTarget").param("targetId", targetId).send();
            currentUrl = targetUrl;
        } else {
            logger.warn("failed to switch page to {}", titleOrUrl);
        }
    }

    @Override
    public void switchPage(int index) {
        if (index == -1) {
            return;
        }
        DevToolsMessage dtm = method("Target.getTargets").send();
        List<Map> targets = dtm.getResult("targetInfos").getAsList();
        if (index < targets.size()) {
            Map target = targets.get(index);
            String targetId = (String) target.get("targetId");
            method("Target.activateTarget").param("targetId", targetId).send();
            currentUrl = (String) target.get("url");
        } else {
            logger.warn("failed to switch frame by index: {}", index);
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
        retryIfEnabled(locator);
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

    public void intercept(Map<String, Object> config) {
        config = new ScriptValue(config).getAsMap(); // ensure js is pre-processed, TODO graal
        List<String> patterns = (List) config.get("patterns");
        if (patterns == null) {
            throw new RuntimeException("missing array argument 'patterns': " + config);
        }
        if (backend != null) {
            throw new RuntimeException("'intercept()' can be called only once");
        }
        ScenarioContext context = getOptions().getContext();
        String mock = (String) config.get("mock");
        if (mock == null) {
            throw new RuntimeException("missing argument 'mock': " + config);
        }
        ScriptValue mockSv = FileUtils.readFile(mock, context);
        if (!mockSv.isFeature()) {
            throw new RuntimeException("'mock' is not a feature file: " + config + ", " + mockSv);
        }
        Feature feature = mockSv.getValue(Feature.class);
        backend = new FeaturesBackend(feature);
        method("Fetch.enable").param("patterns", patterns).send();
    }

    public void inputFile(String locator, String... relativePaths) {
        List<String> files = new ArrayList(relativePaths.length);
        ScriptBridge bridge = options.getContext().bindings.bridge;
        for (String p : relativePaths) {
            files.add(bridge.toAbsolutePath(p));
        }
        Integer nodeId = elementId(locator);
        method("DOM.setFileInputFiles").param("files", files).param("nodeId", nodeId).send();
    }

}
