package com.intuit.karate.junit4.http;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeUiRunner {
    
    @Test
    public void testApp() {
        App.run("src/test/java/com/intuit/karate/junit4/http/chrome.feature", null);
    }     
    
}
