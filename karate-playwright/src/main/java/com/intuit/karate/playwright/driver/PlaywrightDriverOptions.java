package com.intuit.karate.playwright.driver;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.DriverOptions;

import java.util.Map;

public class PlaywrightDriverOptions extends DriverOptions {

    private PlaywrightDriver driver;

    public PlaywrightDriverOptions(Map<String, Object> options, ScenarioRuntime sr, int defaultPort, String defaultExecutable) {
        super(options, sr, defaultPort, defaultExecutable);
    }

    public void setDriver(PlaywrightDriver driver) {
        this.driver = driver;
    }

    @Override
    public void sleep(int millis) {
        driver.sleep(millis);
    }
}
