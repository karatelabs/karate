package com.intuit.karate.netty;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainRunner {
    
    @Test
    public void testMain() {
        Main.main(new String[]{"server.feature", "8080"});        
    }
    
}
