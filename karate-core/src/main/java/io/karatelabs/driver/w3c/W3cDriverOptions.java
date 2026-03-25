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

import io.karatelabs.driver.DriverOptions;
import io.karatelabs.driver.PageLoadStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * W3C WebDriver configuration options.
 * Supports both programmatic construction and config map from Gherkin.
 *
 * <p>Compatible with v1 configuration keys: {@code webDriverUrl}, {@code webDriverSession},
 * {@code capabilities}, {@code start}, {@code executable}, {@code port}, {@code addOptions}.</p>
 */
public class W3cDriverOptions implements DriverOptions {

    private final W3cBrowserType browserType;
    private final int timeout;
    private final int retryCount;
    private final int retryInterval;
    private final boolean headless;
    private final boolean screenshotOnFailure;
    private final boolean highlight;
    private final int highlightDuration;
    private final PageLoadStrategy pageLoadStrategy;
    private final String scope;

    // W3C-specific
    private final String webDriverUrl;
    private final Map<String, Object> webDriverSession;
    private final Map<String, Object> capabilities;
    private final boolean start;
    private final String executable;
    private final int port;
    private final List<String> addOptions;

    @SuppressWarnings("unchecked")
    private W3cDriverOptions(Map<String, Object> map) {
        String type = (String) map.getOrDefault("type", "chromedriver");
        this.browserType = W3cBrowserType.fromType(type);
        if (this.browserType == null) {
            throw new IllegalArgumentException("Unknown W3C driver type: " + type);
        }

        this.timeout = toInt(map.get("timeout"), 30000);
        this.retryCount = toInt(map.get("retryCount"), 3);
        this.retryInterval = toInt(map.get("retryInterval"), 500);
        this.headless = toBool(map.get("headless"), false);
        this.screenshotOnFailure = toBool(map.get("screenshotOnFailure"), true);
        this.highlight = toBool(map.get("highlight"), false);
        this.highlightDuration = toInt(map.get("highlightDuration"), 3000);
        this.pageLoadStrategy = PageLoadStrategy.DOMCONTENT_AND_FRAMES;
        this.scope = (String) map.getOrDefault("scope", "scenario");

        this.webDriverUrl = (String) map.get("webDriverUrl");
        this.webDriverSession = (Map<String, Object>) map.get("webDriverSession");
        this.capabilities = (Map<String, Object>) map.get("capabilities");
        this.start = toBool(map.get("start"), true);
        this.executable = (String) map.get("executable");
        this.port = toInt(map.get("port"), browserType.getDefaultPort());
        Object opts = map.get("addOptions");
        if (opts instanceof List) {
            this.addOptions = (List<String>) opts;
        } else {
            this.addOptions = new ArrayList<>();
        }
    }

    /**
     * Create options from a config map (from {@code configure driver = {...}}).
     */
    public static W3cDriverOptions fromMap(Map<String, Object> map) {
        return new W3cDriverOptions(map);
    }

    /**
     * Build the W3C session creation payload.
     * Compatible with v1: supports {@code webDriverSession} for full override,
     * {@code capabilities} for merge, and auto-generates minimal caps from browser type.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildSessionPayload() {
        // Full override
        if (webDriverSession != null) {
            return new HashMap<>(webDriverSession);
        }

        Map<String, Object> session = new HashMap<>();
        Map<String, Object> caps = new HashMap<>();
        session.put("capabilities", caps);

        Map<String, Object> alwaysMatch = new HashMap<>();
        alwaysMatch.put("browserName", browserType.getBrowserName());

        // Merge user-provided capabilities
        if (capabilities != null) {
            alwaysMatch.putAll(capabilities);
        }

        caps.put("alwaysMatch", alwaysMatch);
        return session;
    }

    // ========== DriverOptions interface ==========

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public Duration getTimeoutDuration() {
        return Duration.ofMillis(timeout);
    }

    @Override
    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public int getRetryInterval() {
        return retryInterval;
    }

    @Override
    public boolean isHeadless() {
        return headless;
    }

    @Override
    public boolean isScreenshotOnFailure() {
        return screenshotOnFailure;
    }

    @Override
    public boolean isHighlight() {
        return highlight;
    }

    @Override
    public int getHighlightDuration() {
        return highlightDuration;
    }

    @Override
    public PageLoadStrategy getPageLoadStrategy() {
        return pageLoadStrategy;
    }

    // ========== W3C-specific getters ==========

    public W3cBrowserType getBrowserType() {
        return browserType;
    }

    public String getWebDriverUrl() {
        return webDriverUrl;
    }

    public boolean isStart() {
        return start;
    }

    public String getExecutable() {
        if (executable != null && !executable.isEmpty()) {
            return executable;
        }
        return browserType.getExecutable();
    }

    public int getPort() {
        return port;
    }

    public List<String> getAddOptions() {
        return addOptions;
    }

    public String getScope() {
        return scope;
    }

    /**
     * Check if this is a remote connection (webDriverUrl set or start=false).
     */
    public boolean isRemote() {
        return (webDriverUrl != null && !webDriverUrl.isEmpty()) || !start;
    }

    // ========== Utility ==========

    private static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean toBool(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

}
