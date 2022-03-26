package demo.report;


import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import demo.TestBase;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CucumberMaskReportTest extends TestBase {

    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:demo/report/maskCucumberReport.feature")
                .tags("~@ignore")
                .outputCucumberJson(true)
                .parallel(5);
        generateReport(results.getReportDir());
        //Assert.assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

    // To generate cucumber HTML report
    public static void generateReport(String karateOutputPath) {
        Collection<File> jsonFiles = FileUtils.listFiles(new File(karateOutputPath), new String[] {"json"}, true);
        List<String> jsonPaths = new ArrayList<>(jsonFiles.size());
        jsonFiles.forEach(file -> jsonPaths.add(file.getAbsolutePath()));
        Configuration config = new Configuration(new File("target"), "sample");
        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();
    }
}
