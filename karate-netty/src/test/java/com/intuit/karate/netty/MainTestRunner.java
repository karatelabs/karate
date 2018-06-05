package com.intuit.karate.netty;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainTestRunner {
    
    @Test
    public void testMain() {
        System.setProperty("karate.config.dir", "src/test/java");
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate/netty"});                
    }
    
}
