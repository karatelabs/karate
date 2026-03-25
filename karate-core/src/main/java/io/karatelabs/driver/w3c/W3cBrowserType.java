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

import java.util.HashMap;
import java.util.Map;

/**
 * Enum-driven configuration for all W3C WebDriver browser types.
 * Encapsulates per-browser differences: executable name, default port,
 * CLI arg format, and W3C capability browserName.
 *
 * <p>Matches Karate v1 type names for backward compatibility.</p>
 */
public enum W3cBrowserType {

    CHROMEDRIVER("chromedriver", "chrome", "chromedriver", 9515, "--port=%d"),
    GECKODRIVER("geckodriver", "firefox", "geckodriver", 4444, "--port %d"),
    SAFARIDRIVER("safaridriver", "safari", "safaridriver", 5555, "-p %d"),
    MSEDGEDRIVER("msedgedriver", "MicrosoftEdge", "msedgedriver", 9515, "--port=%d");

    private static final Map<String, W3cBrowserType> BY_TYPE = new HashMap<>();

    static {
        for (W3cBrowserType bt : values()) {
            BY_TYPE.put(bt.karateType, bt);
        }
    }

    final String karateType;
    final String browserName;
    final String executable;
    final int defaultPort;
    final String portArgFormat;

    W3cBrowserType(String karateType, String browserName, String executable,
                   int defaultPort, String portArgFormat) {
        this.karateType = karateType;
        this.browserName = browserName;
        this.executable = executable;
        this.defaultPort = defaultPort;
        this.portArgFormat = portArgFormat;
    }

    /**
     * Check if a type string corresponds to a W3C WebDriver type.
     */
    public static boolean isW3cType(String type) {
        return type != null && BY_TYPE.containsKey(type);
    }

    /**
     * Get the W3cBrowserType for a Karate type string.
     *
     * @return the browser type, or null if not a W3C type
     */
    public static W3cBrowserType fromType(String type) {
        return BY_TYPE.get(type);
    }

    public String getKarateType() {
        return karateType;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getExecutable() {
        return executable;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    /**
     * Format the port argument for this browser's driver executable.
     */
    public String formatPortArg(int port) {
        return String.format(portArgFormat, port);
    }

}
