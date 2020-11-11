package com.intuit.karate;

import com.intuit.karate.runtime.MockServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class FeatureServerTest {

    private static MockServer server;

    @BeforeClass
    public static void beforeClass() {
        server = MockServer.feature("classpath:com/intuit/karate/server.feature").http(0).corsEnabled().build();        
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");
        // needed to ensure we undo what the other test does to the jvm else ci fails
        System.setProperty("karate.server.ssl", "");
        System.setProperty("karate.server.proxy", "");
    }

    @Test
    public void testClient() {
        Results result = Runner.path("classpath:com/intuit/karate/client.feature").parallel(1);
        assertEquals(result.getErrorMessages(), result.getFailCount(), 0);
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

}
