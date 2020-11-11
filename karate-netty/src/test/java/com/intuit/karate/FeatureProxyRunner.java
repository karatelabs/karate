package com.intuit.karate;

import com.intuit.karate.runtime.MockServer;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class FeatureProxyRunner {
    
    @Test
    public void testServer() {
        MockServer server = MockServer.feature("classpath:com/intuit/karate/proxy.feature").http(8090).build();
        server.waitSync();
    }
    
}
