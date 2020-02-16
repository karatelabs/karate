package com.intuit.karate.driver.appium;

import com.intuit.karate.LogAppender;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.DriverOptions;
import java.util.Map;

/**
 * @author babusekaran
 */
public class IosDriver extends AppiumDriver {

    public IosDriver(DriverOptions options) {
        super(options);
    }

    public static IosDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 4723, "appium");
        options.arg("--port=" + options.port);
        return new IosDriver(options);
    }

    @Override
    public void activate() {
        super.setContext("NATIVE_APP");
    }

}
