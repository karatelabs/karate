package perf;

import com.intuit.karate.core.MockServer;

/**
 *
 * @author pthomas3
 */
public class TestUtils {
    
    public static void startServer() {
        MockServer server = MockServer.feature("classpath:perf/mock.feature").build();
        System.setProperty("mock.server.url", "http://localhost:" + server.getPort());
    }
    
}
