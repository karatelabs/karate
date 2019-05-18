package com.intuit.karate;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainRunner {
    
    @Test
    public void testMain() {
        Main.main(new String[]{"-m", "src/test/java/com/intuit/karate/server.feature", "-p", "8080"});        
    }
    
}
