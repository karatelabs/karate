package com.intuit.karate.fatjar;

import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class FeatureServerRunner {

    @Test
    void testServer() {
        MockServer server = MockServer.feature("classpath:com/intuit/karate/fatjar/server.feature").http(8080).build();
        server.waitSync();
    }

}
