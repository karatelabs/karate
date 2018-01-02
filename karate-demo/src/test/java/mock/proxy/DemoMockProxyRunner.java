package mock.proxy;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.netty.FeatureServer;
import cucumber.api.CucumberOptions;
import demo.TestBase;
import java.io.File;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = "~@ignore", features = {
    "classpath:demo/cats", 
    "classpath:demo/greeting"})
public class DemoMockProxyRunner {

    private static FeatureServer server;
    private static int demoServerPort;

    @BeforeClass
    public static void beforeClass() throws Exception {
        demoServerPort = TestBase.beforeClass();
        Map map = Match.init().def("demoServerPort", null).allAsMap(); // don't rewrite url
        File file = FileUtils.getFileRelativeTo(DemoMockProxyRunner.class, "demo-mock-proceed.feature");
        server = FeatureServer.start(file, 0, false, map);
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
        TestBase.afterClass();
    }     

    @Test
    public void testParallel() {
        System.setProperty("karate.env", "proxy");
        System.setProperty("demo.server.port", demoServerPort + "");
        System.setProperty("demo.proxy.port", server.getPort() + "");         
        String karateOutputPath = "target/mock-proxy";
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, karateOutputPath);
        // DemoMockUtils.generateReport(karateOutputPath);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }

}
