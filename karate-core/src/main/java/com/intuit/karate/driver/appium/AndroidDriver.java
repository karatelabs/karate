package com.intuit.karate.driver.appium;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.ScenarioRuntime;
import java.util.Map;

/**
 * @author babusekaran
 */
public class AndroidDriver extends AppiumDriver {

    public static final String DRIVER_TYPE = "android";

    protected AndroidDriver(MobileDriverOptions options) {
        super(options);
    }

    public static AndroidDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        MobileDriverOptions options = new MobileDriverOptions(map, sr, 4723, FileUtils.isOsWindows() ? "cmd.exe" : "appium");
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
