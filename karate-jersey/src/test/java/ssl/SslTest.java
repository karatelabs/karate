package ssl;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.KarateOptions;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@KarateOptions(features = "classpath:ssl")
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
        Results results = Runner.parallel(getClass(), 1, "target/ssl");
        assertTrue("there are scenario failures", results.getFailCount() == 0);        
    }

    @AfterClass
    public static void afterClass() {
        TestService.stop(context);
    }    

    
}
