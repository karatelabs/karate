package com.intuit.karate.fatjar;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class FeatureServerTest {

    static MockServer server;

    @BeforeAll
    static void beforeClass() {
        server = MockServer.feature("classpath:com/intuit/karate/fatjar/server.feature").http(0).build();
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");
        // needed to ensure we undo what the other test does to the jvm else ci fails
        System.setProperty("karate.server.ssl", "");
        System.setProperty("karate.server.proxy", "");
    }

    @Test
    void testClient() {
        Results result = Runner.path("classpath:com/intuit/karate/fatjar/client.feature")
                .configDir("classpath:com/intuit/karate/fatjar")
                .parallel(1);
        assertEquals(result.getFailCount(), 0, result.getErrorMessages());
    }

    @AfterAll
    static void afterClass() {
        server.stop();
    }

}
