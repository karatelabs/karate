package com.intuit.karate.netty;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainTestRunner {
    
    @Test
    public void testMain() {
        System.setProperty("karate.config", "src/test/java/karate-config.js");
        Main.main(new String[]{"-t", "src/test/java/com/intuit/karate/netty/client.feature"});        
    }
    
}
