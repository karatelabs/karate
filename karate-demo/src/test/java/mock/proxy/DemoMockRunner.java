package mock.proxy;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class DemoMockRunner {

    static MockServer server;

    @BeforeAll
    static void beforeAll() {
        server = MockServer.feature("classpath:mock/proxy/demo-mock.feature").http(0).build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:demo/cats", "classpath:demo/greeting")
                .configDir("classpath:mock/proxy")
                .systemProperty("demo.server.port", server.getPort() + "")
                .systemProperty("demo.server.https", "false")
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}
