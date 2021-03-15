package com.intuit.karate.driver.playwright;

import com.intuit.karate.driver.DriverOptions;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class PlaywrightDriverRunner {

    static final Logger logger = LoggerFactory.getLogger(PlaywrightDriverRunner.class);

    @Test
    void testPlaywright() {
        DriverOptions options = new DriverOptions(Collections.EMPTY_MAP, null, 0, null);
        PlaywrightDriver driver = new PlaywrightDriver(options, null, "ws://127.0.0.1:4444/a9a2cbe14cd3282908de74bf73d2e901");
        driver.setUrl("https://google.com");
        driver.screenshot();
        driver.waitSync();
    }

}
