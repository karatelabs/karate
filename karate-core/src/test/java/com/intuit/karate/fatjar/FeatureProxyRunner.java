package com.intuit.karate.fatjar;

import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class FeatureProxyRunner {

    @Test
    void testServer() {
        MockServer server = MockServer.feature("classpath:com/intuit/karate/fatjar/proxy.feature").http(8090).build();
        server.waitSync();
    }

}
