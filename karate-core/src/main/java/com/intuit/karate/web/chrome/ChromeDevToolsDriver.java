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
import com.intuit.karate.core.Engine;
import com.intuit.karate.shell.CommandThread;
import com.intuit.karate.web.DevToolsDriver;
import com.intuit.karate.web.DriverUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 
 * chrome devtools protocol - the "preferred" driver: https://chromedevtools.github.io/devtools-protocol/
 * 
 * @author pthomas3
 */
public class ChromeDevToolsDriver extends DevToolsDriver {
    
    public static final String DEFAULT_PATH_MAC = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    public static final String DEFAULT_PATH_WIN = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";    
    
    public ChromeDevToolsDriver(CommandThread command, Http http, String webSocketUrl, boolean headless, long timeOut) {
        super(command, http, webSocketUrl, headless, timeOut);
    }
    
    public static ChromeDevToolsDriver start(Map<String, Object> options) {
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
        CommandThread command = new CommandThread(DevToolsDriver.class, logFile, profileDir, args.toArray(new String[]{}));
        command.start();
        Http http = Http.forUrl("http://localhost:" + port);
        String webSocketUrl = http.path("json").get()
                .jsonPath("get[0] $[?(@.type=='page')].webSocketDebuggerUrl").asString();
        Long timeOut = DriverUtils.getTimeOut(options);
        ChromeDevToolsDriver chrome = new ChromeDevToolsDriver(command, http, webSocketUrl, headless, timeOut);
        chrome.activate();
        chrome.enablePageEvents();
        return chrome;
    }    
    
}
