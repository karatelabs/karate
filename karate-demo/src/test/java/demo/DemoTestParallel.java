package demo;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class DemoTestParallel {
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        TestBase.beforeClass();
    }
    
    @AfterClass
    public static void afterClass() {
        TestBase.afterClass();
    }    
    
    @Test
    public void testParallel() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 5, "target/surefire-reports");
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }
    
}
