package driver;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.RequestHandler;
import com.intuit.karate.http.ServerConfig;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {
    
    @Test
    void testServer() {
        ServerConfig config = new ServerConfig()
                .fileSystemRoot("src/test/java/driver/html")
                .useGlobalSession(true)
                .homePagePath("00");
        RequestHandler handler = new RequestHandler(config);
        HttpServer server = new HttpServer(8080, handler);
        server.waitSync();
    }    
    
}
