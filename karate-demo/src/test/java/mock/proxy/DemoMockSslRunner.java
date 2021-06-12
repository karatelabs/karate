package mock.proxy;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.core.MockServer;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DemoMockSslRunner {

    static MockServer server;

    @BeforeClass
    public static void beforeClass() {
        server = MockServer.feature("classpath:mock/proxy/demo-mock.feature").https(0).build();
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

    // @Test TODO investigate CI troubles
    public void testParallel() {
        Results results = Runner.path("classpath:demo/cats", "classpath:demo/greeting")
                .configDir("classpath:mock/proxy")
                .systemProperty("demo.server.port", server.getPort() + "")
                .systemProperty("demo.server.https", "true")
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}
