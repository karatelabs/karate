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
package com.intuit.karate.web.edge;

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
 * @author pthomas3
 */
public class EdgeDevToolsDriver extends DevToolsDriver {

    public EdgeDevToolsDriver(CommandThread command, String webSocketUrl, boolean headless, long timeOut) {
        super(command, webSocketUrl, headless, timeOut);
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
        String executable = (String) options.get("executable");
        String uniqueName = System.currentTimeMillis() + "";
        File profileDir = new File(Engine.getBuildDir() + File.separator + "chrome" + uniqueName);
        List<String> args = Arrays.asList(executable,
                "--devtools-server-port", port + "");
        Boolean headless = (Boolean) options.get("headless");
        if (headless == null) {
            headless = false;
        }
        if (headless) {
            args = new ArrayList(args);
            args.add("--headless");
        }
        String logFile = profileDir.getPath() + File.separator + "karate.log";
        CommandThread command = null;
        if (executable != null) {
            command = new CommandThread(DevToolsDriver.class, logFile, profileDir, args.toArray(new String[]{}));
            command.start();
        }
        Http http = Http.forUrl("http://" + host + ":" + port);
        String webSocketUrl = http.path("json", "list").get()
                .jsonPath("get[0] $[?(@.type=='Page')].webSocketDebuggerUrl").asString();
        Long timeOut = DriverUtils.getTimeOut(options);
        EdgeDevToolsDriver edge = new EdgeDevToolsDriver(command, webSocketUrl, headless, timeOut);
        edge.activate();
        edge.enablePageEvents();
        return edge;
    }

}
