package mock.micro;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 *
 * @author pthomas3
 */
class CatsMockRunner {

    static MockServer server;

    @BeforeAll
    static void beforeAll() {
        server = MockServer
                .feature("classpath:mock/micro/cats-mock.feature")
                .arg("demoServerPort", null)
                .http(0).build();
    }

    @Test
    void testMock() {
        Results results = Runner.path("classpath:mock/micro/cats.feature")
                .karateEnv("mock")
                .systemProperty("mock.cats.url", "http://localhost:" + server.getPort() + "/cats")
                .parallel(1);
        assertTrue( results.getFailCount() == 0, results.getErrorMessages());
    }

}
