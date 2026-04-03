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

import io.karatelabs.js.JavaCallable;
import io.karatelabs.js.SimpleObject;

import java.time.Duration;

/**
 * Proxy that wraps a Driver with custom retry timeout for v1-compatible retry() chaining.
 * <p>
 * In Gherkin, {@code retry(count, interval).waitUntil("expression")} creates a RetryableDriver
 * that passes a computed timeout (count * interval ms) to the underlying wait methods.
 * <p>
 * For action methods like click() and input(), retry implies a waitFor() before the action.
 *
 * @see DriverApi
 */
class RetryableDriver implements SimpleObject {

    private final Driver driver;
    private final Duration timeout;

    RetryableDriver(Driver driver, Integer count, Integer interval) {
        int effectiveCount = count != null ? count : driver.getOptions().getRetryCount();
        int effectiveInterval = interval != null ? interval : driver.getOptions().getRetryInterval();
        this.driver = driver;
        this.timeout = Duration.ofMillis((long) effectiveCount * effectiveInterval);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object jsGet(String name) {
        return switch (name) {
            // Wait methods - use custom timeout
            case DriverApi.WAIT_UNTIL -> (JavaCallable) (ctx, args) -> {
                if (args.length == 1) {
                    return driver.waitUntil(String.valueOf(args[0]), timeout);
                } else {
                    return driver.waitUntil(String.valueOf(args[0]), String.valueOf(args[1]), timeout);
                }
            };
            case DriverApi.WAIT_FOR -> (JavaCallable) (ctx, args) ->
                    driver.waitFor(String.valueOf(args[0]), timeout);
            case DriverApi.WAIT_FOR_TEXT -> (JavaCallable) (ctx, args) ->
                    driver.waitForText(String.valueOf(args[0]), String.valueOf(args[1]), timeout);
            case DriverApi.WAIT_FOR_ENABLED -> (JavaCallable) (ctx, args) ->
                    driver.waitForEnabled(String.valueOf(args[0]), timeout);
            case DriverApi.WAIT_FOR_URL -> (JavaCallable) (ctx, args) ->
                    driver.waitForUrl(String.valueOf(args[0]), timeout);
            case DriverApi.WAIT_FOR_RESULT_COUNT -> (JavaCallable) (ctx, args) ->
                    driver.waitForResultCount(String.valueOf(args[0]), ((Number) args[1]).intValue(), timeout);
            // Action methods - retry implies waitFor before action
            case DriverApi.CLICK -> (JavaCallable) (ctx, args) -> {
                String locator = String.valueOf(args[0]);
                driver.waitFor(locator, timeout);
                return driver.click(locator);
            };
            case DriverApi.INPUT -> (JavaCallable) (ctx, args) -> {
                String locator = String.valueOf(args[0]);
                driver.waitFor(locator, timeout);
                return driver.input(locator, args.length > 1 ? String.valueOf(args[1]) : "");
            };
            // For anything else, delegate to driver
            default -> driver.jsGet(name);
        };
    }

}
