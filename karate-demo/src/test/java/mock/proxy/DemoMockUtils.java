package mock.proxy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;

/**
 *
 * @author pthomas3
 */
public class DemoMockUtils {
    
    private DemoMockUtils() {
        // only static methods
    }
    
    public static void generateReport(String basePath) {
        Collection<File> jsonFiles = org.apache.commons.io.FileUtils.listFiles(new File(basePath), new String[]{"json"}, true);
        List<String> jsonPaths = new ArrayList(jsonFiles.size());
        jsonFiles.forEach(file -> jsonPaths.add(file.getAbsolutePath()));
        Configuration config = new Configuration(new File(basePath), basePath);
        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();
    }    
    
}
