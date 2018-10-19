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
package com.intuit.karate.driver.edge;

import com.intuit.karate.Http;
import com.intuit.karate.core.Engine;
import com.intuit.karate.shell.CommandThread;
import com.intuit.karate.driver.DevToolsDriver;
import com.intuit.karate.driver.DevToolsMessage;
import com.intuit.karate.driver.DriverUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class EdgeDevToolsDriver extends DevToolsDriver {

    public EdgeDevToolsDriver(CommandThread command, Http http, String webSocketUrl, boolean headless, long timeOut) {
        super(command, http, webSocketUrl, headless, timeOut);
    }

    public static EdgeDevToolsDriver start(Map<String, Object> options) {
        Integer port = (Integer) options.get("port");
        if (port == null) {
            port = 9222;
        }
        String host = (String) options.get("host");
        if (host == null) {
            host = "localhost";
        }
        Boolean start = (Boolean) options.get("start");
        String executable = (String) options.get("executable");
        if (executable == null && start != null && start) {
            executable = "MicrosoftEdge";
        }
        CommandThread command;
        if (executable != null) {
            String uniqueName = System.currentTimeMillis() + "";
            File profileDir = new File(Engine.getBuildDir() + File.separator + "chrome" + uniqueName);
            List<String> args = Arrays.asList(executable,
                    "--devtools-server-port", port + "", "about:blank");
            String logFile = profileDir.getPath() + File.separator + "karate.log";
            command = new CommandThread(DevToolsDriver.class, logFile, profileDir, args.toArray(new String[]{}));
            command.start();
            DriverUtils.waitForPort(host, port);
        } else {
            command = null;
        }
        Http http = Http.forUrl("http://" + host + ":" + port);
        String webSocketUrl = http.path("json", "list").get()
                .jsonPath("get[0] $[?(@.type=='Page')].webSocketDebuggerUrl").asString();
        Long timeOut = DriverUtils.getTimeOut(options);
        EdgeDevToolsDriver edge = new EdgeDevToolsDriver(command, http, webSocketUrl, false, timeOut);
        // edge.activate(); // not supported
        edge.enablePageEvents();
        return edge;
    }

    @Override
    public void activate() {
        // not supported apparently
    }

    @Override
    protected int getWaitInterval() {
        return 1000;
    }

    @Override
    public void setLocation(String url) {
        DevToolsMessage dtm = method("Page.navigate").param("url", url).send();
        waitForEvalTrue("document.readyState=='complete'");
        currentUrl = url;
    }

    @Override
    public void input(String id, String value) {
        eval(DriverUtils.selectorScript(id) + ".value = \"" + value + "\"", null);
    }

    @Override
    public void close() {
        // eval("window.close()", null); // this brings up an alert
    }

    @Override
    public void quit() {
        close();
        if (command != null) {
            // TODO this does not work because the command never blocks on windows
            command.close();
        }
    }
}
