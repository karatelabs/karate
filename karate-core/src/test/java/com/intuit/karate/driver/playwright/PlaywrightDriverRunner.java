package com.intuit.karate.driver.playwright;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.DriverOptions;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class PlaywrightDriverRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(PlaywrightDriverRunner.class);
    
    private ScenarioContext getContext() {
        Path featureDir = FileUtils.getPathContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir("dev", featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, null, null);
    }    
    
    @Test
    public void testPlaywright() {
        DriverOptions options = new DriverOptions(getContext(), Collections.EMPTY_MAP, null, 0, null);
        PlaywrightDriver driver = new PlaywrightDriver(options, null, "ws://127.0.0.1:4444/a9a2cbe14cd3282908de74bf73d2e901");
        driver.setUrl("https://google.com");
        driver.screenshot();
        driver.waitSync();
    }
    
}
