package com.intuit.karate.driver.playwright;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.playwright.driver.PlaywrightDriverOptions;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;

import java.util.Map;

/**
 * A drop-in replacement for the "legacy" implementation. May help as long as both the old and the new drivers are in the code-base,
 * should be removed if/when one gets dropped.
 *
 * To enable the new implementation, one just needs to add the karate-playwright dependency in their pom.xml before karate-core.
 *
 * To conditionally enable the new implementation is bit more challenging. Maven profiles are typically used for this,
 * but unfortunately, dependencies from profiles are added AFTER regular dependencies, so that won't work.
 *
 * One solution would be to declare karate-playwright as a regular dependency, with its scope controlled by a property.
 * By default, the property is "import", so it won't be picked up by Maven and the legacy driver will be used.
 * But in the profile, the property is set to "compile" so that it gets picked up. A bit of a hack, but it should work.
 *
 */
public class PlaywrightDriver extends com.intuit.karate.playwright.driver.PlaywrightDriver {

    public static PlaywrightDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        return com.intuit.karate.playwright.driver.PlaywrightDriver.start(map,sr, PlaywrightDriver::new);
    }

    public PlaywrightDriver(PlaywrightDriverOptions options, Browser browser, Playwright playwright) {
        super(options, browser, playwright);
        options.driverLogger.info("Using native PlaywrightDriver");
    }
}
