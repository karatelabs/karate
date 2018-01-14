package ssl;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:ssl/ssl.feature")
public class SslTest {
    
    private static ConfigurableApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        context = TestService.start();
    }
    
    @Test
    public void testSsl() {
        int port = TestService.getPort(context);
        // skip callSingle, note that the karate-config.js copied from demo may be present
        System.setProperty("karate.env", "mock");
        System.setProperty("jersey.ssl.port", port + "");
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, "target/ssl");
        assertTrue("there are scenario failures", stats.getFailCount() == 0);        
    }

    @AfterClass
    public static void afterClass() {
        TestService.stop(context);
    }    

    
}
