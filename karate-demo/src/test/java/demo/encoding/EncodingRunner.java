package demo.encoding;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static demo.DemoTestParallel.generateReport;
import demo.TestBase;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class EncodingRunner {
    
    @BeforeClass
    public static void beforeClass() throws Exception {        
        TestBase.beforeClass();
    }

    @Test
    public void testParallel() {
        System.setProperty("karate.env", "demo"); // ensure reset if other tests (e.g. mock) had set env in CI
        Results results = Runner.parallel(getClass(), 5);
        generateReport(results.getReportDir());
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);        
    }    
    
}
