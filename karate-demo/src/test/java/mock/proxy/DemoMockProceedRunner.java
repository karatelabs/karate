package mock.proxy;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.core.MockServer;
import demo.TestBase;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DemoMockProceedRunner {

    static MockServer server;

    @BeforeClass
    public static void beforeClass() throws Exception {
        int port = TestBase.startServer();
        server = MockServer
                .feature("classpath:mock/proxy/demo-mock-proceed.feature")
                .arg("demoServerPort", port)
                .http(0).build();
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:demo/cats", "classpath:demo/greeting")
                .configDir("classpath:mock/proxy")
                .systemProperty("demo.server.port", server.getPort() + "")
                .systemProperty("demo.server.https", "false")
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}
