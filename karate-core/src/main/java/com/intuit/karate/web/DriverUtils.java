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
package com.intuit.karate.web;

import com.intuit.karate.web.chrome.ChromeDevToolsDriver;
import com.intuit.karate.web.chrome.ChromeWebDriver;
import com.intuit.karate.web.edge.EdgeDevToolsDriver;
import com.intuit.karate.web.edge.MicrosoftWebDriver;
import com.intuit.karate.web.firefox.GeckoWebDriver;
import com.intuit.karate.web.safari.SafariWebDriver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;

import com.intuit.karate.web.winapp.WinAppDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DriverUtils {

    private static final Logger logger = LoggerFactory.getLogger(DriverUtils.class);

    private DriverUtils() {
        // only static methods
    }

    public static final long TIME_OUT_DEFAULT = 30 * 1000; // 30 seconds

    public static long getTimeOut(Map<String, Object> options) {
        Object temp = options.get("timeout");
        if (temp == null) {
            return DriverUtils.TIME_OUT_DEFAULT;
        } else {
            return Long.valueOf(temp.toString());
        }
    }

    public static Driver construct(Map<String, Object> options) {
        String type = (String) options.get("type");
        if (type == null) {
            logger.warn("type was null, defaulting to 'chrome'");
            type = "chrome";
        }
        switch (type) {
            case "chrome":
                return ChromeDevToolsDriver.start(options);
            case "msedge":
                return EdgeDevToolsDriver.start(options);
            case "chromedriver":
                return ChromeWebDriver.start(options);
            case "geckodriver":
                return GeckoWebDriver.start(options);
            case "safaridriver":
                return SafariWebDriver.start(options);
            case "mswebdriver":
                return MicrosoftWebDriver.start(options);
            case "winappdriver":
                return WinAppDriver.start(options);
            default:
                logger.warn("unknown driver type: {}, defaulting to 'chrome'", type);
                return ChromeDevToolsDriver.start(options);
        }
    }

    public static String selectorScript(String id) {
        if (id.startsWith("/")) {
            return "document.evaluate(\"" + id + "\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue";
        } else {
            return "document.querySelector(\"" + id + "\")";
        }
    }

    public static void sleep(int millis) {
        if (millis == 0) {
            return;
        }
        try {
            logger.debug("sleeping for millis: {}", millis);
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean waitForPort(String host, int port) {
        int attempts = 0;
        do {
            SocketAddress address = new InetSocketAddress(host, port);            
            try {
                logger.debug("poll attempt #{} for port to be ready - {}:{}", attempts, host, port);
                SocketChannel sock = SocketChannel.open(address);
                sock.close();
                return true;
            } catch (IOException e) {                
                sleep(250);
            }
        } while (attempts++ < 3);
        return false;
    }

}
