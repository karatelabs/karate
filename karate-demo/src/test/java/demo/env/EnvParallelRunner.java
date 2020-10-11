package demo.env;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import demo.DemoTestParallel;
import demo.TestBase;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;


@KarateOptions(features = "classpath:demo/env")
public class EnvParallelRunner {

    @BeforeClass
    public static void beforeClass() throws Exception {
        TestBase.beforeClass();
    }

    @Test
    public void testParallelWithDemoEnv() {
        Results results = Runner.forClass(getClass()).env("demo").tags("demo").parallel(5);
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }


    @Test
    public void testParallelWithDevEnv() {
        Results results = Runner.forClass(getClass()).env("devunit").tags("devunit").parallel(5);
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

    @Test
    public void testParallelWithDevEnvSystemVariable() {
        System.setProperty("karate.env", "devunit"); // ensure reset if other tests (e.g. mock) had set env in CI
        Results results = Runner.forClass(getClass()).tags("devunit").parallel(5);
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

    @Test
    public void testFailureParallelWithDevEnvironment() {
        Results results = Runner.forClass(getClass()).env("devunit").tags("demo").parallel(5);
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 1);
    }

}
