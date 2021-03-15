package mock.micro;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CatsMockRunner {

    static MockServer server;

    @BeforeClass
    public static void beforeClass() {
        server = MockServer
                .feature("classpath:mock/micro/cats-mock.feature")
                .arg("demoServerPort", null)
                .http(0).build();
    }

    @Test
    public void testMock() {
        Results results = Runner.path("classpath:mock/micro/cats.feature")
                .karateEnv("mock")
                .systemProperty("mock.cats.url", "http://localhost:" + server.getPort() + "/cats")
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}
