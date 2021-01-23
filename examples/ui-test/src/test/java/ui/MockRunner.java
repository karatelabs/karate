package ui;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.ServerConfig;
import org.junit.jupiter.api.Test;

/**
 * run this as a junit test to start an http server at port 8080 the html page
 * can be viewed at http://localhost:8080/page-01 kill / stop this process when
 * done
 */
class MockRunner {

    @Test
    public void testStart() {
        start(8080).waitSync();
    }

    public static HttpServer start(int port) {
        ServerConfig config = new ServerConfig("src/test/java/ui/html")
                .autoCreateSession(true);
        return HttpServer.config(config).port(port).build();
    }

}
