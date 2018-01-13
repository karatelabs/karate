package mock.proxy;

import com.intuit.karate.FileUtils;
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.netty.FeatureServer;
import cucumber.api.CucumberOptions;
import java.io.File;
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
public class DemoMockSslTest {

    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() throws Exception {
        File srcDir = new File("../karate-demo/src/test/java");
        File destDir = new File("target/test-classes");
        org.apache.commons.io.FileUtils.copyDirectory(srcDir, destDir, f -> true, false);        
        File file = FileUtils.getFileRelativeTo(DemoMockSslTest.class, "demo-mock.feature");
        server = FeatureServer.start(file, 0, true, null);
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
        System.setProperty("demo.server.https", "true");
        String karateOutputPath = "target/mock-ssl";
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, karateOutputPath);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }

}
