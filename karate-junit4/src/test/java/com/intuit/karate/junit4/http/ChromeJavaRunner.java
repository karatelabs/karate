package com.intuit.karate.junit4.http;

import com.intuit.karate.web.chrome.Chrome;
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
        Chrome chrome = Chrome.start(options);        
        chrome.browse("https://github.com/login");
        chrome.type("#login_field", "hello");
        chrome.type("#password", "world");
        chrome.click("//input[@name='commit']");
        String html = chrome.html("#js-flash-container");
        assertTrue(html.contains("Incorrect username or password."));
        chrome.browse("https://google.com");
        chrome.type("//input[@name='q']", "karate dsl");
        chrome.click("//input[@name='btnI']");
        chrome.await("Page.frameNavigated");
        assertEquals("https://github.com/intuit/karate", chrome.url());
        chrome.stop();
        // chrome.waitSync();
    }
    
}
