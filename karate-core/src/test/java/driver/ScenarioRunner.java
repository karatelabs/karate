package driver;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.RequestHandler;
import com.intuit.karate.http.ServerConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ScenarioRunner {
    
    static HttpServer server;
    
    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .fileSystemRoot("src/test/java/driver/html")
                .homePagePath("00");
        RequestHandler handler = new RequestHandler(config);
        server = new HttpServer(8080, handler);        
    }
    
    @Test
    void testMock() {
        Results results = Runner.path("src/test/java/driver/00.feature")
                .configDir("src/test/java/driver").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }    
    
}
