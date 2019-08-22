package driver.demo;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import demo.DemoTestParallel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@KarateOptions(features = "classpath:driver/demo/demo-03.feature")
public class Demo03ParallelRunner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

    @Test
    public void testParallel() {
        Results results = Runner.parallel(getClass(), 5, "target/driver-demo");
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}