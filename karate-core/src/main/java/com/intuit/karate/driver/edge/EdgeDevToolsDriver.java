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
import com.intuit.karate.Logger;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.shell.CommandThread;
import com.intuit.karate.driver.DevToolsDriver;
import com.intuit.karate.driver.DriverOptions;

import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class EdgeDevToolsDriver extends DevToolsDriver {

    public EdgeDevToolsDriver(DriverOptions options, CommandThread command, String webSocketUrl) {
        super(options, command, webSocketUrl);
    }

    public static EdgeDevToolsDriver start(ScenarioContext context, Map<String, Object> map, Logger logger) {
        DriverOptions options = new DriverOptions(context, map, logger, 9222, "MicrosoftEdge");
        options.arg("--devtools-server-port");
        options.arg(options.port + "");
        options.arg("about:blank");
        CommandThread command = options.startProcess();
        Http http = Http.forUrl(options.driverLogger, "http://" + options.host + ":" + options.port);
        String webSocketUrl = http.path("json", "list").get()
                .jsonPath("get[0] $[?(@.type=='Page')].webSocketDebuggerUrl").asString();
        EdgeDevToolsDriver edge = new EdgeDevToolsDriver(options, command, webSocketUrl);
        // edge.activate(); // not supported
        edge.enablePageEvents();
        return edge;
    }

    @Override
    public void activate() {
        // not supported apparently
    }

    @Override
    public void setLocation(String url) {
        method("Page.navigate").param("url", url).send();
        waitUntil("document.readyState == 'complete'");
        currentUrl = url;
    }

    @Override
    public void input(String id, String value) {
        evaluate(options.elementSelector(id) + ".value = \"" + value + "\"", null);
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
