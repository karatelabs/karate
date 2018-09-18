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

import com.intuit.karate.Http;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketListener;
import com.intuit.karate.shell.CommandThread;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Chrome implements WebSocketListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketListener.class);

    public static final String PATH_MAC = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

    private final CommandThread command;
    private final int port;
    protected final WebSocketClient client;

    private int next;

    public Chrome(int port) {
        this.port = port;
        String uniqueName = System.currentTimeMillis() + "";
        File profileDir = new File("target/chrome" + uniqueName);
        String logFile = profileDir.getPath() + File.separator + "karate.log";
        command = new CommandThread(logFile, profileDir,
                PATH_MAC,
                "--remote-debugging-port=" + port,
                "--no-first-run",
                "--user-data-dir=" + profileDir.getAbsolutePath());
        command.start();
        Http http = Http.forUrl("http://localhost:" + port);
        String webSocketUrl = http.path("json").get()
                .jsonPath("get[0] $[?(@.type=='page')].webSocketDebuggerUrl").asString();
        client = new WebSocketClient(webSocketUrl, this);
    }

    public int waitSync() {
        return command.waitSync();
    }

    private ChromeMessage method(String method) {
        return new ChromeMessage(++next, method);
    }

    private final Map<Integer, ChromeMessage> messages = new HashMap();

    public void sendAndWait(ChromeMessage cm) {
        String json = JsonUtils.toJson(cm.toMap());
        client.send(json);
        messages.put(cm.getId(), cm);        
        while (messages.containsKey(cm.getId())) {
            synchronized (messages) {
                try {
                    logger.debug(">> sent: {}", cm);
                    messages.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            logger.debug(">> wait: {}", cm);
        }
        logger.debug("<< complete", cm);       
    }
    
    public void receive(ChromeMessage cm) {
        synchronized (messages) {
            if (messages.containsKey(cm.getId())) {
                messages.remove(cm.getId());
                messages.notify();
                logger.debug("<< received: {}", cm);
            } else {
                logger.debug("<< ignored: {}", cm);
            }
        }        
    }

    public void url(String url) {
        method("Page.navigate").param("url", url).send(this);
    }

    @Override
    public void onTextMessage(String text) {
        ChromeMessage cm = JsonUtils.fromJson(text, ChromeMessage.class);
        receive(cm);
    }

    @Override
    public void onBinaryMessage(byte[] bytes) {

    }

}
