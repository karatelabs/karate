package com.intuit.karate.junit4.http;

import com.intuit.karate.web.Driver;
import com.intuit.karate.web.chrome.ChromeDevToolsDriver;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ChromeJavaRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromeJavaRunner.class);
    
    @Test
    public void testChrome() throws Exception {
        Map<String, Object> options = new HashMap();
        options.put("port", 9222);
        // options.put("headless", true);
        Driver driver = ChromeDevToolsDriver.start(options);        
        driver.location("https://github.com/login");
        driver.input("#login_field", "hello");
        driver.input("#password", "world");
        driver.submit("//input[@name='commit']");
        String html = driver.html("#js-flash-container");
        assertTrue(html.contains("Incorrect username or password."));
        driver.location("https://google.com");
        driver.input("//input[@name='q']", "karate dsl");
        driver.submit("//input[@name='btnI']");
        assertEquals("https://github.com/intuit/karate", driver.getLocation());
        driver.quit();
        // chrome.waitSync();
    }
    
}
