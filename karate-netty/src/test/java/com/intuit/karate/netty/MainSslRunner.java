package com.intuit.karate.netty;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainSslRunner {
    
    @Test
    public void testMain() {
        Main.main(new String[]{"-m", "src/test/java/com/intuit/karate/netty/server.feature", "-p", "8443", "-s"});        
    }
    
}
