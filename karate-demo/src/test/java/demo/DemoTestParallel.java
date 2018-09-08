package demo;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"}) // important: do not use @RunWith(Karate.class) !
public class DemoTestParallel {
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        TestBase.beforeClass();
    } 
    
    @Test
    public void testParallel() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 5);
        generateReport(stats.getReportDir());
        assertTrue("there are scenario failures", stats.getFailCount() == 0);        
    }
    
    public static void generateReport(String karateOutputPath) {        
        Collection<File> jsonFiles = FileUtils.listFiles(new File(karateOutputPath), new String[] {"json"}, true);
        List<String> jsonPaths = new ArrayList(jsonFiles.size());
        jsonFiles.forEach(file -> jsonPaths.add(file.getAbsolutePath()));
        Configuration config = new Configuration(new File("target"), "demo");
        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();        
    }
    
}
