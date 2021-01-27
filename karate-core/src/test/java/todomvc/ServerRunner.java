package todomvc;

import com.intuit.karate.http.HttpServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {

    @Test
    void testServer() {
        HttpServer.configRoot("src/test/java/todomvc").port(8080).build().waitSync();
    }

}
