package demo;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import test.ServerStart;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class DemoTestParallel {
    
    private static ServerStart server;
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new ServerStart();
        server.start(new String[]{"--server.port=0"}, false);
        System.setProperty("karate.server.port", server.getPort() + "");
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
    }    
    
    @Test
    public void testParallel() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 5);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }
    
}
