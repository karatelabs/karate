package demo;

import org.junit.jupiter.api.BeforeAll;
import test.ServerStart;

/**
 *
 * @author pthomas3
 */
public abstract class TestBase {

    static ServerStart server;

    public static int startServer() {
        if (server == null) { // keep spring boot side alive for all tests including package 'mock'
            server = new ServerStart();
            try {
                server.start(new String[]{"--server.port=0"}, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        System.setProperty("demo.server.port", server.getPort() + "");
        return server.getPort();
    }

    @BeforeAll
    public static void beforeAll() {
        startServer();
    }

}
