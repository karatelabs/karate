package driver.core;

import com.intuit.karate.core.MockServer;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MockRunner {

    @Test
    public void testStart() {
        MockServer server = MockServer.feature("classpath:driver/core/_mock.feature").http(8080).build();
        server.waitSync();
    }

}
