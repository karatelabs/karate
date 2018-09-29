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
package com.intuit.karate.web.chrome;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.Engine;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketListener;
import com.intuit.karate.shell.CommandThread;
import com.intuit.karate.web.Driver;
import com.intuit.karate.web.DriverUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * chrome devtools protocol - the "preferred" driver: https://chromedevtools.github.io/devtools-protocol/
 * 
 * @author pthomas3
 */
public class Chrome implements Driver, WebSocketListener {

    private static final Logger logger = LoggerFactory.getLogger(Chrome.class);

    public static final String DEFAULT_PATH_MAC = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    public static final String DEFAULT_PATH_WIN = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";

    private final CommandThread command;
    protected final WebSocketClient client;

    private final WaitState waitState = new WaitState();

    private final String pageId;
    private final boolean headless;

    private String currentUrl;

    private int nextId = 1;

    public int getNextId() {
        return nextId++;
    }

    public static Chrome start(Map<String, Object> options) {
        Integer port = (Integer) options.get("port");
        if (port == null) {
            port = 9222;
        }
        String executable = (String) options.get("executable");
        if (executable == null) {
            executable = FileUtils.isWindows() ? DEFAULT_PATH_WIN : DEFAULT_PATH_MAC;
        }
        String uniqueName = System.currentTimeMillis() + "";
        File profileDir = new File(Engine.getBuildDir() + File.separator + "chrome" + uniqueName);
        List<String> args = Arrays.asList(executable,
                "--remote-debugging-port=" + port,
                "--no-first-run",
                "--user-data-dir=" + profileDir.getAbsolutePath());
        Boolean headless = (Boolean) options.get("headless");
        if (headless == null) {
            headless = false;
        }
        if (headless) {
            args = new ArrayList(args);
            args.add("--headless");
        }
        String logFile = profileDir.getPath() + File.separator + "karate.log";
        CommandThread command = new CommandThread(Chrome.class, logFile, profileDir, args.toArray(new String[]{}));
        command.start();
        Http http = Http.forUrl("http://localhost:" + port);
        String webSocketUrl = http.path("json").get()
                .jsonPath("get[0] $[?(@.type=='page')].webSocketDebuggerUrl").asString();
        Chrome chrome = new Chrome(command, webSocketUrl, headless);
        chrome.activate();
        chrome.enablePageEvents();
        return chrome;
    }

    private Chrome(CommandThread command, String webSocketUrl, boolean headless) {
        this.command = command;
        this.headless = headless;
        int pos = webSocketUrl.lastIndexOf('/');
        pageId = webSocketUrl.substring(pos + 1);
        logger.debug("page id: {}", pageId);
        client = new WebSocketClient(webSocketUrl, this);
    }

    public int waitSync() {
        return command.waitSync();
    }

    public ChromeMessage method(String method) {
        return new ChromeMessage(this, method);
    }

    public ChromeMessage sendAndWait(String text) {
        Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
        ChromeMessage cm = new ChromeMessage(this, map);
        if (cm.getId() == null) {
            cm.setId(getNextId());
        }
        return sendAndWait(cm, null);
    }

    public ChromeMessage sendAndWait(ChromeMessage cm, Predicate<ChromeMessage> condition) {
        String json = JsonUtils.toJson(cm.toMap());
        client.send(json);
        logger.debug(">> sent: {}", cm);
        return waitState.sendAndWait(cm, condition);
    }

    public void receive(ChromeMessage cm) {
        waitState.receive(cm);
    }

    @Override
    public void onTextMessage(String text) {
        logger.debug("received raw: {}", text);
        Map<String, Object> map = JsonUtils.toJsonDoc(text).read("$");
        ChromeMessage cm = new ChromeMessage(this, map);
        receive(cm);
    }

    @Override
    public void onBinaryMessage(byte[] bytes) {
        logger.warn("ignoring binary message");
    }

    //==========================================================================
    
    private ChromeMessage eval(String expression, Predicate<ChromeMessage> condition) {
        int count = 0;
        ChromeMessage cm;
        do {
            cm = method("Runtime.evaluate").param("expression", expression).send(condition);
            condition = null; // retries don't care about user-condition, e.g. page on-load
        } while (cm.isResultError() && count++ < 3);
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
        command.interrupt();
    }

    @Override
    public void location(String url) {
        ChromeMessage cm = method("Page.navigate").param("url", url).send(WaitState.FRAME_NAVIGATED);
        currentUrl = cm.getFrameUrl();
    }

    @Override
    public void click(String id) {
        eval(DriverUtils.selectorScript(id) + ".click()", null);
    }

    @Override
    public void submit(String id) {
        ChromeMessage cm = eval(DriverUtils.selectorScript(id) + ".click()", WaitState.FRAME_NAVIGATED);
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
        ChromeMessage cm = eval(DriverUtils.selectorScript(id) + ".textContent", null);
        return cm.getResultValueAsString();
    }

    @Override
    public String html(String id) {
        ChromeMessage cm = eval(DriverUtils.selectorScript(id) + ".innerHTML", null);
        return cm.getResultValueAsString();
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
