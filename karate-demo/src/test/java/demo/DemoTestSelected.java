package demo;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * an alternative way to run selected paths, tags and even features and you can
 * dynamically determine the features that need to be executed
 *
 * @author pthomas3
 */
class DemoTestSelected {
    
    @BeforeAll
    static void beforeAll() {
        TestBase.beforeAll();
    }     

    @Test
    void testSelected() {
        List<String> tags = Arrays.asList("~@skipme");
        List<String> features = Arrays.asList("classpath:demo/cats");
        String karateOutputPath = "target/surefire-reports";
        Results results = Runner.path(features)
                .tags(tags)
                .outputCucumberJson(true)
                .reportDir(karateOutputPath).parallel(5);
        DemoTestParallel.generateReport(karateOutputPath);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}
