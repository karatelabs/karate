package com.intuit.karate.junit4.http;

import com.intuit.karate.web.chrome.Chrome;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ChromeRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromeRunner.class);
    
    @Test
    public void testChrome() throws Exception {
        Chrome chrome = Chrome.start(9222);        
        chrome.url("https://github.com/login");
        chrome.type("#login_field", "hello");
        chrome.type("#password", "world");
        chrome.click("//input[@name='commit']");
        String html = chrome.getHtml("#js-flash-container");
        assertTrue(html.contains("Incorrect username or password."));
        chrome.url("https://google.com");
        chrome.type("//input[@name='q']", "karate dsl");
        chrome.click("//input[@name='btnI']");
        chrome.waitSync();
    }
    
}
