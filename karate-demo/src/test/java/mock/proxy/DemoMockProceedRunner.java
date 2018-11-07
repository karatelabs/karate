package mock.proxy;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.KarateOptions;
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
@KarateOptions(tags = "~@ignore", features = {
    "classpath:demo/cats", 
    "classpath:demo/greeting"})
public class DemoMockProceedRunner {

    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() throws Exception {
        int port = TestBase.startServer();
        Map map = new Match().def("demoServerPort", port).allAsMap();
        File file = FileUtils.getFileRelativeTo(DemoMockProceedRunner.class, "demo-mock-proceed.feature");
        server = FeatureServer.start(file, 0, false, map);
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
    }     

    @Test
    public void testParallel() {
        int port = server.getPort();
        System.setProperty("karate.env", "mock");
        System.setProperty("demo.server.port", port + "");
        System.setProperty("demo.server.https", "false");        
        String karateOutputPath = "target/mock-proceed";
        Results results = Runner.parallel(getClass(), 1, karateOutputPath);
        // DemoMockUtils.generateReport(karateOutputPath);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }

}
