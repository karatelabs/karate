package com.intuit.karate.junit4.demos;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.KarateOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@KarateOptions(features = {
    "classpath:com/intuit/karate/junit4/demos/fail-skipped.feature",
    "classpath:com/intuit/karate/junit4/demos/fail.feature"})
public class FailSkippedParallelRunner {

    @Test
    public void testParallel() {
        String karateOutputPath = "target/surefire-reports";
        Results results = Runner.parallel(getClass(), 1, karateOutputPath);
        generateReport(karateOutputPath);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }

    private static void generateReport(String karateOutputPath) {
        Collection<File> jsonFiles = FileUtils.listFiles(new File(karateOutputPath), new String[]{"json"}, true);
        List<String> jsonPaths = new ArrayList(jsonFiles.size());
        for (File file : jsonFiles) {
            jsonPaths.add(file.getAbsolutePath());
        }
        Configuration config = new Configuration(new File("target"), "demo");
        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();
    }

}
