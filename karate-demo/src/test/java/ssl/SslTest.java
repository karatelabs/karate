package ssl;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:ssl")
public class SslTest {
    
    private static ConfigurableApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        context = TestService.start();
        int port = TestService.getPort(context);
        // skip callSingle, note that the karate-config.js copied from demo may be present
        System.setProperty("karate.env", "mock");
        System.setProperty("jersey.ssl.port", port + "");        
    }

    @AfterClass
    public static void afterClass() {
        TestService.stop(context);
    }
    
}
