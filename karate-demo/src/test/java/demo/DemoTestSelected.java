package demo;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * an alternative way to run selected paths, tags and even features using the
 * java api here you don't need to use the CucumberOptions and you can
 * dynamically determine the features that need to be executed
 *
 * @author pthomas3
 */
public class DemoTestSelected {
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        TestBase.beforeClass();
    }     

    @Test
    public void testSelected() {
        List<String> tags = Arrays.asList("~@ignore");
        List<String> features = Arrays.asList("classpath:demo/cats");
        String karateOutputPath = "target/surefire-reports";
        Results results = Runner.parallel(tags, features, 5, karateOutputPath);
        DemoTestParallel.generateReport(karateOutputPath);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}
