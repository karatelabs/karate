package com.intuit.karate.driver.appium;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.DriverOptions;
import java.util.Map;

/**
 * @author babusekaran
 */
public class IosDriver extends AppiumDriver {

    public IosDriver(DriverOptions options) {
        super(options);
    }

    public static IosDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        DriverOptions options = new DriverOptions(map, sr, 4723, "appium");
        options.arg("--port=" + options.port);
        return new IosDriver(options);
    }

    @Override
    public void activate() {
        super.setContext("NATIVE_APP");
    }

}
