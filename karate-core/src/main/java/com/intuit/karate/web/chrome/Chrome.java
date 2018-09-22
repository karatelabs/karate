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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Chrome implements WebSocketListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketListener.class);

    public static final String DEFAULT_PATH_MAC = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    public static final String DEFAULT_PATH_WIN = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";

    private final CommandThread command;
    protected final WebSocketClient client;
    private final Map<Integer, ChromeMessage> messages = new HashMap();
    private final Map<Integer, ChromeMessage> results = new HashMap();
    private final Set<String> handlers = new HashSet();

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
        return new Chrome(command, webSocketUrl, headless);
    }

    private Chrome(CommandThread command, String webSocketUrl, boolean headless) {
        this.command = command;
        this.headless = headless;
        int pos = webSocketUrl.lastIndexOf('/');
        pageId = webSocketUrl.substring(pos + 1);
        logger.debug("page id: {}", pageId);
        client = new WebSocketClient(webSocketUrl, this);
        activate();
        enablePageEvents();
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
        return sendAndWait(cm);
    }

    public void await(String event) {
        handlers.add(event);
        while (handlers.contains(event)) {
            synchronized (handlers) {
                logger.debug(">> await event: {}", event);
                try {
                    handlers.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        logger.debug("<< *** wait success: {}", event);
    }

    public ChromeMessage sendAndWait(ChromeMessage cm) {
        String json = JsonUtils.toJson(cm.toMap());
        client.send(json);
        logger.debug(">> sent: {}", cm);
        int id = cm.getId();
        messages.put(id, cm);
        while (messages.containsKey(id)) {
            synchronized (messages) {
                logger.debug(">> wait: {}", cm);
                try {
                    messages.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        logger.debug("<< notified: {}", cm);
        return results.remove(id);
    }

    public void receive(ChromeMessage cm) {
        if (cm.getId() == null) {
            synchronized (handlers) {
                if (handlers.contains(cm.getMethod())) {                    
                    // TODO generic
                    if ("Page.frameNavigated".equals(cm.getMethod())) {
                        String temp = cm.getFrameUrl();
                        if (temp.startsWith("http")) {
                            currentUrl = cm.getFrameUrl();
                            logger.debug("detected page load: {}", currentUrl);
                            handlers.remove(cm.getMethod());
                            handlers.notify();
                        }
                    }                    
                }
            }
            logger.debug("<< ignored: {}", cm);
            return;
        } else {
            int id = cm.getId();
            synchronized (messages) {
                if (messages.containsKey(id) && cm.getResult() != null) {
                    messages.remove(id);
                    results.put(id, cm);
                    messages.notify();
                    logger.debug("<< notify: {}", cm);
                } else {
                    logger.warn("<< no match: {}", cm);
                }
            }
        }
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

    private String elementGetter(String id) {
        if (id.startsWith("/")) {
            return "document.evaluate(\"" + id + "\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue";
        } else if (id.startsWith("#")) {
            return "document.getElementById('" + id.substring(1) + "')";
        } else {
            return elementGetter("//*[@value=\"" + id + "\"]");
        }
    }

    //==========================================================================
    
    public ChromeMessage eval(String expression) {
        int count = 0;
        ChromeMessage cm;
        do {
            cm = method("Runtime.evaluate").param("expression", expression).send();
        } while (cm.isResultError() && count++ < 3);
        return cm;
    }

    public void activate() {
        method("Target.activateTarget").param("targetId", pageId).send();
    }

    public void close() {
        method("Page.close").send();
    }

    public void stop() {
        if (headless) {
            close();
        } else {
            method("Browser.close").send();
        }
    }

    public void browse(String url) {
        method("Page.navigate").param("url", url).send();
    }

    public void click(String id) {
        eval(elementGetter(id) + ".click()");
    }

    public void focus(String id) {
        eval(elementGetter(id) + ".focus()");
    }

    public void type(String id, String value) {
        focus(id);
        for (char c : value.toCharArray()) {
            method("Input.dispatchKeyEvent").param("type", "keyDown").param("text", c + "").send();
        }
    }

    public String text(String id) {
        ChromeMessage cm = eval(elementGetter(id) + ".textContent");
        return cm.getResultValueAsString();
    }

    public String html(String id) {
        ChromeMessage cm = eval(elementGetter(id) + ".innerHTML");
        return cm.getResultValueAsString();
    }

    public String url() {
        return currentUrl;
    }

    public void enableNetworkEvents() {
        method("Network.enable").send();
    }

    public void enablePageEvents() {
        method("Page.enable").send();
    }

}
