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
package io.karatelabs.driver.cdp;

import io.karatelabs.driver.DriverOptions;
import io.karatelabs.driver.PageLoadStrategy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CDP-specific driver configuration options.
 * Implements DriverOptions interface and adds CDP-specific settings.
 * Supports both Builder pattern and Map constructor for v1 compatibility.
 */
public class CdpDriverOptions implements DriverOptions {

    private final int timeout;
    private final int retryCount;
    private final int retryInterval;
    private final boolean headless;
    private final String host;
    private final int port;
    private final String executable;
    private final String userDataDir;
    private final String userAgent;
    private final boolean screenshotOnFailure;
    private final boolean highlight;
    private final int highlightDuration;
    private final PageLoadStrategy pageLoadStrategy;
    private final List<String> addOptions;
    private final String webSocketUrl;
    private final String scope;

    private CdpDriverOptions(Builder builder) {
        this.timeout = builder.timeout;
        this.retryCount = builder.retryCount;
        this.retryInterval = builder.retryInterval;
        this.headless = builder.headless;
        this.host = builder.host;
        this.port = builder.port;
        this.executable = builder.executable;
        this.userDataDir = builder.userDataDir;
        this.userAgent = builder.userAgent;
        this.screenshotOnFailure = builder.screenshotOnFailure;
        this.highlight = builder.highlight;
        this.highlightDuration = builder.highlightDuration;
        this.pageLoadStrategy = builder.pageLoadStrategy;
        this.addOptions = builder.addOptions != null ? List.copyOf(builder.addOptions) : List.of();
        this.webSocketUrl = builder.webSocketUrl;
        this.scope = builder.scope;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public static CdpDriverOptions fromMap(Map<String, Object> map) {
        Builder builder = builder();
        if (map == null) {
            return builder.build();
        }

        if (map.containsKey("timeout")) {
            builder.timeout(toInt(map.get("timeout")));
        }
        if (map.containsKey("retryCount")) {
            builder.retryCount(toInt(map.get("retryCount")));
        }
        if (map.containsKey("retryInterval")) {
            builder.retryInterval(toInt(map.get("retryInterval")));
        }
        if (map.containsKey("headless")) {
            builder.headless(toBoolean(map.get("headless")));
        }
        if (map.containsKey("host")) {
            builder.host((String) map.get("host"));
        }
        if (map.containsKey("port")) {
            builder.port(toInt(map.get("port")));
        }
        if (map.containsKey("executable")) {
            builder.executable((String) map.get("executable"));
        }
        if (map.containsKey("userDataDir")) {
            builder.userDataDir((String) map.get("userDataDir"));
        }
        if (map.containsKey("userAgent")) {
            builder.userAgent((String) map.get("userAgent"));
        }
        if (map.containsKey("screenshotOnFailure")) {
            builder.screenshotOnFailure(toBoolean(map.get("screenshotOnFailure")));
        }
        if (map.containsKey("highlight")) {
            builder.highlight(toBoolean(map.get("highlight")));
        }
        if (map.containsKey("highlightDuration")) {
            builder.highlightDuration(toInt(map.get("highlightDuration")));
        }
        if (map.containsKey("pageLoadStrategy")) {
            Object strategy = map.get("pageLoadStrategy");
            if (strategy instanceof PageLoadStrategy) {
                builder.pageLoadStrategy((PageLoadStrategy) strategy);
            } else if (strategy instanceof String) {
                builder.pageLoadStrategy(PageLoadStrategy.valueOf(((String) strategy).toUpperCase()));
            }
        }
        if (map.containsKey("addOptions")) {
            builder.addOptions((List<String>) map.get("addOptions"));
        }
        if (map.containsKey("webSocketUrl")) {
            builder.webSocketUrl((String) map.get("webSocketUrl"));
        }
        if (map.containsKey("scope")) {
            builder.scope((String) map.get("scope"));
        }

        return builder.build();
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    // Getters

    public int getTimeout() {
        return timeout;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    /**
     * Check headless mode. Falls back to KARATE_DRIVER_HEADLESS env var if not set explicitly.
     */
    public boolean isHeadless() {
        if (headless) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("KARATE_DRIVER_HEADLESS"));
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Get executable path. Falls back to KARATE_CHROME_EXECUTABLE env var if not set explicitly.
     */
    public String getExecutable() {
        if (executable != null && !executable.isEmpty()) {
            return executable;
        }
        return System.getenv("KARATE_CHROME_EXECUTABLE");
    }

    public String getUserDataDir() {
        return userDataDir;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isScreenshotOnFailure() {
        return screenshotOnFailure;
    }

    public boolean isHighlight() {
        return highlight;
    }

    public int getHighlightDuration() {
        return highlightDuration;
    }

    public PageLoadStrategy getPageLoadStrategy() {
        return pageLoadStrategy;
    }

    /**
     * Get additional Chrome args. Merges explicit addOptions with KARATE_CHROME_ARGS env var.
     */
    public List<String> getAddOptions() {
        String envArgs = System.getenv("KARATE_CHROME_ARGS");
        if (envArgs == null || envArgs.isEmpty()) {
            return addOptions;
        }
        List<String> merged = new java.util.ArrayList<>(addOptions);
        for (String arg : envArgs.split("\\s+")) {
            if (!arg.isEmpty()) {
                merged.add(arg);
            }
        }
        return merged;
    }

    public String getWebSocketUrl() {
        return webSocketUrl;
    }

    /**
     * Get driver scope (default: "scenario").
     * <ul>
     *   <li>"scenario" - driver released at scenario end (pooled behavior)</li>
     *   <li>"caller" - driver propagates to caller hierarchy (V1 behavior)</li>
     * </ul>
     */
    public String getScope() {
        return scope;
    }

    /**
     * Check if driver scope is "caller" (V1-style behavior).
     */
    public boolean isCallerScope() {
        return "caller".equals(scope);
    }

    public Duration getTimeoutDuration() {
        return Duration.ofMillis(timeout);
    }

    public static class Builder {
        private int timeout = 30000;
        private int retryCount = 3;
        private int retryInterval = 500;
        private boolean headless = false;
        private String host = "localhost";
        private int port = 0; // 0 = auto-assign
        private String executable;
        private String userDataDir;
        private String userAgent;
        private boolean screenshotOnFailure = true;
        private boolean highlight = false;
        private int highlightDuration = 3000;
        private PageLoadStrategy pageLoadStrategy = PageLoadStrategy.DOMCONTENT_AND_FRAMES;
        private List<String> addOptions;
        private String webSocketUrl;
        private String scope = "scenario";

        public Builder timeout(int millis) {
            this.timeout = millis;
            return this;
        }

        public Builder timeout(Duration duration) {
            this.timeout = (int) duration.toMillis();
            return this;
        }

        public Builder retryCount(int count) {
            this.retryCount = count;
            return this;
        }

        public Builder retryInterval(int millis) {
            this.retryInterval = millis;
            return this;
        }

        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder executable(String path) {
            this.executable = path;
            return this;
        }

        public Builder userDataDir(String path) {
            this.userDataDir = path;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder screenshotOnFailure(boolean enabled) {
            this.screenshotOnFailure = enabled;
            return this;
        }

        public Builder highlight(boolean enabled) {
            this.highlight = enabled;
            return this;
        }

        public Builder highlightDuration(int millis) {
            this.highlightDuration = millis;
            return this;
        }

        public Builder pageLoadStrategy(PageLoadStrategy strategy) {
            this.pageLoadStrategy = strategy;
            return this;
        }

        public Builder addOptions(List<String> options) {
            this.addOptions = options != null ? new ArrayList<>(options) : null;
            return this;
        }

        public Builder addOption(String option) {
            if (this.addOptions == null) {
                this.addOptions = new ArrayList<>();
            }
            this.addOptions.add(option);
            return this;
        }

        public Builder webSocketUrl(String url) {
            this.webSocketUrl = url;
            return this;
        }

        /**
         * Set driver scope (default: "scenario").
         * <ul>
         *   <li>"scenario" - driver released at scenario end (pooled behavior)</li>
         *   <li>"caller" - driver propagates to caller hierarchy (V1 behavior)</li>
         * </ul>
         */
        public Builder scope(String scope) {
            this.scope = scope != null ? scope : "scenario";
            return this;
        }

        public CdpDriverOptions build() {
            // Default userDataDir to temp sandbox if not specified
            // This prevents conflicts with user's existing Chrome browser
            if (userDataDir == null) {
                userDataDir = Path.of("target", "chrome-temp-" + UUID.randomUUID().toString().substring(0, 8))
                        .toAbsolutePath().toString();
            }
            return new CdpDriverOptions(this);
        }
    }

}
