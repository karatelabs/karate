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
import com.intuit.karate.Http;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.core.Embed;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.appium.AndroidDriver;
import com.intuit.karate.driver.chrome.Chrome;
import com.intuit.karate.driver.chrome.ChromeWebDriver;
import com.intuit.karate.driver.microsoft.EdgeDevToolsDriver;
import com.intuit.karate.driver.microsoft.IeWebDriver;
import com.intuit.karate.driver.microsoft.MsWebDriver;
import com.intuit.karate.driver.firefox.GeckoWebDriver;
import com.intuit.karate.driver.appium.IosDriver;
import com.intuit.karate.driver.microsoft.MsEdgeDriver;
import com.intuit.karate.driver.safari.SafariWebDriver;
import com.intuit.karate.driver.microsoft.WinAppDriver;
import com.intuit.karate.shell.Command;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public class DriverOptions {

    public final Map<String, Object> options;
    public final int timeout;
    public final boolean start;
    public final String executable;
    public final String type;
    public final int port;
    public final String host;
    public final int pollAttempts;
    public final int pollInterval;
    public final boolean headless;
    public final boolean showProcessLog;
    public final boolean showDriverLog;
    public final Logger logger;
    public final LogAppender appender;
    public final Logger processLogger;
    public final Logger driverLogger;
    public final String uniqueName;
    public final File workingDir;
    public final boolean disableNotifications;
    public final String userAgent;
    public final String userDataDir;
    public final String processLogFile;
    public final int maxPayloadSize;
    public final List<String> addOptions;
    public final List<String> args = new ArrayList<>();
    public final String webDriverUrl;
    public final String webDriverPath;
    public final Map<String, Object> webDriverSession;
    public final Map<String, Object> httpConfig;
    public final Target target;
    public final String beforeStart;
    public final String afterStop;
    public final String videoFile;
    public final boolean highlight;
    public final int highlightDuration;
    public final String attach;

    // mutable during a test
    private boolean retryEnabled;
    private Integer retryInterval = null;
    private Integer retryCount = null;
    private String preSubmitHash = null;

    private Integer timeoutOverride;

    // mutable when we return from called features
    private ScenarioContext context;

    public static final String SCROLL_JS_FUNCTION = "function(e){ var d = window.getComputedStyle(e).display;"
            + " while(d == 'none'){ e = e.parentElement; d = window.getComputedStyle(e).display }"
            + " e.scrollIntoView({block: 'center'}) }";

    public static final String KARATE_REF_GENERATOR = "function(e){"
            + " if (!document._karate) document._karate = { seq: (new Date()).getTime() };"
            + " var ref = 'ref' + document._karate.seq++; document._karate[ref] = e; return ref }";

    public void setContext(ScenarioContext context) {
        this.context = context;
    }

    public ScenarioContext getContext() {
        return context;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public String getPreSubmitHash() {
        return preSubmitHash;
    }

    public void setPreSubmitHash(String preSubmitHash) {
        this.preSubmitHash = preSubmitHash;
    }

    private <T> T get(String key, T defaultValue) {
        T temp = (T) options.get(key);
        return temp == null ? defaultValue : temp;
    }

    public DriverOptions(ScenarioContext context, Map<String, Object> options, LogAppender appender, int defaultPort, String defaultExecutable) {
        this.context = context;
        this.options = options;
        this.appender = appender;
        logger = new Logger(getClass());
        logger.setAppender(appender);
        timeout = get("timeout", Config.DEFAULT_TIMEOUT);
        type = get("type", null);
        start = get("start", true);
        executable = get("executable", defaultExecutable);
        headless = get("headless", false);
        showProcessLog = get("showProcessLog", false);
        addOptions = get("addOptions", null);
        uniqueName = type + "_" + System.currentTimeMillis();
        String packageName = getClass().getPackage().getName();
        processLogger = showProcessLog ? logger : new Logger(packageName + "." + uniqueName);
        showDriverLog = get("showDriverLog", false);
        driverLogger = showDriverLog ? logger : new Logger(packageName + "." + uniqueName);
        if (executable != null) {
            if (executable.startsWith(".")) { // honor path even when we set working dir
                args.add(new File(executable).getAbsolutePath());
            } else {
                args.add(executable);
            }
        }
        disableNotifications = get("disableNotifications", false);
        userAgent = get("userAgent", null);
        if (options.containsKey("userDataDir")) {
            String temp = get("userDataDir", null);
            if (temp != null) {
                workingDir = new File(temp);
                userDataDir = workingDir.getAbsolutePath();
            } else { // special case allow user-specified null
                userDataDir = null;
                workingDir = null;
            }
        } else {
            workingDir = new File(FileUtils.getBuildDir() + File.separator + uniqueName);
            userDataDir = workingDir.getAbsolutePath();
        }
        if (workingDir == null) {
            processLogFile = FileUtils.getBuildDir() + File.separator + uniqueName + ".log";
        } else {
            processLogFile = workingDir.getPath() + File.separator + type + ".log";
        }
        maxPayloadSize = get("maxPayloadSize", 4194304);
        target = get("target", null);
        host = get("host", "localhost");
        webDriverUrl = get("webDriverUrl", null);
        webDriverPath = get("webDriverPath", null);
        webDriverSession = get("webDriverSession", null);
        httpConfig = get("httpConfig", null);
        beforeStart = get("beforeStart", null);
        afterStop = get("afterStop", null);
        videoFile = get("videoFile", null);
        pollAttempts = get("pollAttempts", 20);
        pollInterval = get("pollInterval", 250);
        highlight = get("highlight", false);
        highlightDuration = get("highlightDuration", Config.DEFAULT_HIGHLIGHT_DURATION);
        attach = get("attach", null);

        // do this last to ensure things like logger, start-flag, webDriverUrl etc. are set
        port = resolvePort(defaultPort);
    }

    private int resolvePort(int defaultPort) {
        if (webDriverUrl != null) {
            return 0;
        }
        int preferredPort = get("port", defaultPort);
        if (start) {
            int freePort = Command.getFreePort(preferredPort);
            if (freePort != preferredPort) {
                logger.warn("preferred port {} not available, will use: {}", preferredPort, freePort);
            }
            return freePort;
        }
        return preferredPort;
    }

    public Http getHttp() {
        Http http = Http.forUrl(driverLogger.getAppender(), getUrlBase());
        if (httpConfig != null) {
            http.config(httpConfig);
        }
        return http;
    }

    private String getUrlBase() {
        if (webDriverUrl != null) {
            return webDriverUrl;
        }
        String urlBase = "http://" + host + ":" + port;
        if (webDriverPath != null) {
            return urlBase + webDriverPath;
        }
        return urlBase;
    }

    public void arg(String arg) {
        args.add(arg);
    }

    public Command startProcess() {
        if (beforeStart != null) {
            Command.execLine(null, beforeStart);
        }
        Command command;
        if (target != null || !start) {
            command = null;
        } else {
            if (addOptions != null) {
                args.addAll(addOptions);
            }
            command = new Command(false, processLogger, uniqueName, processLogFile, workingDir, args.toArray(new String[]{}));
            command.start();
        }
        if (start) { // wait for a slow booting browser / driver process
            waitForPort(host, port);
        }
        return command;
    }

    public static Driver start(ScenarioContext context, Map<String, Object> options, LogAppender appender) {
        Target target = (Target) options.get("target");
        Logger logger = context.logger;
        if (target != null) {
            logger.debug("custom target configured, calling start()");
            Map<String, Object> map = target.start(logger);
            logger.debug("custom target returned options: {}", map);
            options.putAll(map);
        }
        String type = (String) options.get("type");
        if (type == null) {
            logger.warn("type was null, defaulting to 'chrome'");
            type = "chrome";
            options.put("type", type);
        }
        try { // to make troubleshooting errors easier
            switch (type) {
                case "chrome":
                    return Chrome.start(context, options, appender);
                case "msedge":
                    return EdgeDevToolsDriver.start(context, options, appender);
                case "chromedriver":
                    return ChromeWebDriver.start(context, options, appender);
                case "geckodriver":
                    return GeckoWebDriver.start(context, options, appender);
                case "safaridriver":
                    return SafariWebDriver.start(context, options, appender);
                case "msedgedriver":
                    return MsEdgeDriver.start(context, options, appender);
                case "mswebdriver":
                    return MsWebDriver.start(context, options, appender);
                case "iedriver":
                    return IeWebDriver.start(context, options, appender);
                case "winappdriver":
                    return WinAppDriver.start(context, options, appender);
                case "android":
                    return AndroidDriver.start(context, options, appender);
                case "ios":
                    return IosDriver.start(context, options, appender);
                default:
                    logger.warn("unknown driver type: {}, defaulting to 'chrome'", type);
                    options.put("type", "chrome");
                    return Chrome.start(context, options, appender);
            }
        } catch (Exception e) {
            String message = "driver config / start failed: " + e.getMessage() + ", options: " + options;
            logger.error(message, e);
            if (target != null) {
                target.stop(logger);
            }
            throw new RuntimeException(message, e);
        }
    }

    private Map<String, Object> getSession(String browserName) {
        Map<String, Object> session = webDriverSession;
        if (session == null) {
            session = new HashMap();
        }
        Map<String, Object> capabilities = (Map) session.get("capabilities");
        if (capabilities == null) {
            capabilities = (Map) session.get("desiredCapabilities");
        }
        if (capabilities == null) {
            capabilities = new HashMap();
            session.put("capabilities", capabilities);
            Map<String, Object> alwaysMatch = new HashMap();
            capabilities.put("alwaysMatch", alwaysMatch);
            alwaysMatch.put("browserName", browserName);
        }
        return session;
    }

    // TODO abstract as method per implementation
    public Map<String, Object> getWebDriverSessionPayload() {
        switch (type) {
            case "chromedriver":
                return getSession("chrome");
            case "geckodriver":
                return getSession("firefox");
            case "safaridriver":
                return getSession("safari");
            case "msedgedriver":
            case "mswebdriver":
                return getSession("edge");
            case "iedriver":
                return getSession("internet explorer");
            default:
                // else user has to specify full payload via webDriverSession
                return getSession(type);
        }
    }

    public static String preProcessWildCard(String locator) {
        boolean contains;
        String tag, prefix, text;
        int index;
        int pos = locator.indexOf('}');
        if (pos == -1) {
            throw new RuntimeException("bad locator prefix: " + locator);
        }
        if (locator.charAt(1) == '^') {
            contains = true;
            prefix = locator.substring(2, pos);
        } else {
            contains = false;
            prefix = locator.substring(1, pos);
        }
        text = locator.substring(pos + 1);
        pos = prefix.indexOf(':');
        if (pos != -1) {
            String tagTemp = prefix.substring(0, pos);
            tag = tagTemp.isEmpty() ? "*" : tagTemp;
            String indexTemp = prefix.substring(pos + 1);
            if (indexTemp.isEmpty()) {
                index = 0;
            } else {
                try {
                    index = Integer.valueOf(indexTemp);
                } catch (Exception e) {
                    throw new RuntimeException("bad locator prefix: " + locator + ", " + e.getMessage());
                }
            }
        } else {
            tag = prefix.isEmpty() ? "*" : prefix;
            index = 0;
        }
        if (!tag.startsWith("/")) {
            tag = "//" + tag;
        }
        String xpath;
        if (contains) {
            xpath = tag + "[contains(normalize-space(text()),'" + text + "')]";
        } else {
            xpath = tag + "[normalize-space(text())='" + text + "']";
        }
        if (index == 0) {
            return xpath;
        }
        return "/(" + xpath + ")[" + index + "]";
    }

    private static final String DOCUMENT = "document";

    public static String selector(String locator) {
        return selector(locator, DOCUMENT);
    }

    public static String selector(String locator, String contextNode) {
        if (locator.startsWith("(")) {
            return locator; // pure js !
        }
        if (locator.startsWith("{")) {
            locator = preProcessWildCard(locator);
        }
        if (locator.startsWith("/")) { // XPathResult.FIRST_ORDERED_NODE_TYPE = 9
            if (locator.startsWith("/(")) {
                locator = locator.substring(1); // hack for wildcard with index (see preProcessWildCard last line)
            }
            return "document.evaluate(\"" + locator + "\", " + contextNode + ", null, 9, null).singleNodeValue";
        }
        return contextNode + ".querySelector(\"" + locator + "\")";
    }

    public void setTimeout(Integer timeout) {
        this.timeoutOverride = timeout;
    }

    public int getTimeout() {
        if (timeoutOverride != null) {
            return timeoutOverride;
        }
        return timeout;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
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

    public <T> T retry(Supplier<T> action, Predicate<T> condition, String logDescription, boolean failWithException) {
        long startTime = System.currentTimeMillis();
        int count = 0, max = getRetryCount();
        T result;
        boolean success;
        do {
            if (count > 0) {
                logger.debug("{} - retry #{}", logDescription, count);
                sleep();
            }
            result = action.get();
            success = condition.test(result);
        } while (!success && count++ < max);
        if (!success) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            String message = logDescription + ": failed after " + (count - 1) + " retries and " + elapsedTime + " milliseconds";
            logger.warn(message);
            if (failWithException) {
                throw new RuntimeException(message);
            }
        }
        return result;
    }

    public static String wrapInFunctionInvoke(String text) {
        return "(function(){ " + text + " })()";
    }

    private static final String HIGHLIGHT_FN = "function(e){ var old = e.getAttribute('style');"
            + " e.setAttribute('style', 'background: yellow; border: 2px solid red;');"
            + " setTimeout(function(){ e.setAttribute('style', old) }, %d) }";

    private static String highlightFn(int millis) {
        return String.format(HIGHLIGHT_FN, millis);
    }

    public String highlight(String locator, int millis) {
        String e = selector(locator);
        String temp = "var e = " + e + "; var fun = " + highlightFn(millis) + "; fun(e)";
        return wrapInFunctionInvoke(temp);
    }

    public String highlightAll(String locator, int millis) {
        return scriptAllSelector(locator, highlightFn(millis));
    }

    public String optionSelector(String locator, String text) {
        boolean textEquals = text.startsWith("{}");
        boolean textContains = text.startsWith("{^}");
        String condition;
        if (textEquals || textContains) {
            text = text.substring(text.indexOf('}') + 1);
            condition = textContains ? "e.options[i].text.indexOf(t) !== -1" : "e.options[i].text === t";
        } else {
            condition = "e.options[i].value === t";
        }
        String e = selector(locator);
        String temp = "var e = " + e + "; var t = \"" + text + "\";"
                + " for (var i = 0; i < e.options.length; ++i)"
                + " if (" + condition + ") { e.options[i].selected = true; e.dispatchEvent(new Event('change')) }";
        return wrapInFunctionInvoke(temp);
    }

    public String optionSelector(String id, int index) {
        String e = selector(id);
        String temp = "var e = " + e + "; var t = " + index + ";"
                + " for (var i = 0; i < e.options.length; ++i)"
                + " if (i === t) { e.options[i].selected = true; e.dispatchEvent(new Event('change')) }";
        return wrapInFunctionInvoke(temp);
    }

    private String fun(String expression) {
        char first = expression.charAt(0);
        return (first == '_' || first == '!') ? "function(_){ return " + expression + " }" : expression;
    }

    public String scriptSelector(String locator, String expression) {
        return scriptSelector(locator, expression, DOCUMENT);
    }

    public String scriptSelector(String locator, String expression, String contextNode) {
        String temp = "var fun = " + fun(expression) + "; var e = " + selector(locator, contextNode) + "; return fun(e)";
        return wrapInFunctionInvoke(temp);
    }

    public String scriptAllSelector(String locator, String expression) {
        return scriptAllSelector(locator, expression, DOCUMENT);
    }

    // the difference here from selector() is the use of querySelectorAll()
    // how the loop for XPath results has to be handled
    public String scriptAllSelector(String locator, String expression, String contextNode) {
        if (locator.startsWith("{")) {
            locator = preProcessWildCard(locator);
        }
        boolean isXpath = locator.startsWith("/");
        String selector;
        if (isXpath) { // XPathResult.ORDERED_NODE_ITERATOR_TYPE = 5
            selector = "document.evaluate(\"" + locator + "\", " + contextNode + ", null, 5, null)";
        } else {
            selector = contextNode + ".querySelectorAll(\"" + locator + "\")";
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
            processLogger.trace("sleeping for millis: {}", millis);
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
                sleep(pollInterval);
            }
        } while (attempts++ < pollAttempts);
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

    public void embedPngImage(byte[] bytes) {
        if (context != null) { // can be null if chrome java api
            context.embed(bytes, "image/png");
        }
    }

    public void embedContent(Embed embed) {
        if (context != null) {
            context.embed(embed);
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
        String js = scriptSelector(locator, expression);
        boolean found = driver.waitUntil(js);
        if (!found) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            throw new RuntimeException("wait failed for: " + locator
                    + " and condition: " + expression + " after " + elapsedTime + " milliseconds");
        }
        return DriverElement.locatorExists(driver, locator);
    }

    public String waitForUrl(Driver driver, String expected) {
        return retry(() -> driver.getUrl(), url -> url.contains(expected), "waitForUrl", true);
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
            return DriverElement.locatorExists(driver, locators[0]);
        }
        for (String locator : locators) {
            Element temp = driver.optional(locator);
            if (temp.isPresent()) {
                return temp;
            }
        }
        // this should never happen
        throw new RuntimeException("unexpected wait failure for locators: " + list);
    }

    public Element optional(Driver driver, String locator) {
        String js = selector(locator);
        String evalJs = js + " != null";
        Object o = driver.script(evalJs);
        if (o instanceof Boolean && (Boolean) o) {
            return DriverElement.locatorExists(driver, locator);
        } else {
            return new MissingElement(driver, locator);
        }
    }

    public static String karateLocator(String karateRef) {
        return "(document._karate." + karateRef + ")";
    }

    public String focusJs(String locator) {
        return "var e = " + selector(locator) + "; e.focus(); try { e.selectionStart = e.selectionEnd = e.value.length } catch(x) {}";
    }

    public List<Element> findAll(Driver driver, String locator) {
        List<String> list = driver.scriptAll(locator, DriverOptions.KARATE_REF_GENERATOR);
        List<Element> elements = new ArrayList(list.size());
        for (String karateRef : list) {
            String karateLocator = karateLocator(karateRef);
            elements.add(DriverElement.locatorExists(driver, karateLocator));
        }
        return elements;
    }

}
