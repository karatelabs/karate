package com.intuit.karate.mock;

import com.intuit.karate.netty.*;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.KarateOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@KarateOptions(tags = "~@ignore")
public class MockServerTest {

    private static FeatureServer server;
    
    public static final byte[] testBytes = new byte[]{15, 98, -45, 0, 0, 7, -124, 75, 12, 26, 0, 9};

    @BeforeClass
    public static void beforeClass() {
        File file = FileUtils.getFileRelativeTo(MockServerTest.class, "_mock.feature");
        server = FeatureServer.start(file, 0, false, null);
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");
    }

    @Test
    public void testServer() {
        // will run all features in 'this' package
        String karateOutputPath = "target/surefire-reports";
        Results results = Runner.parallel(getClass(), 1, karateOutputPath);
        generateReport(karateOutputPath);
        assertTrue("there are scenario failures", results.getFailCount() == 0);        
    }
    
    private static void generateReport(String karateOutputPath) {
        Collection<File> jsonFiles = org.apache.commons.io.FileUtils.listFiles(new File(karateOutputPath), new String[]{"json"}, true);
        List<String> jsonPaths = new ArrayList(jsonFiles.size());
        for (File file : jsonFiles) {
            jsonPaths.add(file.getAbsolutePath());
        }
        Configuration config = new Configuration(new File("target"), "mock");
        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();
    }    

}
