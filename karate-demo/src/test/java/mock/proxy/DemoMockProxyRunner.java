package mock.proxy;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.core.MockServer;
import demo.TestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class DemoMockProxyRunner {

    static MockServer server;
    static int demoServerPort;

    @BeforeAll
    static void beforeAll() throws Exception {
        demoServerPort = TestBase.startServer();
        server = MockServer
                .feature("classpath:mock/proxy/demo-mock-proceed.feature")
                .arg("demoServerPort", null) // don't rewrite url
                .http(0).build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    // @Test // TODO armeria upgrade
    void testParallel() {
        Results results = Runner.path("classpath:demo/cats", "classpath:demo/greeting")
                .configDir("classpath:mock/proxy")
                .systemProperty("demo.server.port", demoServerPort + "")
                .systemProperty("demo.proxy.port", server.getPort() + "")
                .systemProperty("demo.server.https", "false")
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}
