/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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
package com.intuit.karate.driver.appium;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.DriverElement;
import com.intuit.karate.driver.MissingElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author babusekaran
 */
public class MobileDriverOptions extends DriverOptions {

    public MobileDriverOptions(Map<String, Object> options, ScenarioRuntime sr, int defaultPort, String defaultExecutable) {
        super(options, sr, defaultPort, defaultExecutable);
    }

    public boolean isWebSession() {
        // flag to know if driver runs for browser on mobile
        Map<String, Object> sessionPayload = super.getWebDriverSessionPayload();
        return getBrowserName(sessionPayload) != null;
    }

    @Override
    public Element waitForAny(Driver driver, String... locators) {
        if (isWebSession()) {
            return super.waitForAny(driver, locators);
        }
        long startTime = System.currentTimeMillis();
        List<String> list = Arrays.asList(locators);
        boolean found = (boolean)driver.waitUntil(() -> {
            for (String locator: list) {
                try {
                    ((AppiumDriver)driver).elementId(locator);
                    return true;
                }
                catch (RuntimeException re){
                    logger.debug("failed to locate : {}", locator);
                }
            }
            return null;
        });
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

    @Override
    public Element optional(Driver driver, String locator) {
        if (isWebSession()) {
            return super.optional(driver, locator);
        }
        try{
            retry(() -> {
                try {
                    ((AppiumDriver)driver).elementId(locator);
                    return true;
                } catch (RuntimeException re) {
                    return false;
                }
            }, b -> b, "optional (locator)", true);
            // the element exists, if the above function did not throw an exception
            return DriverElement.locatorExists(driver, locator);
        }
        catch (RuntimeException re) {
            return new MissingElement(driver, locator);
        }
    }

    protected static String getBrowserName(Map<String, Object> sessionPayload) {
        // get browserName from capabilities or desiredCapabilities node
        Map<String, Object> capabilities = (Map<String, Object>) sessionPayload.get("capabilities");
        Map<String, Object> desiredCapabilities = (Map<String, Object>) sessionPayload.get("desiredCapabilities");

        if (capabilities != null) {
            if (capabilities.containsKey("firstMatch")) {
                // sauce labs uses the firstMatch node for some reason
                // see https://support.saucelabs.com/hc/en-us/articles/4412359870231-Migrating-Appium-Real-Device-Tests-to-W3C
                List<Map<String, Object>> firstMatch = (List<Map<String, Object>>) capabilities.get("firstMatch");
                if (firstMatch.size() == 0) {
                    throw new RuntimeException("firstMatch node in webdriver session is empty");
                }
                return (String) firstMatch.get(0).get("browserName");
            } else {
                return (String) capabilities.get("browserName");
            }
        } else if (desiredCapabilities != null) {
            return (String) desiredCapabilities.get("browserName");
        } else {
            // no browserName found
            return null;
        }

    }
}