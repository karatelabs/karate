package mock;

import com.intuit.karate.FileUtils;
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.netty.FeatureServer;
import cucumber.api.CucumberOptions;
import demo.TestBase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
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
public class DemoMockProceedRunner {

    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() throws Exception {
        int port = TestBase.beforeClass();
        Map<String, Object> map = Collections.singletonMap("demoServerPort", port);
        File file = FileUtils.getFileRelativeTo(DemoMockProceedRunner.class, "demo-mock-proceed.feature");
        server = FeatureServer.start(file, 0, false, map);
    }
    
    @AfterClass
    public static void afterClass() {
        TestBase.afterClass();
    }     

    @Test
    public void testParallel() {
        int port = server.getPort();
        System.setProperty("karate.env", "mock");
        System.setProperty("demo.server.port", port + "");        
        String karateOutputPath = "target/mock-proceed/surefire-reports";
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, karateOutputPath);
        generateReport(karateOutputPath);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }

    private static void generateReport(String karateOutputPath) {
        Collection<File> jsonFiles = org.apache.commons.io.FileUtils.listFiles(new File(karateOutputPath), new String[]{"json"}, true);
        List<String> jsonPaths = new ArrayList(jsonFiles.size());
        jsonFiles.forEach(file -> jsonPaths.add(file.getAbsolutePath()));
        Configuration config = new Configuration(new File("target/mock-proceed"), "mock-proceed");
        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();
    }

}
