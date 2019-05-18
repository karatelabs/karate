package com.intuit.karate;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ClientUiMainRunner {
    
    @Test
    public void testMain() {
        Main.main(new String[]{"src/test/java/com/intuit/karate/client.feature", "-u"}); 
    }     
    
}
