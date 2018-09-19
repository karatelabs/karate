package com.intuit.karate.junit4.http;

import com.intuit.karate.web.chrome.ChromeApp;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeAppRunner {
    
    @Test
    public void testChromeApp() {
        ChromeApp.main(new String[]{});
    }
    
}
