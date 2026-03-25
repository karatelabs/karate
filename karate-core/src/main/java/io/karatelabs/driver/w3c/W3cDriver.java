/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.driver.w3c;

import io.karatelabs.driver.BaseElement;
import io.karatelabs.driver.Dialog;
import io.karatelabs.driver.DialogHandler;
import io.karatelabs.driver.Driver;
import io.karatelabs.driver.DriverException;
import io.karatelabs.driver.DriverOptions;
import io.karatelabs.driver.Element;
import io.karatelabs.driver.InterceptHandler;
import io.karatelabs.driver.Keys;
import io.karatelabs.driver.Locators;
import io.karatelabs.driver.Mouse;
import io.karatelabs.driver.PageLoadStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * W3C WebDriver protocol driver implementation.
 * Single class supporting all W3C-compatible browser drivers (chromedriver, geckodriver,
 * safaridriver, msedgedriver) via the {@link W3cBrowserType} enum.
 *
 * <p>Uses java.net.http.HttpClient for W3C protocol communication,
 * cleanly separated from scenario HTTP state.</p>
 *
 * <p>CDP-only operations (intercept, PDF, ariaTree, Mouse) throw
 * {@link UnsupportedOperationException}.</p>
 */
public class W3cDriver implements Driver {

    private static final Logger logger = LoggerFactory.getLogger(W3cDriver.class);

    private final W3cSession session;
    private final W3cDriverOptions options;
    private final Process driverProcess;
    private volatile boolean terminated = false;

    private W3cDriver(W3cSession session, W3cDriverOptions options, Process driverProcess) {
        this.session = session;
        this.options = options;
        this.driverProcess = driverProcess;
    }

    // ========== Factory Methods ==========

    /**
     * Start a WebDriver from a config map.
     * If webDriverUrl is set, connects to remote. Otherwise, launches the driver process.
     */
    public static W3cDriver start(Map<String, Object> config) {
        W3cDriverOptions opts = W3cDriverOptions.fromMap(config);
        if (opts.isRemote()) {
            return connect(opts.getWebDriverUrl(), opts);
        }
        return launch(opts);
    }

    /**
     * Connect to an existing WebDriver server (remote hub, SauceLabs, etc.).
     */
    public static W3cDriver connect(String webDriverUrl, W3cDriverOptions opts) {
        logger.info("Connecting to W3C WebDriver at: {}", webDriverUrl);
        W3cSession session = W3cSession.create(webDriverUrl, opts.buildSessionPayload(), opts.getTimeoutDuration());
        return new W3cDriver(session, opts, null);
    }

