package com.intuit.karate.playwright.driver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.intuit.karate.core.Config;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.Driver;
import com.microsoft.playwright.TimeoutError;

/** Run actions and waits with global /specific retry settings and measure 
* Almost all tests (the one exception being {@link #actionWithWait}) within this class are configured with 2 retries of 200ms each, so 0.4s, but the div takes 2.5 to be visible, so they are expected to timeout.
* However, this will validate that the specified settings are correctly taken into account.
*
* Compare that with a test configured with 3 * 1500, it will pass and will take 2.5s but we can't tell if the settings were used or if it's just PW's autowait returning as soon as the div is visible.
* Put it this way: the test would stil pass if the settings were not used and we used PW's default timeout of 30s.
*/
public class PlaywrightDriverTest {

    private static final int RETRY_COUNT = 2;
    private static final int RETRY_INTERVAL = 300;

    private static Driver driver;
    private static ScenarioEngine scenarioEngine;
    private static Config scenarioEngineConfig;

    @BeforeAll
    public static void beforeAll() {
        ScenarioRuntime sr = Mockito.mock(ScenarioRuntime.class, Mockito.RETURNS_DEEP_STUBS);
        Map<String, Object> options = new LinkedHashMap<>();        
        options.put("userDataDir", "target");
        options.put("headless", true);
        Map<String, Object> playwrightOptions = new LinkedHashMap<>();
        options.put("playwrightOptions", playwrightOptions);
        playwrightOptions.put("installBrowsers", false);
        playwrightOptions.put("channel", "chrome");

        driver = PlaywrightDriver.start(options, sr);
    }

    @AfterAll
    public static void cleanup() {
        if (driver != null){
            driver.close();
        }
    }

    // @Test
    void actionWithRetry() {

        driver.setUrl("file://"+System.getProperty("user.dir")+"/src/test/resources/html/02.html");
 
        TimeoutError te = assertThrows(TimeoutError.class, () ->
                driver.retry(RETRY_COUNT, RETRY_INTERVAL).click("#slowDiv"));

        assertTrue(te.getMessage().contains("Timeout 600ms exceeded"));
    }


    // @Test
    void waitForWithRetry() {

        driver.setUrl("file://"+System.getProperty("user.dir")+"/src/test/resources/html/02.html");

        TimeoutError te = assertThrows(TimeoutError.class, () ->
                driver.retry(RETRY_COUNT, RETRY_INTERVAL).waitFor("#slowDiv"));

        assertTrue(te.getMessage().contains("Timeout 600ms exceeded"));
    }

    // make sure that waitFor() and click() are consistent with each other.
    // Previous implementation of waitFor was based on State.ATTACHED, so would return as soon as the element is attached in the DOM.
    // click(), on the other end, would autowait until the element is VISIBLE https://playwright.dev/docs/actionability
    // Since actions' timeout is 100ms, if it takes more than 100 ms for the element to transition from ATTACHED to VISIBLE, click() would timeout.
    // See issue in #2291.
    // @Test
    void actionWithWait() {
        // Note this page is set up so that the element is visible 500 ms (> 100 ms) after it was attached.
        driver.setUrl("file://"+System.getProperty("user.dir")+"/src/test/resources/html/02.html");
        driver.waitFor("#slowDiv")
                .click();           
    }    

    // @Nested
    class GlobalRetryTest {
        
        @BeforeAll
        public static void beforeAll() {
            scenarioEngineConfig = new Config();
            scenarioEngineConfig.setRetryCount(RETRY_COUNT);
            scenarioEngineConfig.setRetryInterval(RETRY_INTERVAL);
            scenarioEngine = Mockito.mock(ScenarioEngine.class);
            Mockito.when(scenarioEngine.getConfig()).thenReturn(scenarioEngineConfig);

            ScenarioEngine.set(scenarioEngine);
        }

        // Per doc and other driver implementations, global retry count is not taken into account for actions, but retry interval is (and defines the wait timeout in PW).
        // @Test
        void actionWithGlobalRetry() {

            // global retry/
            driver.setUrl("file://"+System.getProperty("user.dir")+"/src/test/resources/html/02.html");

            TimeoutError te = assertThrows(TimeoutError.class, () -> driver.click("#slowDiv"));
            assertTrue(te.getMessage().contains("Timeout "+RETRY_INTERVAL+"ms exceeded"), te.getMessage());
        }


        // @Test
        void waitForWithGlobalRetry() {

            driver.setUrl("file://"+System.getProperty("user.dir")+"/src/test/resources/html/02.html");

            TimeoutError te = assertThrows(TimeoutError.class, () -> driver.waitFor("#slowDiv"));
            assertTrue(te.getMessage().contains("Timeout 600ms exceeded"), te.getMessage());
        }

    } 

}