package mock.proxy;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.KarateOptions;
import com.intuit.karate.runtime.MockServer;
import demo.TestBase;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@KarateOptions(tags = "~@ignore", features = {
    "classpath:demo/cats",
    "classpath:demo/greeting"})
public class DemoMockProxySslRunner {

    private static MockServer server;
    private static int demoServerPort;

    @BeforeClass
    public static void beforeClass() throws Exception {
        demoServerPort = TestBase.startServer();
        server = MockServer
                .feature("classpath:mock/proxy/demo-mock-proceed.feature")
                .arg("demoServerPort", null) // don't rewrite url
                .https(0).build();
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

    // @Test TODO SSL proxy
    public void testParallel() {
        System.setProperty("karate.env", "proxy");
        System.setProperty("demo.server.port", demoServerPort + "");
        System.setProperty("demo.proxy.port", server.getPort() + "");
        System.setProperty("demo.server.https", "true");
        String karateOutputPath = "target/mock-proxy-ssl";
        Results results = Runner.parallel(getClass(), 1, karateOutputPath);
        // DemoMockUtils.generateReport(karateOutputPath);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }

}
