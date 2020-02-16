package com.intuit.karate.netty;

import com.intuit.karate.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class WebSocketClientRunner {

    private static final Logger logger = new Logger();
    private String result;

    @Test
    public void testWebSockets() throws Exception {
        String url = "ws://echo.websocket.org";
        WebSocketOptions options = new WebSocketOptions(url);
        options.setTextConsumer(text -> {
            synchronized (this) {
                result = text;
                notify();
            }
        });
        WebSocketClient client = new WebSocketClient(options, logger);
        client.send("hello world !");
        synchronized (this) {
            wait();
        }
        assertEquals("hello world !", result);
    }

}
