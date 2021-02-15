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
class DockerRunner {
    
    static HttpServer server;
    
    @BeforeAll
    static void beforeAll() {
        server = ServerStarter.start(0);
    }
    
    @Test
    void testMock() {
        Results results = Runner.path("src/test/java/driver/00.feature")
                .systemProperty("server.port", server.getPort() + "")
                .karateEnv("docker")
                .configDir("src/test/java/driver").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }    
    
}
