package com.intuit.karate.driver.appium;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.Element;
import com.intuit.karate.driver.DriverElement;
import com.intuit.karate.driver.MissingElement;

import java.util.Arrays;
import java.util.Iterator;
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
        Map<String, Object> desiredCapabilities = (Map<String, Object>) sessionPayload.get("desiredCapabilities");
        return  (desiredCapabilities.get("browserName") != null) ? true : false;
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

}