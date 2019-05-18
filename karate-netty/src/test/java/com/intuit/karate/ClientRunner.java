package com.intuit.karate;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ClientRunner {
    
    @Test
    public void testClient() {
        Runner.runFeature("classpath:com/intuit/karate/client.feature", null, true);
    }
    
}
