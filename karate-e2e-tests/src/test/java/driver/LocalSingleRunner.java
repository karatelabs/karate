package driver;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpServer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class LocalSingleRunner {
    
    static HttpServer server;
    
    @BeforeAll
    static void beforeAll() {
        server = ServerStarter.start(0);        
    }
    
    void run(String id) {
        Results results = Runner.path("src/test/java/driver/" + id + ".feature")
                .karateEnv("single")
                .systemProperty("server.port", server.getPort() + "")
                .systemProperty("driver.type", "chrome")
                .configDir("src/test/java/driver").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());        
    }
    
    @Test
    void testSingle() {
        run("12");
    }    
    
}
