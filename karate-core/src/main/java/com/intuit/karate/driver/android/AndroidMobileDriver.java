package com.intuit.karate.driver.android;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.Logger;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.AppiumMobileDriver;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.shell.CommandThread;

import java.util.Collections;
import java.util.Map;

/**
 * @author babusekaran
 */
public class AndroidMobileDriver extends AppiumMobileDriver {

    protected AndroidMobileDriver(DriverOptions options, CommandThread command, Http http, String sessionId, String windowId) {
        super(options, command, http, sessionId, windowId);
    }

    public static AndroidMobileDriver start(ScenarioContext context, Map<String, Object> map, Logger logger) {
        DriverOptions options = new DriverOptions(context, map, logger, 4723, FileUtils.isWindows() ? "cmd.exe" : "appium");
        // additional commands needed to start appium on windows
        if (FileUtils.isWindows()){
            options.arg("/C");
            options.arg("cmd.exe");
            options.arg("/K");
            options.arg("appium");
        }
        options.arg("--port=" + options.port);
        CommandThread command = options.startProcess();
        String urlBase = "http://" + options.host + ":" + options.port + "/wd/hub";
        Http http = Http.forUrl(options.driverLogger, urlBase);
        http.config("readTimeout","120000");
        String sessionId = http.path("session")
                .post(Collections.singletonMap("desiredCapabilities", map))
                .jsonPath("get[0] response..sessionId").asString();
        options.driverLogger.debug("init session id: {}", sessionId);
        http.url(urlBase + "/session/" + sessionId);
        AndroidMobileDriver driver = new AndroidMobileDriver(options, command, http, sessionId, null);
        driver.activate();
        return driver;
    }

    @Override
    public void activate() {
        super.setContext("NATIVE_APP");
    }

}
