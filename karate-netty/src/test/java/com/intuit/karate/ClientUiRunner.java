package com.intuit.karate;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ClientUiRunner {
    
    @Test
    public void testApp() {
        App.run("src/test/java/com/intuit/karate/client.feature", null);
    }     
    
}
