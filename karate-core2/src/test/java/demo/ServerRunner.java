package demo;

import com.intuit.karate.server.Config;
import com.intuit.karate.server.HttpServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {

    @Test
    void testServer() throws Exception {
        HttpServer server = new HttpServer(8080, 
                new Config().fileSystemRoot("src/test/java/demo")
                        .stripContextPathFromRequest(true));
        server.waitSync();
    }

}
