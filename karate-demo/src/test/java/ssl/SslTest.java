package ssl;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class SslTest {
    
    static ConfigurableApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        context = TestService.start();      
    }
    
    @Test
    public void testParallel() {
        int port = TestService.getPort(context);
        Results results = Runner.path("classpath:ssl")
                .karateEnv("mock") // skip callSingle, note that the karate-config.js copied from demo may be present
                .systemProperty("jersey.ssl.port", port + "")
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }    

    @AfterClass
    public static void afterClass() {
        TestService.stop(context);
    }
    
}
