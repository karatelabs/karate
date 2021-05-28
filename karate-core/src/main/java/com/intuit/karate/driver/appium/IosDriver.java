package com.intuit.karate.driver.appium;

import com.intuit.karate.core.ScenarioRuntime;
import java.util.Map;

/**
 * @author babusekaran
 */
public class IosDriver extends AppiumDriver {

    public static final String DRIVER_TYPE = "ios";

    public IosDriver(MobileDriverOptions options) {
        super(options);
    }

    public static IosDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        MobileDriverOptions options = new MobileDriverOptions(map, sr, 4723, "appium");
        options.arg("--port=" + options.port);
        return new IosDriver(options);
    }

    @Override
    public void activate() {
        super.setContext("NATIVE_APP");
    }

}
