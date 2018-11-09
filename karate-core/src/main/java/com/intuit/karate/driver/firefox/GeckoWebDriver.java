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
package com.intuit.karate.driver.firefox;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.core.Engine;
import com.intuit.karate.shell.CommandThread;
import com.intuit.karate.driver.DriverUtils;
import com.intuit.karate.driver.WebDriver;
import java.io.File;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class GeckoWebDriver extends WebDriver {

    public GeckoWebDriver(CommandThread command, boolean headless, Http http, String sessionId, String windowId) {
        super(command, headless, http, sessionId, windowId);
    }

    public static GeckoWebDriver start(Map<String, Object> options) {
        Integer port = (Integer) options.get("port");
        if (port == null) {
            port = 4444;
        }
        String host = "localhost";
        Boolean start = (Boolean) options.get("start");
        String executable = (String) options.get("executable");
        if (executable == null && start != null && start) {
            executable = "geckodriver";
        }
        CommandThread command;
        if (executable != null) {
            String targetDir = Engine.getBuildDir() + File.separator;
            String logFile = targetDir + "geckodriver.log";
            command = new CommandThread(WebDriver.class, logFile, new File(targetDir), executable, "--port=" + port);
            command.start();
            DriverUtils.waitForPort(host, port);
        } else {
            command = null;
        }
        String urlBase = "http://" + host + ":" + port;
        Http http = Http.forUrl(urlBase);
        String sessionId = http.path("session")
                .post("{ desiredCapabilities: { browserName: 'Firefox' } }")
                .jsonPath("get[0] response..sessionId").asString();
        logger.debug("init session id: {}", sessionId);
        http.url(urlBase + "/session/" + sessionId);
        String windowId = http.path("window").get().jsonPath("$.value").asString();
        logger.debug("init window id: {}", windowId);
        GeckoWebDriver driver = new GeckoWebDriver(command, false, http, sessionId, windowId);
        driver.activate();
        return driver;
    }

    @Override
    protected int getWaitInterval() {
        return 1000;
    }   

    @Override
    public void activate() {
        if (!headless) {
            try {
                switch (FileUtils.getPlatform()) {
                    case MAC:
                        Runtime.getRuntime().exec(new String[]{"osascript", "-e", "tell app \"Firefox\" to activate"});
                        break;
                    default:

                }
            } catch (Exception e) {
                logger.warn("native window switch failed: {}", e.getMessage());
            }
        }
    }

}
