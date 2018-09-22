package com.intuit.karate.netty;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainWinTestRunner {
    
    @Test
    public void testMain() {
        System.setProperty("karate.config.dir", "C:/Users/pthomas3/git/karate/karate-netty/src/test/java");
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate/netty"});                
    }
    
}
