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
package com.intuit.karate.driver.microsoft;

import com.intuit.karate.Http;
import com.intuit.karate.LogAppender;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.shell.Command;
import com.intuit.karate.driver.DevToolsDriver;
import com.intuit.karate.driver.DriverElement;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Element;

import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class EdgeDevToolsDriver extends DevToolsDriver {

    public EdgeDevToolsDriver(DriverOptions options, Command command, String webSocketUrl) {
        super(options, command, webSocketUrl);
    }

    public static EdgeDevToolsDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 9222, "MicrosoftEdge");
        options.arg("--devtools-server-port");
        options.arg(options.port + "");
        options.arg("about:blank");
        Command command = options.startProcess();
        Http http = options.getHttp();
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
    public void setUrl(String url) {
        method("Page.navigate").param("url", url).send();
        currentUrl = url;
    }

    @Override
    public Element input(String locator, String value) {
        eval(options.selector(locator) + ".value = \"" + value + "\"");
        return DriverElement.locatorExists(this, locator);
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
            command.close(true);
        }
    }
}
