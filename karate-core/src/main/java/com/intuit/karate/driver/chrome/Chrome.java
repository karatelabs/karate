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
package com.intuit.karate.driver.chrome;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.shell.Command;
import com.intuit.karate.driver.DevToolsDriver;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.http.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Chrome extends DevToolsDriver {
    
    public static final String DRIVER_TYPE = "chrome";

    public static final String DEFAULT_PATH_MAC = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    public static final String DEFAULT_PATH_WIN = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
    public static final String DEFAULT_PATH_LINUX = "/usr/bin/google-chrome";

    public Chrome(DriverOptions options, Command command, String webSocketUrl) {
        super(options, command, webSocketUrl);
    }

    public static Chrome start(Map<String, Object> map, ScenarioRuntime sr) {
        DriverOptions options = new DriverOptions(map, sr, 9222,
                FileUtils.isOsWindows() ? DEFAULT_PATH_WIN : FileUtils.isOsMacOsX() ? DEFAULT_PATH_MAC : DEFAULT_PATH_LINUX);
        options.arg("--remote-debugging-port=" + options.port);
        options.arg("--no-first-run");
        if (options.userDataDir != null) {
            options.arg("--user-data-dir=" + options.userDataDir);
        }
        options.arg("--disable-popup-blocking");
        if (options.headless) {
            options.arg("--headless");
        }
        Command command = options.startProcess();
        Http http = options.getHttp();        
        Command.waitForHttp(http.urlBase + "/json", r -> r.getStatus() == 200 && !r.json().asList().isEmpty());
        Response res = http.path("json").get();
        if (res.json().asList().isEmpty()) {
            if (command != null) {
                command.close(true);
            }
            throw new RuntimeException("chrome server returned empty list from " + http.urlBase);
        }
        String webSocketUrl = null;
        List<Map<String, Object>> targets = res.json().asList();
        for (Map<String, Object> target : targets) {
            String targetUrl = (String) target.get("url");
            if (targetUrl == null || targetUrl.startsWith("chrome-")) {
                continue;
            }
            String targetType = (String) target.get("type");
            if (!"page".equals(targetType)) {
                continue;
            }
            webSocketUrl = (String) target.get("webSocketDebuggerUrl");
            if (options.attach == null) { // take the first                
                break;
            }
            if (targetUrl.contains(options.attach)) {
                break;
            }
        }
        if (webSocketUrl == null) {
            throw new RuntimeException("failed to attach to chrome debug server");
        }
        Chrome chrome = new Chrome(options, command, webSocketUrl);
        chrome.activate();
        chrome.enablePageEvents();
        chrome.enableRuntimeEvents();
        if (!options.headless) {
            chrome.initWindowIdAndState();
        }
        return chrome;
    }

    public static Chrome start(String chromeExecutablePath, boolean headless) {
        Map<String, Object> options = new HashMap();
        options.put("executable", chromeExecutablePath);
        options.put("headless", headless);
        return Chrome.start(options);
    }

    public static Chrome start(Map<String, Object> options) {
        if (options == null) {
            options = new HashMap();
        }
        options.putIfAbsent("type", DRIVER_TYPE);
        ScenarioRuntime runtime = FeatureRuntime.forTempUse().scenarios.next();
        ScenarioEngine.set(runtime.engine);
        return Chrome.start(options, runtime);
    }

    public static Chrome start() {
        return start(null);
    }

    public static Chrome startHeadless() {
        return start(Collections.singletonMap("headless", true));
    }

}
