package driver;

import com.intuit.karate.http.HttpServer;
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
        ServerConfig config = new ServerConfig("src/test/java/driver/html")
                .autoCreateSession(true)
                .homePagePath("00");
        return HttpServer.config(config).port(port).build();
    }

}
