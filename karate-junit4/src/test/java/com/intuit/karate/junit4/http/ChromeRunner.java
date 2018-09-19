package com.intuit.karate.junit4.http;

import com.intuit.karate.web.chrome.Chrome;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ChromeRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromeRunner.class);
    
    @Test
    public void testChrome() {
        Chrome chrome = Chrome.start(9222);
        chrome.method("Page.navigate").param("url", "https://google.com").send();
        chrome.waitSync();
    }
    
}
