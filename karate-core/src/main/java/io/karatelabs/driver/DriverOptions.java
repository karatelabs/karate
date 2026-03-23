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
package io.karatelabs.driver;

import java.time.Duration;

/**
 * Base options interface for browser drivers.
 * Provides common configuration that applies to all driver backends.
 * <p>
 * Phase 8: Extracted from CdpDriverOptions to enable multi-backend support.
 */
public interface DriverOptions {

    /**
     * Get timeout in milliseconds.
     */
    int getTimeout();

    /**
     * Get timeout as Duration.
     */
    Duration getTimeoutDuration();

    /**
     * Get retry count for operations.
     */
    int getRetryCount();

    /**
     * Get retry interval in milliseconds.
     */
    int getRetryInterval();

    /**
     * Check if running in headless mode.
     */
    boolean isHeadless();

    /**
     * Check if screenshots should be taken on failure.
     */
    boolean isScreenshotOnFailure();

    /**
     * Check if element highlighting is enabled.
     */
    boolean isHighlight();

    /**
     * Get highlight duration in milliseconds.
     */
    int getHighlightDuration();

    /**
     * Get page load strategy.
     */
    PageLoadStrategy getPageLoadStrategy();

}
