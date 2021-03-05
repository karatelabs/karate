package demo;

import com.intuit.karate.http.HttpServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerRunner {

    @Test
    void testServer() {
        HttpServer.root("src/test/java/demo")
                .port(8080)
                .corsEnabled(true)
                .build().waitSync();
    }

}