    /**
     * Launch a local WebDriver process and create a session.
     */
    private static W3cDriver launch(W3cDriverOptions opts) {
        W3cBrowserType browserType = opts.getBrowserType();
        int port = opts.getPort();
        String executable = opts.getExecutable();

        logger.info("Launching {} on port {}", executable, port);

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add(browserType.formatPortArg(port));
        command.addAll(opts.getAddOptions());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Wait for the driver to start accepting connections
            waitForPort("localhost", port, opts.getTimeoutDuration().toMillis());
            logger.info("{} started on port {}", executable, port);

            String baseUrl = "http://localhost:" + port;
            W3cSession session = W3cSession.create(baseUrl, opts.buildSessionPayload(), opts.getTimeoutDuration());
            return new W3cDriver(session, opts, process);
        } catch (IOException e) {
            throw new DriverException("Failed to start " + executable + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get the underlying W3C session for direct protocol access.
     */
    public W3cSession getSession() {
        return session;
    }

    // ========== CoreDriver Tier 1: Essential Primitives ==========

    @Override
    @SuppressWarnings("unchecked")
    public Object eval(String js) {
        Object result = session.executeScript(js);
        // Auto-wrap W3C element references
        if (W3cSession.isElementReference(result)) {
            String elementId = W3cSession.elementIdFrom(result);
            return new W3cElement(this, "(eval result)", true, elementId, session);
        }
        // Auto-wrap lists of element references
        if (result instanceof List) {
            List<Object> list = (List<Object>) result;
            boolean hasElements = false;
            for (Object item : list) {
                if (W3cSession.isElementReference(item)) {
                    hasElements = true;
                    break;
                }
            }
            if (hasElements) {
                List<Object> wrapped = new ArrayList<>();
                for (Object item : list) {
                    if (W3cSession.isElementReference(item)) {
                        String eid = W3cSession.elementIdFrom(item);
                        wrapped.add(new W3cElement(this, "(eval result)", true, eid, session));
                    } else {
                        wrapped.add(item);
                    }
                }
                return wrapped;
            }
        }
        return result;
    }

    @Override
    public void navigate(String url) {
        session.navigateTo(url);
    }

    @Override
    public Keys keys() {
        return new W3cKeys(session);
    }

    @Override
    public byte[] screenshot() {
        return session.screenshot();
    }

    @Override
    public void frame(Object target) {
        if (target == null) {
            session.switchFrame(null);
        } else if (target instanceof Integer) {
            session.switchFrame(target);
        } else {
            // Locator - find the element first, then switch
            String locator = target.toString();
            try {
                String elementId = findElementId(locator);
                Map<String, Object> elementRef = Map.of(W3cSession.W3C_ELEMENT_KEY, elementId);
                session.switchFrame(elementRef);
            } catch (Exception e) {
                // Try as index
                try {
                    session.switchFrame(Integer.parseInt(locator));
                } catch (NumberFormatException nfe) {
                    throw new DriverException("Failed to switch frame: " + target, e);
                }
            }
        }
    }

    @Override
    public void dialog(boolean accept, String input) {
        if (input != null) {
            session.sendAlertText(input);
        }
        if (accept) {
            session.acceptAlert();
        } else {
            session.dismissAlert();
        }
    }

    // ========== CoreDriver Tier 2: Extended Primitives ==========

    @Override
    public Mouse mouse() {
        throw new UnsupportedOperationException(
                "Mouse coordinate input not supported on WebDriver backend. "
                        + "Use click(locator) instead.");
    }

    @Override
    public byte[] pdf(Map<String, Object> pdfOptions) {
        throw new UnsupportedOperationException(
                "PDF generation not supported on WebDriver backend");
    }

    @Override
    public Map<String, Object> cookie(String name) {
        return session.getCookie(name);
    }

    @Override
    public void cookie(Map<String, Object> cookie) {
        session.addCookie(cookie);
    }

    @Override
    public void intercept(List<String> patterns, InterceptHandler handler) {
        throw new UnsupportedOperationException(
                "Request interception not supported on WebDriver backend");
    }

    @Override
    public void window(String operation) {
        switch (operation) {
            case "maximize" -> session.maximizeWindow();
            case "minimize" -> session.minimizeWindow();
            case "fullscreen" -> session.fullscreenWindow();
            default -> logger.warn("Unknown window operation: {}", operation);
        }
    }

    @Override
    public void tab(Object target) {
        if (target instanceof Integer) {
            List<String> handles = session.getWindowHandles();
            int index = (Integer) target;
            if (index >= 0 && index < handles.size()) {
                session.switchWindow(handles.get(index));
            } else {
                throw new DriverException("Tab index out of bounds: " + index
                        + " (available: " + handles.size() + ")");
            }
        } else if (target instanceof String) {
            String titleOrUrl = (String) target;
            String currentHandle = session.getWindowHandle();
            List<String> handles = session.getWindowHandles();
            for (String handle : handles) {
                session.switchWindow(handle);
                String title = session.getTitle();
                String url = session.getUrl();
                if ((title != null && title.contains(titleOrUrl))
                        || (url != null && url.contains(titleOrUrl))) {
                    return; // Found it
                }
            }
            // Not found, restore original
            session.switchWindow(currentHandle);
            throw new DriverException("No tab found matching: " + titleOrUrl);
        }
    }

    // ========== Element Operations (override Driver defaults with native W3C calls) ==========

    @Override
    public Element click(String locator) {
        String elementId = findElementIdWithRetry(locator);
        session.clickElement(elementId);
        return new W3cElement(this, locator, true, elementId, session);
    }

    @Override
    public Element input(String locator, String value) {
        String elementId = findElementIdWithRetry(locator);
        session.clearElement(elementId);
        session.sendKeys(elementId, value);
        return new W3cElement(this, locator, true, elementId, session);
    }

    @Override
    public Element clear(String locator) {
        String elementId = findElementIdWithRetry(locator);
        session.clearElement(elementId);
        return new W3cElement(this, locator, true, elementId, session);
    }

    @Override
    public Element focus(String locator) {
        // No native W3C focus - use JS
        eval(Locators.focusJs(locator));
        return BaseElement.of(this, locator);
    }

    @Override
    public Element scroll(String locator) {
        eval(Locators.scrollJs(locator));
        return BaseElement.of(this, locator);
    }

    @Override
    public Element highlight(String locator) {
        eval(Locators.highlight(locator, options.getHighlightDuration()));
        return BaseElement.of(this, locator);
    }

    // ========== Element State (override with native W3C where possible) ==========

    @Override
    public String text(String locator) {
        try {
            String elementId = findElementIdWithRetry(locator);
            return session.getElementText(elementId);
        } catch (Exception e) {
            // Fallback to JS
            return (String) eval(Locators.textJs(locator));
        }
    }

    @Override
    public String html(String locator) {
        // No native W3C endpoint for outerHTML - use JS
        return (String) eval(Locators.outerHtmlJs(locator));
    }

    @Override
    public String value(String locator) {
        return (String) eval(Locators.valueJs(locator));
    }

    @Override
    public String attribute(String locator, String name) {
        try {
            String elementId = findElementIdWithRetry(locator);
            return session.getElementAttribute(elementId, name);
        } catch (Exception e) {
            return (String) eval(Locators.attributeJs(locator, name));
        }
    }

    @Override
    public boolean enabled(String locator) {
        try {
            String elementId = findElementIdWithRetry(locator);
            return session.isElementEnabled(elementId);
        } catch (Exception e) {
            Object result = eval(Locators.enabledJs(locator));
            return Boolean.TRUE.equals(result);
        }
    }

    @Override
    public boolean exists(String locator) {
        Object result = eval(Locators.existsJs(locator));
        return Boolean.TRUE.equals(result);
    }

    // ========== Locators ==========

    @Override
    public List<Element> locateAll(String locator) {
        // Use JS to find all and count
        Object countResult = eval(Locators.countJs(locator));
        int count = countResult instanceof Number ? ((Number) countResult).intValue() : 0;
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String indexedLocator = locator + ":nth-of-type(" + (i + 1) + ")";
            elements.add(new BaseElement(this, indexedLocator, true));
        }
        return elements;
    }

    // ========== Wait Methods ==========

    @Override
    public Element waitFor(String locator) {
        return waitFor(locator, options.getTimeoutDuration());
    }

    @Override
    public Element waitFor(String locator, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (exists(locator)) {
                return BaseElement.of(this, locator);
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitFor timeout: " + locator);
    }

    @Override
    public Element waitForAny(String locator1, String locator2) {
        return waitForAny(new String[]{locator1, locator2});
    }

    @Override
    public Element waitForAny(String[] locators) {
        return waitForAny(locators, options.getTimeoutDuration());
    }

    @Override
    public Element waitForAny(String[] locators, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (String locator : locators) {
                if (exists(locator)) {
                    return BaseElement.of(this, locator);
                }
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitForAny timeout: " + String.join(", ", locators));
    }

    @Override
    public Element waitForText(String locator, String expected) {
        return waitForText(locator, expected, options.getTimeoutDuration());
    }

    @Override
    public Element waitForText(String locator, String expected, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                String text = text(locator);
                if (text != null && text.contains(expected)) {
                    return BaseElement.of(this, locator);
                }
            } catch (Exception e) {
                // Element may not exist yet
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitForText timeout: " + locator + " expected: " + expected);
    }

    @Override
    public Element waitForEnabled(String locator) {
        return waitForEnabled(locator, options.getTimeoutDuration());
    }

    @Override
    public Element waitForEnabled(String locator, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                if (enabled(locator)) {
                    return BaseElement.of(this, locator);
                }
            } catch (Exception e) {
                // Element may not exist yet
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitForEnabled timeout: " + locator);
    }

    @Override
    public String waitForUrl(String expected) {
        return waitForUrl(expected, options.getTimeoutDuration());
    }

    @Override
    public String waitForUrl(String expected, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            String url = getUrl();
            if (url != null && url.contains(expected)) {
                return url;
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitForUrl timeout: expected URL containing: " + expected);
    }

    @Override
    public Element waitUntil(String locator, String expression) {
        return waitUntil(locator, expression, options.getTimeoutDuration());
    }

    @Override
    public Element waitUntil(String locator, String expression, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String js = Locators.scriptSelector(locator, expression);
        while (System.currentTimeMillis() < deadline) {
            try {
                Object result = eval(js);
                if (isTruthy(result)) {
                    return BaseElement.of(this, locator);
                }
            } catch (Exception e) {
                // Element may not exist yet
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitUntil timeout: " + locator + " expression: " + expression);
    }

    @Override
    public boolean waitUntil(String expression) {
        return waitUntil(expression, options.getTimeoutDuration());
    }

    @Override
    public boolean waitUntil(String expression, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                Object result = eval(expression);
                if (isTruthy(result)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitUntil timeout: " + expression);
    }

    @Override
    public Object waitUntil(java.util.function.Supplier<Object> condition) {
        return waitUntil(condition, options.getTimeoutDuration());
    }

    @Override
    public Object waitUntil(java.util.function.Supplier<Object> condition, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Object result = condition.get();
            if (isTruthy(result)) {
                return result;
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitUntil (supplier) timeout");
    }

    @Override
    public List<Element> waitForResultCount(String locator, int count) {
        return waitForResultCount(locator, count, options.getTimeoutDuration());
    }

    @Override
    public List<Element> waitForResultCount(String locator, int count, java.time.Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            List<Element> results = locateAll(locator);
            if (results.size() == count) {
                return results;
            }
            sleep(options.getRetryInterval());
        }
        throw new DriverException("waitForResultCount timeout: " + locator + " expected count: " + count);
    }

    // ========== Navigation ==========

    @Override
    public String getUrl() {
        return session.getUrl();
    }

    @Override
    public String getTitle() {
        return session.getTitle();
    }

    // ========== Cookies ==========

    @Override
    public void deleteCookie(String name) {
        session.deleteCookie(name);
    }

    @Override
    public void clearCookies() {
        session.deleteAllCookies();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCookies() {
        return session.getAllCookies();
    }

    // ========== Window Management ==========

    @Override
    public Map<String, Object> getDimensions() {
        return session.getWindowRect();
    }

    @Override
    public void setDimensions(Map<String, Object> dimensions) {
        session.setWindowRect(dimensions);
    }

    // ========== Pages/Tabs ==========

    @Override
    public List<String> getPages() {
        return session.getWindowHandles();
    }

    // ========== Dialog (limited support) ==========

    @Override
    public Dialog getDialog() {
        try {
            String text = session.getAlertText();
            if (text != null) {
                return new W3cDialog(session, text);
            }
        } catch (Exception e) {
            // No alert present
        }
        return null;
    }

    @Override
    public void onDialog(DialogHandler handler) {
        throw new UnsupportedOperationException(
                "Dialog callback handler not supported on WebDriver backend. "
                        + "Use dialog(true/false) after the dialog appears.");
    }

    // ========== Navigation (abstract in Driver) ==========

    @Override
    public void setUrl(String url) {
        session.navigateTo(url);
    }

    @Override
    public void waitForPageLoad(PageLoadStrategy strategy) {
        // W3C handles page load internally - navigation commands are blocking
    }

    @Override
    public void waitForPageLoad(PageLoadStrategy strategy, java.time.Duration timeout) {
        // W3C handles page load internally
    }

    @Override
    public void refresh() {
        session.refresh();
    }

    @Override
    public void reload() {
        session.refresh(); // W3C doesn't distinguish between refresh and reload
    }

    @Override
    public void back() {
        session.back();
    }

    @Override
    public void forward() {
        session.forward();
    }

    // ========== JavaScript Evaluation ==========

    @Override
    public Object script(String expression) {
        return eval(expression);
    }

    // ========== Screenshot ==========

    @Override
    public byte[] screenshot(boolean embed) {
        return screenshot(); // embed is handled by the reporting layer
    }

    // ========== Dialog (additional abstract methods) ==========

    @Override
    public String getDialogText() {
        try {
            return session.getAlertText();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void dialog(boolean accept) {
        dialog(accept, null);
    }

    // ========== Frame Switching (Driver interface methods) ==========

    @Override
    public void switchFrame(int index) {
        frame(index);
    }

    @Override
    public void switchFrame(String locator) {
        frame(locator);
    }

    @Override
    public Map<String, Object> getCurrentFrame() {
        return null; // W3C doesn't expose current frame info directly
    }

    // ========== Window Management (abstract methods) ==========

    @Override
    public void maximize() {
        window("maximize");
    }

    @Override
    public void minimize() {
        window("minimize");
    }

    @Override
    public void fullscreen() {
        window("fullscreen");
    }

    @Override
    public void activate() {
        // W3C doesn't have a direct "bring to front" command
        // Some drivers support it via executeScript
        try {
            eval("window.focus()");
        } catch (Exception e) {
            logger.debug("activate() not supported: {}", e.getMessage());
        }
    }

    // ========== PDF ==========

    @Override
    public byte[] pdf() {
        return pdf(null);
    }

    // ========== Mouse (additional overloads) ==========

    @Override
    public Mouse mouse(String locator) {
        throw new UnsupportedOperationException(
                "Mouse coordinate input not supported on WebDriver backend");
    }

    @Override
    public Mouse mouse(Number x, Number y) {
        throw new UnsupportedOperationException(
                "Mouse coordinate input not supported on WebDriver backend");
    }

    // ========== Pages/Tabs ==========

    @Override
    public void switchPage(String titleOrUrl) {
        tab(titleOrUrl);
    }

    @Override
    public void switchPage(int index) {
        tab(index);
    }

    // ========== Request Interception ==========

    @Override
    public void intercept(InterceptHandler handler) {
        throw new UnsupportedOperationException(
                "Request interception not supported on WebDriver backend");
    }

    @Override
    public void stopIntercept() {
        throw new UnsupportedOperationException(
                "Request interception not supported on WebDriver backend");
    }

    // ========== Lifecycle ==========

    @Override
    public void close() {
        quit();
    }

    // ========== Lifecycle (original) ==========

    @Override
    public void quit() {
        if (terminated) return;
        terminated = true;
        try {
            session.deleteSession();
        } catch (Exception e) {
            logger.warn("Error deleting session: {}", e.getMessage());
        }
        if (driverProcess != null) {
            driverProcess.destroyForcibly();
            logger.info("Driver process terminated");
        }
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    // ========== Private Helpers ==========

    /**
     * Find element with auto-retry (matches CDP auto-wait behavior).
     */
    private String findElementIdWithRetry(String locator) {
        int retries = options.getRetryCount();
        for (int i = 0; i <= retries; i++) {
            try {
                return findElementId(locator);
            } catch (Exception e) {
                if (i == retries) {
                    throw new DriverException("Element not found after " + retries + " retries: " + locator, e);
                }
                sleep(options.getRetryInterval());
            }
        }
        throw new DriverException("Element not found: " + locator);
    }

    /**
     * Find element by locator, using JS evaluation (supports all Karate locator types).
     */
    private String findElementId(String locator) {
        // Use JS to find element (supports CSS, XPath, wildcard, shadow DOM)
        String js = "return " + Locators.selector(locator);
        Object result = session.executeScript(js);
        if (W3cSession.isElementReference(result)) {
            return W3cSession.elementIdFrom(result);
        }
        throw new DriverException("Element not found: " + locator);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitForPort(String host, int port, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket(host, port)) {
                return; // Port is open
            } catch (IOException e) {
                sleep(200);
            }
        }
        throw new DriverException("Timeout waiting for WebDriver on " + host + ":" + port);
    }

    // ========== W3C Dialog ==========

    private static class W3cDialog implements Dialog {
        private final W3cSession session;
        private final String text;
        private boolean handled = false;

        W3cDialog(W3cSession session, String text) {
            this.session = session;
            this.text = text;
        }

        @Override
        public String getMessage() {
            return text;
        }

        @Override
        public String getType() {
            return "alert"; // W3C doesn't expose dialog type
        }

        @Override
        public String getDefaultPrompt() {
            return null; // W3C doesn't expose default prompt value
        }

        @Override
        public void accept() {
            session.acceptAlert();
            handled = true;
        }

        @Override
        public void accept(String promptText) {
            if (promptText != null) {
                session.sendAlertText(promptText);
            }
            session.acceptAlert();
            handled = true;
        }

        @Override
        public void dismiss() {
            session.dismissAlert();
            handled = true;
        }

        @Override
        public boolean isHandled() {
            return handled;
        }
    }

}
