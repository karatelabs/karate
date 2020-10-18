package demo;

import com.intuit.karate.server.ServerConfig;
import com.intuit.karate.server.HttpServer;
import com.intuit.karate.server.RequestHandler;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {

    @Test
    void testServer() throws Exception {
        ServerConfig config = new ServerConfig().fileSystemRoot("src/test/java/demo")
                .stripContextPathFromRequest(true);
        RequestHandler handler = new RequestHandler(config);
        HttpServer server = new HttpServer(8080, handler);
        server.waitSync();
    }

}
