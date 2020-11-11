package com.intuit.karate;

import com.intuit.karate.runtime.MockServer;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class FeatureServerRunner {

    @Test
    public void testServer() {
        MockServer server = MockServer.feature("classpath:com/intuit/karate/server.feature").http(8080).build();
        server.waitSync();
    }

}
