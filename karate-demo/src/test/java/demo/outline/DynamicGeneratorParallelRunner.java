package demo.outline;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import demo.DemoTestParallel;
import demo.TestBase;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DynamicGeneratorParallelRunner {

    @BeforeClass
    public static void beforeClass() throws Exception {
        TestBase.beforeClass();
    }

    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:demo/outline/dynamic-generator.feature")
                .outputCucumberJson(true)
                .reportDir("target/dynamic-generator").parallel(5);
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}
