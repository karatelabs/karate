package com.intuit.karate.driver.ios;

import com.intuit.karate.Http;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.AppiumDriver;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.shell.Command;

import java.util.Collections;
import java.util.Map;

/**
 * @author babusekaran
 */
public class IosDriver extends AppiumDriver {

    public IosDriver(DriverOptions options, Command command, Http http, String sessionId, String windowId) {
        super(options, command, http, sessionId, windowId);
    }

    public static IosDriver start(ScenarioContext context, Map<String, Object> map, LogAppender appender) {
        DriverOptions options = new DriverOptions(context, map, appender, 4723, "appium");
        options.arg("--port=" + options.port);
        Command command = options.startProcess();
        String urlBase = "http://" + options.host + ":" + options.port + "/wd/hub";
        Http http = Http.forUrl(options.driverLogger.getLogAppender(), urlBase);
        http.config("readTimeout","120000");
        String sessionId = http.path("session")
                .post(Collections.singletonMap("desiredCapabilities", map))
                .jsonPath("get[0] response..sessionId").asString();
        options.driverLogger.debug("init session id: {}", sessionId);
        http.url(urlBase + "/session/" + sessionId);
        IosDriver driver = new IosDriver(options, command, http, sessionId, null);
        driver.activate();
        return driver;
    }

    @Override
    public void activate() {
        super.setContext("NATIVE_APP");
    }

}
