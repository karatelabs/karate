package driver;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.RequestHandler;
import com.intuit.karate.http.ServerConfig;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class ServerStarter {

    @Test
    void testServer() {
        HttpServer server = start(8080);
        server.waitSync();
    }

    public static HttpServer start(int port) {
        ServerConfig config = new ServerConfig()
                .fileSystemRoot("src/test/java/driver/html")
                .useGlobalSession(true)
                .homePagePath("00");
        RequestHandler handler = new RequestHandler(config);
        return new HttpServer(port, handler);
    }

}
