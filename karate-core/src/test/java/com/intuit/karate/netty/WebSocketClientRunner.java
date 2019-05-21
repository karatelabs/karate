package com.intuit.karate.netty;

import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketClientRunner {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientRunner.class);
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
        WebSocketClient client = new WebSocketClient(options);
        client.send("hello world !");
        synchronized (this) {
            wait();
        }
        assertEquals("hello world !", result);
    }

}
