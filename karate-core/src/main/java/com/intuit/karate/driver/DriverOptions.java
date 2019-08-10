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
package com.intuit.karate.driver;

import com.intuit.karate.Config;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.android.AndroidDriver;
import com.intuit.karate.driver.chrome.Chrome;
import com.intuit.karate.driver.chrome.ChromeWebDriver;
import com.intuit.karate.driver.edge.EdgeDevToolsDriver;
import com.intuit.karate.driver.edge.MicrosoftWebDriver;
import com.intuit.karate.driver.firefox.GeckoWebDriver;
import com.intuit.karate.driver.ios.IosDriver;
import com.intuit.karate.driver.safari.SafariWebDriver;
import com.intuit.karate.driver.windows.WinAppDriver;
import com.intuit.karate.shell.Command;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class DriverOptions {

    public static final long DEFAULT_TIMEOUT = 30 * 1000; // 30 seconds    

    public final Map<String, Object> options;
    public final long timeout;
    public final boolean start;
    public final String executable;
    public final String type;
    public final int port;
    public final String host = "localhost";
    public final boolean headless;
    public final boolean showProcessLog;
    public final boolean showDriverLog;
    public final Logger logger;
    public final Logger processLogger;
    public final Logger driverLogger;
    public final String uniqueName;
    public final File workingDir;
    public final String workingDirPath;
    public final String processLogFile;
    public final int maxPayloadSize;
    public final List<String> addOptions;
    public final List<String> args = new ArrayList();
    public final Target target;

    // mutable during a test
    private boolean retryEnabled;
    private Integer retryInterval = null;
    private Integer retryCount = null;
    private String submitTarget = null;

    // mutable when we return from called features
    private ScenarioContext context;

    public static final String SCROLL_JS_FUNCTION = "function(e){ var d = window.getComputedStyle(e).display;"
            + " while(d == 'none'){ e = e.parentElement; d = window.getComputedStyle(e).display }"
            + " e.scrollIntoView({block: 'center'}) }";

    public void setContext(ScenarioContext context) {
        this.context = context;
    }

    public ScenarioContext getContext() {
        return context;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public String getSubmitTarget() {
        return submitTarget;
    }

    public void setSubmitTarget(String submitTarget) {
        this.submitTarget = submitTarget;
    }

    private <T> T get(String key, T defaultValue) {
        T temp = (T) options.get(key);
        return temp == null ? defaultValue : temp;
    }

    public DriverOptions(ScenarioContext context, Map<String, Object> options, Logger logger, int defaultPort, String defaultExecutable) {
        this.context = context;
        this.options = options;
        this.logger = logger == null ? new Logger(getClass()) : logger;
        timeout = get("timeout", DEFAULT_TIMEOUT);
        type = get("type", null);
        port = get("port", defaultPort);
        start = get("start", true);
        executable = get("executable", defaultExecutable);
        headless = get("headless", false);
        showProcessLog = get("showProcessLog", false);
        addOptions = get("addOptions", null);
        uniqueName = type + "_" + System.currentTimeMillis();
        String packageName = getClass().getPackage().getName();
        processLogger = showProcessLog ? this.logger : new Logger(packageName + "." + uniqueName);
        showDriverLog = get("showDriverLog", false);
        driverLogger = showDriverLog ? this.logger : new Logger(packageName + "." + uniqueName);
        if (executable != null) {
            if (executable.startsWith(".")) { // honor path even when we set working dir
                args.add(new File(executable).getAbsolutePath());
            } else {
                args.add(executable);
            }
        }
        workingDir = new File(FileUtils.getBuildDir() + File.separator + uniqueName);
        workingDirPath = workingDir.getAbsolutePath();
        processLogFile = workingDir.getPath() + File.separator + type + ".log";
        maxPayloadSize = get("maxPayloadSize", 4194304);
        target = get("target", null);
    }

    public void arg(String arg) {
        args.add(arg);
    }

    public Command startProcess() {
        if (target != null || !start) {
            return null;
        }
        if (addOptions != null) {
            args.addAll(addOptions);
        }
        Command command = new Command(processLogger, uniqueName, processLogFile, workingDir, args.toArray(new String[]{}));
        command.start();
        waitForPort(host, port);
        return command;
    }

    public static Driver start(ScenarioContext context, Map<String, Object> options, Logger logger) {
        Target target = (Target) options.get("target");
        if (target != null) {
            target.setLogger(logger);
            logger.debug("custom target configured, calling start()");
            Map<String, Object> map = target.start();
            logger.debug("custom target returned options: {}", map);
            options.putAll(map);
        }
        String type = (String) options.get("type");
        if (type == null) {
            logger.warn("type was null, defaulting to 'chrome'");
            type = "chrome";
        }
        try { // to make troubleshooting errors easier
            switch (type) {
                case "chrome":
                    return Chrome.start(context, options, logger);
                case "msedge":
                    return EdgeDevToolsDriver.start(context, options, logger);
                case "chromedriver":
                    return ChromeWebDriver.start(context, options, logger);
                case "geckodriver":
                    return GeckoWebDriver.start(context, options, logger);
                case "safaridriver":
                    return SafariWebDriver.start(context, options, logger);
                case "mswebdriver":
                    return MicrosoftWebDriver.start(context, options, logger);
                case "winappdriver":
                    return WinAppDriver.start(context, options, logger);
                case "android":
                    return AndroidDriver.start(context, options, logger);
                case "ios":
                    return IosDriver.start(context, options, logger);
                default:
                    logger.warn("unknown driver type: {}, defaulting to 'chrome'", type);
                    return Chrome.start(context, options, logger);
            }
        } catch (Exception e) {
            String message = "driver config / start failed: " + e.getMessage() + ", options: " + options;
            logger.error(message);
            if (target != null) {
                target.stop();
            }
            throw new RuntimeException(message, e);
        }
    }

    public String selector(String locator) {
        return selector("*", locator);
    }

    private static String preProcessIfWildCard(String elementName, String locator) {
        if (locator.startsWith("^")) {
            return "//" + elementName + "[text()='" + locator.substring(1) + "']";
        }
        if (locator.startsWith("*")) {
            return "//" + elementName + "[contains(text(),'" + locator.substring(1) + "')]";
        }
        return locator;
    }

    public String selector(String elementName, String locator) {
        locator = preProcessIfWildCard(elementName, locator);
        if (locator.startsWith("/")) { // XPathResult.FIRST_ORDERED_NODE_TYPE = 9
            return "document.evaluate(\"" + locator + "\", document, null, 9, null).singleNodeValue";
        }
        return "document.querySelector(\"" + locator + "\")";
    }

    public int getRetryInterval() {
        if (retryInterval != null) {
            return retryInterval;
        }
        if (context == null) {
            return Config.DEFAULT_RETRY_INTERVAL;
        } else {
            return context.getConfig().getRetryInterval();
        }
    }

    public int getRetryCount() {
        if (retryCount != null) {
            return retryCount;
        }
        if (context == null) {
            return Config.DEFAULT_RETRY_COUNT;
        } else {
            return context.getConfig().getRetryCount();
        }
    }

    public String wrapInFunctionInvoke(String text) {
        return "(function(){ " + text + " })()";
    }

    public String highlighter(String locator) {
        String e = selector(locator);
        String temp = "var e = " + e + ";"
                + " var old = e.getAttribute('style');"
                + " e.setAttribute('style', 'background: yellow; border: 2px solid red;');"
                + " setTimeout(function(){ e.setAttribute('style', old) }, 3000);";
        return wrapInFunctionInvoke(temp);
    }

    public String optionSelector(String id, String text) {
        boolean textEquals = text.startsWith("^");
        boolean textContains = text.startsWith("*");
        String condition;
        if (textEquals || textContains) {
            text = text.substring(1);
            condition = textContains ? "e.options[i].text.indexOf(t) !== -1" : "e.options[i].text === t";
        } else {
            condition = "e.options[i].value === t";
        }
        String e = selector(id);
        String temp = "var e = " + e + "; var t = \"" + text + "\";"
                + " for (var i = 0; i < e.options.length; ++i)"
                + " if (" + condition + ") e.options[i].selected = true";
        return wrapInFunctionInvoke(temp);
    }

    public String optionSelector(String id, int index) {
        String e = selector(id);
        String temp = "var e = " + e + "; var t = " + index + ";"
                + " for (var i = 0; i < e.options.length; ++i)"
                + " if (i === t) e.options[i].selected = true";
        return wrapInFunctionInvoke(temp);
    }

    private String fun(String expression) {
        char first = expression.charAt(0);
        return (first == '_' || first == '!') ? "function(_){ return " + expression + " }" : expression;
    }

    public String selectorScript(String locator, String expression) {
        String temp = "var fun = " + fun(expression) + "; var e = " + selector(locator) + "; return fun(e)";
        return wrapInFunctionInvoke(temp);
    }

    public String selectorAllScript(String locator, String expression) {
        locator = preProcessIfWildCard("*", locator);
        boolean isXpath = locator.startsWith("/");
        String selector;
        if (isXpath) {
            selector = "document.evaluate(\"" + locator + "\", document, null, 5, null)";
        } else {
            selector = "document.querySelectorAll(\"" + locator + "\")";
        }
        String temp = "var res = []; var fun = " + fun(expression) + "; var es = " + selector + "; ";
        if (isXpath) {
            temp = temp + "var e = null; while(e = es.iterateNext()) res.push(fun(e)); return res";
        } else {
            temp = temp + "es.forEach(function(e){ res.push(fun(e)) }); return res";
        }
        return wrapInFunctionInvoke(temp);
    }

    public void sleep() {
        sleep(getRetryInterval());
    }

    public void sleep(int millis) {
        if (millis == 0) {
            return;
        }
        try {
            processLogger.debug("sleeping for millis: {}", millis);
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean waitForPort(String host, int port) {
        int attempts = 0;
        do {
            SocketAddress address = new InetSocketAddress(host, port);
            try {
                processLogger.debug("poll attempt #{} for port to be ready - {}:{}", attempts, host, port);
                SocketChannel sock = SocketChannel.open(address);
                sock.close();
                return true;
            } catch (IOException e) {
                sleep(250);
            }
        } while (attempts++ < 19);
        return false;
    }

    public Map<String, Object> newMapWithSelectedKeys(Map<String, Object> map, String... keys) {
        Map<String, Object> out = new HashMap(keys.length);
        for (String key : keys) {
            Object o = map.get(key);
            if (o != null) {
                out.put(key, o);
            }
        }
        return out;
    }

    public String removeProtocol(String url) {
        int pos = url.indexOf("://");
        return pos == -1 ? url : url.substring(pos + 3);
    }

    public void embedPngImage(byte[] bytes) {
        if (context != null) { // can be null if chrome java api
            context.embed(bytes, "image/png");
        }
    }

    public static final Set<String> DRIVER_METHOD_NAMES = new HashSet();

    static {
        for (Method m : Driver.class.getDeclaredMethods()) {
            DRIVER_METHOD_NAMES.add(m.getName());
        }
    }

    public void disableRetry() {
        retryEnabled = false;
        retryCount = null;
        retryInterval = null;
    }

    public void enableRetry(Integer count, Integer interval) {
        retryEnabled = true;
        retryCount = count; // can be null
        retryInterval = interval; // can be null
    }

    public Element waitUntil(Driver driver, String locator, String expression) {
        long startTime = System.currentTimeMillis();
        String js = selectorScript(locator, expression);
        boolean found = driver.waitUntil(js);
        if (!found) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            throw new RuntimeException("wait failed for: " + locator
                    + " and condition: " + expression + " after " + elapsedTime + " milliseconds");
        }
        return driver.element(locator, true);
    }

    public Element waitForAny(Driver driver, String... locators) {
        long startTime = System.currentTimeMillis();
        List<String> list = Arrays.asList(locators);
        Iterator<String> iterator = list.iterator();
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            String locator = iterator.next();
            String js = selector(locator);
            sb.append("(").append(js).append(" != null)");
            if (iterator.hasNext()) {
                sb.append(" || ");
            }
        }
        boolean found = driver.waitUntil(sb.toString());
        // important: un-set the retry flag        
        disableRetry();
        if (!found) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            throw new RuntimeException("wait failed for: " + list + " after " + elapsedTime + " milliseconds");
        }
        if (locators.length == 1) {
            return driver.element(locators[0], true);
        }
        for (String locator : locators) {
            Element temp = driver.exists(locator);
            if (temp.isExists()) {
                return temp;
            }
        }
        // this should never happen
        throw new RuntimeException("unexpected wait failure for locators: " + list);
    }

    public Element exists(Driver driver, String locator) {
        String js = selector(locator);
        String evalJs = js + " != null";
        Object o = driver.script(evalJs);
        if (o instanceof Boolean && (Boolean) o) {
            return driver.element(locator, true);
        } else {
            return new MissingElement(locator);
        }
    }

}
