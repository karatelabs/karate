package com.intuit.karate.debug;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DapServerRunner {
    
    @Test
    public void testDap() {
        DapServer server = new DapServer(4711);
        server.waitSync();
    }
    
}
