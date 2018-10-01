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
package com.intuit.karate.web;

import com.intuit.karate.Http;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketListener;
import com.intuit.karate.shell.CommandThread;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author pthomas3
 */
public abstract class DevToolsDriver implements Driver, WebSocketListener {

    protected static final Logger logger = LoggerFactory.getLogger(DevToolsDriver.class);

    protected final CommandThread command;
    protected final Http http;
    protected final WebSocketClient client;

    private final WaitState waitState;

    private final String pageId;
    protected final boolean headless;
    private final long timeOut;

    protected String currentUrl;

    private int nextId = 1;

    public int getNextId() {
        return nextId++;
    }

    protected DevToolsDriver(CommandThread command, Http http, String webSocketUrl, boolean headless, long timeOut) {
        this.command = command;
        this.http = http;
        this.headless = headless;
        this.timeOut = timeOut;
        this.waitState = new WaitState(timeOut);
        int pos = webSocketUrl.lastIndexOf('/');
        pageId = webSocketUrl.substring(pos + 1);
        logger.debug("page id: {}", pageId);
        client = new WebSocketClient(webSocketUrl, this);
    }

    public int waitSync() {
        return command.waitSync();
    }

    public DevToolsMessage method(String method) {
        return new DevToolsMessage(this, method);
    }

    public DevToolsMessage sendAndWait(String text) {
        Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
        DevToolsMessage cm = new DevToolsMessage(this, map);
        if (cm.getId() == null) {
            cm.setId(getNextId());
        }
        return sendAndWait(cm, null);
    }

    public DevToolsMessage sendAndWait(DevToolsMessage cm, Predicate<DevToolsMessage> condition) {
        String json = JsonUtils.toJson(cm.toMap());
        client.send(json);
        logger.debug(">> sent: {}", cm);
        return waitState.sendAndWait(cm, condition);
    }

    public void receive(DevToolsMessage cm) {
        waitState.receive(cm);
    }

    @Override
    public void onMessage(String text) {
        logger.debug("received raw: {}", text);
        Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
        DevToolsMessage cm = new DevToolsMessage(this, map);
        receive(cm);
    }

    @Override
    public void onMessage(byte[] bytes) {
        logger.warn("ignoring binary message");
    }

    //==========================================================================

    protected int getWaitInterval() {
        return 0;
    }
    
    protected DevToolsMessage eval(String expression, Predicate<DevToolsMessage> condition) {
        int count = 0;
        DevToolsMessage cm;
        do {
            logger.debug("eval try #{}", count + 1);
            cm = method("Runtime.evaluate").param("expression", expression).send(condition);
            condition = null; // retries don't care about user-condition, e.g. page on-load
        } while (cm != null && cm.isResultError() && count++ < 3);
        return cm;
    }

    @Override
    public void activate() {
        method("Target.activateTarget").param("targetId", pageId).send();
    }

    @Override
    public void close() {
        method("Page.close").send();
    }

    @Override
    public void stop() {
        if (headless) {
            close();
        } else {
            method("Browser.close").send();
        }
        if (command != null) {
            command.interrupt();
        }
    }

    @Override
    public void location(String url) {
        DevToolsMessage cm = method("Page.navigate").param("url", url).send(WaitState.CHROME_FRAME_NAVIGATED);
        currentUrl = cm.getFrameUrl();
    }

    @Override
    public void click(String id) {
        eval(DriverUtils.selectorScript(id) + ".click()", null);
    }

    @Override
    public void submit(String id) {
        DevToolsMessage cm = eval(DriverUtils.selectorScript(id) + ".click()", WaitState.CHROME_FRAME_NAVIGATED);
        currentUrl = cm.getFrameUrl();
    }

    @Override
    public void focus(String id) {
        eval(DriverUtils.selectorScript(id) + ".focus()", null);
    }

    @Override
    public void input(String id, String value) {
        focus(id);
        for (char c : value.toCharArray()) {
            method("Input.dispatchKeyEvent").param("type", "keyDown").param("text", c + "").send();
        }
    }

    @Override
    public String text(String id) {
        DevToolsMessage cm = eval(DriverUtils.selectorScript(id) + ".textContent", null);
        return cm.getResult("value").getAsString();
    }

    @Override
    public String html(String id) {
        DevToolsMessage cm = eval(DriverUtils.selectorScript(id) + ".innerHTML", null);
        return cm.getResult("value").getAsString();
    }

    @Override
    public String value(String id) {
        DevToolsMessage cm = eval(DriverUtils.selectorScript(id) + ".value", null);
        return cm.getResult("value").getAsString();
    }

    @Override
    public void waitForEvalTrue(String expression) {
        int count = 0;
        ScriptValue sv;
        do {
            DriverUtils.sleep(getWaitInterval());
            logger.debug("poll try #{}", count + 1);
            DevToolsMessage dtm = eval(expression, null);
            sv = dtm.getResult("value");
        } while (!sv.isBooleanTrue() && count++ < 3);
    }        

    @Override
    public String getTitle() {
        DevToolsMessage cm = eval("document.title", null);
        return cm.getResult("value").getAsString();
    }        

    @Override
    public String getLocation() {
        return currentUrl;
    }

    public void enableNetworkEvents() {
        method("Network.enable").send();
    }

    public void enablePageEvents() {
        method("Page.enable").send();
    }

}
