package com.intuit.karate.driver.appium;

import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.DriverOptions;
import java.util.Map;

/**
 * @author babusekaran
 */
public class AndroidDriver extends AppiumDriver {

    protected AndroidDriver(DriverOptions options) {
        super(options);
    }

    public static AndroidDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 4723, FileUtils.isOsWindows() ? "cmd.exe" : "appium");
        // additional commands needed to start appium on windows
        if (FileUtils.isOsWindows()){
            options.arg("/C");
            options.arg("cmd.exe");
            options.arg("/K");
            options.arg("appium");
        }
        options.arg("--port=" + options.port);
        return new AndroidDriver(options);
    }

    @Override
    public void activate() {
        super.setContext("NATIVE_APP");
    }

}
