package demo.websocket;

import com.intuit.karate.netty.WebSocketClient;
import com.intuit.karate.netty.WebSocketOptions;
import demo.TestBase;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketClientRunner {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientRunner.class);

    private WebSocketClient client;
    private String result;

    @BeforeClass
    public static void beforeClass() throws Exception {
        TestBase.beforeClass();
    }

    @Test
    public void testWebSocketClient() throws Exception {
        String port = System.getProperty("demo.server.port");
        WebSocketOptions options = new WebSocketOptions("ws://localhost:" + port + "/websocket");
        options.setTextConsumer(text -> {
            logger.debug("websocket listener text: {}", text);
            synchronized (this) {
                result = text;
                notify();
            }
        });
        client = new WebSocketClient(options);
        client.send("Billie");
        synchronized (this) {
            wait();
        }
        assertEquals("hello Billie !", result);
    }

}
