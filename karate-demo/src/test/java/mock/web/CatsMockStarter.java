package mock.web;

import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class CatsMockStarter {

    @Test
    void testStart() {
        MockServer server = MockServer.feature("classpath:mock/web/cats-mock.feature").http(8080).build();
        server.waitSync();
    }

}
