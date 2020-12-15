package com.intuit.karate.http;

import com.intuit.karate.Logger;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class WebSocketClientRunner {

    static final Logger logger = new Logger();
    String result;

    @Test
    void testWebSockets() throws Exception {
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
