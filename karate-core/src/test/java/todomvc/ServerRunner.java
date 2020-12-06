package todomvc;

import com.intuit.karate.http.ServerConfig;
import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.RequestHandler;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {

    @Test
    void testServer() {
        ServerConfig config = new ServerConfig().fileSystemRoot("src/test/java/todomvc");
        RequestHandler handler = new RequestHandler(config);
        HttpServer server = new HttpServer(8080, handler);
        server.waitSync();
    }

}
