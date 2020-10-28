package driver.demo;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import test.ServerStart;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/demo/demo-06.feature")
public class Demo06Runner {

    private static ServerStart server;

    public static int startServer() throws Exception {
        if (server == null) { // keep spring boot side alive for all tests including package 'mock'
            server = new ServerStart();
            server.start(new String[]{"--server.port=0"}, false);
        }
        System.setProperty("demo.server.port", server.getPort() + "");
        return server.getPort();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        System.setProperty("karate.env", "mock");
        startServer();
    }

}
