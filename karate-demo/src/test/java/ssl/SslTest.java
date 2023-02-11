package ssl;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class SslTest {
    
    static ConfigurableApplicationContext context;
    
    @BeforeAll
    static void beforeAll() {
        context = TestService.start();      
    }
    
    @Test
    void testParallel() {
        int port = TestService.getPort(context);
        Results results = Runner.path("classpath:ssl")
                .karateEnv("mock") // skip callSingle, note that the karate-config.js copied from demo may be present
                .systemProperty("jersey.ssl.port", port + "")
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }    

    @AfterAll
    static void afterAll() {
        TestService.stop(context);
    }
    
}
