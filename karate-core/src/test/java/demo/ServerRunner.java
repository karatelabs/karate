package demo;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.ServerConfig;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {

    @Test
    void testServer() {
        ServerConfig config = new ServerConfig("src/test/java/demo")
                .useGlobalSession(true)
                .autoCreateSession(true);
        HttpServer.config(config)
                .http(8080)
                .corsEnabled(true)
                .build().waitSync();
    }

}
