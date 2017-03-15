package demo;

import com.intuit.karate.cucumber.CucumberRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import test.ServerStart;

/**
 *
 * @author pthomas3
 */
public class TestParallel {
    
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
        CucumberRunner.parallel(getClass(), 5);
    }
    
}
