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
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

    // Karate JS runtime — same driver.js used by CDP for wildcard locators, shadow DOM, etc.
    private static final String DRIVER_JS = loadResource("driver.js");

    private static String loadResource(String name) {
        try (InputStream is = W3cDriver.class.getResourceAsStream("/io/karatelabs/driver/" + name)) {
            if (is == null) {
                logger.warn("Resource not found: {}", name);
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to load resource: {}", name);
            return "";
        }
    }

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

    /**
     * Execute JavaScript via W3C executeScript.
     *
     * <p>Battle-tested pattern from v1 WebDriver: if JS execution fails, sleep once and
     * retry before throwing. This handles transient failures that occur when the page is
     * still loading or transitioning. The v1 codebase proved this single-retry approach
     * effective across thousands of real-world test suites.</p>
     *
     * <p>W3C element references in the result are auto-wrapped as W3cElement instances.</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object eval(String js) {
        // W3C executeScript wraps the script in `function anonymous() { <script> }`.
        // This means the script must use `return` for the value to come back.
        // But we can't blindly prefix "return" — statement blocks like `var e = ...; e.focus()`
        // would become `return var e = ...` which is a syntax error.
        //
        // Strategy (matching v1 prefixReturn + real-world patterns):
        // - Already has "return" → pass through
        // - Is an IIFE `(function(){ ... })()` → prefix "return" (IIFE evaluates to value)
        // - Starts with a bare expression (no var/let/const/if/for) → prefix "return"
        // - Statement blocks (var/let/const/if/for/try) → execute as-is (no return value)
        // Inject __kjs runtime if the script references it (wildcard locators, shadow DOM)
        if (js.contains("__kjs")) {
            ensureKjsRuntime();
        }
        String expression = prefixReturnIfNeeded(js);
        Object result;
        try {
            result = session.executeScript(expression);
        } catch (Exception e) {
            // v1 pattern: single retry on JS error — handles transient page-load timing issues
            logger.warn("javascript failed, will retry once: {}", e.getMessage());
            sleep(options.getRetryInterval());
            try {
                result = session.executeScript(expression);
            } catch (Exception e2) {
                logger.error("javascript failed twice: {}", e2.getMessage());
                throw new DriverException("javascript failed: " + e2.getMessage(), e2);
            }
        }
        return wrapElementReferences(result);
    }

    @SuppressWarnings("unchecked")
    private Object wrapElementReferences(Object result) {
        if (W3cSession.isElementReference(result)) {
            String elementId = W3cSession.elementIdFrom(result);
            return new W3cElement(this, "(eval result)", true, elementId, session);
        }
        if (result instanceof List) {
            List<Object> list = (List<Object>) result;
            boolean hasElements = list.stream().anyMatch(W3cSession::isElementReference);
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
            // W3C spec: POST /frame with {"id": null} switches to TOP-LEVEL context
            // This is different from parentFrame() which only goes up one level.
            // v1 switchFrame(null) meant "go back to main/top frame" which maps to this.
            session.switchFrame(null);
        } else if (target instanceof Integer) {
            int index = (Integer) target;
            if (index == -1) {
                // v1 convention: -1 means parent frame
                session.parentFrame();
            } else {
                session.switchFrame(index);
            }
        } else {
            // Improvement over v1: pass element reference directly to W3C switchFrame.
            // v1 had to iterate all iframe/frame elements to find the index because older
            // drivers (pre-W3C) didn't support element references. Since we're W3C-only,
            // all compliant drivers accept an element reference in POST /session/{id}/frame.
            // This is simpler (one call vs many), avoids race conditions (iframes added/removed
            // between find and iterate), and avoids cross-call element ID mismatch issues.
            String locator = target.toString();
            String frameElementId = findElementIdWithRetry(locator);
            Map<String, Object> elementRef = Map.of(W3cSession.W3C_ELEMENT_KEY, frameElementId);
            session.switchFrame(elementRef);
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

    // ========== Element Operations ==========
    //
    // V1 PATTERN: Almost all element operations use JS eval, NOT native W3C element endpoints.
    // This was a deliberate, battle-tested choice in v1:
    //   - click() uses JS .click() — more reliable across browsers than POST /element/{id}/click
    //   - text/html/value/attribute/enabled all use JS — avoids stale element reference issues
    //   - clear() uses JS value = '' — more consistent than POST /element/{id}/clear
    //   - ONLY input() uses native W3C sendKeys — because JS can't simulate real keyboard events
    //     that trigger framework event handlers (React, Vue, Angular)
    //
    // The single-retry pattern in eval() provides the retry safety net for all JS operations.

    @Override
    public Element click(String locator) {
        // v1 pattern: JS click, not W3C endpoint — more reliable, handles shadow DOM, custom elements
        eval(Locators.clickJs(locator));
        return BaseElement.existing(this, locator);
    }

    @Override
    public Element input(String locator, String value) {
        // For date/time inputs, use JS value assignment (sendKeys doesn't work for these)
        Boolean isDateTimeInput = (Boolean) eval(Locators.scriptSelector(locator,
                "_ && ['time','date','datetime-local','month','week'].indexOf(_.type) >= 0"));
        if (Boolean.TRUE.equals(isDateTimeInput)) {
            eval(Locators.inputJs(locator, value));
        } else {
            // v1 pattern: input is the ONE operation that uses native W3C sendKeys
            // because JS value assignment doesn't trigger framework input event handlers
            String elementId = findElementIdWithRetry(locator);
            eval(Locators.clearJs(locator));
            session.sendKeys(elementId, value);
        }
        return BaseElement.existing(this, locator);
    }

    @Override
    public Element clear(String locator) {
        // v1 pattern: JS value = '' — more consistent than W3C clear endpoint
        eval(Locators.clearJs(locator));
        return BaseElement.existing(this, locator);
    }

    @Override
    public Element focus(String locator) {
        eval(Locators.focusJs(locator));
        return BaseElement.existing(this, locator);
    }

    @Override
    public Element scroll(String locator) {
        eval(Locators.scrollJs(locator));
        return BaseElement.existing(this, locator);
    }

    @Override
    public Element highlight(String locator) {
        eval(Locators.highlight(locator, options.getHighlightDuration()));
        return BaseElement.existing(this, locator);
    }

    // ========== Element State (all via JS eval — v1 pattern) ==========
    //
    // V1 used evalReturn(locator, "textContent"), evalReturn(locator, "outerHTML") etc.
    // This approach avoids stale element reference errors that plague W3C element endpoints,
    // because each operation re-evaluates the locator in the browser context.

    @Override
    public String text(String locator) {
        return (String) eval(Locators.textJs(locator));
    }

    @Override
    public String html(String locator) {
        return (String) eval(Locators.outerHtmlJs(locator));
    }

    @Override
    public String value(String locator) {
        return (String) eval(Locators.valueJs(locator));
    }

    @Override
    public String attribute(String locator, String name) {
        return (String) eval(Locators.attributeJs(locator, name));
    }

    @Override
    public boolean enabled(String locator) {
        Object result = eval(Locators.enabledJs(locator));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean exists(String locator) {
        // exists() must NOT retry — it's a check, not an assertion
        try {
            String js = Locators.existsJs(locator);
            // Inject __kjs if needed (shadow DOM, wildcard locators)
            if (js.contains("__kjs")) {
                ensureKjsRuntime();
            }
            String expression = prefixReturnIfNeeded(js);
            Object result = session.executeScript(expression);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
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
                return BaseElement.existing(this, locator);
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
                    return BaseElement.existing(this, locator);
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
                    return BaseElement.existing(this, locator);
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
                    return BaseElement.existing(this, locator);
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
                    return BaseElement.existing(this, locator);
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
        // eval() already handles return-prefixing for W3C executeScript,
        // so script() can just delegate directly
        return eval(expression);
    }

    // ========== Screenshot ==========

    @Override
    public byte[] screenshot(boolean embed) {
        byte[] bytes = screenshot();
        if (embed) {
            LogContext.get().embed(bytes, "image/png", "screenshot.png");
        }
        return bytes;
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
        // v1 pattern: -1 means parent frame
        if (index == -1) {
            session.parentFrame();
        } else {
            session.switchFrame(index);
        }
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) {
            // null means reset to top-level frame (W3C: POST /frame {"id": null})
            session.switchFrame(null);
        } else {
            frame(locator);
        }
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
        // W3C: close current window (DELETE /window), not the session
        // v1 also did this — close() != quit()
        // After closing, switch to first remaining handle to keep session alive
        session.closeWindow();
        try {
            List<String> handles = session.getWindowHandles();
            if (handles != null && !handles.isEmpty()) {
                session.switchWindow(handles.get(0));
            }
        } catch (Exception e) {
            // No remaining windows — session may be invalid
            logger.debug("No windows remaining after close: {}", e.getMessage());
        }
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
     * Find element with auto-retry for operations that need a W3C element ID (e.g., sendKeys).
     *
     * <p>Uses the v1 auto-wait pattern: if retry mode is active, waits for the element
     * to exist before attempting to find it. Otherwise, does a single-retry on failure
     * (same as the built-in retry in eval() and v1's elementId()).</p>
     */
    private String findElementIdWithRetry(String locator) {
        // v1 pattern: single retry on locator failure — handles transient DOM changes
        String js = "return " + Locators.selector(locator);
        try {
            Object result = session.executeScript(js);
            if (W3cSession.isElementReference(result)) {
                return W3cSession.elementIdFrom(result);
            }
        } catch (Exception e) {
            logger.warn("locator failed, will retry once: {}", e.getMessage());
        }
        // Single retry after sleep
        sleep(options.getRetryInterval());
        try {
            Object result = session.executeScript(js);
            if (W3cSession.isElementReference(result)) {
                return W3cSession.elementIdFrom(result);
            }
        } catch (Exception e2) {
            throw new DriverException("locator failed twice: " + locator, e2);
        }
        throw new DriverException("Element not found: " + locator);
    }

    /**
     * Inject the Karate JS runtime (__kjs) into the browser if not already present.
     * Same pattern as CdpDriver — loads driver.js from classpath resources.
     * Provides wildcard locator resolution, shadow DOM traversal, and shared utilities.
     */
    private void ensureKjsRuntime() {
        try {
            Object exists = session.executeScript("return typeof window.__kjs !== 'undefined'");
            if (!Boolean.TRUE.equals(exists)) {
                session.executeScript(DRIVER_JS);
                logger.debug("Injected __kjs runtime into browser");
            }
        } catch (Exception e) {
            // May fail during navigation — will retry on next access
            logger.debug("__kjs injection deferred: {}", e.getMessage());
        }
    }

    /**
     * Prefix "return" for W3C executeScript when the expression produces a value.
     *
     * <p>W3C executeScript wraps script in a function body, so value-producing expressions
     * need "return" to get the result back. But statement blocks (var/let/const declarations,
     * control flow) must NOT be prefixed — "return var x" is a syntax error.</p>
     *
     * <p>This matches v1 WebDriver.prefixReturn() logic but is smarter about statement blocks
     * that v1 never had to deal with (because v1 called prefixReturn only in script(), not eval()).</p>
     */
    static String prefixReturnIfNeeded(String js) {
        if (js == null || js.isEmpty()) return js;
        String trimmed = js.stripLeading();
        // Already has return
        if (trimmed.startsWith("return ") || trimmed.startsWith("return;")) return js;
        // Statement blocks — cannot prefix with return
        if (trimmed.startsWith("var ") || trimmed.startsWith("let ") || trimmed.startsWith("const ")
                || trimmed.startsWith("if ") || trimmed.startsWith("if(")
                || trimmed.startsWith("for ") || trimmed.startsWith("for(")
                || trimmed.startsWith("while ") || trimmed.startsWith("while(")
                || trimmed.startsWith("try ") || trimmed.startsWith("try{")
                || trimmed.startsWith("switch ") || trimmed.startsWith("switch(")
                || trimmed.startsWith("throw ")) {
            return js;
        }
        // Multi-statement detection: a top-level semicolon followed by more code means
        // prefixing `return` would turn everything after the first `;` into dead code
        // (e.g. `return a = 1; b = 2` only runs the first assignment). Leave such scripts
        // untouched so W3C executeScript runs the full function body. See
        // https://github.com/karatelabs/karate/issues/2803.
        int semi = indexOfTopLevelSemicolon(trimmed);
        if (semi >= 0 && !trimmed.substring(semi + 1).isBlank()) {
            return js;
        }
        // Everything else (IIFEs, bare expressions, property access, function calls) — prefix return
        return "return " + js;
    }

    /**
     * Scan for the first top-level semicolon, ignoring ones inside string literals
     * (single, double, or backtick-quoted) and inside any brackets/braces/parens.
     * A {@code ;} only separates top-level statements when every enclosing group
     * has been closed — otherwise it's inside an IIFE body, call expression, object
     * literal, etc. and belongs to that construct.
     *
     * <p>Distinguishes {@code "a = 1; b = 2"} (multi-statement) from
     * {@code "(function(){ var e = x; return e })()"} (single IIFE expression)
     * and {@code "foo('a;b')"} (single call with string arg).</p>
     */
    static int indexOfTopLevelSemicolon(String s) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean escape = false;
        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && (inSingle || inDouble || inBacktick)) {
                escape = true;
                continue;
            }
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (inBacktick) {
                if (c == '`') inBacktick = false;
                continue;
            }
            switch (c) {
                case '\'' -> inSingle = true;
                case '"' -> inDouble = true;
                case '`' -> inBacktick = true;
                case '(' -> parenDepth++;
                case ')' -> { if (parenDepth > 0) parenDepth--; }
                case '{' -> braceDepth++;
                case '}' -> { if (braceDepth > 0) braceDepth--; }
                case '[' -> bracketDepth++;
                case ']' -> { if (bracketDepth > 0) bracketDepth--; }
                case ';' -> {
                    if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) return i;
                }
                default -> { /* no-op */ }
            }
        }
        return -1;
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
