package demo;

import com.intuit.karate.junit4.Karate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import test.ServerStart;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
public abstract class TestBase {
    
    private static ServerStart server;
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new ServerStart();
        server.start(new String[]{"--server.port=0"}, false);
        System.setProperty("karate.server.port", server.getPort() + "");
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
    }
    
}
